<#
.SYNOPSIS
  Carries the upgrade-26-3 world (scenarios upgrade-26-3-author / -verify) from the authoring node to a node to verify.

.DESCRIPTION
  Run with no client open. The first call archives the author run's state (versions/<From>/run-smoke: saves/bc-upgrade,
  snapshots-server, snapshots-client) under build/upgrade-world/pristine; every call then replaces the target node's
  copies with that archive, so a verify run always starts from the world as the author run left it (-RefreshArchive
  re-archives after a new author run). Blueprint B's server file (the newest one: the author scans it last) is deleted
  from the copy, so the target server only gets B through the Electronic Library upload, which reads the client's file.
  -Config copies the author node's buildcraftunofficial-common.toml over the target's with disableRfPipe = true (B8),
  after keeping the target's own file in the archive folder; -RestoreConfig puts that file back.

.EXAMPLE
  pwsh scripts/smoke/Copy-UpgradeWorld.ps1 -To 26.3 -Config     # before verifying on 26.3
  pwsh scripts/smoke/Copy-UpgradeWorld.ps1 -To 26.2             # before the 26.2 baseline
  pwsh scripts/smoke/Copy-UpgradeWorld.ps1 -To 26.3 -RestoreConfig
#>
[CmdletBinding()]
param(
    [string]$From = '26.2',
    [Parameter(Mandatory)][string]$To,
    [switch]$Config,
    [switch]$RestoreConfig,
    [switch]$RefreshArchive
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$src = Join-Path $repo "versions/$From/run-smoke"
$dst = Join-Path $repo "versions/$To/run-smoke"
$arch = Join-Path $repo 'build/upgrade-world'
$pristine = Join-Path $arch 'pristine'
$parts = @{ 'saves/bc-upgrade' = 'bc-upgrade'; 'snapshots-server' = 'snapshots-server'; 'snapshots-client' = 'snapshots-client' }
$cfgName = 'config/buildcraftunofficial-common.toml'
$cfgKept = Join-Path $arch "$To-common.toml.orig"

if ($RestoreConfig) {
    if (-not (Test-Path $cfgKept)) { throw "no kept config for $To at $cfgKept" }
    Copy-Item -LiteralPath $cfgKept -Destination (Join-Path $dst $cfgName) -Force
    "restored $To's $cfgName"
    return
}
if (@(Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' }).Count) {
    throw 'a Minecraft client is running: close it first (it may still be writing the world)'
}
if ($RefreshArchive -and (Test-Path $pristine)) { Remove-Item -Recurse -Force $pristine }
if (-not (Test-Path $pristine)) {
    New-Item -ItemType Directory -Force $pristine | Out-Null
    foreach ($p in $parts.GetEnumerator()) {
        $from = Join-Path $src $p.Key
        if (-not (Test-Path $from)) { throw "missing ${from}: run upgrade-26-3-author on $From first" }
        Copy-Item -Recurse -LiteralPath $from -Destination (Join-Path $pristine $p.Value)
    }
    "archived $From's author state in $pristine"
}
foreach ($p in $parts.GetEnumerator()) {
    $target = Join-Path $dst $p.Key
    if (Test-Path $target) { Remove-Item -Recurse -Force -LiteralPath $target }
    Copy-Item -Recurse -LiteralPath (Join-Path $pristine $p.Value) -Destination $target
}
$server = @(Get-ChildItem (Join-Path $dst 'snapshots-server') -Filter *.bcnbt | Sort-Object LastWriteTime)
if ($server.Count -ne 3) { throw "expected 3 server snapshots (A, T, B), found $($server.Count)" }
# Copy-Item keeps the author run's write times, which is what picks B; the template is the smallest file (no palette).
if ($server[-1].Length -eq ($server | Measure-Object Length -Minimum).Minimum) {
    throw "the newest server snapshot $($server[-1].Name) looks like the template: were the files copied without their times?"
}
Remove-Item -LiteralPath $server[-1].FullName
"copied to $To; dropped blueprint B's server file $($server[-1].Name)"

if ($Config) {
    $cfg = Join-Path $dst $cfgName
    if (-not (Test-Path $cfgKept)) { Copy-Item -LiteralPath $cfg -Destination $cfgKept }
    $text = Get-Content -Raw -LiteralPath (Join-Path $src $cfgName)
    if ($text -notmatch 'disableRfPipe = false') { throw "$From's config has no 'disableRfPipe = false' to flip" }
    Set-Content -NoNewline -Encoding utf8 -LiteralPath $cfg -Value ($text -replace 'disableRfPipe = false', 'disableRfPipe = true')
    "wrote $To's $cfgName from $From's with disableRfPipe = true (its own kept in $cfgKept)"
}
