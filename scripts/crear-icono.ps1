# Genera scripts\finanzas.ico (cuadrado negro con una linea verde ascendente, como la app).
# Solo hace falta ejecutarlo si se quiere cambiar el diseno del icono.
Add-Type -AssemblyName System.Drawing

function Dibujar-Icono([int]$Tam) {
    $bmp = New-Object System.Drawing.Bitmap($Tam, $Tam)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.Clear([System.Drawing.Color]::Transparent)
    $e = $Tam / 256.0

    # fondo: cuadrado redondeado casi negro
    $radio = 56 * $e
    $ruta = New-Object System.Drawing.Drawing2D.GraphicsPath
    $ruta.AddArc(0, 0, $radio, $radio, 180, 90)
    $ruta.AddArc($Tam - $radio, 0, $radio, $radio, 270, 90)
    $ruta.AddArc($Tam - $radio, $Tam - $radio, $radio, $radio, 0, 90)
    $ruta.AddArc(0, $Tam - $radio, $radio, $radio, 90, 90)
    $ruta.CloseFigure()
    $g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 12, 12, 12))), $ruta)

    # linea ascendente verde
    $verde = [System.Drawing.Color]::FromArgb(255, 34, 214, 128)
    $lapiz = New-Object System.Drawing.Pen($verde, [float](22 * $e))
    $lapiz.StartCap = 'Round'; $lapiz.EndCap = 'Round'; $lapiz.LineJoin = 'Round'
    $puntos = @(
        (New-Object System.Drawing.PointF([float](46 * $e), [float](176 * $e))),
        (New-Object System.Drawing.PointF([float](100 * $e), [float](120 * $e))),
        (New-Object System.Drawing.PointF([float](144 * $e), [float](150 * $e))),
        (New-Object System.Drawing.PointF([float](206 * $e), [float](76 * $e)))
    )
    $g.DrawLines($lapiz, [System.Drawing.PointF[]]$puntos)
    $punta = New-Object System.Drawing.SolidBrush($verde)
    $r = 17 * $e
    $g.FillEllipse($punta, [float](206 * $e - $r), [float](76 * $e - $r), [float](2 * $r), [float](2 * $r))
    $g.Dispose()
    return $bmp
}

$tamanos = 16, 24, 32, 48, 64, 128, 256
$imagenes = foreach ($t in $tamanos) {
    $bmp = Dibujar-Icono $t
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    , $ms.ToArray()
}

# Formato ICO con cada imagen en PNG
$salida = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter($salida)
$w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$tamanos.Count)
$desplazamiento = 6 + 16 * $tamanos.Count
for ($i = 0; $i -lt $tamanos.Count; $i++) {
    $t = $tamanos[$i]
    $w.Write([byte]($(if ($t -ge 256) { 0 } else { $t })))
    $w.Write([byte]($(if ($t -ge 256) { 0 } else { $t })))
    $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([uint16]1); $w.Write([uint16]32)
    $w.Write([uint32]$imagenes[$i].Length)
    $w.Write([uint32]$desplazamiento)
    $desplazamiento += $imagenes[$i].Length
}
foreach ($img in $imagenes) { $w.Write($img) }
$w.Flush()
[System.IO.File]::WriteAllBytes((Join-Path $PSScriptRoot 'finanzas.ico'), $salida.ToArray())
Write-Host "Icono generado: $(Join-Path $PSScriptRoot 'finanzas.ico')"
