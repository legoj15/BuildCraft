<#
.SYNOPSIS
  Scripted in-client smoke run: boots each Stonecutter node's dev client, drives a scenario through the
  McDevBridge HTTP bridge, and collects screenshots + a pass/fail report for review.

.DESCRIPTION
  Per node, one client at a time (McDevBridge's ~/.mcdevbridge/bridge.json only ever points at the newest client):
    1. Prepares versions/<node>/run-smoke/ — a game directory of its own (-PbcRunDir), so the hand-used run/ with its
       extra mods and saves is never touched: installs the node's McDevBridge jar, writes quiet/unpausing options.
    2. Boots :<node>:runClient straight into the "bc-smoke" save (-PbcQuickPlay). The first run on a node has no save
       yet, so it creates one through the menus (Singleplayer -> Create New World) and renames it afterwards.
    3. Runs the scenario's steps (scripts/smoke/scenarios/*.json; format in scripts/smoke/README.md), screenshots
       included, then collects every ERROR log line mentioning BuildCraft since the world loaded.
    4. Saves the world, closes the client, and writes <OutDir>/<node>/report.json plus a combined summary.md.

  Screenshots are for a human (or agent) to look at; the pass/fail column only covers what the game can report
  (which screen opened, whether a click hit a widget, log errors).

  Keep the physical mouse pointer OFF the game window while it runs: a real mouse move overwrites the bridge's
  synthetic cursor and breaks hovers/clicks.

.EXAMPLE
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1                       # every node, default scenario
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.2,1.21.1    # just these
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.1.2 -Scenario scripts/smoke/scenarios/line8.json
#>
[CmdletBinding()]
param(
    [string[]]$Nodes = @('26.2', '26.1.2', '1.21.11', '1.21.10', '1.21.1'),
    [string]$Scenario,
    [string]$OutDir,
    [string]$McDevBridgeRoot = 'E:/GitHub/McDevBridge',
    # Build the node's McDevBridge jar (gradlew :<node>:build in McDevBridgeRoot) when none is found.
    [switch]$BuildBridge,
    # Minutes to wait for a client to boot (the first run on a node also compiles and downloads assets).
    [int]$BootMinutes = 10
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# `pwsh -File ... -Nodes a,b` hands the list over as ONE string; accept both forms.
$Nodes = @($Nodes | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if (-not $Scenario) { $Scenario = Join-Path $PSScriptRoot 'scenarios/line8.json' }
$ScenarioData = Get-Content -Raw -LiteralPath $Scenario | ConvertFrom-Json
if (-not $OutDir) { $OutDir = Join-Path $Repo ("build/smoke/" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$BridgeFile = Join-Path $HOME '.mcdevbridge/bridge.json'
$SaveName = 'bc-smoke'

# ── Bridge plumbing ──────────────────────────────────────────────────────────

$script:Bridge = $null
$script:Spawned = $null

function Invoke-Bridge([string]$Method, [string]$Path, $Body = $null, [int]$TimeoutSec = 60) {
    $req = @{
        Uri        = "http://127.0.0.1:$($script:Bridge.port)$Path"
        Method     = $Method
        Headers    = @{ 'X-MCDB-Token' = $script:Bridge.token }
        TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
        $req.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $req.ContentType = 'application/json'
    }
    try {
        Invoke-RestMethod @req
    } catch {
        # Name the endpoint and keep the bridge's own error body: a bare "500" says nothing.
        $detail = if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path$(if ($null -ne $Body) { ' ' + ($Body | ConvertTo-Json -Compress -Depth 5) }) -> $detail"
    }
}

function Wait-Until([scriptblock]$Condition, [int]$TimeoutSec, [string]$What, [int]$PollMs = 500) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            $r = & $Condition
            if ($r) { return $r }
        } catch {
            # A condition throws 'FATAL: ...' when waiting longer cannot help (e.g. the process it waits on is gone).
            if ($_.Exception.Message.StartsWith('FATAL: ')) { throw $_.Exception.Message.Substring(7) }
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds $PollMs
    }
    throw "timed out after ${TimeoutSec}s waiting for $What" + $(if ($lastError) { " (last error: $lastError)" } else { '' })
}

function Get-State { Invoke-Bridge GET '/state' }

function Wait-NoOverlay {
    # No screen at all is a real failure (a previous step didn't open it) — fail now, not after the 60 s overlay wait.
    if (-not (Get-ScreenClass)) { throw 'no screen is open' }
    Wait-Until { -not (Invoke-Bridge POST '/screenclick' @{ list = $true }).overlay } 60 'the loading overlay to clear' | Out-Null
}

function Wait-InWorld([int]$TimeoutSec = 300) {
    Wait-Until {
        $s = Get-State
        ($null -ne $s.PSObject.Properties['player']) -and ($null -eq $s.PSObject.Properties['screen'])
    } $TimeoutSec 'the player to be in a world with no screen open' 1000 | Out-Null
}

function Get-ScreenClass {
    $s = Get-State
    if ($s.PSObject.Properties['screen']) { $s.screen.class } else { '' }
}

# ── Node preparation ─────────────────────────────────────────────────────────

function Find-BridgeJar([string]$Node) {
    $suffix = "+mc$Node.jar"
    $candidates = @()
    foreach ($root in @((Join-Path $McDevBridgeRoot "versions/$Node/build/libs")) +
            @(Get-ChildItem -Directory -Path (Join-Path $McDevBridgeRoot '.claude/worktrees') -ErrorAction SilentlyContinue |
                ForEach-Object { Join-Path $_.FullName "versions/$Node/build/libs" })) {
        if (Test-Path $root) {
            $candidates += Get-ChildItem -File -Path $root -Filter 'mcdevbridge-*.jar' |
                Where-Object { $_.Name.EndsWith($suffix) -and $_.Name -notmatch '-(sources|javadoc)' }
        }
    }
    $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Initialize-RunDir([string]$Node) {
    $runDir = Join-Path $Repo "versions/$Node/run-smoke"
    $mods = Join-Path $runDir 'mods'
    New-Item -ItemType Directory -Force -Path $mods | Out-Null

    $jar = Find-BridgeJar $Node
    if (-not $jar -and $BuildBridge) {
        Push-Location $McDevBridgeRoot
        try { & ./gradlew.bat ":${Node}:build" -q; if ($LASTEXITCODE) { throw "McDevBridge :${Node}:build failed" } }
        finally { Pop-Location }
        $jar = Find-BridgeJar $Node
    }
    if (-not $jar) {
        throw "no McDevBridge jar for $Node under $McDevBridgeRoot (build it: gradlew :${Node}:build there, or pass -BuildBridge)"
    }
    Get-ChildItem -File -Path $mods -Filter 'mcdevbridge-*.jar' | Where-Object Name -ne $jar.Name | Remove-Item
    if (-not (Test-Path (Join-Path $mods $jar.Name))) { Copy-Item -LiteralPath $jar.FullName -Destination $mods }

    # Written once: no pause menu when the window loses focus, no first-launch accessibility screen, silent, and a
    # fixed GUI scale so screenshots from different runs line up. Keys unknown to an older line are ignored.
    $options = Join-Path $runDir 'options.txt'
    if (-not (Test-Path $options)) {
        @(
            'pauseOnLostFocus:false', 'onboardAccessibility:false', 'tutorialStep:none', 'skipMultiplayerWarning:true',
            'joinedFirstServer:true', 'narrator:0', 'guiScale:2', 'renderDistance:6', 'simulationDistance:6',
            'soundCategory_master:0.0'
        ) | Set-Content -LiteralPath $options -Encoding utf8
    }
    [pscustomobject]@{ RunDir = $runDir; Jar = $jar.Name }
}

# ── Client lifecycle ─────────────────────────────────────────────────────────

function Assert-NoOtherClient {
    # Every McDevBridge client overwrites the one ~/.mcdevbridge/bridge.json: a second dev client (another session's,
    # or one left open by hand) would steal this run's bridge mid-scenario.
    # Waits briefly first: the previous node's window can outlive its kill by a few seconds.
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $others = @(Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' })
        if (-not $others.Count) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ($others.Count) {
        throw "another Minecraft client is running (pid $(@($others.Id) -join ', ')); close it first — clients share ~/.mcdevbridge/bridge.json"
    }
}

function Start-Client([string]$Node, [string]$RunDir, [bool]$QuickPlay, [string]$LogDir) {
    Assert-NoOtherClient
    $before = if (Test-Path $BridgeFile) { (Get-Item $BridgeFile).LastWriteTimeUtc } else { [datetime]::MinValue }
    $gradleArgs = @(":${Node}:runClient", '-PbcRunDir=run-smoke', '--console=plain')
    if ($QuickPlay) { $gradleArgs += "-PbcQuickPlay=$SaveName" }
    $proc = Start-Process -FilePath (Join-Path $Repo 'gradlew.bat') -ArgumentList $gradleArgs -WorkingDirectory $Repo `
        -RedirectStandardOutput (Join-Path $LogDir 'gradle.out.log') -RedirectStandardError (Join-Path $LogDir 'gradle.err.log') `
        -WindowStyle Hidden -PassThru
    # Recorded before any wait can throw, so the main loop can clean up a client that booted without a bridge.
    $script:Spawned = [pscustomobject]@{ Gradle = $proc; Since = Get-Date }

    Wait-Until {
        if ($proc.HasExited) { throw "FATAL: runClient exited early (code $($proc.ExitCode)); see $LogDir" }
        (Test-Path $BridgeFile) -and (Get-Item $BridgeFile).LastWriteTimeUtc -gt $before
    } ($BootMinutes * 60) 'the client to publish a new bridge.json' 2000 | Out-Null
    # A read can catch the file mid-write; retry briefly.
    $script:Bridge = Wait-Until { Get-Content -Raw $BridgeFile | ConvertFrom-Json } 10 'a readable bridge.json' 200
    $ping = Wait-Until { Invoke-Bridge GET '/ping' $null 5 } 120 'the bridge to answer /ping'
    [pscustomobject]@{ Gradle = $proc; Pid = [int]$script:Bridge.pid; BridgeVersion = $ping.version }
}

function Stop-Spawned {
    # A boot that failed before Start-Client returned: no bridge pid to stop, so take down any Minecraft window that
    # appeared since the spawn, then the Gradle wrapper's tree — otherwise the next node refuses to start beside it.
    if (-not $script:Spawned) { return }
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like 'Minecraft*' -and $_.StartTime -ge $script:Spawned.Since } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    if (-not $script:Spawned.Gradle.HasExited) { & taskkill /PID $script:Spawned.Gradle.Id /T /F | Out-Null }
    $script:Spawned = $null
}

function Stop-Client($Client) {
    try { Invoke-Bridge POST '/command' @{ command = 'save-all flush' } 120 | Out-Null } catch { }
    Stop-Process -Id $Client.Pid -Force -ErrorAction SilentlyContinue
    if (-not $Client.Gradle.WaitForExit(90000)) {
        # The Gradle wrapper outlived its client: take its process tree down so the next node can boot.
        & taskkill /PID $Client.Gradle.Id /T /F | Out-Null
    }
    $script:Bridge = $null
}

function Invoke-MenuClick([string]$Text) {
    # Menu clicks that load the world list or generate a world block the game thread for longer than the bridge's
    # bounded wait, so the call reports a TimeoutException although the click happened. The callers poll the screen
    # afterwards, which is the real check.
    try { Invoke-Bridge POST '/screenclick' @{ widgetText = $Text } 120 | Out-Null }
    catch { if ($_.Exception.Message -notmatch 'TimeoutException|timed out') { throw } }
}

function New-SmokeWorld {
    # Fresh run-smoke dir: Singleplayer opens the create screen directly when there are no saves; an older run dir
    # that somehow has saves lands on the world list, which has its own "Create New World" button.
    Wait-Until { (Get-ScreenClass) -match 'TitleScreen' } 300 'the title screen' 1000 | Out-Null
    Wait-NoOverlay
    Invoke-MenuClick 'Singleplayer'
    $screen = Wait-Until { $c = Get-ScreenClass; if ($c -match 'CreateWorldScreen|SelectWorldScreen') { $c } } 60 'the world screens'
    if ($screen -match 'SelectWorldScreen') {
        Invoke-MenuClick 'Create New World'
        Wait-Until { (Get-ScreenClass) -match 'CreateWorldScreen' } 60 'the create-world screen' | Out-Null
    }
    Invoke-MenuClick 'Create New World'
    Wait-InWorld 600
}

# ── Scenario steps ───────────────────────────────────────────────────────────

$Base = @($ScenarioData.base)   # the rig's origin; step coordinates are relative to it

function Expand-Coords([string]$Text) {
    # {X}, {Y+2}, {Z-1}: the scenario base plus an offset.
    [regex]::Replace($Text, '\{([XYZ])([+-]\d+)?\}', {
            param($m)
            $axis = 'XYZ'.IndexOf($m.Groups[1].Value)
            $off = if ($m.Groups[2].Success) { [int]$m.Groups[2].Value } else { 0 }
            [string]($Base[$axis] + $off)
        })
}

function Test-NodeFilter($Step, [string]$Node) {
    if (-not $Step.PSObject.Properties['nodes']) { return $true }
    return @($Step.nodes) -contains $Node
}

function Invoke-Step($Step, [string]$Node, [string]$NodeOut, [int]$Index) {
    $detail = ''
    $shot = $null
    switch ($Step.do) {
        'cmd' {
            foreach ($c in @($Step.run)) {
                # A leading '?' marks one command as allowed to fail (e.g. a fill that finds nothing to replace).
                $optional = $c.StartsWith('?')
                if ($optional) { $c = $c.Substring(1) }
                $r = Invoke-Bridge POST '/command' @{ command = (Expand-Coords $c) }
                # The world persists between runs, so "already so" replies are success for a rig builder.
                $benign = (@($r.output) -join ' ') -match '(?i)did not change|could not set the block|no blocks were filled'
                if (-not $r.success -and -not $benign -and -not $optional -and -not $Step.PSObject.Properties['allowFail']) {
                    throw "command failed: $(Expand-Coords $c) -> $(@($r.output) -join ' | ')"
                }
            }
            $detail = "$(@($Step.run).Count) command(s)"
        }
        'tp' {
            $x = $Base[0] + $Step.at[0] + 0.5; $y = $Base[1] + $Step.at[1]; $z = $Base[2] + $Step.at[2] + 0.5
            $yaw = if ($Step.PSObject.Properties['yaw']) { $Step.yaw } else { 0 }
            $pitch = if ($Step.PSObject.Properties['pitch']) { $Step.pitch } else { 0 }
            # A bare server-context tp does not move the player; running it AS the player does.
            Invoke-Bridge POST '/command' @{ command = "execute as @p at @p run tp @p $x $y $z $yaw $pitch" } | Out-Null
            Wait-Until { $p = (Get-State).player.pos; [math]::Abs($p.x - $x) -lt 0.6 -and [math]::Abs($p.z - $z) -lt 0.6 } 30 "the player to reach $x $y $z" | Out-Null
            Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $yaw; pitch = $pitch } | Out-Null
            $detail = "at $x $y $z"
        }
        'aim' {
            $p = (Get-State).player.pos
            $dx = $Base[0] + $Step.at[0] + 0.5 - $p.x
            $dy = $Base[1] + $Step.at[1] + 0.5 - ($p.y + 1.62)
            $dz = $Base[2] + $Step.at[2] + 0.5 - $p.z
            $yaw = [math]::Atan2(-$dx, $dz) * 180 / [math]::PI
            $pitch = - [math]::Atan2($dy, [math]::Sqrt($dx * $dx + $dz * $dz)) * 180 / [math]::PI
            Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $yaw; pitch = $pitch } | Out-Null
            Start-Sleep -Milliseconds 150
            $hit = (Get-State).hit
            $detail = "yaw $([math]::Round($yaw,1)) pitch $([math]::Round($pitch,1)); hit $($hit | ConvertTo-Json -Compress -Depth 3)"
        }
        'look' { Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $Step.yaw; pitch = $Step.pitch } | Out-Null }
        'hold' {
            $r = Invoke-Bridge POST '/command' @{ command = "item replace entity @p weapon.mainhand with $($Step.item)" }
            if (-not $r.success) { throw "hold failed: $(@($r.output) -join ' | ')" }
            # The client learns about the swap a tick or more later; a click before that uses the OLD stack
            # client-side (the server still sees the new one — e.g. the guide's advancement fires, no book opens).
            $want = ($Step.item -replace '\[.*$', '')
            try {
                Wait-Until {
                    $held = (Get-State).player.selectedItem
                    if ($want -eq 'minecraft:air') { $held.empty } else { $held.id -eq $want }
                } 5 "the client to hold $want" 100 | Out-Null
            } catch {
                $inv = Invoke-Bridge POST '/inventory'
                throw "$($_.Exception.Message); server said '$(@($r.output) -join ' ')'; client inventory: $($inv | ConvertTo-Json -Depth 5 -Compress)"
            }
            $detail = $Step.item
        }
        # The click reaches the server as a packet, while the next step's /command runs on the server loop directly:
        # without a pause the command can land first (e.g. emptying the hand before the bucket click is handled).
        { $_ -in 'use', 'attack' } {
            $r = Invoke-Bridge POST '/worldclick' @{ action = $(if ($Step.do -eq 'use') { 'right_click' } else { 'left_click' }) }
            if ($r.PSObject.Properties['success'] -and -not $r.success) { throw "worldclick refused: $($r.message)" }
            Start-Sleep -Milliseconds 400
            $detail = "hit $($r.hitType)"
        }
        { $_ -in 'click', 'hover' } {
            Wait-NoOverlay
            $body = @{}
            foreach ($k in 'widgetText', 'slotIndex', 'x', 'y', 'fx', 'fy', 'button', 'modifiers') {
                if ($Step.PSObject.Properties[$k] -and -not ($Step.do -eq 'hover' -and $k -in 'button', 'modifiers')) { $body[$k] = $Step.$k }
            }
            try {
                $r = Invoke-Bridge POST "/screen$($Step.do)" $body
            } catch {
                # allowMiss also covers a target that isn't on screen at all (a hidden widget, a closed screen).
                if ($Step.PSObject.Properties['allowMiss']) { return [pscustomobject]@{ detail = "missed (allowed): $($_.Exception.Message)"; shot = $null } }
                throw
            }
            $detail = ($r | ConvertTo-Json -Compress -Depth 4)
            if ($r.PSObject.Properties['hit'] -and -not $r.hit -and -not $Step.PSObject.Properties['allowMiss']) {
                throw "$($Step.do) hit nothing: $detail"
            }
            if ($Step.do -eq 'hover') { Start-Sleep -Milliseconds 600 }   # tooltips draw on the next frames
        }
        'key' { Invoke-Bridge POST '/screenkeys' @{ type = 'key'; key = $Step.key } | Out-Null; $detail = $Step.key }
        # The close packet and a following /command race on the server: a slot change made while the server still has
        # the old menu open is sent under that menu's id, which the client (already back on its inventory) drops.
        'close' { Invoke-Bridge POST '/screenclose' | Out-Null; Start-Sleep -Milliseconds 400 }
        'wait' { Start-Sleep -Milliseconds $Step.ms; $detail = "$($Step.ms) ms" }
        'widgets' {
            $r = Invoke-Bridge POST '/screenclick' @{ list = $true }
            $file = Join-Path $NodeOut ('{0:D2}-{1}.widgets.json' -f $Index, $Step.name)
            $r | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $file -Encoding utf8
            $detail = "$(@($r.widgets).Count) widgets -> $(Split-Path -Leaf $file)"
        }
        'hoverEach' {
            # Every visible, active widget on the open screen: hover its centre and screenshot the tooltip.
            Wait-NoOverlay
            $list = Invoke-Bridge POST '/screenclick' @{ list = $true }
            $max = if ($Step.PSObject.Properties['max']) { $Step.max } else { 12 }
            $targets = @($list.widgets | Where-Object { $_.visible -and $_.active } | Select-Object -First $max)
            $shots = @()
            $k = 0
            foreach ($w in $targets) {
                $k++
                Invoke-Bridge POST '/screenhover' @{ x = $w.x + $w.width / 2; y = $w.y + $w.height / 2 } | Out-Null
                Start-Sleep -Milliseconds 600
                $file = Join-Path $NodeOut ('{0:D2}-{1}-hover{2:D2}.png' -f $Index, $Step.name, $k)
                Invoke-Bridge POST '/screenshot' @{ path = ($file -replace '\\', '/') } | Out-Null
                $shots += "$(Split-Path -Leaf $file)=$($w.text)"
            }
            Invoke-Bridge POST '/screenhover' @{ x = 1; y = 1 } | Out-Null   # park the cursor off every widget
            $detail = "$($targets.Count) widgets: " + ($shots -join ', ')
            if ($targets.Count) { $shot = ($shots[0] -split '=')[0] }
        }
        'shot' {
            $file = Join-Path $NodeOut ('{0:D2}-{1}.png' -f $Index, $Step.name)
            Invoke-Bridge POST '/screenshot' @{ path = ($file -replace '\\', '/') } | Out-Null
            Wait-Until { (Test-Path $file) -and (Get-Item $file).Length -gt 0 } 20 "screenshot $file" 200 | Out-Null
            $shot = Split-Path -Leaf $file
            $detail = $Step.PSObject.Properties['look'] ? $Step.look : ''
        }
        'expectScreen' {
            $want = $Step.contains
            $timeout = if ($Step.PSObject.Properties['timeoutSec']) { $Step.timeoutSec } else { 5 }
            $got = Wait-Until { $c = Get-ScreenClass; if (($want -eq '' -and $c -eq '') -or ($want -ne '' -and $c -match $want)) { if ($c) { $c } else { '(none)' } } } $timeout "screen matching '$want' (open: '$(Get-ScreenClass)')"
            $detail = $got
        }
        default { throw "unknown step type '$($Step.do)'" }
    }
    [pscustomobject]@{ detail = $detail; shot = $shot }
}

function Invoke-Scenario([string]$Node, [string]$NodeOut) {
    $results = @()
    $i = 0
    foreach ($step in @($ScenarioData.steps)) {
        $i++
        if (-not (Test-NodeFilter $step $Node)) { continue }
        $label = if ($step.PSObject.Properties['label']) { $step.label } elseif ($step.PSObject.Properties['name']) { $step.name } else { $step.do }
        try {
            $r = Invoke-Step $step $Node $NodeOut $i
            $results += [pscustomobject]@{ n = $i; step = $label; do = $step.do; ok = $true; detail = $r.detail; shot = $r.shot }
        } catch {
            $results += [pscustomobject]@{ n = $i; step = $label; do = $step.do; ok = $false; detail = $_.Exception.Message; shot = $null }
            # A failed step leaves the client in an unknown state: take a picture of it, close any screen, go on.
            try {
                $file = Join-Path $NodeOut ('{0:D2}-FAILED.png' -f $i)
                Invoke-Bridge POST '/screenshot' @{ path = ($file -replace '\\', '/') } | Out-Null
                $results[-1].shot = Split-Path -Leaf $file
                if (Get-ScreenClass) { Invoke-Bridge POST '/screenclose' | Out-Null }
            } catch { }
        }
    }
    $results
}

# ── Main ─────────────────────────────────────────────────────────────────────

$summary = @()
foreach ($node in $Nodes) {
    $nodeOut = Join-Path $OutDir $node
    New-Item -ItemType Directory -Force -Path $nodeOut | Out-Null
    Write-Host "[$node] preparing"
    $report = [ordered]@{ node = $node; scenario = $ScenarioData.name; started = (Get-Date).ToString('s'); steps = @(); buildcraftErrors = @(); fatal = $null }
    $client = $null
    $prep = $null
    try {
        $prep = Initialize-RunDir $node
        $report.bridgeJar = $prep.Jar
        $savePath = Join-Path $prep.RunDir "saves/$SaveName"
        # A first run names the save whatever the menus defaulted to ("New World", or its translation); adopt the
        # only save there is, before booting, so an interrupted first run doesn't leave an orphan behind.
        $saves = @(Get-ChildItem -Directory -Path (Join-Path $prep.RunDir 'saves') -ErrorAction SilentlyContinue)
        if (-not (Test-Path $savePath) -and $saves.Count -eq 1) { Rename-Item -LiteralPath $saves[0].FullName -NewName $SaveName }
        $haveSave = Test-Path $savePath
        Write-Host "[$node] booting client ($(if ($haveSave) { 'quick-play' } else { 'first run: creating the world' }))"
        $client = Start-Client $node $prep.RunDir $haveSave $nodeOut
        $report.bridgeVersion = $client.BridgeVersion
        if ($haveSave) { Wait-InWorld 600 } else { New-SmokeWorld }
        $logCursor = (Invoke-Bridge POST '/log' @{ level = 'ERROR'; since = 0; limit = 1 }).nextCursor

        Write-Host "[$node] running $(@($ScenarioData.steps).Count) steps"
        $report.steps = @(Invoke-Scenario $node $nodeOut)

        $logs = (Invoke-Bridge POST '/log' @{ level = 'ERROR'; since = $logCursor; limit = 500 }).logs
        $report.buildcraftErrors = @($logs | Where-Object { ($_ | ConvertTo-Json -Depth 6 -Compress) -match '(?i)buildcraft' })
    } catch {
        $report.fatal = $_.Exception.Message
        Write-Warning "[$node] $($report.fatal)"
    } finally {
        if ($client) { Stop-Client $client } else { Stop-Spawned }
        $script:Spawned = $null
    }
    # First run: adopt the save the menus just created (same rule as before booting).
    if ($prep) {
        $saves = @(Get-ChildItem -Directory -Path (Join-Path $prep.RunDir 'saves') -ErrorAction SilentlyContinue)
        if (-not (Test-Path (Join-Path $prep.RunDir "saves/$SaveName")) -and $saves.Count -eq 1) {
            Rename-Item -LiteralPath $saves[0].FullName -NewName $SaveName
        }
    }
    $report.finished = (Get-Date).ToString('s')
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $nodeOut 'report.json') -Encoding utf8
    $failed = @($report.steps | Where-Object { -not $_.ok }).Count
    $summary += [pscustomobject]@{ node = $node; steps = @($report.steps).Count; failed = $failed; bcErrors = @($report.buildcraftErrors).Count; fatal = $report.fatal; report = $report }
    Write-Host "[$node] done: $(@($report.steps).Count) steps, $failed failed, $(@($report.buildcraftErrors).Count) BuildCraft log errors$(if ($report.fatal) { ", FATAL: $($report.fatal)" })"
}

