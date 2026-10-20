Add-Type -AssemblyName System.Drawing

function Get-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = [float]($r * 2.0)
    $path.AddArc($x, $y, $d, $d, 180.0, 90.0)
    $path.AddArc(($x + $w - $d), $y, $d, $d, 270.0, 90.0)
    $path.AddArc(($x + $w - $d), ($y + $h - $d), $d, $d, 0.0, 90.0)
    $path.AddArc($x, ($y + $h - $d), $d, $d, 90.0, 90.0)
    $path.CloseFigure()
    return $path
}

function Build-BillingIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $s = [float]($size / 512.0)

    # 1. Base Squircle Canvas Background
    $bgX = 16.0 * $s
    $bgY = 16.0 * $s
    $bgW = 480.0 * $s
    $bgH = 480.0 * $s
    $bgR = 96.0 * $s
    $bgPath = Get-RoundedPath $bgX $bgY $bgW $bgH $bgR

    $p1 = New-Object System.Drawing.PointF(0.0, 0.0)
    $p2 = New-Object System.Drawing.PointF((512.0 * $s), (512.0 * $s))
    $cTop = [System.Drawing.Color]::FromArgb(255, 15, 23, 42)    # Slate 900
    $cBot = [System.Drawing.Color]::FromArgb(255, 30, 58, 138)   # Blue 900
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($p1, $p2, $cTop, $cBot)
    $g.FillPath($bgBrush, $bgPath)
    $bgBrush.Dispose()

    # Subtle Cyan Border Highlight
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(120, 56, 189, 248), (4.0 * $s))
    $g.DrawPath($borderPen, $bgPath)
    $borderPen.Dispose()
    $bgPath.Dispose()

    # 2. Receipt Bill Slip (Top Ejecting Paper)
    $rcW = 220.0 * $s
    $rcH = 150.0 * $s
    $rcX = (512.0 * $s - $rcW) / 2.0
    $rcY = 54.0 * $s
    $rcR = 14.0 * $s
    $rcPath = Get-RoundedPath $rcX $rcY $rcW $rcH $rcR

    $rcBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 248, 250, 252))
    $g.FillPath($rcBrush, $rcPath)
    $rcBrush.Dispose()
    $rcPath.Dispose()

    # Receipt Header Band (Royal Blue Stripe on receipt)
    $rcHeaderBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 37, 99, 235))
    $g.FillRectangle($rcHeaderBrush, ($rcX + (20.0 * $s)), ($rcY + (20.0 * $s)), (180.0 * $s), (10.0 * $s))
    $rcHeaderBrush.Dispose()

    # Receipt Itemized Lines
    $rcLineBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 148, 163, 184))
    $g.FillRectangle($rcLineBrush, ($rcX + (20.0 * $s)), ($rcY + (38.0 * $s)), (140.0 * $s), (6.0 * $s))
    $g.FillRectangle($rcLineBrush, ($rcX + (20.0 * $s)), ($rcY + (50.0 * $s)), (165.0 * $s), (6.0 * $s))
    $g.FillRectangle($rcLineBrush, ($rcX + (20.0 * $s)), ($rcY + (62.0 * $s)), (120.0 * $s), (6.0 * $s))
    $rcLineBrush.Dispose()

    # Receipt Total Bar (Emerald Green Total on Receipt)
    $rcTotalBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))
    $g.FillRectangle($rcTotalBrush, ($rcX + (20.0 * $s)), ($rcY + (76.0 * $s)), (180.0 * $s), (8.0 * $s))
    $rcTotalBrush.Dispose()

    # 3. Main POS Console Body
    $posW = 330.0 * $s
    $posH = 340.0 * $s
    $posX = (512.0 * $s - $posW) / 2.0
    $posY = 140.0 * $s
    $posR = 38.0 * $s
    $posPath = Get-RoundedPath $posX $posY $posW $posH $posR

    $posP1 = New-Object System.Drawing.PointF($posX, $posY)
    $posP2 = New-Object System.Drawing.PointF(($posX + $posW), ($posY + $posH))
    $posTopColor = [System.Drawing.Color]::FromArgb(255, 51, 65, 85)   # Slate 700
    $posBotColor = [System.Drawing.Color]::FromArgb(255, 15, 23, 42)   # Slate 900
    $posBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($posP1, $posP2, $posTopColor, $posBotColor)
    $g.FillPath($posBrush, $posPath)
    $posBrush.Dispose()

    $posBorderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(160, 100, 116, 139), (3.5 * $s))
    $g.DrawPath($posBorderPen, $posPath)
    $posBorderPen.Dispose()
    $posPath.Dispose()

    # Receipt Exit Slot (Bevel cutout at top of console)
    $slotW = $posW - (50.0 * $s)
    $slotX = $posX + (25.0 * $s)
    $slotY = $posY + (10.0 * $s)
    $slotPath = Get-RoundedPath $slotX $slotY $slotW (12.0 * $s) (6.0 * $s)
    $slotBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 10, 15, 26))
    $g.FillPath($slotBrush, $slotPath)
    $slotBrush.Dispose()
    $slotPath.Dispose()

    # 4. Terminal Digital LCD Screen
    $scW = 270.0 * $s
    $scH = 110.0 * $s
    $scX = (512.0 * $s - $scW) / 2.0
    $scY = $posY + (34.0 * $s)
    $scR = 20.0 * $s
    $scPath = Get-RoundedPath $scX $scY $scW $scH $scR

    $scBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 8, 14, 26))
    $g.FillPath($scBg, $scPath)
    $scBg.Dispose()

    $scBorder = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 16, 185, 129), (2.5 * $s)) # Emerald Glow
    $g.DrawPath($scBorder, $scPath)
    $scBorder.Dispose()
    $scPath.Dispose()

    # Screen Graphics: Top status dots (Wi-Fi, Battery, POS OK)
    $dotBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 52, 211, 153))
    $g.FillEllipse($dotBrush, ($scX + (20.0 * $s)), ($scY + (16.0 * $s)), (8.0 * $s), (8.0 * $s))
    $g.FillEllipse($dotBrush, ($scX + (34.0 * $s)), ($scY + (16.0 * $s)), (8.0 * $s), (8.0 * $s))
    $g.FillEllipse($dotBrush, ($scX + (48.0 * $s)), ($scY + (16.0 * $s)), (8.0 * $s), (8.0 * $s))
    $dotBrush.Dispose()

    # Screen Title Bar
    $scTitleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 100, 116, 139))
    $g.FillRectangle($scTitleBrush, ($scX + (70.0 * $s)), ($scY + (17.0 * $s)), (100.0 * $s), (6.0 * $s))
    $scTitleBrush.Dispose()

    # Big Digital Bill Amount Graphic (simulating LED amount on LCD)
    $digitBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))
    $g.FillRectangle($digitBrush, ($scX + (20.0 * $s)), ($scY + (44.0 * $s)), (110.0 * $s), (24.0 * $s))
    $g.FillRectangle($digitBrush, ($scX + (20.0 * $s)), ($scY + (74.0 * $s)), (80.0 * $s), (14.0 * $s))
    $digitBrush.Dispose()

    # Screen "PAID / SUCCESS" Checkmark Badge (Right side of screen)
    $badgeW = 72.0 * $s
    $badgeH = 42.0 * $s
    $badgeX = $scX + $scW - $badgeW - (16.0 * $s)
    $badgeY = $scY + (40.0 * $s)
    $badgeR = 12.0 * $s
    $badgePath = Get-RoundedPath $badgeX $badgeY $badgeW $badgeH $badgeR

    $badgeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))
    $g.FillPath($badgeBrush, $badgePath)
    $badgeBrush.Dispose()
    $badgePath.Dispose()

    # Checkmark inside badge
    $chkPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, (4.0 * $s))
    $chkPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $chkPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawLine($chkPen, ($badgeX + (20.0 * $s)), ($badgeY + (22.0 * $s)), ($badgeX + (32.0 * $s)), ($badgeY + (32.0 * $s)))
    $g.DrawLine($chkPen, ($badgeX + (32.0 * $s)), ($badgeY + (32.0 * $s)), ($badgeX + (54.0 * $s)), ($badgeY + (12.0 * $s)))
    $chkPen.Dispose()

    # 5. Keypad Matrix (3 rows x 3 columns)
    $keyStartY = $scY + $scH + (18.0 * $s)
    $keyW = 58.0 * $s
    $keyH = 28.0 * $s
    $gapX = 14.0 * $s
    $gapY = 10.0 * $s
    $gridStartX = (512.0 * $s - ((3.0 * $keyW) + (2.0 * $gapX))) / 2.0

    $numKeyBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 71, 85, 105)) # Slate 600
    for ($r = 0; $r -lt 3; $r++) {
        for ($c = 0; $c -lt 3; $c++) {
            $kx = $gridStartX + ($c * ($keyW + $gapX))
            $ky = $keyStartY + ($r * ($keyH + $gapY))
            $kPath = Get-RoundedPath $kx $ky $keyW $keyH (8.0 * $s)
            $g.FillPath($numKeyBrush, $kPath)
            $kPath.Dispose()
        }
    }
    $numKeyBrush.Dispose()

    # Bottom Row Action Keys: Red Cancel, Slate 0, Green Enter/Pay
    $actionY = $keyStartY + (3.0 * ($keyH + $gapY))

    # Red Cancel Button
    $cancelX = $gridStartX
    $cancelPath = Get-RoundedPath $cancelX $actionY $keyW $keyH (8.0 * $s)
    $cancelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 239, 68, 68)) # Red 500
    $g.FillPath($cancelBrush, $cancelPath)
    $cancelBrush.Dispose()
    $cancelPath.Dispose()

    # Zero Key
    $zeroX = $gridStartX + $keyW + $gapX
    $zeroPath = Get-RoundedPath $zeroX $actionY $keyW $keyH (8.0 * $s)
    $zeroBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 71, 85, 105))
    $g.FillPath($zeroBrush, $zeroPath)
    $zeroBrush.Dispose()
    $zeroPath.Dispose()

    # Green Enter / Print Bill Button
    $enterX = $gridStartX + (2.0 * ($keyW + $gapX))
    $enterPath = Get-RoundedPath $enterX $actionY $keyW $keyH (8.0 * $s)
    $enterBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129)) # Emerald 500
    $g.FillPath($enterBrush, $enterPath)
    $enterBrush.Dispose()
    $enterPath.Dispose()

    # 6. Contactless Card Reader Gold Strip at Bottom
    $chipW = 120.0 * $s
    $chipH = 6.0 * $s
    $chipX = (512.0 * $s - $chipW) / 2.0
    $chipY = $actionY + $keyH + (14.0 * $s)
    $chipPath = Get-RoundedPath $chipX $chipY $chipW $chipH (3.0 * $s)
    $chipBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(200, 245, 158, 11)) # Amber 500
    $g.FillPath($chipBrush, $chipPath)
    $chipBrush.Dispose()
    $chipPath.Dispose()

    $g.Flush()
    $g.Dispose()

    return $bmp
}

