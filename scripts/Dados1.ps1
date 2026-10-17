# Terminal 1: compila e sobe o Servidor de Dados 1 (porta 7001, arquivo dados\dados1.txt).
# Uso: .\scripts\Dados1.ps1      (Ctrl+C para parar)

& "$PSScriptRoot\Compilar.ps1"
if ($LASTEXITCODE -ne 0) { exit 1 }

Set-Location (Split-Path $PSScriptRoot -Parent)
# Saída do Java em UTF-8, para os acentos aparecerem corretamente no console
[Console]::OutputEncoding = [Text.Encoding]::UTF8
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out dados.ServidorDados 1
