# QShop 滚动条贴图(放大尺寸生成,避免 System.Drawing 小图损坏)
# scroll_track: 8x16 纯色轨道(border 1);scroll_knob: 16x16 圆角滑块(border 2)
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

# 轨道 8x16:整体 #3A3A3A(alpha 200),四角透明营造圆角,border 1 九宫格拉伸
$b = New-Bitmap 8 16
for ($y=0; $y -lt 16; $y++) { for ($x=0; $x -lt 8; $x++) { Set-Px $b $x $y 58 58 58 200 } }
Set-Px $b 0 0 0 0 0 0; Set-Px $b 7 0 0 0 0 0; Set-Px $b 0 15 0 0 0 0; Set-Px $b 7 15 0 0 0 0
Set-Px $b 1 0 200 200 200 200; Set-Px $b 6 0 200 200 200 200; Set-Px $b 1 15 30 30 30 200; Set-Px $b 6 15 30 30 30 200
Save-Png $b 'scroll_track'

# 滑块 16x16:#949494(alpha 235),四角透明圆角,顶部两行高光、底部两行暗边,border 2 九宫格
$b = New-Bitmap 16 16
for ($y=0; $y -lt 16; $y++) {
    for ($x=0; $x -lt 16; $x++) {
        $v = 148
        if ($y -lt 2) { $v = 190 } elseif ($y -ge 14) { $v = 110 }
        Set-Px $b $x $y $v $v $v 235
    }
}
Set-Px $b 0 0 0 0 0 0; Set-Px $b 1 0 0 0 0 0; Set-Px $b 0 1 0 0 0 0
Set-Px $b 15 0 0 0 0 0; Set-Px $b 14 0 0 0 0 0; Set-Px $b 15 1 0 0 0 0
Set-Px $b 0 15 0 0 0 0; Set-Px $b 1 15 0 0 0 0; Set-Px $b 0 14 0 0 0 0
Set-Px $b 15 15 0 0 0 0; Set-Px $b 14 15 0 0 0 0; Set-Px $b 15 14 0 0 0 0
Save-Png $b 'scroll_knob'

Write-Host 'done'