function Write-WindowsIco([System.Drawing.Bitmap[]]$bitmaps, [string]$outputPath) {
    $msList = New-Object System.Collections.Generic.List[System.IO.MemoryStream]
    foreach ($bmp in $bitmaps) {
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $msList.Add($ms)
    }

    $fs = [System.IO.File]::Create($outputPath)
    $bw = New-Object System.IO.BinaryWriter($fs)

    # ICONDIR
    $bw.Write([uint16]0)                  # Reserved
    $bw.Write([uint16]1)                  # 1 = ICO
    $bw.Write([uint16]$bitmaps.Length)    # Image count

    # ICONDIRENTRY[]
    $offset = 6 + (16 * $bitmaps.Length)
    for ($i = 0; $i -lt $bitmaps.Length; $i++) {
        $bmp = $bitmaps[$i]
        $pngBytes = $msList[$i].ToArray()

        $w = if ($bmp.Width -ge 256) { [byte]0 } else { [byte]$bmp.Width }
        $h = if ($bmp.Height -ge 256) { [byte]0 } else { [byte]$bmp.Height }

        $bw.Write($w)
        $bw.Write($h)
        $bw.Write([byte]0)                # Palette
        $bw.Write([byte]0)                # Reserved
        $bw.Write([uint16]1)              # Color planes
        $bw.Write([uint16]32)             # Bits per pixel
        $bw.Write([uint32]$pngBytes.Length) # Image data length
        $bw.Write([uint32]$offset)        # Offset to image data

        $offset += $pngBytes.Length
    }

    # Write Image Data (PNG chunks)
    for ($i = 0; $i -lt $bitmaps.Length; $i++) {
        $pngBytes = $msList[$i].ToArray()
        $bw.Write($pngBytes)
        $msList[$i].Dispose()
    }

    $bw.Flush()
    $bw.Close()
    $fs.Close()
}

