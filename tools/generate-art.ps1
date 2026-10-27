Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$resRoot = Join-Path $projectRoot "app\src\main\res"

function New-RoundedRectPath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-Icon {
    param(
        [string]$Path,
        [int]$Size
    )

    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

    $scale = $Size / 192.0
    $graphics.ScaleTransform($scale, $scale)

    $bounds = New-Object System.Drawing.RectangleF(4, 4, 184, 184)
    $backgroundPath = New-RoundedRectPath 4 4 184 184 44
    $backgroundBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $bounds,
        [System.Drawing.Color]::FromArgb(255, 31, 107, 222),
        [System.Drawing.Color]::FromArgb(255, 9, 38, 76),
        45.0
    )
    $graphics.FillPath($backgroundBrush, $backgroundPath)

    # Subtle top highlight.
    $highlightPath = New-RoundedRectPath 10 10 172 78 38
    $highlightBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.RectangleF(10, 10, 172, 78)),
        [System.Drawing.Color]::FromArgb(55, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(0, 255, 255, 255),
        90.0
    )
    $graphics.FillPath($highlightBrush, $highlightPath)

    $speaker = New-Object System.Drawing.Drawing2D.GraphicsPath
    $speakerPoints = @(
        (New-Object System.Drawing.PointF(48, 78)),
        (New-Object System.Drawing.PointF(66, 78)),
        (New-Object System.Drawing.PointF(88, 56)),
        (New-Object System.Drawing.PointF(88, 136)),
        (New-Object System.Drawing.PointF(66, 114)),
        (New-Object System.Drawing.PointF(48, 114))
    )
    $speaker.AddPolygon($speakerPoints)
    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $graphics.FillPath($whiteBrush, $speaker)

    $wavePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 187, 221, 255), 9.0)
    $wavePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $wavePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawArc($wavePen, 93, 70, 24, 52, -58, 116)
    $graphics.DrawArc($wavePen, 103, 54, 52, 84, -58, 116)

    # Small accent dot under the speaker, balancing the mark.
    $dotBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 139, 198, 255))
    $graphics.FillEllipse($dotBrush, 88, 156, 16, 16)

    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)

    $graphics.Dispose()
    $bitmap.Dispose()
}

function Draw-Banner {
    param(
        [string]$Path,
        [string]$IconPath
    )

    $bitmap = New-Object System.Drawing.Bitmap(320, 180, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $background = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.RectangleF(0, 0, 320, 180)),
        [System.Drawing.Color]::FromArgb(255, 13, 24, 37),
        [System.Drawing.Color]::FromArgb(255, 9, 15, 23),
        20.0
    )
    $graphics.FillRectangle($background, 0, 0, 320, 180)

    $glowBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.RectangleF(0, 0, 210, 180)),
        [System.Drawing.Color]::FromArgb(75, 32, 112, 220),
        [System.Drawing.Color]::FromArgb(0, 10, 18, 28),
        0.0
    )
    $graphics.FillRectangle($glowBrush, 0, 0, 210, 180)

    $icon = [System.Drawing.Image]::FromFile($IconPath)
    $graphics.DrawImage($icon, 30, 54, 74, 74)
    $icon.Dispose()

    $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 242, 247, 252))
    $titleFont = New-Object System.Drawing.Font("Segoe UI", 19, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawString("VOLUME CONTROL", $titleFont, $titleBrush, 122, 56)

    $accentBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 77, 154, 255))
    $accentPath = New-RoundedRectPath 124 96 68 4 2
    $graphics.FillPath($accentBrush, $accentPath)

    $subtitleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 159, 182, 207))
    $subtitleFont = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawString("Per-app sound levels", $subtitleFont, $subtitleBrush, 122, 110)

    $stepColors = @(70, 110, 160, 220, 255)
    $barX = 258
    $baseline = 140
    $heights = @(14, 24, 36, 24, 14)
    for ($index = 0; $index -lt 5; $index++) {
        $color = [System.Drawing.Color]::FromArgb($stepColors[$index], 77, 154, 255)
        $barBrush = New-Object System.Drawing.SolidBrush($color)
        $barPath = New-RoundedRectPath ($barX + $index * 10) ($baseline - $heights[$index]) 6 $heights[$index] 3
        $graphics.FillPath($barBrush, $barPath)
        $barPath.Dispose()
        $barBrush.Dispose()
    }

    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)

    $accentPath.Dispose()
    $titleFont.Dispose()
    $titleBrush.Dispose()
    $accentBrush.Dispose()
    $subtitleFont.Dispose()
    $subtitleBrush.Dispose()
    $background.Dispose()
    $glowBrush.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}

$iconSourcePath = Join-Path $env:TEMP "app-volume-icon-192.png"
Draw-Icon -Path $iconSourcePath -Size 192

$iconSizes = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

$sourceIcon = [System.Drawing.Image]::FromFile($iconSourcePath)
foreach ($entry in $iconSizes.GetEnumerator()) {
    $directory = Join-Path $resRoot $entry.Key
    New-Item -ItemType Directory -Force -Path $directory | Out-Null

    $size = $entry.Value
    $bitmap = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($sourceIcon, 0, 0, $size, $size)
    $bitmap.Save((Join-Path $directory "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}
$sourceIcon.Dispose()

$bannerDirectory = Join-Path $resRoot "drawable-nodpi"
New-Item -ItemType Directory -Force -Path $bannerDirectory | Out-Null
Draw-Banner -Path (Join-Path $bannerDirectory "banner.png") -IconPath $iconSourcePath

Remove-Item $iconSourcePath -Force
Write-Output "Generated launcher icons and TV banner."
