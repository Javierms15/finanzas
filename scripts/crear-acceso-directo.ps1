# Crea el icono "Finanzas" en el escritorio (y en el menu Inicio) para abrir la aplicacion con doble clic.
# Se ejecuta una sola vez; si se mueve la carpeta del proyecto hay que volver a ejecutarlo.
$raiz = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'iniciar-finanzas.ps1'
$icono = Join-Path $PSScriptRoot 'finanzas.ico'
if (-not (Test-Path $icono)) { & (Join-Path $PSScriptRoot 'crear-icono.ps1') }

$destinos = @(
    (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Finanzas.lnk'),
    (Join-Path ([Environment]::GetFolderPath('Programs')) 'Finanzas.lnk')
)
$shell = New-Object -ComObject WScript.Shell
foreach ($destino in $destinos) {
    $acceso = $shell.CreateShortcut($destino)
    $acceso.TargetPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $acceso.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
    $acceso.WorkingDirectory = $raiz
    $acceso.IconLocation = $icono
    $acceso.Description = 'Abre Finanzas (arranca todo lo necesario y abre la aplicacion)'
    $acceso.Save()
    Write-Host "Acceso directo creado: $destino"
}