# summary.md: one table per node, screenshots linked relative to the run folder.
$md = @("# In-client smoke: $($ScenarioData.name)", '', "Run folder: ``$OutDir``", '', '| Node | Steps | Failed | BuildCraft log errors | Fatal |', '|---|---|---|---|---|')
foreach ($s in $summary) { $md += "| $($s.node) | $($s.steps) | $($s.failed) | $($s.bcErrors) | $($s.fatal) |" }
foreach ($s in $summary) {
    $md += @('', "## $($s.node)", '', '| # | Step | OK | Detail | Screenshot |', '|---|---|---|---|---|')
    foreach ($st in $s.report.steps) {
        $d = ([string]$st.detail) -replace '\|', '\|' -replace "`r?`n", ' '
        if ($d.Length -gt 160) { $d = $d.Substring(0, 157) + '...' }
        $img = if ($st.shot) { "[$($st.shot)]($($s.node)/$($st.shot))" } else { '' }
        $md += "| $($st.n) | $($st.step) | $(if ($st.ok) { 'yes' } else { '**NO**' }) | $d | $img |"
    }
    foreach ($e in $s.report.buildcraftErrors) { $md += "- log: $((($e | ConvertTo-Json -Compress -Depth 4)) -replace "`r?`n", ' ')" }
}
$md | Set-Content -LiteralPath (Join-Path $OutDir 'summary.md') -Encoding utf8
Write-Host "summary: $(Join-Path $OutDir 'summary.md')"
if (@($summary | Where-Object { $_.failed -or $_.fatal -or $_.bcErrors }).Count) { exit 1 }