Write-Host "Creating Master High-Res Billing Icon (512x512)..." -ForegroundColor Cyan
$masterBmp = Build-BillingIcon 512

$assetsDir = Join-Path $PSScriptRoot "assets"
if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
}

$pngPath = Join-Path $assetsDir "icon.png"
$masterBmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Saved: $pngPath" -ForegroundColor Green

Write-Host "Generating Multi-Resolution ICO (256, 128, 64, 48, 32, 16)..." -ForegroundColor Cyan
$resolutions = @(256, 128, 64, 48, 32, 16)
$bmpList = New-Object System.Collections.Generic.List[System.Drawing.Bitmap]

foreach ($res in $resolutions) {
    $rBmp = Build-BillingIcon $res
    $bmpList.Add($rBmp)
}

$icoPath = Join-Path $assetsDir "icon.ico"
Write-WindowsIco $bmpList.ToArray() $icoPath
Write-Host "Saved: $icoPath" -ForegroundColor Green

# Update electron-pos/dist
$distDir = Join-Path $PSScriptRoot "dist"
if (Test-Path $distDir) {
    Copy-Item $icoPath (Join-Path $distDir "favicon.ico") -Force
    Copy-Item $pngPath (Join-Path $distDir "icon.png") -Force
    Write-Host "Updated electron-pos/dist/favicon.ico & icon.png" -ForegroundColor Green
}

# Update frontend public
$frontendPublic = Join-Path $PSScriptRoot "..\frontend\order-management\public"
if (Test-Path $frontendPublic) {
    Copy-Item $icoPath (Join-Path $frontendPublic "favicon.ico") -Force
    Copy-Item $pngPath (Join-Path $frontendPublic "icon.png") -Force
    Write-Host "Updated frontend/order-management/public/favicon.ico & icon.png" -ForegroundColor Green
}

# Cleanup
$masterBmp.Dispose()
foreach ($b in $bmpList) { $b.Dispose() }

Write-Host "SUCCESS: Billing & POS Terminal Icon successfully created and deployed!" -ForegroundColor Green
