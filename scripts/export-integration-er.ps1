param(
    [string]$ModelDirectory = (Join-Path $PSScriptRoot '../resource/项目发开文档/数据同步功能开发文档/数据库模型')
)

$ErrorActionPreference = 'Stop'
$ModelDirectory = (Resolve-Path -LiteralPath $ModelDirectory).Path
$source = Join-Path $ModelDirectory 'integration_schema-ER模型.drawio'
$document = [xml](Get-Content -LiteralPath $source -Raw -Encoding UTF8)
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Preserve the existing tables and field content. Only the orthogonal edge
# routes are made explicit, since SVG has no diagrams.net routing engine.
$overviewRoutes = @{
    '0' = '400,171 470,171 470,427 540,427'
    '1' = '735,310 735,210'
    '2' = '220,262 220,380'
    '3' = '400,741 430,741 430,224 400,224'
    '4' = '40,171 20,171 20,1008 40,1008'
    '5' = '360,262 360,285 1010,285 1010,610 1050,610'
    '6' = '930,360 980,360 980,158 1050,158'
    '7' = '930,420 1000,420 1000,398 1050,398'
    '8' = '735,544 735,915 1050,915'
    '9' = '930,490 960,490 960,1171 1050,1171'
    '10' = '930,510 990,510 990,690 1050,690'
    '11' = '540,357 505,357 505,497 540,497'
}
$overviewLabels = @{
    '0' = @(440, 283); '1' = @(745, 260); '2' = @(230, 332)
    '3' = @(440, 610); '4' = @(28, 902); '5' = @(720, 277)
    '6' = @(985, 251); '7' = @(950, 300); '8' = @(745, 901)
    '9' = @(745, 1158); '10' = @(755, 570); '11' = @(405, 560)
}

foreach ($diagram in $document.mxfile.diagram) {
    $isOverview = $diagram.id -eq 'overview'
    $label = if ($isOverview) { '关系总览' } else { '全字段模型' }
    $parts = New-Object 'System.Collections.Generic.List[string]'
    $parts.Add(@'
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xhtml="http://www.w3.org/1999/xhtml" width="4100" height="3000" viewBox="0 0 2050 1500">
<rect width="2050" height="1500" fill="#ffffff"/>
<defs>
  <marker id="one" viewBox="0 0 16 16" refX="0" refY="8" markerWidth="12" markerHeight="12" orient="auto-start-reverse" markerUnits="userSpaceOnUse"><path d="M5 1V15 M10 1V15" stroke="context-stroke" stroke-width="1.5" fill="none"/></marker>
  <marker id="many" viewBox="0 0 24 20" refX="24" refY="10" markerWidth="24" markerHeight="20" orient="auto" markerUnits="userSpaceOnUse"><circle cx="5" cy="10" r="4" stroke="context-stroke" stroke-width="1.5" fill="white"/><path d="M10 10L24 1 M10 10L24 10 M10 10L24 19" stroke="context-stroke" stroke-width="1.5" fill="none"/></marker>
</defs>
'@)
    $labels = New-Object 'System.Collections.Generic.List[string]'
    foreach ($cell in $diagram.mxGraphModel.root.mxCell) {
        if ($cell.edge -ne '1') { continue }
        $number = ($cell.id -split '_edge_')[-1]
        $logical = $cell.style -match 'dashed=1'
        $color = if ($logical) { '#64748b' } else { '#2563eb' }
        $strokeWidth = if ($logical) { 1.3 } else { 2 }
        $dash = if ($logical) { 'stroke-dasharray="5 5"' } else { '' }
        if ($isOverview) {
            $points = $overviewRoutes[$number]
            $position = $overviewLabels[$number]
        } elseif ($number -eq '0') {
            $points = '490,208 515,208 515,436 540,436'
            $position = @(300, 366)
        } else {
            $points = '765,792 765,845'
            $position = @(775, 825)
        }
        $parts.Add("<polyline points=`"$points`" fill=`"none`" stroke=`"$color`" stroke-width=`"$strokeWidth`" $dash marker-start=`"url(#one)`" marker-end=`"url(#many)`"/>")
        $cardinality = if ($cell.style -match 'endArrow=ERzeroToOne') { '1 : 0..1' } elseif ($cell.style -match 'startArrow=ERzeroToOne') { '0..1 : 0..N' } else { '1 : 0..N' }
        $text = [System.Security.SecurityElement]::Escape(($cell.value + ' [' + $cardinality + ']'))
        $labels.Add("<text x=`"$($position[0])`" y=`"$($position[1])`" font-family=`"Microsoft YaHei,Arial,sans-serif`" font-size=`"10`" fill=`"$color`" stroke=`"white`" stroke-width=`"4`" paint-order=`"stroke`">$text</text>")
    }
    foreach ($cell in $diagram.mxGraphModel.root.mxCell) {
        if ($cell.vertex -ne '1') { continue }
        $geometry = $cell.mxGeometry
        $x = $geometry.x; $y = $geometry.y
        $width = $geometry.width; $height = $geometry.height
        if ($cell.id -like '*_note') {
            $note = [System.Security.SecurityElement]::Escape($cell.value)
            $parts.Add("<text x=`"$x`" y=`"38`" font-family=`"Microsoft YaHei,Arial,sans-serif`" font-size=`"14`" fill=`"#0f172a`">$note</text>")
        } else {
            $border = [regex]::Match($cell.style, 'strokeColor=([^;]+)').Groups[1].Value
            $parts.Add("<rect x=`"$x`" y=`"$y`" width=`"$width`" height=`"$height`" fill=`"white`" stroke=`"$border`" stroke-width=`"1`"/>")
            $parts.Add("<foreignObject x=`"$x`" y=`"$y`" width=`"$width`" height=`"$height`"><div xmlns=`"http://www.w3.org/1999/xhtml`" style=`"font-family:Microsoft YaHei,Arial,sans-serif;line-height:1.2;`">$($cell.value)</div></foreignObject>")
        }
    }
    $parts.AddRange($labels)
    $parts.Add('<text x="40" y="1470" font-family="Microsoft YaHei,Arial,sans-serif" font-size="13" fill="#475569">integration_schema · 14 张表 · 130 个字段 · 2 个实际外键 ｜ PK 主键；UK 唯一键；U* 联合唯一键成员；? 可空</text>')
    $parts.Add('</svg>')
    $svgPath = Join-Path $ModelDirectory ("integration_schema-ER模型-$label.svg")
    [System.IO.File]::WriteAllText($svgPath, ($parts -join "`n"), $utf8)
    Write-Output "Exported: $svgPath"
}
