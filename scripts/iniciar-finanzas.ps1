# Arranca Finanzas como una aplicacion de escritorio:
#   1. Base de datos (Docker Desktop + PostgreSQL)
#   2. Servidor (backend Spring Boot, puerto 8080)
#   3. Interfaz (frontend Vite, puerto 5173), arrancada EN PARALELO con lo anterior
#   4. Abre la web en una ventana propia (modo "app": sin pestanas ni barra de direcciones)
#   5. Al CERRAR la web, apaga lo que haya arrancado este script. La pagina avisa al servidor mientras esta
#      abierta (latido) y al cerrarse; no depende de como se abrio ni de si Edge sigue en segundo plano.
#
# -SinNavegador : arranca todo pero no abre ventana ni apaga al final (para pruebas)
# -NoDetener    : abre la ventana pero no apaga los servicios al cerrarla
# -NoAbrirNavegador : no abre ventana, pero vigila igualmente que la web se cierre (para pruebas)
#
# Solo ASCII a proposito: Windows PowerShell 5.1 lee los .ps1 sin BOM como ANSI y rompe los acentos.

param([switch]$SinNavegador, [switch]$NoDetener, [switch]$NoAbrirNavegador)

. "$PSScriptRoot\_comun.ps1"

# Sin esto Windows escala la ventana del banner como un bitmap y se ve borrosa en pantallas de alta resolucion.
# Tiene que ejecutarse antes de crear ninguna ventana.
Add-Type -TypeDefinition @"
using System.Runtime.InteropServices;
public static class DpiHelper {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[void][DpiHelper]::SetProcessDPIAware()
Add-Type -AssemblyName System.Windows.Forms
New-Item -ItemType Directory -Force -Path $script:Logs | Out-Null

# ---------------------------------------------------------------- banner de progreso
# Vive en su PROPIO hilo con su propio bucle de mensajes: asi sigue fluido mientras el script principal
# espera a docker, maven, etc. El script principal solo escribe en $ui (texto, paso y progreso objetivo).
$TotalPasos = 4
$ui = [hashtable]::Synchronized(@{ Texto = 'Iniciando'; Paso = ''; Objetivo = 3.0; Cerrar = $false })

$bloqueBanner = {
    param($ui)
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()
    $g = [System.Drawing.Graphics]::FromHwnd([IntPtr]::Zero)
    $k = $g.DpiX / 96.0
    $g.Dispose()
    function Px([double]$v) { return [int][Math]::Round($v * $k) }

    $fondo = [System.Drawing.Color]::FromArgb(12, 12, 12)
    $blanco = [System.Drawing.Color]::FromArgb(245, 245, 245)
    $gris = [System.Drawing.Color]::FromArgb(138, 138, 144)
    $verde = [System.Drawing.Color]::FromArgb(34, 214, 128)

    $form = New-Object System.Windows.Forms.Form
    $form.FormBorderStyle = 'None'
    $form.StartPosition = 'CenterScreen'
    $form.ClientSize = New-Object System.Drawing.Size((Px 480), (Px 168))
    $form.BackColor = $fondo
    $form.TopMost = $true
    $form.ShowInTaskbar = $false
    $form.Add_Paint({
        param($s, $e)
        $lapiz = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(48, 48, 52))
        $e.Graphics.DrawRectangle($lapiz, 0, 0, $s.Width - 1, $s.Height - 1)
        $lapiz.Dispose()
    })

    $titulo = New-Object System.Windows.Forms.Label
    $titulo.Text = 'Finanzas'
    $titulo.ForeColor = $blanco
    $titulo.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 22)
    $titulo.AutoSize = $true
    $titulo.Location = New-Object System.Drawing.Point((Px 28), (Px 18))
    $form.Controls.Add($titulo)

    $estado = New-Object System.Windows.Forms.Label
    $estado.ForeColor = $blanco
    $estado.Font = New-Object System.Drawing.Font('Segoe UI', 11)
    $estado.AutoSize = $false
    $estado.AutoEllipsis = $true
    $estado.Size = New-Object System.Drawing.Size((Px 424), (Px 26))
    $estado.Location = New-Object System.Drawing.Point((Px 28), (Px 76))
    $form.Controls.Add($estado)

    $pista = New-Object System.Windows.Forms.Panel
    $pista.BackColor = [System.Drawing.Color]::FromArgb(40, 40, 44)
    $pista.Size = New-Object System.Drawing.Size((Px 424), (Px 6))
    $pista.Location = New-Object System.Drawing.Point((Px 28), (Px 114))
    $form.Controls.Add($pista)

    $relleno = New-Object System.Windows.Forms.Panel
    $relleno.BackColor = $verde
    $relleno.Size = New-Object System.Drawing.Size(0, (Px 6))
    $relleno.Location = New-Object System.Drawing.Point(0, 0)
    $pista.Controls.Add($relleno)

    $paso = New-Object System.Windows.Forms.Label
    $paso.ForeColor = $gris
    $paso.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $paso.AutoSize = $false
    $paso.Size = New-Object System.Drawing.Size((Px 424), (Px 20))
    $paso.Location = New-Object System.Drawing.Point((Px 28), (Px 130))
    $form.Controls.Add($paso)

    # El progreso mostrado persigue suavemente al objetivo (movimiento fluido, sin saltos)
    $anim = @{ Actual = 0.0 }
    $reloj = New-Object System.Windows.Forms.Timer
    $reloj.Interval = 30
    $reloj.Add_Tick({
        if ($ui.Cerrar) { $reloj.Stop(); $form.Close(); return }
        $anim.Actual += ($ui.Objetivo - $anim.Actual) * 0.10
        $relleno.Width = [int]($pista.Width * [Math]::Min(100.0, $anim.Actual) / 100.0)
        if ($estado.Text -ne $ui.Texto) { $estado.Text = $ui.Texto }
        if ($paso.Text -ne $ui.Paso) { $paso.Text = $ui.Paso }
    }.GetNewClosure())
    $reloj.Start()
    [System.Windows.Forms.Application]::Run($form)
}

