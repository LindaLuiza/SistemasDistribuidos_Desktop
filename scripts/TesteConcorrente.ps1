# Dispara requisições SIMULTÂNEAS ao Balanceador ou direto a um Servidor de Dados ou de Aplicação.
#   -Alvo Dados: confere que nenhum número foi inserido duas vezes, que as
#                linhas são coerentes e que o arquivo em disco não tem duplicatas.
#   -Alvo App / Balanceador: confere que todas as requisições foram confirmadas (RECEBIDO);
#                as frases de resultado aparecem no terminal do servidor de aplicação.
# Uso:
#   .\scripts\TesteConcorrente.ps1                                  # 50 requisições, números 1-20, Dados 1
#   .\scripts\TesteConcorrente.ps1 -Servidor 2 -Quantidade 100 -Maximo 30
#   .\scripts\TesteConcorrente.ps1 -Alvo App -Servidor 1
#   .\scripts\TesteConcorrente.ps1 -Alvo Balanceador -Quantidade 60

param(
    [ValidateSet("Dados", "App", "Balanceador")]
    [string]$Alvo = "Dados",
    [int]$Servidor = 1,
    [int]$Quantidade = 50,
    [int]$Maximo = 20
)

Set-Location (Split-Path $PSScriptRoot -Parent)
# Saída do Java em UTF-8, para os acentos aparecerem corretamente no console
[Console]::OutputEncoding = [Text.Encoding]::UTF8
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out teste.TesteConcorrente $Alvo $Servidor $Quantidade $Maximo

if ($Alvo -ne "Dados") { return }

$arquivo = "dados\dados$Servidor.txt"
if (Test-Path $arquivo) {
    $linhas = Get-Content $arquivo
    $duplicatas = $linhas | Group-Object | Where-Object Count -gt 1
    Write-Host ""
    Write-Host "Arquivo ${arquivo}: $($linhas.Count) linhas"
    if ($duplicatas) {
        Write-Host "FALHOU: duplicatas no arquivo: $($duplicatas.Name -join ', ')" -ForegroundColor Red
    } else {
        Write-Host "OK: arquivo sem duplicatas." -ForegroundColor Green
    }
}
