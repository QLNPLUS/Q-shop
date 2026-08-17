# QShop GUI 贴图生成 — panel_add(添加条目窗口)/ panel_tab(编辑子商店窗口)
Add-Type -AssemblyName System.Drawing
$out = 'D:\idea\Q-shop\src\main\resources\assets\qshop\textures\gui'

function New-Bitmap([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }
function Set-Px([System.Drawing.Bitmap]$b, [int]$x, [int]$y, [byte]$r, [byte]$g, [byte]$bl, [byte]$a) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $b.Width -or $y -ge $b.Height) { return }
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $bl))
}
function Fill-Rect([System.Drawing.Bitmap]$b, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [byte]$r, [byte]$g, [byte]$bl, [byte]$a) {
    for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Set-Px $b $x $y $r $g $bl $a } }
}
function Save-Png([System.Drawing.Bitmap]$b, [string]$name) {
    $b.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose(); Write-Host "wrote $name.png"
}

# ---- panel_add.png (250x200): 添加条目窗口,深色底 + 绿色调描边 ----
$b = New-Bitmap 250 200
Fill-Rect $b 0 0 249 199 16 22 18 230          # 墨绿调底色
Fill-Rect $b 0 0 249 0 74 138 90 255           # 顶部亮绿边
Fill-Rect $b 0 1 249 1 46 86 56 255            # 顶部次亮绿边
Fill-Rect $b 0 199 249 199 40 76 50 255        # 底部边
Fill-Rect $b 0 0 0 199 46 86 56 255            # 左边
Fill-Rect $b 249 0 249 199 46 86 56 255        # 右边
Save-Png $b 'panel_add'

# ---- panel_tab.png (250x200): 编辑子商店窗口,深色底 + 蓝色调描边 ----
$b = New-Bitmap 250 200
Fill-Rect $b 0 0 249 199 16 18 24 230          # 墨蓝调底色
Fill-Rect $b 0 0 249 0 90 122 176 255          # 顶部亮蓝边
Fill-Rect $b 0 1 249 1 56 76 110 255           # 顶部次亮蓝边
Fill-Rect $b 0 199 249 199 46 62 92 255        # 底部边
Fill-Rect $b 0 0 0 199 56 76 110 255           # 左边
Fill-Rect $b 249 0 249 199 56 76 110 255       # 右边
Save-Png $b 'panel_tab'

Write-Host 'done'
