# Sobe o Servidor de Aplicação 3 (porta 6003). Os resultados (frases) aparecem aqui.
# Não compila: rode o Dados1.ps1 (ou o Compilar.ps1) antes.
# Uso: .\scripts\Aplicacao3.ps1      (Ctrl+C para parar)

Set-Location (Split-Path $PSScriptRoot -Parent)
# Saída do Java em UTF-8, para os acentos aparecerem corretamente no console
[Console]::OutputEncoding = [Text.Encoding]::UTF8
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp out aplicacao.ServidorAplicacao 3
