# SDMShop (sdmshop.snbt) -> QShop (shops/*.json) 转换脚本 v2
# 用法: pwsh -File sdm_to_qshop.ps1 <sdmshop.snbt路径> <输出目录>

param(
    [Parameter(Mandatory=$true)][string]$SnbtPath,
    [Parameter(Mandatory=$true)][string]$OutDir
)

$ErrorActionPreference = 'Stop'
$text = [System.IO.File]::ReadAllText($SnbtPath)

# ---- 字符级引号感知:从 startIdx 的 { 起,返回配对的 } 的索引 ----
function Get-MatchEnd([string]$t, [int]$startIdx) {
    $depth = 0; $inStr = $false
    for ($j = $startIdx; $j -lt $t.Length; $j++) {
        $c = $t[$j]
        if ($inStr) {
            if ($c -eq '"') { $inStr = $false }
        } else {
            if ($c -eq '"') { $inStr = $true }
            elseif ($c -eq '{') { $depth++ }
            elseif ($c -eq '}') { $depth--; if ($depth -eq 0) { return $j } }
        }
    }
    return $t.Length - 1
}

# ---- 数组块扫描:startAfterBracket 指向 '[' 之后,返回每个 {...} 块字符串 ----
function Get-ArrayBlocks([string]$t, [int]$startAfterBracket) {
    $blocks = [System.Collections.Generic.List[string]]::new()
    $i = $startAfterBracket
    while ($i -lt $t.Length) {
        while ($i -lt $t.Length -and ($t[$i] -in @(' ', "`t", "`n", "`r", ','))) { $i++ }
        if ($i -ge $t.Length) { break }
        $ch = $t[$i]
        if ($ch -eq ']') { break }
        if ($ch -eq '{') {
            $e = Get-MatchEnd $t $i
            $blocks.Add($t.Substring($i, $e - $i + 1))
            $i = $e + 1
        } else { $i++ }
    }
    return ,$blocks.ToArray()
}

# ---- 字段提取 ----
function Get-Field([string]$t, [string]$name) {
    $m = [regex]::Match($t, '(?m)^[ \t]*' + [regex]::Escape($name) + ': "(.*?)"')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}
function Get-Num([string]$t, [string]$name) {
    $m = [regex]::Match($t, '(?m)^[ \t]*' + [regex]::Escape($name) + ': (-?\d+)[Lb]?')
    if ($m.Success) { return [long]$m.Groups[1].Value }
    return $null
}
function Get-StrList([string]$t, [string]$name) {
    $m = [regex]::Match($t, '(?ms)^[ \t]*' + [regex]::Escape($name) + ': \[(.*?)\]')
    if (-not $m.Success) { return @() }
    $out = @()
    foreach ($sm in [regex]::Matches($m.Groups[1].Value, '"((?:[^"\\]|\\.)*)"')) { $out += $sm.Groups[1].Value }
    return @($out)
}
# 取 `name: { ... }` 复合块文本(引号感知配对),返回从 { 开始的完整对象
function Get-Compound([string]$t, [string]$name) {
    $m = [regex]::Match($t, '(?m)^[ \t]*' + [regex]::Escape($name) + ': \{')
    if (-not $m.Success) { return $null }
    $open = $m.Index + $m.Length - 1
    return $t.Substring($open, (Get-MatchEnd $t $open) - $open + 1)
}
# 物品:{ id, Count, tag? }
function Get-Item([string]$t, [string]$name) {
    $obj = Get-Compound $t $name
    if (-not $obj) { return $null }
    $id = Get-Field $obj 'id'
    $cnt = Get-Num $obj 'Count'
    $tag = Get-Compound $obj 'tag'
    return @{ id = $id; count = if ($cnt -ne $null) { [int]$cnt } else { 1 }; tag = $tag }
}

# ---- 方括号数组配对(引号感知) ----
function Get-MatchEndArr([string]$t, [int]$startIdx) {
    $depth = 0; $inStr = $false
    for ($j = $startIdx; $j -lt $t.Length; $j++) {
        $c = $t[$j]
        if ($inStr) {
            if ($c -eq '"') { $inStr = $false }
        } else {
            if ($c -eq '"') { $inStr = $true }
            elseif ($c -eq '[') { $depth++ }
            elseif ($c -eq ']') { $depth--; if ($depth -eq 0) { return $j } }
        }
    }
    return $t.Length - 1
}

