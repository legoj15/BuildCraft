# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/

<#
.SYNOPSIS
  Runs several smoke scenarios on ONE node, one runner invocation each, and writes a combined table. For a long
  unattended pass (every scenario on a node, on a machine other sessions also run clients on).

.DESCRIPTION
  Invoke-InClientSmoke.ps1 plays one scenario per call and exits at once (3) while another run holds the smoke lock.
  This wrapper calls it once per scenario, waits out a busy lock instead of giving up (-LockWaitMinutes), kills a
  scenario that overruns (-ScenarioTimeoutMinutes: the runner's process tree, then the client this attempt started),
  and reads each run's report.json back into <OutDir>/batch-summary.md and batch-summary.json.

  Output: <OutDir>/<scenario>[-<lang>]/ is the runner's own -OutDir for that scenario (summary.md, <node>/report.json,
  screenshots), <OutDir>/<scenario>[-<lang>].console.log its console (a language other than en_us is part of both
  names, so the same scenario in two languages keeps two folders and two logs). Re-running the same -OutDir replaces
  those scenarios' folders; -Scenarios picks which.

  A scenario the runner skips is shown as SKIPPED, not as a failure: one whose "loaders" leave out this node's loader,
  one marked "englishOnly" when -Lang names another language (it clicks by English widget text), and one that needs
  something the node's loader lacks (a recipe viewer on a Forge node) or whose mod aliases the node does not define; the reason is in the table.

  Exit code: 0 when every scenario ran clean or was skipped, 1 when any step failed, a node was fatal, a scenario
  timed out or never got the lock.

.EXAMPLE
  pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric                                  # every scenario that runs unaided
  pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric -Scenarios guide-robot,quarry-pit
  pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric -Scenarios rei-parity -Lang ru_ru,zh_cn
  pwsh scripts/smoke/Invoke-SmokeBatch.ps1 -Node 26.3-fabric -Skip lang-overflow,port-26-3 -OutDir build/smoke/batch-1