$runspaceBanner = [runspacefactory]::CreateRunspace()
$runspaceBanner.ApartmentState = 'STA'
$runspaceBanner.ThreadOptions = 'ReuseThread'
$runspaceBanner.Open()
$hiloBanner = [powershell]::Create()
$hiloBanner.Runspace = $runspaceBanner
[void]$hiloBanner.AddScript($bloqueBanner).AddArgument($ui)
[void]$hiloBanner.BeginInvoke()

function Cerrar-Banner {
    $ui.Cerrar = $true
    Start-Sleep -Milliseconds 250
    $hiloBanner.Dispose()
    $runspaceBanner.Dispose()
}

function Paso([string]$texto, [int]$numero, [double]$progreso) {
    Write-Host $texto
    $ui.Texto = $texto
    $ui.Paso = "Paso $numero de $TotalPasos"
    $ui.Objetivo = $progreso
}

# Espera una condicion moviendo el progreso de $Desde hacia $Hasta con una curva suave (nunca llega
# del todo hasta que la condicion se cumple), para que la barra avance aunque no sepamos cuanto falta.
function Esperar([scriptblock]$Condicion, [int]$TimeoutSeg, [string]$Que, [double]$Desde, [double]$Hasta, [double]$Tau = 6) {
    $inicio = Get-Date
    while ($true) {
        if (& $Condicion) { $ui.Objetivo = $Hasta; return }
        $t = ((Get-Date) - $inicio).TotalSeconds
        if ($t -gt $TimeoutSeg) { throw "Tiempo agotado esperando $Que. Revisa los registros en $($script:Logs)" }
        $ui.Objetivo = $Desde + ($Hasta - $Desde) * (1 - [Math]::Exp(-$t / $Tau)) * 0.95
        Start-Sleep -Milliseconds 150
    }
}

