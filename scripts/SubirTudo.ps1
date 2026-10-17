# Compila e sobe o sistema inteiro, cada componente na sua própria janela,
# na ordem certa: Dados -> Aplicação -> Balanceador -> Clientes.
# Antes de passar para a próxima camada, espera a porta da camada anterior abrir.
# Uso:
#   .\scripts\SubirTudo.ps1                 # tudo, incluindo os 2 clientes
#   .\scripts\SubirTudo.ps1 -SemClientes    # só os servidores (para testar com TesteConcorrente/Requisicoes)
#   .\scripts\SubirTudo.ps1 -Limpar         # apaga dados\*.txt e logs\*.txt antes de subir
# Para fechar tudo: .\scripts\PararTudo.ps1

param(
    [switch]$SemClientes,
    [switch]$Limpar
)

$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz
$arquivoPids = Join-Path $raiz "out\processos.txt"

# Portas TCP em escuta (API do .NET: instantânea; o Get-NetTCPConnection leva ~3 s por chamada)
function PortasEscutando {
    [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | ForEach-Object { $_.Port }
}

# Não sobe nada se alguma porta já estiver em uso (sistema já rodando ou processo esquecido)
$portas = 7001, 7002, 6001, 6002, 6003, 5000
$escutando = PortasEscutando
$ocupadas = $portas | Where-Object { $escutando -contains $_ }
if ($ocupadas) {
    Write-Host "Portas já em uso: $($ocupadas -join ', '). Rode .\scripts\PararTudo.ps1 primeiro." -ForegroundColor Red
    exit 1
}

if ($Limpar) { & "$PSScriptRoot\Limpar.ps1" }

& "$PSScriptRoot\Compilar.ps1"
if ($LASTEXITCODE -ne 0) { exit 1 }

Set-Content -Path $arquivoPids -Value $null
# Arquivo-sinal de parada que tenha sobrado: com ele, os clientes sairiam na hora
Remove-Item (Join-Path $raiz "out\parar") -ErrorAction SilentlyContinue

# Abre uma janela do PowerShell com título e roda a classe Java nela.
# -NoExit: se o processo Java cair, a janela fica aberta mostrando o erro.
function Abrir($titulo, $classe, $argumento) {
    $comando = @"
`$Host.UI.RawUI.WindowTitle = '$titulo'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location '$raiz'
java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out $classe $argumento
"@
    $codificado = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($comando))
    $p = Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $codificado -PassThru
    Add-Content -Path $arquivoPids -Value $p.Id
    Write-Host "  aberto: $titulo"
}

# Espera até todas as portas estarem escutando (no máximo 15 s)
function Esperar($listaPortas) {
    $limite = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $limite) {
        $escutando = PortasEscutando
        $faltando = $listaPortas | Where-Object { $escutando -notcontains $_ }
        if (-not $faltando) { return }
        Start-Sleep -Milliseconds 300
    }
    Write-Host "Portas não abriram a tempo: $($faltando -join ', '). Veja as janelas abertas." -ForegroundColor Red
    exit 1
}

Write-Host "Servidores de Dados..."
Abrir "Dados 1" "dados.ServidorDados" 1
Abrir "Dados 2" "dados.ServidorDados" 2
Esperar 7001, 7002

Write-Host "Servidores de Aplicação..."
Abrir "Aplicacao 1" "aplicacao.ServidorAplicacao" 1
Abrir "Aplicacao 2" "aplicacao.ServidorAplicacao" 2
Abrir "Aplicacao 3" "aplicacao.ServidorAplicacao" 3
Esperar 6001, 6002, 6003

Write-Host "Balanceador..."
Abrir "Balanceador" "balanceador.Balanceador" ""
Esperar 5000

if (-not $SemClientes) {
    Write-Host "Clientes..."
    Abrir "Cliente 1" "cliente.Cliente" 1
    Abrir "Cliente 2" "cliente.Cliente" 2
}

Write-Host "Sistema no ar. Para fechar tudo: .\scripts\PararTudo.ps1" -ForegroundColor Green
