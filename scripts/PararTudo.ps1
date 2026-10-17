# Para o sistema em ordem, sem perder requisições:
#   1) cria o arquivo-sinal out\parar: cada cliente termina a requisição atual e sai sozinho
#      (se fosse morto na hora, um número gravado no log poderia nunca ser enviado);
#   2) espera os workers terminarem o que já confirmaram com RECEBIDO
#      (nenhuma conexão ativa nos servidores por 1 s seguido);
#   3) fecha as janelas abertas pelo SubirTudo.ps1 e qualquer Java do projeto aberto à mão.
# Os clientes param primeiro para não gerarem requisições para servidores já fechados.
# Uso: .\scripts\PararTudo.ps1

$raiz = Split-Path $PSScriptRoot -Parent
$arquivoPids = Join-Path $raiz "out\processos.txt"
$arquivoParar = Join-Path $raiz "out\parar"

function ProcessosJava($classe) {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like "*$classe*" }
}

# 1) Clientes saem sozinhos pelo arquivo-sinal (no máximo 20 s)
if (ProcessosJava 'cliente.Cliente') {
    New-Item -ItemType File -Path $arquivoParar -Force | Out-Null
    Write-Host "Pedindo aos clientes para pararem..."
    $limite = (Get-Date).AddSeconds(20)
    while ((ProcessosJava 'cliente.Cliente') -and (Get-Date) -lt $limite) { Start-Sleep -Milliseconds 200 }
    if (ProcessosJava 'cliente.Cliente') { Write-Host "Clientes não pararam a tempo; serão encerrados à força." -ForegroundColor Yellow }

    # 2) Espera os workers esvaziarem (no máximo 30 s)
    Write-Host "Esperando os servidores terminarem as requisições em andamento..."
    $portas = 5000, 6001, 6002, 6003, 7001, 7002
    $limite = (Get-Date).AddSeconds(30)
    $quietoDesde = $null
    while ((Get-Date) -lt $limite) {
        # API do .NET: instantânea (o Get-NetTCPConnection leva ~3 s por chamada)
        $ativas = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpConnections() |
            Where-Object { $_.State -eq 'Established' -and $portas -contains $_.LocalEndPoint.Port }
        if ($ativas) { $quietoDesde = $null }
        elseif (-not $quietoDesde) { $quietoDesde = Get-Date }
        elseif (((Get-Date) - $quietoDesde).TotalMilliseconds -ge 1000) { break }
        Start-Sleep -Milliseconds 100
    }
}

# 3) Janelas abertas pelo SubirTudo: um único taskkill para todas (/T fecha a janela e o Java filho)
if (Test-Path $arquivoPids) {
    $vivos = @(Get-Content $arquivoPids | Where-Object { $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue) })
    if ($vivos) {
        $argumentos = @('/T', '/F') + ($vivos | ForEach-Object { '/PID', $_ })
        # Saída descartada: o taskkill reclama de filhos que já terminaram sozinhos (inofensivo)
        taskkill @argumentos 2>&1 | Out-Null
    }
    Remove-Item $arquivoPids
}

# Processos Java do projeto que sobraram (abertos à mão pelos scripts individuais)
ProcessosJava '' | Where-Object { $_.CommandLine -match 'cliente\.Cliente|balanceador\.Balanceador|aplicacao\.ServidorAplicacao|dados\.ServidorDados' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Remove-Item $arquivoParar -ErrorAction SilentlyContinue
Write-Host "Sistema parado." -ForegroundColor Green
