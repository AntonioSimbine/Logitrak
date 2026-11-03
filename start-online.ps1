<#
    Põe o projeto a funcionar online, a partir do teu computador:

      1. garante que a API está a correr em http://localhost:8080;
      2. abre um túnel HTTPS público (Cloudflare Quick Tunnel) para a API;
      3. escreve o link pronto a usar no site publicado no GitHub Pages.

    Uso:
        .\start-online.ps1

    O link muda sempre que o túnel é reiniciado. Para um serviço permanente
    (24/7, sem depender do teu PC), publica a API no Render — ver docs\DEPLOY.md.

    Requisitos: backend\.env configurado e cloudflared instalado
    (winget install --id Cloudflare.cloudflared).
#>
[CmdletBinding()]
param(
    [string]$Site = 'https://antoniosimbine.github.io/Logitrak/',
    [int]$Porta = 8080
)

$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot

function Porta-Aberta([int]$p) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $p)
        $c.Close()
        return $true
    } catch {
        return $false
    }
}

function Encontrar-Cloudflared {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $caminhos = @(
        'C:\Program Files (x86)\cloudflared\cloudflared.exe',
        'C:\Program Files\cloudflared\cloudflared.exe',
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe"
    )
    foreach ($c in $caminhos) { if (Test-Path -LiteralPath $c) { return $c } }
    return $null
}

# 1) API
if (Porta-Aberta $Porta) {
    Write-Host "API ja esta a responder na porta $Porta." -ForegroundColor DarkGray
} else {
    Write-Host "A arrancar a API numa nova janela..." -ForegroundColor Cyan
    $script = Join-Path $raiz 'backend\run-local.ps1'
    if (-not (Test-Path -LiteralPath $script)) {
        Write-Error "Nao encontrei backend\run-local.ps1. Corre o script a partir da raiz do projeto."
        exit 1
    }
    Start-Process -FilePath 'powershell' `
        -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-NoExit', '-File', $script `
        -WorkingDirectory (Join-Path $raiz 'backend') | Out-Null

    Write-Host "A esperar que a API fique pronta (pode demorar ~40 s)..." -ForegroundColor DarkGray
    $pronta = $false
    for ($i = 0; $i -lt 60; $i++) {
        if (Porta-Aberta $Porta) { $pronta = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $pronta) {
        Write-Error "A API nao arrancou. Verifica a janela do backend e o ficheiro backend\.env."
        exit 1
    }
}

# 2) Túnel
$cloudflared = Encontrar-Cloudflared
if (-not $cloudflared) {
    Write-Error "cloudflared nao encontrado. Instala com: winget install --id Cloudflare.cloudflared"
    exit 1
}

$log = Join-Path $env:TEMP 'logitrack-tunnel.log'
Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue

Write-Host "A abrir o tunel HTTPS..." -ForegroundColor Cyan
Start-Process -FilePath $cloudflared `
    -ArgumentList 'tunnel', '--url', "http://localhost:$Porta", '--no-autoupdate', '--logfile', $log `
    -WindowStyle Hidden | Out-Null

$url = $null
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Path -LiteralPath $log) {
        $texto = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
        if ($texto -and $texto -match 'https://[a-z0-9-]+\.trycloudflare\.com') {
            $url = $matches[0]
            break
        }
    }
}
if (-not $url) {
    Write-Error "Nao consegui obter o URL do tunel. Consulta $log"
    exit 1
}

# 3) Verificação e link final
$apiOk = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "$url/api/v1/health" -TimeoutSec 8 -ErrorAction Stop
        if ($h.estado -eq 'UP') { $apiOk = $true; break }
    } catch { Start-Sleep -Seconds 3 }
}

$link = $Site + '?api=' + $url

Write-Host ""
if ($apiOk) {
    Write-Host "  API publica : $url  (UP)" -ForegroundColor Green
} else {
    Write-Host "  API publica : $url  (ainda sem resposta; pode levar ~30 s)" -ForegroundColor Yellow
}
Write-Host "  Painel web  : $link" -ForegroundColor Green
Write-Host "  App movel   : $link  -> abrir o icone Logitrack (PWA) ou /m/" -ForegroundColor Green
Write-Host ""
Write-Host "  Abre o link uma vez no browser: o endereco da API fica guardado." -ForegroundColor DarkGray
Write-Host "  Para parar: fecha as janelas do backend e o processo cloudflared." -ForegroundColor DarkGray
Write-Host ""

try { Set-Clipboard -Value $link; Write-Host "  (Link copiado para a area de transferencia)" -ForegroundColor DarkGray } catch { }