#>
[CmdletBinding()]
param(
    [string]$Node = '26.3-fabric',
    # Scenario names (file names under scenarios/ without .json). Default: all of them, alphabetically, except the three
    # that need a real-mouse driver or a listener beside them (see $needsCompany below).
    [string[]]$Scenarios = @(),
    [string[]]$Skip = @(),
    # Each scenario is run once per language (en_us, ru_ru, ...). A name other than en_us is added to the folder name.
    [string[]]$Lang = @('en_us'),
    [string]$OutDir,
    [string]$McDevBridgeRoot,
    # Passed to the runner as -BootMinutes when above 0 (a node's first boot downloads assets and can need 30 or more).
    [int]$BootMinutes = 0,
    # Passed to the runner as -NoDaemon (see there: needed whenever Gradle daemons from another Windows session may idle).
    [switch]$NoDaemon,
    # Optional dev-runtime mod aliases for every scenario of the batch, passed on as the runner's -Mods (-PwithMods).
    [string[]]$Mods = @(),
    # Passed on as the runner's -Save / -SaveSeed / -FreshSave: every scenario plays a fresh copy of the seed save, so
    # runs of the same scenario with and without mods start from the same world.
    [string]$Save = '',
    [string]$SaveSeed = '',
    [switch]$FreshSave,
    # How long one scenario may hold the batch (the runner's own boot wait is 10 minutes of it).
    [int]$ScenarioTimeoutMinutes = 45,
    # How long to keep retrying while another session's run holds the machine-wide smoke lock.
    [int]$LockWaitMinutes = 240
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
. (Join-Path $here 'SmokeGuard.ps1')

function Test-SmokeLockFree {
    # True when no live process holds the machine-wide smoke lock (the runner takes it and, losing a race, exits 3).
    $held = Read-SmokeLock (Get-SmokeLockPath)
    -not $held -or -not (Test-SmokeLockHolderAlive $held)
}
$repo = (Resolve-Path (Join-Path $here '../..')).Path
$scenarioDir = Join-Path $here 'scenarios'
# `pwsh -File ... -Scenarios a,b` hands a list over as ONE string; accept both forms.
function Split-List($Values) { @($Values | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
$Scenarios = Split-List $Scenarios
$Skip = Split-List $Skip
$Lang = Split-List $Lang
$Mods = Split-List $Mods
if (-not $Lang.Count) { $Lang = @('en_us') }
# Scenarios that wait while something else acts: a real-mouse driver (Drive-RealMouse.ps1) or a person listening.
# Alone, the two real-mouse ones fail their expectFile step (the driver's marker never appears) and pipe-sounds would
# show PASS having checked nothing, so the default list leaves them out; name one in -Scenarios to run it (with its
# driver).
$needsCompany = @('zone-planner-drag', 'library-scrollbar-drag', 'pipe-sounds')
if (-not $Scenarios.Count) {
    $Scenarios = @(Get-ChildItem -File -Path $scenarioDir -Filter '*.json' | Sort-Object Name | ForEach-Object BaseName | Where-Object { $_ -notin $needsCompany })
    Write-Host "not in the default list (they need a driver or a person): $($needsCompany -join ', ')"
}
$Scenarios = @($Scenarios | Where-Object { $_ -notin $Skip })
foreach ($s in $Scenarios) { if (-not (Test-Path (Join-Path $scenarioDir "$s.json"))) { throw "no scenario '$s' under $scenarioDir" } }
if (-not $OutDir) { $OutDir = Join-Path $repo ('build/smoke/batch-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir, (Get-Location).Path)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$pwshExe = (Get-Process -Id $PID).Path

$rows = @()
$batchClock = [System.Diagnostics.Stopwatch]::StartNew()
foreach ($name in $Scenarios) {
    # Not $lang: PowerShell names are case-insensitive, so that would overwrite the -Lang list after the first scenario.
    foreach ($language in $Lang) {
        $tag = if ($language -eq 'en_us') { $name } else { "$name-$language" }
        $out = Join-Path $OutDir $tag
        $console = Join-Path $OutDir "$tag.console.log"
        [string[]]$runArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $here 'Invoke-InClientSmoke.ps1'),
            '-Nodes', $Node, '-Scenario', (Join-Path $scenarioDir "$name.json"), '-OutDir', $out, '-Lang', $language)
        if ($McDevBridgeRoot) { $runArgs += @('-McDevBridgeRoot', $McDevBridgeRoot) }
        if ($BootMinutes -gt 0) { $runArgs += @('-BootMinutes', [string]$BootMinutes) }
        if ($NoDaemon) { $runArgs += '-NoDaemon' }
        if ($Mods.Count) { $runArgs += @('-Mods', ($Mods -join ',')) }
        if ($Save) { $runArgs += @('-Save', $Save) }
        if ($SaveSeed) { $runArgs += @('-SaveSeed', $SaveSeed) }
        if ($FreshSave) { $runArgs += '-FreshSave' }

        $clock = [System.Diagnostics.Stopwatch]::StartNew()
        $lockDeadline = (Get-Date).AddMinutes($LockWaitMinutes)
        $exit = $null
        $waitedForLock = [TimeSpan]::Zero
        $lockLosses = 0
        while ($true) {
            # Look at the lock every few seconds instead of booting a runner each time: another session's sweeps take
            # it node after node with only seconds between, which a one-minute retry would miss for hours.
            $lockClock = [System.Diagnostics.Stopwatch]::StartNew()
            while (-not (Test-SmokeLockFree)) {
                if ((Get-Date) -gt $lockDeadline) { break }
                Start-Sleep -Seconds 3
            }
            $waitedForLock += $lockClock.Elapsed
            if ((Get-Date) -gt $lockDeadline -and -not (Test-SmokeLockFree)) {
                Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag gave up waiting for the smoke lock"
                $exit = 3
                break
            }
            Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag on ${Node}: starting"
            # Only now, with the lock free: a batch that never gets the lock keeps the previous run's folder.
            if (Test-Path -LiteralPath $out) { Remove-Item -Recurse -Force -LiteralPath $out }
            $attemptStart = Get-Date
            $proc = Start-Process -FilePath $pwshExe -ArgumentList @($runArgs | ForEach-Object { if ($_ -match '\s') { "`"$_`"" } else { $_ } }) -WorkingDirectory $repo -PassThru -WindowStyle Hidden `
                -RedirectStandardOutput $console -RedirectStandardError "$console.err"
            # A longer boot wait than the runner's default 10 minutes extends the scenario's budget by the difference.
            $budgetMinutes = $ScenarioTimeoutMinutes + [math]::Max(0, $BootMinutes - 10)
            if (-not $proc.WaitForExit($budgetMinutes * 60000)) {
                # Under the runner: its Gradle wrapper and, with -NoDaemon, the client it started.
                & taskkill /PID $proc.Id /T /F | Out-Null
                # Without -NoDaemon the client is a child of a reused Gradle daemon, outside that tree: stop the client
                # bridge.json names when it started during this attempt (an older one is not this run's).
                try {
                    $bridge = Get-Content -Raw -LiteralPath (Join-Path $HOME '.mcdevbridge/bridge.json') -ErrorAction Stop | ConvertFrom-Json
                    $client = Get-Process -Id ([int]$bridge.pid) -ErrorAction Stop
                    if ($client.ProcessName -in 'java', 'javaw' -and $client.StartTime -ge $attemptStart) { Stop-Process -Id $client.Id -Force; Write-Host "stopped the client left behind (pid $($client.Id))" }
                } catch { }
                $exit = 124
                Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag timed out after $budgetMinutes min, stopped"
                break
            }
            $exit = $proc.ExitCode
            if ($exit -ne 3) { break }
            # A lock that reads as free here but that the runner can never take (an unreadable lock file) must not loop for ever.
            if (++$lockLosses -ge 20) { Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag lost the smoke lock 20 times running, giving up"; break }
            Start-Sleep -Seconds 3
            # 3 = another run holds the machine-wide smoke lock: the runner booted nothing. Wait and ask again.
            # Another run took the lock between the check and the runner's own: wait for it again.
            Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag smoke lock busy: $((Get-Content -Raw -LiteralPath "$console.err" -ErrorAction SilentlyContinue))"
        }
        $clock.Stop()

        $reportPath = Join-Path $out "$Node/report.json"
        $row = [ordered]@{
            scenario = $tag; exit = $exit; status = ''; steps = 0; failed = 0; bcErrors = 0; fatal = ''; firstFailure = ''
            minutes = [math]::Round(($clock.Elapsed - $waitedForLock).TotalMinutes, 1); outDir = $out
        }
        $r = $null
        # No lock means no run: whatever sits in the folder is an earlier batch's and is not this row's result.
        if ($exit -ne 3 -and (Test-Path -LiteralPath $reportPath)) {
            # A report cut short (the timeout kill, a full disk) must not end the batch: it counts as no report.
            try { $r = Get-Content -Raw -LiteralPath $reportPath | ConvertFrom-Json } catch { $r = $null }
        }
        if ($r) {
            $bad = @($r.steps | Where-Object { -not $_.ok })
            $row.steps = @($r.steps).Count; $row.failed = $bad.Count; $row.bcErrors = @($r.buildcraftErrors).Count
            $row.fatal = [string]$r.fatal
            if ($bad.Count) { $row.firstFailure = "step $($bad[0].n) $($bad[0].step): $($bad[0].detail)" }
            $row.status = if ($exit -eq 124) { 'TIMEOUT' } elseif ($row.fatal) { 'FATAL' } elseif ($row.failed -or $row.bcErrors) { 'FAILED' } elseif ($exit -ne 0) { "FAILED (exit $exit)" } else { 'PASS' }
        } elseif ($exit -eq 0 -and (Test-Path -LiteralPath (Join-Path $out 'summary.md')) -and
                (Get-Content -Raw -LiteralPath (Join-Path $out 'summary.md')) -match 'skipped:') {
            # The runner's summary says why (Get-SkipReason in Invoke-InClientSmoke.ps1); the reason goes in the table too.
            $skipText = Get-Content -Raw -LiteralPath (Join-Path $out 'summary.md')
            $row.status = if ($skipText -match 'skipped: runs in English only') { 'SKIPPED (English only)' } elseif ($skipText -match 'skipped: needs ') { 'SKIPPED (loader lacks a need)' } else { 'SKIPPED (loader filter)' }
            if ($skipText -match '\| skipped: ([^|\r\n]+?) \|') { $row.firstFailure = $Matches[1] }
        } else {
            $row.status = if ($exit -eq 124) { 'TIMEOUT' } elseif ($exit -eq 3) { 'NO LOCK' } else { "NO REPORT (exit $exit)" }
        }
        if ($row.status -notmatch '^(PASS|SKIPPED)' -and -not $row.fatal -and -not $row.firstFailure) { $row.firstFailure = "see $(Split-Path -Leaf $console) and its .err" }
        $rows += [pscustomobject]$row
        Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $tag -> $($row.status), $($row.steps) steps, $($row.failed) failed, $($row.bcErrors) BC log errors, $($row.minutes) min"
        # Keep the combined table current: a long batch that dies halfway still leaves what it learned.
        ConvertTo-Json -InputObject @($rows) -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir 'batch-summary.json') -Encoding utf8
    }
}

$md = @("# Smoke batch on $Node", '', "Run folder: ``$OutDir``  ($([math]::Round($batchClock.Elapsed.TotalMinutes, 1)) min wall, lock waits included)", '',
    '| Scenario | Status | Steps | Failed | BC log errors | Min | First failure / fatal |', '|---|---|---|---|---|---|---|')
foreach ($r in $rows) {
    $why = (@($r.fatal, $r.firstFailure) | Where-Object { $_ }) -join ' / '
    $why = ($why -replace '\|', '\|' -replace "`r?`n", ' ')
    if ($why.Length -gt 220) { $why = $why.Substring(0, 217) + '...' }
    $md += "| $($r.scenario) | $($r.status) | $($r.steps) | $($r.failed) | $($r.bcErrors) | $($r.minutes) | $why |"
}
$md | Set-Content -LiteralPath (Join-Path $OutDir 'batch-summary.md') -Encoding utf8
Write-Host "batch summary: $(Join-Path $OutDir 'batch-summary.md')"
if (@($rows | Where-Object { $_.status -notmatch '^(PASS|SKIPPED)' }).Count) { exit 1 }