try {
    # ------------------------------------------------------------ requisitos
    $javaHome = Resolver-JavaHome
    if (-not $javaHome) { throw 'No se encuentra Java (JDK 21 o superior).' }
    $nodeDir = Resolver-NodeDir
    if (-not $nodeDir) { throw 'No se encuentra Node.js.' }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'No se encuentra Docker. Instala Docker Desktop.' }
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$nodeDir;$env:PATH"

    $yaBackend = Puerto-Escuchando $script:PuertoBackend
    $yaFrontend = Puerto-Escuchando $script:PuertoFrontend
    $yaBase = Base-De-Datos-Arriba
    $carpetaFront = Join-Path $script:Raiz 'frontend'

    # ------------------------------------------------------------ interfaz: no depende de nada, se lanza ya
    if (-not $yaFrontend) {
        # npm escribe node_modules\.package-lock.json al terminar de instalar: si package.json o package-lock.json
        # son mas recientes, las dependencias han cambiado desde entonces y hay que volver a instalar
        $modulos = Join-Path $carpetaFront 'node_modules'
        $marcaInstalacion = Join-Path $modulos '.package-lock.json'
        $faltan = -not (Test-Path $modulos)
        $cambiaron = $false
        if ((-not $faltan) -and (Test-Path $marcaInstalacion)) {
            $ultimaInstalacion = (Get-Item $marcaInstalacion).LastWriteTime
            foreach ($nombre in 'package.json', 'package-lock.json') {
                $ruta = Join-Path $carpetaFront $nombre
                if ((Test-Path $ruta) -and ((Get-Item $ruta).LastWriteTime -gt $ultimaInstalacion)) { $cambiaron = $true }
            }
        }
        if ($faltan -or $cambiaron) {
            Paso $(if ($faltan) { 'Primera vez: instalando la interfaz' } else { 'Actualizando dependencias de la interfaz' }) 3 8
            Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "npm install > `"$(Join-Path $script:Logs 'npm-install.log')`" 2>&1" `
                -WorkingDirectory $carpetaFront -WindowStyle Hidden -Wait | Out-Null
            # Si npm decide que no habia nada que cambiar no reescribe el fichero y se repetiria en cada arranque
            if (Test-Path $marcaInstalacion) { (Get-Item $marcaInstalacion).LastWriteTime = Get-Date }
        }
        Start-Process -FilePath (Join-Path $nodeDir 'node.exe') -ArgumentList 'node_modules\vite\bin\vite.js' `
            -WorkingDirectory $carpetaFront -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $script:Logs 'frontend.log') `
            -RedirectStandardError (Join-Path $script:Logs 'frontend-error.log') | Out-Null
    }

    # ------------------------------------------------------------ 1. base de datos
    if (-not $yaBase) {
        if (-not (Docker-Responde)) {
            Paso 'Abriendo Docker Desktop' 1 4
            $dockerExe = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
            if (-not (Test-Path $dockerExe)) { throw 'Docker no esta en marcha y no encuentro Docker Desktop.' }
            Start-Process $dockerExe | Out-Null
            Esperar { Docker-Responde } 240 'a Docker Desktop' 4 14 12
        }
        Paso 'Arrancando la base de datos' 1 14
        cmd /c 'docker start finanzas-db >nul 2>&1'
        if ($LASTEXITCODE -ne 0) {
            Push-Location $script:Raiz
            cmd /c 'docker compose up -d >nul 2>&1'
            Pop-Location
        }
    }
    Paso 'Arrancando la base de datos' 1 14
    Esperar { (cmd /c 'docker exec finanzas-db pg_isready -U finanzas 2>nul') -match 'accepting connections' } 90 'la base de datos' 14 30 3

    # ------------------------------------------------------------ 2. servidor (jar ya compilado: arranca en segundos)
    if (-not $yaBackend) {
        $carpetaTarget = Join-Path $script:Raiz 'target'
        $jar = Get-ChildItem $carpetaTarget -Filter 'finanzas-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
        $ultimoCambio = (Get-ChildItem (Join-Path $script:Raiz 'src\main') -Recurse -File |
            Measure-Object -Property LastWriteTime -Maximum).Maximum
        $pom = (Get-Item (Join-Path $script:Raiz 'pom.xml')).LastWriteTime
        if ($pom -gt $ultimoCambio) { $ultimoCambio = $pom }

        if ((-not $jar) -or ($jar.LastWriteTime -lt $ultimoCambio)) {
            $primera = -not $jar
            Paso $(if ($primera) { 'Primera vez: preparando el servidor' } else { 'Compilando los cambios nuevos' }) 2 32
            $proceso = Start-Process -FilePath 'cmd.exe' -PassThru -WindowStyle Hidden -WorkingDirectory $script:Raiz `
                -ArgumentList '/c', "`"`"$(Join-Path $script:Raiz 'mvnw.cmd')`" -q package -DskipTests > `"$(Join-Path $script:Logs 'compilacion.log')`" 2>&1`""
            $limite = $(if ($primera) { 1200 } else { 300 })
            $arranque = Get-Date
            while (-not $proceso.HasExited) {
                $t = ((Get-Date) - $arranque).TotalSeconds
                if ($t -gt $limite) { Matar-Arbol $proceso.Id; throw "La compilacion tarda demasiado. Revisa $($script:Logs)\compilacion.log" }
                $ui.Objetivo = 32 + (52 - 32) * (1 - [Math]::Exp(-$t / 25)) * 0.95
                Start-Sleep -Milliseconds 200
            }
            if ($proceso.ExitCode -ne 0) { throw "La compilacion ha fallado. Revisa $($script:Logs)\compilacion.log" }
            $jar = Get-ChildItem $carpetaTarget -Filter 'finanzas-*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
        }
        # Secreto con el que se firman las sesiones: uno aleatorio por instalacion (el que trae el codigo fuente es publico)
        $archivoSecreto = Join-Path $script:Raiz '.secreto-jwt'
        if (-not (Test-Path $archivoSecreto)) {
            $bytes = New-Object byte[] 48
            $generador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            $generador.GetBytes($bytes)
            $generador.Dispose()
            [Convert]::ToBase64String($bytes) | Set-Content -Path $archivoSecreto -Encoding ASCII
        }
        $env:JWT_SECRET = (Get-Content $archivoSecreto -Raw).Trim()

        Paso 'Arrancando el servidor' 2 54
        Start-Process -FilePath (Join-Path $javaHome 'bin\java.exe') -WorkingDirectory $script:Raiz -WindowStyle Hidden `
            -ArgumentList '-XX:TieredStopAtLevel=1', '-XX:+UseSerialGC', '-jar', "`"$($jar.FullName)`"" `
            -RedirectStandardOutput (Join-Path $script:Logs 'backend.log') `
            -RedirectStandardError (Join-Path $script:Logs 'backend-error.log') | Out-Null
    }
    Paso 'Arrancando el servidor' 2 54
    Esperar { (Codigo-Http "http://localhost:$($script:PuertoBackend)/") -ne '000' } 180 'el servidor' 54 88 5

    # ------------------------------------------------------------ 3. interfaz
    Paso 'Arrancando la interfaz' 3 88
    Esperar { (Codigo-Http $script:UrlApp) -eq '200' } 120 'la interfaz' 88 97 2

    Paso 'Abriendo Finanzas' 4 100
    Start-Sleep -Milliseconds 700
    Cerrar-Banner

    if ($SinNavegador) { return }

    # ------------------------------------------------------------ ventana de la aplicacion
    $ventana = $null
    $abiertoPorNosotros = $false
    if (-not $NoAbrirNavegador) {
        $navegador = @(
            "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
            "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
            "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
            "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
            "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
        ) | Where-Object { Test-Path $_ } | Select-Object -First 1

        $abiertoPorNosotros = $true
        if ($navegador) {
            # Perfil propio: la ventana es un proceso aparte y se conserva el login entre usos
            $perfil = Join-Path $script:Raiz '.perfil-navegador'
            $ventana = Start-Process -FilePath $navegador -PassThru -ArgumentList @(
                "--app=$($script:UrlApp)", "--user-data-dir=`"$perfil`"", '--window-size=1320,880',
                '--no-first-run', '--no-default-browser-check')
        } else {
            Start-Process $script:UrlApp
        }
    }

    # ------------------------------------------------------------ vigilancia: apagar cuando se cierra la web
    # Dos senales independientes, la que ocurra primero:
    #   - el proceso de la ventana termina (cerro la ventana de la app)
    #   - la pagina deja de avisar al servidor: aviso de cierre (pagehide) o sin latidos (navegador cerrado a la fuerza)
    # Esto ultimo cubre que Edge se quede en segundo plano al cerrar la ventana o que la web se abriera en una
    # pestana normal. Se pide ver la condicion en 2 vueltas seguidas y se ignoran las vueltas tras suspender el equipo.
    $inicioVigilancia = Get-Date
    $momentoVentana = Get-Date
    $ultimaVuelta = Get-Date
    $consecutivas = 0
    $endpointRespondio = $false
    $motivo = $null
    while (-not $motivo) {
        Start-Sleep -Seconds 2
        $ahora = Get-Date
        if (($ahora - $ultimaVuelta).TotalSeconds -gt 15) { $ultimaVuelta = $ahora; $consecutivas = 0; continue }
        $ultimaVuelta = $ahora

        if ($ventana -and $ventana.HasExited) {
            if (($ahora - $momentoVentana).TotalSeconds -ge 8) { $motivo = 'ventana cerrada'; break }
            $ventana = $null   # el navegador cedio la ventana a otro proceso: solo valen los latidos
        }

        $actividad = Estado-Actividad
        if ($null -eq $actividad) { continue }
        $endpointRespondio = $true
        if ($actividad.latidos -eq 0) {
            # Nunca se ha abierto la pagina: si la abrimos nosotros y no aparece, no dejar todo encendido para siempre
            if ($abiertoPorNosotros -and (($ahora - $inicioVigilancia).TotalSeconds -gt 120)) { $motivo = 'la pagina no llego a abrirse' }
            continue
        }
        $cerrada = ($actividad.cierrePendiente -and $actividad.segundosDesdeCierre -ge 8)
        $muda = ($actividad.segundosDesdeLatido -ge 150)
        if ($cerrada -or $muda) { $consecutivas++ } else { $consecutivas = 0 }
        if ($consecutivas -ge 2) { $motivo = $(if ($cerrada) { 'pagina cerrada' } else { 'sin latidos' }) }
    }
    Write-Host "Apagando: $motivo"

    if ($NoDetener) { return }
    Cerrar-Ventana-App
    Detener-Servicios -Backend (-not $yaBackend) -Frontend (-not $yaFrontend) -BaseDeDatos (-not $yaBase)
} catch {
    Cerrar-Banner
    [System.Windows.Forms.MessageBox]::Show("$($_.Exception.Message)`n`nRegistros: $($script:Logs)", 'Finanzas', 'OK', 'Error') | Out-Null
}
