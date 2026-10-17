# Sobe o Cliente 1 (log em logs\log_cliente1.txt).
# Não compila: rode o Dados1.ps1 (ou o Compilar.ps1) antes.
# Uso: .\scripts\Cliente1.ps1      (Ctrl+C para parar: termina a requisição atual e sai)

Set-Location (Split-Path $PSScriptRoot -Parent)
# Saída do Java em UTF-8, para os acentos aparecerem corretamente no console
[Console]::OutputEncoding = [Text.Encoding]::UTF8
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out cliente.Cliente 1
