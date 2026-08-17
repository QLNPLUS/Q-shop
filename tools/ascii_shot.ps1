param([string]$Path, [int]$W = 120, [int]$H = 70)
Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile($Path)
"== $Path  $($bmp.Width)x$($bmp.Height)"
$sw = [Math]::Max(1, [int]($bmp.Width / $W))
$sh = [Math]::Max(1, [int]($bmp.Height / $H))
for ($by = 0; $by -lt $H; $by++) {
    $row = ''
    for ($bx = 0; $bx -lt $W; $bx++) {
        $l = 0.0
        for ($dy = 0; $dy -lt $sh; $dy += 2) {
            for ($dx = 0; $dx -lt $sw; $dx += 2) {
                $x = [Math]::Min($bmp.Width - 1, $bx * $sw + $dx)
                $y = [Math]::Min($bmp.Height - 1, $by * $sh + $dy)
                $p = $bmp.GetPixel($x, $y)
                # luminance; ignore near-black background
                $l += (0.3 * $p.R + 0.6 * $p.G + 0.1 * $p.B) * ($p.A / 255.0)
            }
        }
        $n = [Math]::Max(1, [int](($sw / 2) * ($sh / 2)))
        $v = $l / $n
        if ($v -gt 200) { $row += '#' } elseif ($v -gt 140) { $row += 'o' } elseif ($v -gt 80) { $row += '+' } elseif ($v -gt 35) { $row += '.' } else { $row += ' ' }
    }
    $row
}
$bmp.Dispose()
