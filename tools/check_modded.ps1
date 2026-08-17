$j = Get-Content "D:\idea\Q-shop\converted_sdmshop\shops\sdm.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$vanilla = 0
$modded = 0
$modItems = @{}
foreach ($t in $j.tabs) {
    if (-not $t.entries) { continue }
    foreach ($e in $t.entries) {
        $ids = @()
        if ($e.item) { $ids += $e.item.item }
        if ($e.give) { foreach ($g in $e.give) { $ids += $g.item } }
        if ($e.receive) { foreach ($r in $e.receive) { $ids += $r.item } }
        $allVanilla = $true
        foreach ($id in $ids) {
            if ($id -and -not $id.StartsWith('minecraft:')) {
                $allVanilla = $false
                $ns = $id.Split(':')[0]
                if ($modItems.ContainsKey($ns)) { $modItems[$ns]++ } else { $modItems[$ns] = 1 }
            }
        }
        if ($allVanilla) { $vanilla++ } else { $modded++ }
    }
}
"vanilla-only entries: $vanilla / modded entries: $modded"
"--- modded namespaces ---"
$modItems.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object { "  $($_.Key): $($_.Value)" }
