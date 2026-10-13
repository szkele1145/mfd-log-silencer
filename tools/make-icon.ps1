Add-Type -AssemblyName System.Drawing

# All paths are ASCII + relative to $PSScriptRoot (project dir contains non-ASCII chars,
# so absolute paths inside this file would break when pwsh parses the file without BOM).
$src = Join-Path $PSScriptRoot 'src-icon.png'
$outDir = Join-Path $PSScriptRoot '..\src\main\resources'
$out = Join-Path $outDir 'icon.png'

$orig = [System.Drawing.Image]::FromFile($src)
Write-Host ("Source: {0}x{1}" -f $orig.Width, $orig.Height)

# 1) Square canvas, original centered vertically (circular badge stays uncut)
$canvas = 2860
$bmp = New-Object System.Drawing.Bitmap($canvas, $canvas)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

$y = [int](($canvas - $orig.Height) / 2)
$g.DrawImage($orig, 0, $y, $orig.Width, $orig.Height)
$g.Dispose()
$orig.Dispose()

# 2) Overlay a red mute/prohibition sign: ring + 45 degree slash, semi-transparent,
#    with a dark edge so it reads clearly over the blue badge
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

$cx = $canvas / 2; $cy = $canvas / 2
$rOuter = 900    # ring outer radius (kept small so the original badge rim stays visible)
$ringW  = 150    # ring width

$penEdge = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(170, 40, 10, 10), ($ringW + 34))
$penEdge.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$penEdge.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawEllipse($penEdge, ($cx - $rOuter), ($cy - $rOuter), (2 * $rOuter), (2 * $rOuter))

$red = [System.Drawing.Color]::FromArgb(205, 216, 27, 27)
$penRed = New-Object System.Drawing.Pen($red, $ringW)
$penRed.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$penRed.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawEllipse($penRed, ($cx - $rOuter), ($cy - $rOuter), (2 * $rOuter), (2 * $rOuter))

# Slash from top-left to bottom-right
$d = [Math]::Sqrt(2) / 2
$x1 = $cx - $rOuter * $d; $y1 = $cy - $rOuter * $d
$x2 = $cx + $rOuter * $d; $y2 = $cy + $rOuter * $d
$g.DrawLine($penEdge, $x1, $y1, $x2, $y2)
$g.DrawLine($penRed,  $x1, $y1, $x2, $y2)

$g.Dispose()

# 3) Downscale to 512x512 and save
$final = New-Object System.Drawing.Bitmap(512, 512)
$g2 = [System.Drawing.Graphics]::FromImage($final)
$g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g2.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g2.Clear([System.Drawing.Color]::Transparent)
$g2.DrawImage($bmp, 0, 0, 512, 512)
$g2.Dispose()

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$final.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$final.Dispose(); $bmp.Dispose()
Write-Host ("Saved: {0}" -f $out)
