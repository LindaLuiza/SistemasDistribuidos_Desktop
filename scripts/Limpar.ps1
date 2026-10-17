# Apaga os arquivos dos servidores de dados (dados\) e os logs dos clientes
# (logs\), para recomeçar uma execução do zero. Apaga os dois juntos de
# propósito: a validação compara logs com arquivos de dados, então eles
# precisam corresponder à mesma execução.
# Uso (com todos os processos parados): .\scripts\Limpar.ps1

$raiz = Split-Path $PSScriptRoot -Parent
$arquivos = @(
    Get-ChildItem -Path (Join-Path $raiz "dados") -Filter "dados*.txt" -File -ErrorAction SilentlyContinue
    Get-ChildItem -Path (Join-Path $raiz "logs") -Filter "log_cliente*.txt" -File -ErrorAction SilentlyContinue
)

if (-not $arquivos) {
    Write-Host "Nada para limpar."
    return
}

foreach ($a in $arquivos) {
    Remove-Item $a.FullName -Confirm:$false
    Write-Host "Removido: $($a.Directory.Name)\$($a.Name)"
}
