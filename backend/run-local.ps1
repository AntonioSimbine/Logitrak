<#
    Arranca o backend Logitrack em ambiente de desenvolvimento.

    Uso:
        cd backend
        .\run-local.ps1

    Requisitos:
        - JDK 17+
        - MySQL 8 a correr em localhost:3306
        - Ficheiro .env (em backend\ ou na raiz) com as variáveis obrigatórias.
          Usa ..\.env.example como modelo.

    O script lê o .env, valida as variáveis obrigatórias e arranca a API em
    http://localhost:8080. O .env está no .gitignore e nunca é versionado.
#>
[CmdletBinding()]
param(
    [switch]$Jar  # arranca o JAR já compilado em vez de mvnw spring-boot:run
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Carregar-Env([string]$caminho) {
    Get-Content -LiteralPath $caminho | ForEach-Object {
        $linha = $_.Trim()
        if (-not $linha -or $linha.StartsWith('#') -or $linha -notmatch '=') { return }
        $partes = $linha.Split('=', 2)
        $nome = $partes[0].Trim()
        $valor = $partes[1].Trim().Trim('"').Trim("'")
        if (-not [Environment]::GetEnvironmentVariable($nome, 'Process')) {
            [Environment]::SetEnvironmentVariable($nome, $valor, 'Process')
        }
    }
}

$envBackend = Join-Path $PSScriptRoot '.env'
$envRaiz = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'

if (Test-Path -LiteralPath $envBackend) {
    Carregar-Env $envBackend
    Write-Host "Configuracao carregada de backend\.env" -ForegroundColor DarkGray
} elseif (Test-Path -LiteralPath $envRaiz) {
    Carregar-Env $envRaiz
    Write-Host "Configuracao carregada de .env (raiz)" -ForegroundColor DarkGray
} else {
    Write-Warning "Ficheiro .env nao encontrado."
    Write-Host "Cria backend\.env a partir de ..\.env.example e preenche:" -ForegroundColor Yellow
    Write-Host "  DB_PASSWORD, JWT_SECRET e LOGITRACK_DEMO_PASSWORD" -ForegroundColor Yellow
    exit 1
}

# Valores por omissao para uso local.
if (-not $env:DB_URL) {
    $env:DB_URL = 'jdbc:mysql://localhost:3306/logitrack?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Maputo&characterEncoding=UTF-8'
}
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'root' }
if (-not $env:LOGITRACK_UPLOAD_DIR) { $env:LOGITRACK_UPLOAD_DIR = 'uploads' }
if (-not $env:CORS_ALLOWED_ORIGINS) {
    $env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000,http://127.0.0.1:3000'
}

$faltam = @()
if (-not $env:DB_PASSWORD) { $faltam += 'DB_PASSWORD' }
if (-not $env:JWT_SECRET) { $faltam += 'JWT_SECRET' }
if ($env:LOGITRACK_DEMO_SEED -ne 'false' -and -not $env:LOGITRACK_DEMO_PASSWORD) {
    $faltam += 'LOGITRACK_DEMO_PASSWORD'
}
if ($faltam.Count -gt 0) {
    Write-Error ("Variaveis obrigatorias em falta: " + ($faltam -join ', ') +
        ". Edita o .env e volta a tentar.")
    exit 1
}
if ($env:JWT_SECRET.Length -lt 32) {
    Write-Error "JWT_SECRET tem de ter pelo menos 32 caracteres."
    exit 1
}

Write-Host ""
Write-Host "  Logitrack API" -ForegroundColor Cyan
Write-Host ("  Base de dados : " + $env:DB_URL.Split('?')[0])
Write-Host ("  CORS          : " + $env:CORS_ALLOWED_ORIGINS)
Write-Host ("  Seed demo     : " + $(if ($env:LOGITRACK_DEMO_SEED -eq 'false') { 'desativado' } else { 'ativo' }))
Write-Host "  URL           : http://localhost:8080/api/v1/health" -ForegroundColor Green
Write-Host ""

if ($Jar) {
    $jar = Join-Path $PSScriptRoot 'target\logitrack-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) {
        Write-Error "JAR nao encontrado. Corre primeiro: .\mvnw.cmd clean package -DskipTests"
        exit 1
    }
    java -jar $jar
} else {
    .\mvnw.cmd spring-boot:run
}