# ---- 把 SDMShop 的 NBT 标签(换行分隔、无逗号)转为标准 SNBT 单行 ----
function Convert-TagToSnbt([string]$tagText) {
    $tagText = $tagText.Trim()
    if (-not $tagText.StartsWith('{')) { return $tagText }
    $inner = $tagText.Substring(1, $tagText.Length - 2)
    $parts = [System.Collections.Generic.List[string]]::new()
    $i = 0; $n = $inner.Length
    while ($i -lt $n) {
        while ($i -lt $n -and ($inner[$i] -match '\s' -or $inner[$i] -eq ',')) { $i++ }
        if ($i -ge $n) { break }
        $keyStart = $i
        while ($i -lt $n -and $inner[$i] -ne ':') { $i++ }
        $key = $inner.Substring($keyStart, $i - $keyStart).Trim()
        $i++
        if ($i -ge $n) { break }
        while ($i -lt $n -and ($inner[$i] -match '\s')) { $i++ }
        $val = ''
        if ($i -lt $n -and $inner[$i] -eq '{') {
            $e = Get-MatchEnd $inner $i
            $val = Convert-TagToSnbt ($inner.Substring($i, $e - $i + 1))
            $i = $e + 1
        } elseif ($i -lt $n -and $inner[$i] -eq '[') {
            $e = Get-MatchEndArr $inner $i
            $arrInner = $inner.Substring($i + 1, $e - $i - 1)
            $elems = [System.Collections.Generic.List[string]]::new()
            $k = 0
            # 类型化数组 [I; 1 2 3] / [B; ...] / [L; ...]
            $typed = [regex]::Match($arrInner.Trim(), '^([BLI]);\s*')
            if ($typed.Success) {
                $nums = ($arrInner.Substring($typed.Length) -replace '\s+', ' ').Trim()
                $val = "[$($typed.Groups[1].Value); $nums]"
            } else {
                while ($k -lt $arrInner.Length) {
                    while ($k -lt $arrInner.Length -and ($arrInner[$k] -match '\s' -or $arrInner[$k] -eq ',')) { $k++ }
                    if ($k -ge $arrInner.Length) { break }
                    $c0 = $arrInner[$k]
                    if ($c0 -eq '{') {
                        $ee = Get-MatchEnd $arrInner $k
                        $elems.Add((Convert-TagToSnbt ($arrInner.Substring($k, $ee - $k + 1))))
                        $k = $ee + 1
                    } elseif ($c0 -eq '"') {
                        $qe = $k + 1
                        while ($qe -lt $arrInner.Length -and -not ($arrInner[$qe] -eq '"' -and $arrInner[$qe - 1] -ne '\')) { $qe++ }
                        $elems.Add($arrInner.Substring($k, $qe - $k + 1))
                        $k = $qe + 1
                    } elseif ($c0 -eq '[') {
                        $ae = Get-MatchEndArr $arrInner $k
                        $elems.Add(($arrInner.Substring($k, $ae - $k + 1) -replace '\s+', ' ').Trim())
                        $k = $ae + 1
                    } else {
                        $te2 = $k
                        while ($te2 -lt $arrInner.Length -and -not ($arrInner[$te2] -match '\s') -and $arrInner[$te2] -ne ',' -and $arrInner[$te2] -ne ']' -and $arrInner[$te2] -ne '{' -and $arrInner[$te2] -ne '[') { $te2++ }
                        $elems.Add($arrInner.Substring($k, $te2 - $k).Trim())
                        $k = $te2
                    }
                }
                $val = '[' + ($elems -join ', ') + ']'
            }
            $i = $e + 1
        } elseif ($i -lt $n -and $inner[$i] -eq '"') {
            $qEnd = $i + 1
            while ($qEnd -lt $n) {
                if ($inner[$qEnd] -eq '"' -and ($qEnd -eq 0 -or $inner[$qEnd - 1] -ne '\')) { break }
                $qEnd++
            }
            $val = $inner.Substring($i, $qEnd - $i + 1)
            $i = $qEnd + 1
        } else {
            $tEnd = $i
            while ($tEnd -lt $n -and -not ($inner[$tEnd] -match '\s') -and $inner[$tEnd] -ne ',' `
                    -and $inner[$tEnd] -ne '}' -and $inner[$tEnd] -ne '{' -and $inner[$tEnd] -ne ']') { $tEnd++ }
            $val = $inner.Substring($i, $tEnd - $i).Trim()
            $i = $tEnd
        }
        if ($key -ne '' -and $val -ne '') { $parts.Add("$key`: $val") }
    }
    return '{' + ($parts -join ', ') + '}'
}

# ---- 主流程 ----
$m = [regex]::Match($text, 'shopTabs: \[')
if (-not $m.Success) { throw '未找到 shopTabs' }
$tabStrs = Get-ArrayBlocks $text ($m.Index + $m.Length)
Write-Host "解析到 $($tabStrs.Count) 个 tab"

$shopName = ''
$nm = [regex]::Match($text, '(?m)^[ \t]*shopName: "(.*?)"')
if ($nm.Success) { $shopName = $nm.Groups[1].Value }

function ItemToJson($it) {
    if (-not $it) { return $null }
    $o = @{ item = $it.id }
    if ($it.count -gt 1) { $o.count = $it.count }
    if ($it.tag) { $o.nbt = Convert-TagToSnbt $it.tag }
    return $o
}

$tabsJson = @()
foreach ($tabStr in $tabStrs) {
    # 条目: tabEntry 数组
    $entries = @()
    $entryRaws = @()
    $te = [regex]::Match($tabStr, 'tabEntry: \[')
    if ($te.Success) {
        foreach ($entryStr in (Get-ArrayBlocks $tabStr ($te.Index + $te.Length))) {
            $entryRaws += $entryStr
            $eType = Get-Field $entryStr 'shopEntryTypeID'
            $price = Get-Num $entryStr 'entryPrice'
            $count = Get-Num $entryStr 'entryCount'
            $limit = Get-Num $entryStr 'limit'
            $eTitle = Get-Field $entryStr 'title'
            $eDesc = Get-StrList $entryStr 'description'
            $eQuests = Get-StrList $entryStr 'questID'
            $eStages = Get-StrList $entryStr 'gameStages'
            $money = Get-Field $entryStr 'moneyID'
            $sellerType = Get-Field $entryStr 'shopSellerTypeID'
            $sellerItem = Get-Item $entryStr 'item'   # shopSeller.item(物品换物品时玩家付出的物品)
            $eIcon = Get-Item $entryStr 'icon'
            $cmdText = Get-Field $entryStr 'command'
            $cmdSilent = Get-Num $entryStr 'silent'
            $cmdOp = Get-Num $entryStr 'elevatePerms'
            $iconPathNew = Get-Item $entryStr 'iconPathNew'
            $stack = Get-Item $entryStr 'itemStack'
            $entries += @{
                type = $eType; price = $price; count = $count; limit = $limit
                title = $eTitle; desc = $eDesc; quests = $eQuests; stages = $eStages
                money = $money; sellerType = $sellerType; sellerItem = $sellerItem
                icon = $eIcon; command = $cmdText
                cmdSilent = $cmdSilent; cmdOp = $cmdOp; iconPathNew = $iconPathNew; stack = $stack
            }
        }
    }

    # tab 级字段:先移除条目块文本,避免条目字段干扰
    $tabLevel = $tabStr
    foreach ($raw in $entryRaws) {
        $idx = $tabLevel.IndexOf($raw.Substring(0, [Math]::Min(40, $raw.Length)))
        if ($idx -ge 0) { $tabLevel = $tabLevel.Remove($idx, $raw.Length) }
    }
    # tab 标题:取缩进最小的 title(条目 title 缩进更深)
    $tabTitle = $null; $minInd = 99999
    foreach ($tm2 in [regex]::Matches($tabStr, '(?m)^([ \t]*)title: "(.*?)"')) {
        $ind = $tm2.Groups[1].Value.Length
        if ($ind -lt $minInd) { $minInd = $ind; $tabTitle = $tm2.Groups[2].Value }
    }
    $tabIcon = Get-Item $tabLevel 'icon'
    $tabDesc = Get-StrList $tabLevel 'description'
    $tabQuests = Get-StrList $tabLevel 'questID'
    $tabStages = Get-StrList $tabLevel 'gameStages'

    $tabObj = @{}
    if ($tabTitle) { $tabObj.name = $tabTitle }
    $tabIconJson = ItemToJson $tabIcon
    if ($tabIconJson) { $tabObj.icon = $tabIconJson }
    if ($tabQuests.Count -gt 0) { $tabObj.requiredQuests = @($tabQuests) }
    if ($tabStages.Count -gt 0) { $tabObj.requiredStages = @($tabStages) }

    $entriesJson = @()
    $skipped = 0
    foreach ($en in $entries) {
        $e = @{}
        if ($en.type -eq 'commandType') {
            $e.type = 'COMMAND'
            $disp = ItemToJson $en.iconPathNew
            if ($disp) { $e.displayItem = $disp }
            if ($en.command) {
                $cmd = $en.command
                if ($cmd.StartsWith('/')) { $cmd = $cmd.Substring(1) }
                $e.commands = @(@{ command = $cmd; op = ($en.cmdOp -eq 1); silent = ($en.cmdSilent -ne 0) })
            }
        } else {
            $stack = $en.stack
            if (-not $stack -or $stack.id -eq 'minecraft:air') { $skipped++; continue }
            if ($en.sellerType -eq 'item' -and $en.sellerItem) {
                # 物品换物品:玩家付出 shopSeller.item,获得 entryType.itemStack
                $e.type = 'BARTER'
                $cnt = if ($en.count -ne $null) { $en.count } else { $stack.count }
                $recv = @{ item = $stack.id; count = [int]$cnt }
                if ($stack.tag) { $recv.nbt = Convert-TagToSnbt $stack.tag }
                $e.receive = @($recv)
                $give = @{ item = $en.sellerItem.id; count = [int]$en.sellerItem.count }
                if ($en.sellerItem.tag) { $give.nbt = Convert-TagToSnbt $en.sellerItem.tag }
                $e.give = @($give)
            } else {
                $e.type = 'BUY'
                $cnt = if ($en.count -ne $null) { $en.count } else { $stack.count }
                $item = @{ item = $stack.id; count = [int]$cnt }
                if ($stack.tag) { $item.nbt = Convert-TagToSnbt $stack.tag }
                $e.item = $item
                if ($en.price -ne $null -and $en.price -gt 0) {
                    $e.price = [double]$en.price
                    $e.currency = if ($en.money) { $en.money } else { 'base_money' }
                }
            }
            if ($en.icon -and $en.icon.id -ne 'minecraft:barrier') {
                $e.displayItem = (ItemToJson $en.icon)
            }
        }
        if ($en.title) { $e.displayName = $en.title }
        if ($en.desc.Count -gt 0) { $e.description = ($en.desc -join "`n") }
        if ($en.limit -ne $null -and $en.limit -gt 0) { $e.playerLimit = [int]$en.limit }
        if ($en.quests.Count -gt 0) { $e.requiredQuests = @($en.quests) }
        if ($en.stages.Count -gt 0) { $e.requiredStages = @($en.stages) }
        $entriesJson += $e
    }
    if ($entriesJson.Count -gt 0) { $tabObj.entries = @($entriesJson) }
    $tabsJson += $tabObj
    Write-Host ("  tab '{0}': 条目 {1}(跳过占位 {2})" -f $tabTitle, $entriesJson.Count, $skipped)
}

$shop = @{
    id = 'sdm'
    displayName = if ($shopName) { $shopName } else { '综合商店' }
    currency = 'base_money'
    tabs = @($tabsJson)
}

$shopsDir = Join-Path $OutDir 'shops'
New-Item -ItemType Directory -Force -Path $shopsDir | Out-Null
$shopJson = ConvertTo-Json $shop -Depth 12
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $shopsDir 'sdm.json'), $shopJson, $utf8NoBom)
Write-Host "已写出: $(Join-Path $shopsDir 'sdm.json')"

$cur = @{ currencies = @(
    @{ id = 'coins'; name = '金币'; color = '#FFD700' },
    @{ id = 'points'; name = '点数'; color = '#55FFFF' },
    @{ id = 'base_money'; name = '货币'; color = '#55FF55' }
) }
[System.IO.File]::WriteAllText((Join-Path $OutDir 'currencies.json'), (ConvertTo-Json $cur -Depth 5), $utf8NoBom)
Write-Host "已写出: $(Join-Path $OutDir 'currencies.json')"
