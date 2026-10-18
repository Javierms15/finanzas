# Utilidades compartidas por iniciar-finanzas.ps1 y detener-finanzas.ps1.
# Solo ASCII a proposito: Windows PowerShell 5.1 lee los .ps1 sin BOM como ANSI y rompe los acentos.

$script:Raiz = Split-Path -Parent $PSScriptRoot
$script:Logs = Join-Path $script:Raiz 'logs'
$script:UrlApp = 'http://localhost:5173'
$script:PuertoBackend = 8080
$script:PuertoFrontend = 5173

function Puerto-Escuchando([int]$Puerto) {
    return [bool](Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue)
}

function Codigo-Http([string]$Url) {
    $codigo = & curl.exe -s -o NUL -w '%{http_code}' --max-time 2 $Url 2>$null
    return [string]$codigo
}

function Resolver-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return $env:JAVA_HOME }
    $candidatos = @()
    foreach ($base in @("$env:USERPROFILE\.jdks", 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
                        'C:\Program Files\Microsoft', 'C:\Program Files\Amazon Corretto')) {
        if (Test-Path $base) {
            $candidatos += Get-ChildItem $base -Directory |
                Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
                Sort-Object Name -Descending
        }
    }
    if ($candidatos.Count -gt 0) { return $candidatos[0].FullName }
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { return (Split-Path -Parent (Split-Path -Parent $java.Source)) }
    return $null
}

function Resolver-NodeDir {
    $nodo = Get-Command node -ErrorAction SilentlyContinue
    if ($nodo) { return (Split-Path -Parent $nodo.Source) }
    foreach ($base in @("$env:LOCALAPPDATA\Programs\nodejs-portable", "$env:ProgramFiles\nodejs", "${env:ProgramFiles(x86)}\nodejs")) {
        if (Test-Path "$base\node.exe") { return $base }
        if (Test-Path $base) {
            $sub = Get-ChildItem $base -Directory |
                Where-Object { Test-Path "$($_.FullName)\node.exe" } |
                Sort-Object Name -Descending | Select-Object -First 1
            if ($sub) { return $sub.FullName }
        }
    }
    return $null
}

function Docker-Responde {
    cmd /c 'docker info >nul 2>&1'
    return ($LASTEXITCODE -eq 0)
}

function Base-De-Datos-Arriba {
    $estado = cmd /c 'docker inspect -f {{.State.Running}} finanzas-db 2>nul'
    return ($estado -eq 'true')
}

function Matar-Arbol([int]$ProcesoId) {
    cmd /c "taskkill /PID $ProcesoId /T /F >nul 2>&1"
}

function Detener-Servicios([bool]$Backend = $true, [bool]$Frontend = $true, [bool]$BaseDeDatos = $true) {
    if ($Frontend) {
        Get-NetTCPConnection -LocalPort $script:PuertoFrontend -State Listen -ErrorAction SilentlyContinue |
            ForEach-Object { Matar-Arbol $_.OwningProcess }
    }
    if ($Backend) {
        Get-NetTCPConnection -LocalPort $script:PuertoBackend -State Listen -ErrorAction SilentlyContinue |
            ForEach-Object { Matar-Arbol $_.OwningProcess }
        # El Maven que lanzo la app (y su wrapper) puede seguir vivo aunque la app ya no escuche
        Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -like "*$($script:Raiz)*" } |
            ForEach-Object { Matar-Arbol $_.ProcessId }
    }
    if ($BaseDeDatos -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        Push-Location $script:Raiz
        cmd /c 'docker compose stop >nul 2>&1'
        Pop-Location
    }
}
