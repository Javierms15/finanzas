# Ejecuta todos los tests del proyecto: backend (JUnit + Testcontainers) y frontend (Vitest).
# Los tests de integracion del backend arrancan su propio PostgreSQL en Docker (necesitan Docker en marcha);
# si Docker no esta disponible se omiten y solo corren los unitarios.
# Solo ASCII a proposito: Windows PowerShell 5.1 lee los .ps1 sin BOM como ANSI y rompe los acentos.
param([switch]$SoloBackend, [switch]$SoloFrontend, [switch]$Cobertura)

. "$PSScriptRoot\_comun.ps1"

$fallos = @()

if (-not $SoloFrontend) {
    $java = Resolver-JavaHome
    if (-not $java) { throw 'No se ha encontrado Java (JDK 21 o superior).' }
    $env:JAVA_HOME = $java
    Write-Host '== Backend: mvnw test =='
    Push-Location $script:Raiz
    try {
        if ($Cobertura) { & "$script:Raiz\mvnw.cmd" verify -Pcobertura } else { & "$script:Raiz\mvnw.cmd" test }
        if ($LASTEXITCODE -ne 0) { $fallos += 'backend' }
    } finally { Pop-Location }
}

if (-not $SoloBackend) {
    $nodo = Resolver-NodeDir
    if (-not $nodo) { throw 'No se ha encontrado Node.js.' }
    $env:PATH = "$nodo;$env:PATH"
    Write-Host '== Frontend: vitest =='
    Push-Location (Join-Path $script:Raiz 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) { & "$nodo\npm.cmd" install }
        & "$nodo\node.exe" 'node_modules\vitest\vitest.mjs' run
        if ($LASTEXITCODE -ne 0) { $fallos += 'frontend' }
    } finally { Pop-Location }
}

if ($fallos.Count -gt 0) {
    Write-Host ('TESTS FALLIDOS EN: ' + ($fallos -join ', '))
    exit 1
}
Write-Host 'Todos los tests han pasado.'
