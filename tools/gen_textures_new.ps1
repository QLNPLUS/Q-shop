# QShop GUI texture generator — new textures (tab bar, tab buttons, menu panel, trade panel, type icons)
Add-Type -AssemblyName System.Drawing

$out = 'D:\idea\Q-shop\src\main\resources\assets\qshop\textures\gui'

function New-Bitmap([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Px([System.Drawing.Bitmap]$b, [int]$x, [int]$y, [byte]$r, [byte]$g, [byte]$bl, [byte]$a) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $b.Width -or $y -ge $b.Height) { return }
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $bl))
}

function Fill-Rect([System.Drawing.Bitmap]$b, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [byte]$r, [byte]$g, [byte]$bl, [byte]$a) {
    for ($y = $y0; $y -le $y1; $y++) {
        for ($x = $x0; $x -le $x1; $x++) {
            Set-Px $b $x $y $r $g $bl $a
        }
    }
}

function Fill-Poly([System.Drawing.Bitmap]$b, [int[]]$xs, [int[]]$ys, [byte]$r, [byte]$gn, [byte]$bl, [byte]$a) {
    $pts = New-Object System.Drawing.Point[] ($xs.Length)
    for ($i = 0; $i -lt $xs.Length; $i++) { $pts[$i] = New-Object System.Drawing.Point($xs[$i], $ys[$i]) }
    $gr = [System.Drawing.Graphics]::FromImage($b)
    $gr.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($a, $r, $gn, $bl))
    $gr.FillPolygon($brush, $pts)
    $brush.Dispose(); $gr.Dispose()
}

function Save-Png([System.Drawing.Bitmap]$b, [string]$name) {
    $b.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
    Write-Host "wrote $name.png"
}

# ---- tab_bar.png (46x200): fixed panel, no 9-slice ----
$b = New-Bitmap 46 200
Fill-Rect $b 0 0 45 199 16 16 16 224          # body
Fill-Rect $b 0 0 0 199 30 30 30 255           # left edge
Fill-Rect $b 45 0 45 199 58 58 58 255         # right edge (border toward main panel)
Save-Png $b 'tab_bar'

# ---- tab button base/hover/selected (40x24, 9-slice border 4) ----
function New-TabBtn([byte]$fr, [byte]$fg, [byte]$fb, [byte]$br, [byte]$bg, [byte]$bb, [byte]$hlAlpha) {
    $b = New-Bitmap 40 24
    Fill-Rect $b 0 0 39 23 $fr $fg $fb 255
    Fill-Rect $b 0 0 39 0 $br $bg $bb 255      # top ring
    Fill-Rect $b 0 23 39 23 $br $bg $bb 255    # bottom ring
    Fill-Rect $b 0 0 0 23 $br $bg $bb 255      # left ring
    Fill-Rect $b 39 0 39 23 $br $bg $bb 255    # right ring
    Fill-Rect $b 1 1 38 1 255 255 255 $hlAlpha # top inner highlight
    Fill-Rect $b 1 22 38 22 0 0 0 45           # bottom inner shade
    return $b
}
$b = New-TabBtn 38 38 38 62 62 62 18
Save-Png $b 'tab_btn'
$b = New-TabBtn 50 50 50 76 76 76 26
Save-Png $b 'tab_btn_hover'
$b = New-TabBtn 62 74 90 96 108 124 30
Save-Png $b 'tab_btn_sel'

# ---- menu_panel.png (96x80, 9-slice border 4) ----
$b = New-Bitmap 96 80
Fill-Rect $b 0 0 95 79 20 20 20 245
Fill-Rect $b 0 0 95 0 58 58 58 255
Fill-Rect $b 0 79 95 79 46 46 46 255
Fill-Rect $b 0 0 0 79 58 58 58 255
Fill-Rect $b 95 0 95 79 58 58 58 255
Fill-Rect $b 1 1 94 1 255 255 255 18          # top highlight
Fill-Rect $b 1 78 94 78 0 0 0 40
Save-Png $b 'menu_panel'

# ---- trade_panel.png (150x128, 9-slice border 6) ----
$b = New-Bitmap 150 128
Fill-Rect $b 0 0 149 127 20 20 20 245
Fill-Rect $b 0 0 149 0 58 58 58 255
Fill-Rect $b 0 127 149 127 46 46 46 255
Fill-Rect $b 0 0 0 127 58 58 58 255
Fill-Rect $b 149 0 149 127 58 58 58 255
Fill-Rect $b 1 1 148 1 255 255 255 18
Fill-Rect $b 1 126 148 126 0 0 0 40
Save-Png $b 'trade_panel'

# ---- barter.png (12x12): top arrow right, bottom arrow left ----
$b = New-Bitmap 12 12
# top arrow (right)
Fill-Rect $b 2 4 8 4 200 200 200 255
Fill-Poly $b @(8,10,8) @(2,4,6) 200 200 200 255
# bottom arrow (left)
Fill-Rect $b 4 8 10 8 200 200 200 255
Fill-Poly $b @(4,2,4) @(6,8,10) 200 200 200 255
Save-Png $b 'barter'

# ---- command.png (12x12): terminal chevron + underscore ----
$b = New-Bitmap 12 12
Fill-Poly $b @(2,5,2) @(2,5,8) 200 200 200 255
Fill-Rect $b 5 10 10 10 200 200 200 255
Save-Png $b 'command'

Write-Host 'all textures generated'
