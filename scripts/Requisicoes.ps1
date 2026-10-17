# Envia requisições, uma após a outra, ao Balanceador ou direto a um Servidor de Dados ou de Aplicação.
# Uso:
#   .\scripts\Requisicoes.ps1                              # 10 números aleatórios (1-20) ao Dados 1
#   .\scripts\Requisicoes.ps1 -Numeros 37,50,37            # números específicos
#   .\scripts\Requisicoes.ps1 -Servidor 2 -Quantidade 5 -Maximo 10
#   .\scripts\Requisicoes.ps1 -Alvo App -Servidor 1        # ao App 1 (resultado aparece no terminal dele)
#   .\scripts\Requisicoes.ps1 -Alvo Balanceador -Quantidade 30   # sistema completo, como um cliente

param(
    [ValidateSet("Dados", "App", "Balanceador")]
    [string]$Alvo = "Dados",
    [int]$Servidor = 1,
    [string[]]$Numeros,
    [int]$Quantidade = 10,
    [int]$Maximo = 20
)

$porta = switch ($Alvo) { "App" { 6000 + $Servidor } "Balanceador" { 5000 } default { 7000 + $Servidor } }

function Enviar($n) {
    $c = New-Object Net.Sockets.TcpClient("localhost", $porta)
    try {
        $s = $c.GetStream()
        $w = New-Object IO.StreamWriter($s)
        $r = New-Object IO.StreamReader($s)
        $w.WriteLine("CHECK;$n"); $w.Flush()
        $r.ReadLine()
    } finally {
        $c.Close()
    }
}

if ($Numeros) {
    # Aceita "37,50,37" tanto como array quanto como texto único (ex.: via powershell -File)
    $Numeros = $Numeros -join ',' -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { [int]$_ }
} else {
    $Numeros = 1..$Quantidade | ForEach-Object { Get-Random -Minimum 1 -Maximum ($Maximo + 1) }
}

Write-Host "Enviando para $Alvo$(if ($Alvo -ne 'Balanceador') { " $Servidor" }) (porta $porta)"
foreach ($n in $Numeros) {
    try {
        Write-Host ("CHECK;{0,-4} -> {1}" -f $n, (Enviar $n))
    } catch {
        Write-Host "Não foi possível conectar na porta $porta. O servidor está rodando?" -ForegroundColor Red
        exit 1
    }
}
