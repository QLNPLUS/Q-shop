# QShop 贴图生成 — tab 列表渐变遮罩 + 滚动条轨道/滑块
Add-Type -AssemblyName System.Drawing
$out = 'D:\idea\Q-shop\src\main\resources\assets\qshop\textures\gui'

function New-Bitmap([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }
function Set-Px([System.Drawing.Bitmap]$b, [int]$x, [int]$y, [byte]$r, [byte]$g, [byte]$bl, [byte]$a) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $b.Width -or $y -ge $b.Height) { return }
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $bl))
}
function Save-Png([System.Drawing.Bitmap]$b, [string]$name) {
    $b.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose(); Write-Host "wrote $name.png"
}

# 顶部遮罩:外缘(与 tab 栏底色一致)实心 → 向下渐隐到透明
$b = New-Bitmap 46 10
for ($y = 0; $y -lt 10; $y++) {
    $a = [int](255 * (1 - $y / 10.0))
    for ($x = 0; $x -lt 46; $x++) { Set-Px $b $x $y 16 16 16 ([byte]$a) }
}
Save-Png $b 'tab_fade_top'

# 底部遮罩:透明 → 向下渐隐到实心
$b = New-Bitmap 46 10
for ($y = 0; $y -lt 10; $y++) {
    $a = [int](255 * ($y / 10.0))
    for ($x = 0; $x -lt 46; $x++) { Set-Px $b $x $y 16 16 16 ([byte]$a) }
}
Save-Png $b 'tab_fade_bottom'

# 滚动条轨道:3x16 竖条(border 1 九宫格)
$b = New-Bitmap 3 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 3; $x++) { Set-Px $b $x $y 58 58 58 200 } }
Set-Px $b 1 0 0 0 0 120; Set-Px $b 1 15 0 0 0 120
Save-Png $b 'scroll_track'

# 滚动条滑块:5x16 圆角(border 2 九宫格)
$b = New-Bitmap 5 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 5; $x++) { Set-Px $b $x $y 148 148 148 235 } }
Set-Px $b 0 0 0 0 0 0; Set-Px $b 4 0 0 0 0 0; Set-Px $b 0 15 0 0 0 0; Set-Px $b 4 15 0 0 0 0
Set-Px $b 1 0 200 200 200 235; Set-Px $b 3 0 200 200 200 235; Set-Px $b 1 15 90 90 90 235; Set-Px $b 3 15 90 90 90 235
Save-Png $b 'scroll_knob'

Write-Host 'done'
