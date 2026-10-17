# Compila todo o projeto para a pasta out.
# Uso: .\scripts\Compilar.ps1

Set-Location (Split-Path $PSScriptRoot -Parent)

$fontes = (Get-ChildItem -Recurse src -Filter *.java).FullName
javac -encoding UTF-8 -d out $fontes
if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilado com sucesso." -ForegroundColor Green
} else {
    Write-Host "Erro de compilação." -ForegroundColor Red
    exit 1
}
