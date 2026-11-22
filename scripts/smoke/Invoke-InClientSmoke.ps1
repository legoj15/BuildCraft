<#
.SYNOPSIS
  Scripted in-client smoke run: boots each Stonecutter node's dev client, drives a scenario through the
  McDevBridge HTTP bridge, and collects screenshots + a pass/fail report for review.

.DESCRIPTION
  One run per machine: McDevBridge's ~/.mcdevbridge/bridge.json only ever points at the newest client, so the run takes
  the smoke lock (~/.mcdevbridge/smoke.lock, see SmokeGuard.ps1) before the first boot and holds it to the end. A run that
  finds the lock held by a live process exits 3 naming the holder; a lock whose process is gone is taken over. The bridge
  url, token and pid read at boot are pinned and must belong to the client this run spawned: a step fails, and the node
  ends fatal, when bridge.json later names another client or the pinned client is gone.

  Nodes are the Stonecutter node names: "26.3" is the NeoForge node, "26.3-fabric" its Fabric twin, "26.3-forge" its
  MinecraftForge twin (not NeoForge). The default is the twelve NeoForge and Fabric nodes (the Forge nodes are opt-in, by
  name, while nothing ships there); -Loader narrows it to one loader.

  Per node, one client at a time:
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
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1                       # every node (both loaders), default scenario
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Loader fabric        # the six Fabric nodes
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3-forge     # the MinecraftForge node (opt-in)
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.2,1.21.1-fabric   # just these
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Lang ru_ru -Scenario scripts/smoke/scenarios/lang-overflow.json
  pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.1.2 -Scenario scripts/smoke/scenarios/line8.json
#>
[CmdletBinding()]
param(
    [string[]]$Nodes = @('26.3', '26.2', '26.1.2', '1.21.11', '1.21.10', '1.21.1',
        '26.3-fabric', '26.2-fabric', '26.1.2-fabric', '1.21.11-fabric', '1.21.10-fabric', '1.21.1-fabric'),
    # Keep only this loader's nodes of -Nodes (a bare node name is NeoForge, "<mc>-fabric" is Fabric, "<mc>-forge" is
    # MinecraftForge; see Get-NodeLoader). The default node list leaves the Forge nodes out (nothing ships there yet): name
    # one with -Nodes 26.3-forge.
    [ValidateSet('', 'neoforge', 'fabric', 'forge')]
    [string]$Loader = '',
    # Game language for the run (options.txt `lang`), e.g. ru_ru or zh_cn for a text-overflow look. Re-applied every
    # run, so a run without it is back in English. Steps that click by `widgetText` only work in English: a scenario
    # marked "englishOnly" is reported as skipped under any other language.
    [ValidatePattern('^[a-z]{2,3}_[a-z]{2,3}$')]
    [string]$Lang = 'en_us',
    # Optional dev-runtime mods to load (aliases, e.g. -Mods a,b), passed to Gradle as -PwithMods=a,b next to the
    # recipe-viewer switch; added to the scenario's own "mods" list.
    [string[]]$Mods = @(),
    # Play this save instead of the scenario's own (or bc-smoke), copied from -SaveSeed (default: the scenario's saveSeed,
    # else bc-smoke) when it is missing. With -FreshSave it is deleted first, so every run starts from the seed as it
    # is: what a comparison between runs (the same scenario bare and with mods) needs, since a scenario that builds on
    # the world an earlier run left (item entities, loaded chunks) would otherwise start from a different state.
    [string]$Save = '',
    [string]$SaveSeed = '',
    [switch]$FreshSave,
    [string]$Scenario,
    [string]$OutDir,
    # The McDevBridge checkout whose build/libs holds the node jars. $env:MCDEVBRIDGE_ROOT overrides the default, for a
    # machine where it lives elsewhere (Server1: C:/Dev/McDevBridge-bc).
    [string]$McDevBridgeRoot = $(if ($env:MCDEVBRIDGE_ROOT) { $env:MCDEVBRIDGE_ROOT } else { 'E:/GitHub/McDevBridge' }),
    # Build the node's McDevBridge jar (gradlew :<node>:build in McDevBridgeRoot) when none is found.
    [switch]$BuildBridge,
    # Minutes to wait for a client to boot (the first run on a node also compiles and downloads assets).
    [int]$BootMinutes = 10,
    # Run Gradle with --no-daemon. The client is a child of the Gradle daemon that serves the build, so it lives in that
    # daemon's Windows session: when an idle daemon started from another session (an ssh login, session 0) is reused,
    # the window opens in a session with no desktop and the game dies creating its GL context ("No context is current"
    # in GlCommandEncoder). With the switch the client is always a child of this run. Needed for runs over ssh/scheduled
    # tasks (Server1); harmless, only slower, on a normal desktop.
    [switch]$NoDaemon
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0
. (Join-Path $PSScriptRoot 'SmokeGuard.ps1')

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# `pwsh -File ... -Nodes a,b` hands the list over as ONE string; accept both forms.
$Nodes = @($Nodes | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$Mods = @($Mods | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })

# A node's loader is the `loader=` key of its own versions/<node>/gradle.properties (the key the build itself reads, so the
# runner and Gradle cannot disagree; a NeoForge node carries none and inherits neoforge). A node whose file does not say falls
# back to its name's suffix. The Minecraft line is the name less that suffix: "26.3" is NeoForge on 26.3, "26.3-fabric" and
# "26.3-forge" (MinecraftForge, not NeoForge) are its twins.
$script:NodeLoaders = @{}
function Get-NodeLoader([string]$Node) {
    if (-not $script:NodeLoaders.ContainsKey($Node)) {
        $found = $null
        $props = Join-Path $Repo "versions/$Node/gradle.properties"
        if (Test-Path -LiteralPath $props) {
            foreach ($line in Get-Content -LiteralPath $props) {
                if ($line -match '^\s*loader\s*=\s*(\w+)\s*$') { $found = $Matches[1].ToLowerInvariant(); break }
            }
        }
        if (-not $found) { $found = if ($Node -like '*-fabric') { 'fabric' } elseif ($Node -like '*-forge') { 'forge' } else { 'neoforge' } }
        $script:NodeLoaders[$Node] = $found
    }
    $script:NodeLoaders[$Node]
}
function Get-NodeMcVersion([string]$Node) { $Node -replace '-(fabric|forge)$', '' }
# Whether a node pins a recipe viewer: the `jei_version` / `rei_version` key of its own versions/<node>/gradle.properties,
# the pin the build compiles that viewer's plugins and puts it in the dev runtime by (every NeoForge and Fabric node pins
# both; of the Forge nodes only 1.21.1-forge pins one, JEI).
$script:NodeViewers = @{}
function Test-NodeViewer([string]$Node, [string]$Viewer) {
    if (-not $script:NodeViewers.ContainsKey($Node)) {
        $pinned = @()
        $props = Join-Path $Repo "versions/$Node/gradle.properties"
        if (Test-Path -LiteralPath $props) {
            foreach ($line in Get-Content -LiteralPath $props) {
                if ($line -match '^\s*(jei|rei)_version\s*=\s*\S') { $pinned += $Matches[1].ToLowerInvariant() }
            }
        }
        $script:NodeViewers[$Node] = $pinned
    }
    if ($Viewer) { return @($script:NodeViewers[$Node]) -contains $Viewer.ToLowerInvariant() }
    return @($script:NodeViewers[$Node]).Count -gt 0
}
# Whether a node's quick-play is defective: `quickplay_drops_game_events=true` in its gradle.properties (1.21.1-forge: Forge
# 52 loads a quick-played world before it starts its game event bus, and the integrated server crashes at the first loot
# roll). Such a node boots to the title screen and its save is opened through Singleplayer (Open-SaveFromMenu).
function Test-QuickPlayDefective([string]$Node) {
    $props = Join-Path $Repo "versions/$Node/gradle.properties"
    if (-not (Test-Path -LiteralPath $props)) { return $false }
    foreach ($line in Get-Content -LiteralPath $props) {
        if ($line -match '^\s*quickplay_drops_game_events\s*=\s*true\s*$') { return $true }
    }
    return $false
}
# What a loader's dev runtime lacks, by the capability names a scenario or a step lists in "needs". A scenario or step that
# needs one the node lacks is SKIPPED with the reason (never failed): it asks for something the node cannot give. The
# recipe viewers are per node, not per loader: "recipeViewer" is any viewer (the node's default one), "viewer:<name>" the
# one a scenario's "recipeViewer" boots (Test-NodeViewer). devMods is the -PwithMods runtime (bc.forge reads it too since
# #209: the aliases a node defines load there); devProperties is a scenario's "gradleProperties" (-PwithGuideMe,
# -PwithFallingTree ...), which only the NeoForge scripts wire.
$LoaderLacks = @{
    forge = @{
        devProperties = 'the optional dev-runtime switches a scenario names in "gradleProperties" (-PwithGuideMe ...): the Forge node wires none of them'
    }
}
# True when the node's own gradle.properties defines the -PwithMods alias (`withmod.<alias>=`, empty counts: a deliberate no-op).
function Test-NodeDefinesMod([string]$Node, [string]$Alias) {
    $props = Join-Path $Repo "versions/$Node/gradle.properties"
    if (-not (Test-Path -LiteralPath $props)) { return $false }
    $defined = $false
    foreach ($line in Get-Content -LiteralPath $props) {
        if ($line -match ('^\s*withmod\.' + [regex]::Escape($Alias.ToLowerInvariant()) + '\s*=')) { $defined = $true }
    }
    return $defined
}
# True when the node's gradle.properties says `config_screen=false` (1.21.1-forge, #225: no config screen there).
function Test-NodeLacksConfigScreen([string]$Node) {
    $props = Join-Path $Repo "versions/$Node/gradle.properties"
    if (-not (Test-Path -LiteralPath $props)) { return $false }
    return [bool](Get-Content -LiteralPath $props | Where-Object { $_ -match '^\s*config_screen\s*=\s*false\s*$' })
}
function Get-LackReason($Needs, [string]$Node) {
    # The first capability of $Needs the node lacks, as "needs <what>"; $null when it lacks none.
    $lacks = $LoaderLacks[(Get-NodeLoader $Node)]
    foreach ($n in @($Needs)) {
        $need = [string]$n
        if ($need -eq 'recipeViewer' -and -not (Test-NodeViewer $Node)) {
            return "needs a recipe viewer (this node pins neither JEI nor REI: no jei_version or rei_version in versions/$Node/gradle.properties)"
        }
        if ($need -like 'viewer:*') {
            $viewer = $need.Substring(7)
            if (-not (Test-NodeViewer $Node $viewer)) { return "needs $viewer (this node does not pin it: no ${viewer}_version in versions/$Node/gradle.properties)" }
        }
        if ($need -eq 'configScreen' -and (Test-NodeLacksConfigScreen $Node)) {
            return "needs a config screen (this node has none: config_screen=false in versions/$Node/gradle.properties)"
        }
        if ($lacks -and $lacks.ContainsKey($need)) { return "needs $($lacks[$need])" }
    }
    return $null
}
if ($Loader) { $Nodes = @($Nodes | Where-Object { (Get-NodeLoader $_) -eq $Loader }) }
if (-not $Nodes.Count) { throw 'no nodes to run (check -Nodes and -Loader)' }
if (-not $Scenario) { $Scenario = Join-Path $PSScriptRoot 'scenarios/line8.json' }
$ScenarioData = Get-Content -Raw -LiteralPath $Scenario | ConvertFrom-Json
if (-not $OutDir) { $OutDir = Join-Path $Repo ("build/smoke/" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
# Absolute: screenshot paths are handed to the CLIENT, whose working directory is its run folder, not this one.
$OutDir = [System.IO.Path]::GetFullPath($OutDir, (Get-Location).Path)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$BridgeFile = Join-Path $HOME '.mcdevbridge/bridge.json'
$SaveName = 'bc-smoke'
# A scenario may play a world of its own ("save"); "saveSeed" names the save to copy it from when it is missing (a
# scenario without a seed needs its save copied in by hand first, e.g. a world carried over from another node).
$PlaySave = if ($Save) { $Save } elseif ($ScenarioData.PSObject.Properties['save']) { [string]$ScenarioData.save } else { $SaveName }
$PlaySeed = if ($SaveSeed) { $SaveSeed } elseif ($ScenarioData.PSObject.Properties['saveSeed']) { [string]$ScenarioData.saveSeed } elseif ($Save) { $SaveName } else { $null }
# A save name is one folder under saves/: a separator or ".." would point the fresh-save delete somewhere else.
if ($null -ne $PlaySeed) { $PlaySeed = $PlaySeed.Trim() }
$PlaySave = "$PlaySave".Trim()
foreach ($candidate in @($PlaySave, $PlaySeed) | Where-Object { $null -ne $_ }) {
    if (-not $candidate -or $candidate.IndexOfAny([char[]]'\/:*?') -ge 0 -or $candidate.Trim('.') -eq '') { throw "'$candidate' is not a save name (one folder name, no path)" }
}
$WantFresh = $FreshSave -or ($ScenarioData.PSObject.Properties['freshSave'] -and $ScenarioData.freshSave)
if ($WantFresh -and $PlaySave -eq $SaveName) { Write-Host "note: a fresh save applies to a named save only; this run plays '$SaveName' as it is" }
# Optional dev-runtime mods: the scenario's "mods" aliases plus -Mods, in that order, each once. Empty = no -PwithMods.
$ModAliases = @(@(if ($ScenarioData.PSObject.Properties['mods']) { $ScenarioData.mods }) + $Mods |
        ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique)

# ── Bridge plumbing ──────────────────────────────────────────────────────────

$script:Bridge = $null          # the pin: bridge.json as read at boot (url, token, pid, port) plus the client's startFileTime
$script:Spawned = $null
$script:PinViolation = $null    # why the pin broke, once it has; the scenario stops there
$script:ScenarioStarted = [datetime]::MinValue   # when the current node's steps began: `expectFile` ignores older files

function Assert-BridgePinned {
    # Every bridge call first proves the run still talks to the client it pinned at boot (SmokeGuard.ps1). The pinned url
    # and token would keep answering for that client after another one took bridge.json over, so a breach is the signal:
    # a second client means another session may be driving this world, or this run a foreign one.
    if (-not $script:Bridge) { return }
    $problem = Get-BridgePinProblem -Pin $script:Bridge -BridgeFile $BridgeFile
    if ($problem) {
        if (-not $script:PinViolation) { $script:PinViolation = "bridge pin violated: $problem" }
        throw $script:PinViolation
    }
}

function Invoke-Bridge([string]$Method, [string]$Path, $Body = $null, [int]$TimeoutSec = 60, [switch]$NoPinCheck) {
    if (-not $NoPinCheck) { Assert-BridgePinned }
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
            # A condition throws 'FATAL: ...' when waiting longer cannot help (e.g. the process it waits on is gone);
            # a broken bridge pin is the same, and Invoke-Scenario reads the flag.
            if ($_.Exception.Message.StartsWith('FATAL: ')) { throw $_.Exception.Message.Substring(7) }
            if ($script:PinViolation) { throw $_.Exception.Message }
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds $PollMs
    }
    throw "timed out after ${TimeoutSec}s waiting for $What" + $(if ($lastError) { " (last error: $lastError)" } else { '' })
}

# Remove-Item -Recurse races with whatever else touches the folder (a virus scan, the indexer, a Gradle file watcher): "The
# directory is not empty" on a folder that was empty a moment later. Retried; a folder that is gone by then is a success.
function Remove-TreeWithRetry([string]$Path) {
    for ($try = 1; $try -le 6; $try++) {
        try { Remove-Item -Recurse -Force -LiteralPath $Path -ErrorAction Stop; return } catch {
            if (-not (Test-Path -LiteralPath $Path)) { return }
            if ($try -eq 6) { throw }
            Start-Sleep -Seconds 2
        }
    }
}

# POST /command, repeated when the bridge gave up on it ("TimeoutException": the server thread stayed busy for longer than the bridge
# waits, on a loaded machine or while a fresh world generates chunks). The first attempt either ran or runs when the thread frees,
# so the repeat may run the command a second time. Most rig commands are idempotent in effect (setblock, fill, item replace, tp,
# queries): the repeat finds the work done and its "did not change" / "could not set the block" replies are the ones the cmd step
# already forgives (in English runs). A command that is not (clear, a claim, a team command) answers the repeat with a failure, so
# such a step can fail after a timeout though its first attempt worked: never a false pass, and the step failed before retries too.
# A command that generates chunks on the tick thread (spreadplayers, place, forceload add, locate) asks the bridge to wait 30 s
# instead of its 5 s default (#216), so the first attempt ends it and a retry does not repeat it; a bridge jar older than 1.4.4
# ignores the field and only the retry is left.
function Invoke-ServerCommand([string]$Command, [int]$Attempts = 3) {
    $body = @{ command = $Command }
    if ($Command -match '^\s*/?(spreadplayers|place|forceload\s+add|locate)\b') { $body.timeoutMs = 30000 }
    for ($attempt = 1; ; $attempt++) {
        try { return Invoke-Bridge POST '/command' $body } catch {
            if ($attempt -ge $Attempts -or "$_" -notmatch 'TimeoutException') { throw }
            Start-Sleep -Seconds 2
        }
    }
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
        if (($null -ne $s.PSObject.Properties['player']) -and ($null -eq $s.PSObject.Properties['screen'])) { return $true }
        # A world saved by a Forge client before #213 carries the experimental lifecycle (see New-SmokeWorld), so loading it (quick-play, or
        # Play Selected World) stops at vanilla's "Worlds using Experimental Settings are not supported" confirmation, which
        # waits for a click for ever (the render thread idles in the frame limiter, no server thread ever starts). Load without
        # a backup, as a person making the smoke world would; any other screen is left alone.
        if ($s.PSObject.Properties['screen'] -and $s.screen.class -match 'BackupConfirmScreen' -and "$($s.screen.title)" -match 'Experimental') {
            Write-Host 'note: loaded a world that carries the experimental lifecycle (clicked "I Know What I''m Doing!" on the backup confirmation)'
            Invoke-MenuClick "I Know What I'm Doing!"
        }
        $false
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
    # GUI scale is re-applied every run: the scenario's "guiScale" (Minecraft clamps it to what the window fits, so a
    # big scale only takes effect after a `window` step grows the client area), else 2. Particles go back to All every
    # run, so a scenario that lowers them in Video Settings and fails before restoring them can't thin the next one.
    $scale = if ($ScenarioData.PSObject.Properties['guiScale']) { [int]$ScenarioData.guiScale } else { 2 }
    # The language is re-applied too (-Lang, else English), so a translated run never leaks into the next one.
    # A scenario may pin more keys ("options": {"renderDistance": 4}). renderDistance goes back to 6 on a run that names
    # none, so one scenario's value never reaches the next; any other key it pins stays in the file until changed.
    $extra = [ordered]@{ renderDistance = 6 }
    if ($ScenarioData.PSObject.Properties['options']) {
        foreach ($p in $ScenarioData.options.PSObject.Properties) { $extra[$p.Name] = $p.Value }
    }
    $managed = @('guiScale', 'particles', 'lang') + @($extra.Keys)
    $lines = @(Get-Content -LiteralPath $options | Where-Object { $_ -notmatch "^($($managed -join '|')):" }) +
        "guiScale:$scale" + 'particles:0' + "lang:$Lang" +
        @($extra.Keys | ForEach-Object { "${_}:$(if ($extra[$_] -is [bool]) { "$($extra[$_])".ToLower() } else { $extra[$_] })" })
    $lines | Set-Content -LiteralPath $options -Encoding utf8
    # Ledgers remember open/closed per screen in this file: start every run with all of them closed, so a screenshot
    # (or a click on a ledger tab) never depends on what an earlier run left open.
    Remove-Item -LiteralPath (Join-Path $runDir 'config/buildcraft/gui_state.json') -ErrorAction SilentlyContinue
    # forge-config-screen flips this client option in the config screen and flips it back: a run that failed in between
    # would leave searing steam off for every later scenario, so it goes back to its default before every run.
    $bcClient = Join-Path $runDir 'config/buildcraftunofficial-client.toml'
    if (Test-Path -LiteralPath $bcClient) {
        $toml = Get-Content -LiteralPath $bcClient -Raw
        $fixed = $toml -replace '(?m)^(\s*searingFluidSteam\s*=\s*)false\s*$', '${1}true'
        if ($fixed -ne $toml) { Set-Content -LiteralPath $bcClient -Value $fixed -NoNewline -Encoding utf8 }
    }
    if ((Get-NodeLoader $Node) -eq 'neoforge') {
        # NeoForge's "mods loaded with warnings" screen blocks quick-play (REI trips it on 26.3 with @OnlyIn). The log
        # keeps the warnings; NeoForge fills in the rest of a file that only holds this key. Fabric has no such screen.
        $neoClient = Join-Path $runDir 'config/neoforge-client.toml'
        if (Test-Path $neoClient) {
            (Get-Content -LiteralPath $neoClient) -replace '^showLoadWarnings\s*=.*', 'showLoadWarnings = false' |
                Set-Content -LiteralPath $neoClient -Encoding utf8
        } else {
            New-Item -ItemType Directory -Force -Path (Split-Path $neoClient) | Out-Null
            'showLoadWarnings = false' | Set-Content -LiteralPath $neoClient -Encoding utf8
        }
    }
    [pscustomobject]@{ RunDir = $runDir; Jar = $jar.Name }
}

# ── Client lifecycle ─────────────────────────────────────────────────────────

function Assert-NoOtherClient {
    # Every McDevBridge client overwrites the one ~/.mcdevbridge/bridge.json: a second dev client (another session's,
    # or one left open by hand) would steal this run's bridge mid-scenario. Another runner is kept out by the smoke lock
    # for the whole run; this catches a client started by hand, and only at boot (the bridge pin catches one that
    # appears mid-run).
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
    # Every java process already running: a bridge.json naming one of them is not the client about to be spawned.
    $javaBefore = @(Get-Process java, javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $gradleArgs = @(":${Node}:runClient", '-PbcRunDir=run-smoke', '--console=plain')
    if ($NoDaemon) { $gradleArgs += '--no-daemon' }
    if ($QuickPlay) { $gradleArgs += "-PbcQuickPlay=$PlaySave" }
    # A scenario's "gradleProperties" (e.g. ["withGuideMe"]) become -P flags: optional dev-runtime mods it needs.
    if ($ScenarioData.PSObject.Properties['gradleProperties']) {
        $gradleArgs += @($ScenarioData.gradleProperties | ForEach-Object { "-P$_" })
    }
    # A scenario's "recipeViewer" ("rei") boots that viewer in place of the default JEI. A NeoForge dev client has JEI
    # without being asked, as has a Forge node that pins it (1.21.1-forge); a Fabric one loads a viewer only when told to,
    # so it is always named there. Get-SkipReason keeps a scenario off a node that does not pin the viewer it names.
    if ($ScenarioData.PSObject.Properties['recipeViewer']) { $gradleArgs += "-Precipe_viewer=$($ScenarioData.recipeViewer)" }
    elseif ((Get-NodeLoader $Node) -eq 'fabric') { $gradleArgs += '-Precipe_viewer=jei' }
    # A scenario's "mods" and -Mods name optional dev-runtime mods by alias; the Gradle side resolves them.
    if ($ModAliases.Count) { $gradleArgs += "-PwithMods=$($ModAliases -join ',')" }
    $proc = Start-Process -FilePath (Join-Path $Repo 'gradlew.bat') -ArgumentList $gradleArgs -WorkingDirectory $Repo `
        -RedirectStandardOutput (Join-Path $LogDir 'gradle.out.log') -RedirectStandardError (Join-Path $LogDir 'gradle.err.log') `
        -WindowStyle Hidden -PassThru
    # Recorded before any wait can throw, so the main loop can clean up a client that booted without a bridge.
    $script:Spawned = [pscustomobject]@{ Gradle = $proc; Since = Get-Date }
    $script:PinViolation = $null

    # Only a bridge.json written by the client THIS run spawned is taken. The port that client logged to our own Gradle
    # output names it exactly: a file naming another port is a second client booting beside ours (another session's, or
    # a stale file), so wait on for ours to be written. The process checks (alive, a Minecraft process, started after the
    # spawn, owner of that port) come on top.
    $gradleLog = Join-Path $LogDir 'gradle.out.log'
    $script:Bridge = Wait-Until {
        if ($proc.HasExited) { throw "FATAL: runClient exited early (code $($proc.ExitCode)); see $LogDir" }
        # A mod set the loader refuses (a mod that wants another version of a library, a missing dependency): Fabric Loader
        # shows its error window and the process stays alive, so the wait for bridge.json would run out the whole
        # boot budget with the smoke lock held. The loader's own message is in the console log; say it now.
        if (Test-Path $gradleLog) {
            $refusal = @(Get-Content -LiteralPath $gradleLog -Tail 400 -ErrorAction SilentlyContinue |
                    Where-Object { $_ -match 'Incompatible mod set|FormattedException|ModResolutionException|ModLoadingException' })
            if ($refusal.Count) {
                $why = @(Get-Content -LiteralPath $gradleLog -Tail 400 | Where-Object { $_ -match '^\s+- Mod |Mod .* (is incompatible|requires|depends)' } | Select-Object -First 4) -join ' | '
                throw "FATAL: the mod loader refused this mod set: $($refusal[0].Trim())$(if ($why) { " [$why]" })"
            }
        }
        if (-not (Test-Path $BridgeFile) -or (Get-Item $BridgeFile).LastWriteTimeUtc -le $before) { return $null }
        # A read can catch the file mid-write: the parse throws and the next poll retries.
        $info = Get-Content -Raw $BridgeFile | ConvertFrom-Json
        $logPort = Get-SpawnLogBridgePort $gradleLog
        if ($null -eq $logPort) { throw "bridge.json is there, but the spawned client has not logged its bridge port to $gradleLog" }
        $problem = Get-BridgeBootProblem -Info $info -Since $script:Spawned.Since -JavaBefore $javaBefore -SpawnLogPort $logPort
        if ($problem) { throw $problem }
        $info
    } ($BootMinutes * 60) "this run's client to publish its bridge.json" 500
    # The pin is that file's url, token and pid, plus when the client started (to tell it from a later owner of the pid).
    $script:Bridge | Add-Member -NotePropertyName startFileTime -NotePropertyValue (Get-ProcessStartFileTime ([int]$script:Bridge.pid)) -Force
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
    # The pinned url still reaches the pinned client when another one has taken bridge.json over, so save through it
    # anyway; $Client.Pid is the pinned pid, never whatever bridge.json says now.
    try { Invoke-Bridge POST '/command' @{ command = 'save-all flush' } 120 -NoPinCheck | Out-Null } catch { }
    Stop-Process -Id $Client.Pid -Force -ErrorAction SilentlyContinue
    if (-not $Client.Gradle.WaitForExit(90000)) {
        # The Gradle wrapper outlived its client: take its process tree down so the next node can boot.
        & taskkill /PID $Client.Gradle.Id /T /F | Out-Null
    }
    $script:Bridge = $null
}

function Send-GameEscape([switch]$Focus) {
    # The bridge can only press a key into an OPEN screen (/screenkeys answers "no screen open" in the world), and the
    # pause menu has no keybind, so this posts a real Esc key message to the game window. PostMessage needs no focus
    # with GLFW (it turns the message into a key event on the window it was posted to), so it works from this hidden
    # background process, takes no focus from the user, and can never land in another application.
    # 26.3 runs SDL3, which drops key messages for a window without keyboard focus: -Focus brings the game window to
    # the front first and then presses a real Esc, but only while that window really is in front.
    if (-not ('SmokeWin32' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class SmokeWin32 {
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint from, uint to, bool attach);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
}
'@
    }
    $handle = (Get-Process -Id ([int]$script:Bridge.pid)).MainWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw 'the game window has no handle to send Esc to' }
    if ($Focus) {
        # Windows only lets the foreground owner hand focus on, so ask three ways, weakest first: borrow the foreground
        # thread's input queue; a bare Alt tap (lifts the foreground lock); a minimise/restore of the game window.
        $note = ''
        foreach ($attempt in 1..3) {
            $front = [SmokeWin32]::GetForegroundWindow()
            $pid2 = [uint32]0
            $frontThread = [SmokeWin32]::GetWindowThreadProcessId($front, [ref]$pid2)
            $me = [SmokeWin32]::GetCurrentThreadId()
            $attached = $false
            if ($attempt -eq 1 -and $frontThread -ne 0 -and $frontThread -ne $me) { $attached = [SmokeWin32]::AttachThreadInput($me, $frontThread, $true) }
            if ($attempt -eq 2) {
                [SmokeWin32]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero)
                [SmokeWin32]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
            }
            if ($attempt -eq 3) {
                [SmokeWin32]::ShowWindow($handle, 6) | Out-Null
                Start-Sleep -Milliseconds 200
                [SmokeWin32]::ShowWindow($handle, 9) | Out-Null
            }
            $focused = [SmokeWin32]::SetForegroundWindow($handle)
            if ($attached) { [SmokeWin32]::AttachThreadInput($me, $frontThread, $false) | Out-Null }
            Start-Sleep -Milliseconds 400
            $nowFront = [SmokeWin32]::GetForegroundWindow()
            $note += "[try $attempt SetForegroundWindow=$focused front=$front thread=$frontThread attached=$attached now=$nowFront] "
            if ($nowFront -eq $handle) {
                [SmokeWin32]::keybd_event(0x1B, 0x01, 0, [UIntPtr]::Zero)
                Start-Sleep -Milliseconds 80
                [SmokeWin32]::keybd_event(0x1B, 0x01, 2, [UIntPtr]::Zero)
                return "real Esc sent to the focused game window $note"
            }
        }
        return "could not focus the game window $note"
    }
    # WM_KEYDOWN / WM_KEYUP, VK_ESCAPE, scancode 1 (lParam bits 16-23), repeat 1; the key-up also sets bits 30 and 31.
    [SmokeWin32]::PostMessage($handle, 0x100, [IntPtr]0x1B, [IntPtr]0x00010001) | Out-Null
    Start-Sleep -Milliseconds 80
    [SmokeWin32]::PostMessage($handle, 0x101, [IntPtr]0x1B, [IntPtr]([int64]0xC0010001)) | Out-Null
}

function Set-GameClientSize([int]$Width, [int]$Height) {
    # options.txt overrideWidth/Height lose to NeoForge's early window, so resize the real window: SetWindowPos with
    # the frame added back (AdjustWindowRectEx) gives a client area, and so a framebuffer, of exactly Width x Height.
    if (-not ('SmokeWin32Size' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class SmokeWin32Size {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h, int i);
    [DllImport("user32.dll")] public static extern bool AdjustWindowRectEx(ref RECT r, int style, bool menu, int exStyle);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
}
'@
    }
    $handle = (Get-Process -Id ([int]$script:Bridge.pid)).MainWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw 'the game window has no handle to resize' }
    $rect = New-Object SmokeWin32Size+RECT
    $rect.Right = $Width; $rect.Bottom = $Height
    [SmokeWin32Size]::AdjustWindowRectEx([ref]$rect, [SmokeWin32Size]::GetWindowLong($handle, -16), $false,
        [SmokeWin32Size]::GetWindowLong($handle, -20)) | Out-Null
    # SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE: size only, window stays where it is, no focus change.
    [SmokeWin32Size]::SetWindowPos($handle, [IntPtr]::Zero, 0, 0, $rect.Right - $rect.Left, $rect.Bottom - $rect.Top, 0x16) | Out-Null
    $client = New-Object SmokeWin32Size+RECT
    [SmokeWin32Size]::GetClientRect($handle, [ref]$client) | Out-Null
    if ($client.Right -ne $Width -or $client.Bottom -ne $Height) {
        throw "client area is $($client.Right)x$($client.Bottom), wanted ${Width}x${Height}"
    }
    "client area $($client.Right)x$($client.Bottom)"
}

function Invoke-MenuClick([string]$Text) {
    # Menu clicks that load the world list or generate a world block the game thread for longer than the bridge's
    # bounded wait, so the call reports a TimeoutException although the click happened. The callers poll the screen
    # afterwards, which is the real check.
    try { Invoke-Bridge POST '/screenclick' @{ widgetText = $Text } 120 | Out-Null }
    catch { if ($_.Exception.Message -notmatch 'TimeoutException|timed out|HttpClient\.Timeout') { throw } }
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
    # A Forge client answers "Create New World" with vanilla's "Warning! These settings are using experimental features"
    # confirmation (a non-vanilla data pack's registry entries carry the experimental lifecycle; 26.3-forge before #213's
    # MappedRegistryMixin, and still for any other Forge mod with worldgen data), which
    # waits for a click for ever: say Yes to it, as a person making the smoke world would, then wait for the world.
    Wait-Until {
        $s = Get-State
        if (($null -ne $s.PSObject.Properties['player']) -and ($null -eq $s.PSObject.Properties['screen'])) { return $true }
        if ($s.PSObject.Properties['screen'] -and $s.screen.class -match 'ConfirmScreen' -and "$($s.screen.title)" -match 'experimental') {
            Write-Host 'note: the new world is flagged experimental (clicked Yes on the experimental-features warning)'
            Invoke-MenuClick 'Yes'
        }
        $false
    } 600 'the player to be in a world with no screen open' 1000 | Out-Null
    # A new world's spawn is random and the chunks around the player arrive seconds after the join: a first step that edits
    # blocks at the scenario's base would meet "That position is not loaded". Stand at the base and wait for it to load. Only
    # here (the first run of a node): a run that quick-plays the saved world starts as it always did. Creative first: the
    # tp is a fall of the base's height (a survival player dies of it before the rig stands); the saved worlds of the other
    # nodes hold a creative player too, since nearly every scenario opens with `gamemode creative @p`.
    if ($Base.Count -ge 3) {
        $pos = "$($Base[0]) $($Base[1]) $($Base[2])"
        Invoke-Bridge POST '/command' @{ command = 'gamemode creative @p' } | Out-Null
        Invoke-Bridge POST '/command' @{ command = "execute as @p run tp @p $pos" } | Out-Null
        Wait-Until { (Invoke-Bridge POST '/command' @{ command = "execute if loaded $pos" }).success } 120 "the scenario base ($pos) to be loaded" 1000 | Out-Null
    }
}

function Open-SaveFromMenu([string]$Save) {
    # The quick-play path by hand, for a node whose quick-play is defective (Test-QuickPlayDefective): Singleplayer, the
    # save's folder name typed into the search box (focused when the screen opens) so it is the only entry, a click on the
    # entry's row to select it, then "Play Selected World". The row is the world list's first: its top is the list's top
    # plus 4, and it is 36 high (vanilla WorldSelectionList, 1.21.1); the click lands right of the 32-pixel icon, which
    # would join on its own and bypass the button this path checks.
    Wait-Until { (Get-ScreenClass) -match 'TitleScreen' } 300 'the title screen' 1000 | Out-Null
    Wait-NoOverlay
    Invoke-MenuClick 'Singleplayer'
    Wait-Until { (Get-ScreenClass) -match 'SelectWorldScreen' } 120 'the world list' 1000 | Out-Null
    Invoke-Bridge POST '/screenkeys' @{ type = 'text'; value = $Save } | Out-Null
    # The list reads the saves off the game thread, so the row may not exist at the first click: click it until the
    # button turns active (it is inactive while nothing is selected).
    $script:listing = $null
    try { Wait-Until {
        $script:listing = Invoke-Bridge POST '/screenclick' @{ list = $true }
        if (@($script:listing.widgets | Where-Object { "$($_.text)" -eq 'Play Selected World' -and $_.active }).Count) { return $true }
        $list = @($script:listing.widgets | Where-Object { $_.class -match 'WorldSelectionList' }) | Select-Object -First 1
        if ($list) { $top = [int]$list.y; $mid = [int]$list.x + [int]([int]$list.width / 2) }
        else { $top = 48; $mid = [int]([int]$script:listing.width / 2) }
        Invoke-Bridge POST '/screenclick' @{ x = $mid; y = $top + 4 + 18 } | Out-Null
        Start-Sleep -Milliseconds 400   # past the list's 250 ms double-click window: the next click selects, never joins
        $false
    } 60 "'Play Selected World' to turn active" 1000 | Out-Null }
    catch {
        $seen = @($(if ($script:listing) { $script:listing.widgets }) | ForEach-Object { "$($_.class) '$($_.text)' @$($_.x),$($_.y) $($_.width)x$($_.height) active=$($_.active)" }) -join '; '
        throw "could not select '$Save' on the world list: $($_.Exception.Message) (widgets: $seen)"
    }
    Invoke-MenuClick 'Play Selected World'
    Write-Host "note: opened '$Save' through Singleplayer (this node's quick-play is defective)"
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

function Test-LoaderFilter($Item, [string]$Node) {
    # "loaders": ["neoforge"] on a step, or on the scenario itself, keeps it off the other loader's nodes.
    if (-not $Item.PSObject.Properties['loaders']) { return $true }
    return @($Item.loaders) -contains (Get-NodeLoader $Node)
}

function Get-SkipReason([string]$Node) {
    # Why the whole scenario is not run on this node, or $null when it is. Not a failure either way: the scenario needs
    # something this node or this language does not have.
    if (-not (Test-LoaderFilter $ScenarioData $Node)) { return "runs on $(@($ScenarioData.loaders) -join ', ') only" }
    # What the scenario needs from the loader's dev runtime: its own top-level "needs" list, a "recipeViewer" (it boots that
    # viewer) and optional dev-runtime mods ("mods" / -Mods, "gradleProperties"). A loader that lacks one skips the scenario.
    $needs = @(if ($ScenarioData.PSObject.Properties['needs']) { $ScenarioData.needs })
    if ($ScenarioData.PSObject.Properties['recipeViewer']) { $needs += 'recipeViewer'; $needs += "viewer:$($ScenarioData.recipeViewer)" }
    if ($ModAliases.Count) { $needs += 'devMods' }
    if ($ScenarioData.PSObject.Properties['gradleProperties'] -and @($ScenarioData.gradleProperties).Count) { $needs += 'devProperties' }
    $lack = Get-LackReason $needs $Node
    if ($lack) { return $lack }
    # A scenario that names mods ("mods") needs at least one of them on this node: Gradle skips an alias the node does not
    # define (a lifecycle line only), so a scenario whose every mod is missing would run bare and fail on its first lane.
    # One defined alias is enough to run: crossmod-energy names Tech Reborn too, which only some nodes have.
    $own = @()
    if ($ScenarioData.PSObject.Properties['mods']) { $own = @($ScenarioData.mods | Where-Object { $_ }) }
    if ($own.Count -and -not @($own | Where-Object { Test-NodeDefinesMod $Node $_ }).Count) {
        return "needs a dev mod this node does not define ($($own -join ', '): no withmod.<alias> in versions/$Node/gradle.properties)"
    }
    # A top-level "englishOnly": true marks a scenario that clicks by English widget text, which a translated client
    # does not match. (The batch recognises this reason by its wording: Invoke-SmokeBatch.ps1.)
    if ($Lang -ne 'en_us' -and $ScenarioData.PSObject.Properties['englishOnly'] -and $ScenarioData.englishOnly) {
        return "runs in English only (it clicks by widget text; this run is -Lang $Lang)"
    }
    return $null
}

function Test-NodeFilter($Step, [string]$Node) {
    if (-not (Test-LoaderFilter $Step $Node)) { return $false }
    if (-not $Step.PSObject.Properties['nodes']) { return $true }
    # A step's "nodes" names Minecraft lines: "26.3" covers every loader's node of that line (the NeoForge node, its Fabric
    # twin and its Forge twin), so a step that only one loader can run says so with "loaders". Naming the twin itself
    # ("26.3-fabric", "26.3-forge") covers that node only.
    return (@($Step.nodes) -contains $Node) -or (@($Step.nodes) -contains (Get-NodeMcVersion $Node))
}

# True when a reply line is vanilla's parse-error context (Commands.performCommand): the input itself when the error sits
# in its first ten characters, else "..." and the input from ten characters before the error, in both cases running to
# the end of the input and then a translated "<--[HERE]". Anchored on that shape, so a run-time refusal that merely ends
# with the command's last words ("already peaceful") does not count.
function Test-ParseErrorEcho([string]$Command, $Lines) {
    foreach ($line in $Lines) {
        $l = [string]$line
        if ($l.StartsWith($Command)) { return $true }
        if (-not $l.StartsWith('...')) { continue }
        $rest = $l.Substring(3)
        for ($p = 1; $p -le $Command.Length - 10; $p++) {
            if ($rest.StartsWith($Command.Substring($p))) { return $true }
        }
    }
    return $false
}

function Invoke-Step($Step, [string]$Node, [string]$NodeOut, [int]$Index) {
    $detail = ''
    $shot = $null
    switch ($Step.do) {
        'cmd' {
            # "benign": true says every command of the step may legitimately fail (nothing to kill, nothing changed on a
            # re-run): such a failure is not an error, in every language. `success` is the bridge's language-neutral result
            # code, so any other failed command fails the step the same way in English and under -Lang.
            $stepBenign = $Step.PSObject.Properties['benign'] -and [bool]$Step.benign
            $gaveUp = 0
            foreach ($c in @($Step.run)) {
                # A leading '?' marks one command as allowed to fail (e.g. a fill that finds nothing to replace).
                $optional = $c.StartsWith('?')
                if ($optional) { $c = $c.Substring(1) }
                try {
                    $r = Invoke-ServerCommand (Expand-Coords $c)
                } catch {
                    # An optional command the bridge kept giving up on (the server thread was busy: a forceload over chunks
                    # that were still generating) is not an error: the command was handed over and runs when the thread frees.
                    if ($optional -and "$_" -match 'TimeoutException') { $gaveUp++; continue }
                    throw
                }
                # The world persists between runs, so "already so" replies are success for a rig builder. The reply TEXT is
                # translated under -Lang, so the English wording is read in English runs only; a scenario meant for -Lang
                # says it with "benign" or '?'.
                # 26.3 also fails `time set N` when the frozen clock already reads N ("Clock ... is already set to ...", and for
                # a named marker "Clock ... is already at time marker minecraft:noon").
                # World-state setters that fail only when the state is already so are benign by the COMMAND, in any language,
                # unless the command never parsed (an unknown gamerule, a bad coordinate): vanilla then echoes the input up to
                # the error and everything after it (see Test-ParseErrorEcho). A refusal at run time ("already set") does not.
                $parseError = Test-ParseErrorEcho (Expand-Coords $c).TrimStart('/') @($r.output)
                $idempotent = -not $parseError -and $c -match '^(difficulty|time set|weather|gamerule|gamemode|forceload add|tick (un)?freeze)\b'
                $benign = $stepBenign -or $idempotent -or ($Lang -eq 'en_us' -and
                    (@($r.output) -join ' ') -match '(?i)did not change|could not set the block|no blocks were filled|is already set to|is already at time marker')
                if (-not $r.success -and -not $benign -and -not $optional -and -not $Step.PSObject.Properties['allowFail']) {
                    throw "command failed: $(Expand-Coords $c) -> $(@($r.output) -join ' | ')"
                }
            }
            $detail = "$(@($Step.run).Count) command(s)"
            if ($gaveUp) { $detail += ", $gaveUp optional one(s) the bridge timed out on" }
            # A step that asks for it ("echo": true) puts the last command's reply in the report (state dumps).
            if ($Step.PSObject.Properties['echo'] -and $Step.echo) { $detail += ": $(@($r.output) -join ' | ')" }
        }
        # Runs each command AS the player (the bridge's /chat command: the player's own permission level and an entity as the
        # source). For a command whose requirements read the source's entity at parse time: `execute as @p run ...` does not
        # pass them, because Brigadier checks a requirement against the ORIGINAL source, before the execute modifiers apply
        # (FTB Teams' `party create`). The reply goes to the client's chat, not back here: gate on observed state with a
        # waitCmd afterwards.
        'playerCmd' {
            foreach ($c in @($Step.run)) {
                Invoke-Bridge POST '/chat' @{ action = 'command'; text = (Expand-Coords $c).TrimStart('/') } | Out-Null
                Start-Sleep -Milliseconds 800
            }
            $detail = "$(@($Step.run).Count) player command(s)"
        }
        # Re-runs one command until its reply matches `match` (a regex): gates on observed state instead of a fixed
        # wait (a machine finishing its work). The reply is in the report either way.
        'waitCmd' {
            $c = Expand-Coords ([string]$Step.run)
            $timeout = if ($Step.PSObject.Properties['timeoutSec']) { $Step.timeoutSec } else { 60 }
            $poll = if ($Step.PSObject.Properties['pollMs']) { $Step.pollMs } else { 1000 }
            $deadline = (Get-Date).AddSeconds($timeout)
            $last = ''
            do {
                # A poll the bridge gave up on (the server thread was busy, a world still generating chunks) is a poll that
                # did not match yet, not a failed step.
                $timedOut = $false
                try {
                    $r = Invoke-Bridge POST '/command' @{ command = $c }
                    $last = @($r.output) -join ' | '
                } catch {
                    if ("$_" -notmatch 'TimeoutException') { throw }
                    $last = 'the bridge timed out (the server thread is busy)'
                    $timedOut = $true
                }
                if (-not $timedOut -and $last -match $Step.match) { break }
                Start-Sleep -Milliseconds $poll
            } while ((Get-Date) -lt $deadline)
            if ($timedOut -or $last -notmatch $Step.match) { throw "no reply matched '$($Step.match)' within ${timeout}s: $c -> $last" }
            $detail = $last
        }
        'tp' {
            $x = $Base[0] + $Step.at[0] + 0.5; $y = $Base[1] + $Step.at[1]; $z = $Base[2] + $Step.at[2] + 0.5
            $yaw = if ($Step.PSObject.Properties['yaw']) { $Step.yaw } else { 0 }
            $pitch = if ($Step.PSObject.Properties['pitch']) { $Step.pitch } else { 0 }
            # A bare server-context tp does not move the player; running it AS the player does.
            Invoke-ServerCommand "execute as @p at @p run tp @p $x $y $z $yaw $pitch" | Out-Null
            Wait-Until { $p = (Get-State).player.pos; [math]::Abs($p.x - $x) -lt 0.6 -and [math]::Abs($p.z - $z) -lt 0.6 } 30 "the player to reach $x $y $z" | Out-Null
            Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $yaw; pitch = $pitch } | Out-Null
            $detail = "at $x $y $z"
        }
        'aim' {
            # `exact` aims at the point `at` itself (a box corner, an addon slot) instead of that block's centre.
            $c = if ($Step.PSObject.Properties['exact'] -and $Step.exact) { 0 } else { 0.5 }
            $p = (Get-State).player.pos
            $dx = $Base[0] + $Step.at[0] + $c - $p.x
            $dy = $Base[1] + $Step.at[1] + $c - ($p.y + 1.62)
            $dz = $Base[2] + $Step.at[2] + $c - $p.z
            $yaw = [math]::Atan2(-$dx, $dz) * 180 / [math]::PI
            $pitch = - [math]::Atan2($dy, [math]::Sqrt($dx * $dx + $dz * $dz)) * 180 / [math]::PI
            Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $yaw; pitch = $pitch } | Out-Null
            Start-Sleep -Milliseconds 150
            $hit = (Get-State).hit
            $detail = "yaw $([math]::Round($yaw,1)) pitch $([math]::Round($pitch,1)); hit $($hit | ConvertTo-Json -Compress -Depth 3)"
        }
        'look' {
            Invoke-Bridge POST '/look' @{ mode = 'absolute'; yaw = $Step.yaw; pitch = $Step.pitch } | Out-Null
            # The crosshair target is re-picked on a later frame (26.3 lags enough for a `use` right after to hit the old
            # aim), so settle before any click, as `aim` does.
            Start-Sleep -Milliseconds 150
        }
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
                # Leaving a world blocks the game thread while it saves and unloads, longer than the bridge's bounded wait
                # (slow on Server1's disk): the call reports a TimeoutException although the click happened, as with
                # Invoke-MenuClick. The step after it (expectScreen) is the real check.
                if ($Step.do -eq 'click' -and $Step.PSObject.Properties['widgetText'] -and $Step.widgetText -match '^Save and Quit to Title$' -and
                        $_.Exception.Message -match 'TimeoutException|timed out|HttpClient\.Timeout') {
                    return [pscustomobject]@{ detail = 'bridge timed out while the world saved (the click still reached the game)'; shot = $null }
                }
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
        'scroll' {
            Wait-NoOverlay
            $body = @{}
            foreach ($k in 'widgetText', 'slotIndex', 'x', 'y', 'fx', 'fy', 'scrollX', 'scrollY', 'notches') {
                if ($Step.PSObject.Properties[$k]) { $body[$k] = $Step.$k }
            }
            $r = Invoke-Bridge POST '/screenscroll' $body
            $detail = "handled $($r.handled)/$($r.notches) at ($($r.x), $($r.y))"
            if ($r.handled -lt 1) { throw "scroll was not handled by the screen: $detail" }
            Start-Sleep -Milliseconds 300   # let the next frames redraw
        }
        'key' { Invoke-Bridge POST '/screenkeys' @{ type = 'key'; key = $Step.key } | Out-Null; $detail = $Step.key }
        # A game keybind by translation key: `press`/`release` hold one (key.sneak for a sneak-click), `tap` clicks it once.
        'keybind' {
            $r = Invoke-Bridge POST '/keybind' @{ keyName = $Step.key; action = $Step.action }
            if ($r.PSObject.Properties['success'] -and -not $r.success) { throw "keybind refused: $($r.message)" }
            Start-Sleep -Milliseconds 150   # held state reaches the server with the next tick's input packet
            $detail = "$($Step.key) $($Step.action)"
        }
        # Typed text goes to whichever widget of the open screen has focus (click a search box first).
        'text' { Invoke-Bridge POST '/screenkeys' @{ type = 'text'; value = $Step.text } | Out-Null; $detail = $Step.text }
        # The close packet and a following /command race on the server: a slot change made while the server still has
        # the old menu open is sent under that menu's id, which the client (already back on its inventory) drops.
        'close' { Invoke-Bridge POST '/screenclose' | Out-Null; Start-Sleep -Milliseconds 400 }
        'wait' { Start-Sleep -Milliseconds $Step.ms; $detail = "$($Step.ms) ms" }
        'window' {
            $detail = Set-GameClientSize ([int]$Step.width) ([int]$Step.height)
            # The game re-lays out the open screen on the next frame.
            Start-Sleep -Milliseconds 1500
        }
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
            # The open screen is named only after the wait failed: a game thread still busy (a world saving) answers /state
            # with a timeout, which Wait-Until retries but a message built beforehand would not.
            $got = try { Wait-Until { $c = Get-ScreenClass; if (($want -eq '' -and $c -eq '') -or ($want -ne '' -and $c -match $want)) { if ($c) { $c } else { '(none)' } } } $timeout "screen matching '$want'" }
            catch { throw "$($_.Exception.Message) (open: '$(try { Get-ScreenClass } catch { '?' })')" }
            $detail = $got
        }
        # A file some other process leaves in this node's output folder: how a check the game cannot see learns that the
        # outside driver really acted (Drive-RealMouse.ps1 writes real-mouse-*.done when its gesture is finished). A file
        # from before this node's steps began is an earlier run's and does not count.
        'expectFile' {
            $rel = [string]$Step.path
            $root = [System.IO.Path]::GetFullPath($NodeOut)
            $full = [System.IO.Path]::GetFullPath($rel, $root)
            if ([System.IO.Path]::IsPathRooted($rel) -or -not $full.StartsWith($root.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "expectFile path '$rel' must be relative and stay inside the run's output folder"
            }
            $timeout = if ($Step.PSObject.Properties['timeoutSec']) { $Step.timeoutSec } else { 10 }
            Wait-Until {
                $f = Get-Item -LiteralPath $full -ErrorAction SilentlyContinue
                $f -and $f.Length -gt 0 -and $f.LastWriteTime -ge $script:ScenarioStarted
            } $timeout "file '$rel' in the run's output folder, written after this node's steps began" 500 | Out-Null
            $detail = "${rel}: $((Get-Content -Raw -LiteralPath $full).Trim())"
        }
        # Esc into the game window: opens the pause menu from the world (see Send-GameEscape). Follow with
        # `click` "Save and Quit to Title" to leave the world.
        'pause' {
            Send-GameEscape
            # GLFW nodes open the menu from the posted message; SDL3 (26.3) needs the window in front, so retry focused.
            $detail = try { Wait-Until { $c = Get-ScreenClass; if ($c -match 'PauseScreen') { $c } } 4 'the pause menu to open' }
            catch {
                $note = Send-GameEscape -Focus
                $screen = try { Wait-Until { $c = Get-ScreenClass; if ($c -match 'PauseScreen') { $c } } 10 'the pause menu to open' }
                catch { throw "$($_.Exception.Message) [$note]" }
                "$screen (needed the focused retry: $note)"
            }
        }
        # Waits until the player is in a world with no screen open: after `click` "Play Selected World".
        'inWorld' {
            $timeout = if ($Step.PSObject.Properties['timeoutSec']) { $Step.timeoutSec } else { 300 }
            Wait-InWorld $timeout
            $detail = 'in world'
        }
        default { throw "unknown step type '$($Step.do)'" }
    }
    [pscustomobject]@{ detail = $detail; shot = $shot }
}

function Invoke-Scenario([string]$Node, [string]$NodeOut) {
    $results = @()
    $i = 0
    $script:ScenarioStarted = Get-Date
    foreach ($step in @($ScenarioData.steps)) {
        $i++
        if (-not (Test-NodeFilter $step $Node)) { continue }
        $label = if ($step.PSObject.Properties['label']) { $step.label } elseif ($step.PSObject.Properties['name']) { $step.name } else { $step.do }
        # A step that needs something this loader's dev runtime lacks (a recipe viewer's click area) is recorded as skipped,
        # with the reason, not run and not failed: it asks for something the loader cannot give.
        $lack = if ($step.PSObject.Properties['needs']) { Get-LackReason $step.needs $Node } else { $null }
        if ($lack) {
            $results += [pscustomobject]@{ n = $i; step = $label; do = $step.do; ok = $true; skipped = $true; detail = "skipped: $lack"; shot = $null }
            continue
        }
        try {
            $r = Invoke-Step $step $Node $NodeOut $i
            $results += [pscustomobject]@{ n = $i; step = $label; do = $step.do; ok = $true; skipped = $false; detail = $r.detail; shot = $r.shot }
        } catch {
            $results += [pscustomobject]@{ n = $i; step = $label; do = $step.do; ok = $false; skipped = $false; detail = $_.Exception.Message; shot = $null }
            # A broken bridge pin ends the node: whatever a later step would drive, it is not the client this run pinned.
            if ($script:PinViolation) { break }
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

# One run per machine (SmokeGuard.ps1): taken before the first boot, held to the end of the last node, released in the
# finally below. A refusal is exit 3, so a caller can tell "someone else is running" from "a step failed" (exit 1).
try {
    $lock = Enter-SmokeLock -Owner 'Invoke-InClientSmoke.ps1' -Repo $Repo -Detail "nodes $($Nodes -join ','), scenario $($ScenarioData.name)$(if ($Lang -ne 'en_us') { ", lang $Lang" })"
} catch {
    [Console]::Error.WriteLine("smoke lock: $($_.Exception.Message)")
    Remove-Item -LiteralPath $OutDir -ErrorAction SilentlyContinue   # still empty
    exit 3
}
if ($lock.Recovered) { Write-Host "[smoke lock] took over a stale lock: its pid $($lock.Recovered.pid) is gone" }

$summary = @()
try {
    foreach ($node in $Nodes) {
        $why = Get-SkipReason $node
        if ($why) {
            # Not a failure: the scenario needs something only the other loader's dev runtime has, or English.
            Write-Host "[$node] skipped: scenario '$($ScenarioData.name)' $why"
            $summary += [pscustomobject]@{ node = $node; steps = 0; failed = 0; bcErrors = 0; fatal = $null; skipped = $why; report = $null }
            continue
        }
        $nodeOut = Join-Path $OutDir $node
        New-Item -ItemType Directory -Force -Path $nodeOut | Out-Null
        Write-Host "[$node] preparing"
        $report = [ordered]@{ node = $node; loader = (Get-NodeLoader $node); lang = $Lang; scenario = $ScenarioData.name; started = (Get-Date).ToString('s'); steps = @(); buildcraftErrors = @(); fatal = $null }
        $client = $null
        $prep = $null
        try {
            $prep = Initialize-RunDir $node
            $report.bridgeJar = $prep.Jar
            $savePath = Join-Path $prep.RunDir "saves/$SaveName"
            # A first run names the save whatever the menus defaulted to ("New World", or its translation); adopt the
            # only save there is, before booting, so an interrupted first run doesn't leave an orphan behind.
            # Open Parties and Claims on Forge (#215): a world's first write of its server config dies on Windows (Forge Config
            # API Port renames a file it still holds open; docs/forge-port.md), and every run of an OPAC scenario plays a fresh
            # copy of its seed save. A file in <gameDir>/defaultconfigs is copied instead of written, so seed it from the
            # complete default file shipped in scripts/smoke/seeds (#232; OPAC's keys are the same on every line, only
            # 1.21.1's default entity names differ). Before the fresh-save delete below.
            if ((Get-NodeLoader $node) -eq 'forge' -and $ModAliases -contains 'opac') {
                $opacSeed = Join-Path $prep.RunDir 'defaultconfigs/openpartiesandclaims-server.toml'
                if (-not (Test-Path -LiteralPath $opacSeed)) {
                    $shipped = Join-Path $PSScriptRoot "seeds/openpartiesandclaims-server.$(Get-NodeMcVersion $node).toml"
                    if (-not (Test-Path -LiteralPath $shipped)) { $shipped = Join-Path $PSScriptRoot 'seeds/openpartiesandclaims-server.toml' }
                    New-Item -ItemType Directory -Force -Path (Split-Path $opacSeed) | Out-Null
                    Copy-Item -LiteralPath $shipped -Destination $opacSeed
                    Write-Host "[$node] note: seeded defaultconfigs/openpartiesandclaims-server.toml from $(Split-Path -Leaf $shipped)"
                }
            }
            $saves = @(Get-ChildItem -Directory -Path (Join-Path $prep.RunDir 'saves') -ErrorAction SilentlyContinue)
            if (-not (Test-Path $savePath) -and $saves.Count -eq 1 -and $saves[0].Name -ne $PlaySave) { Rename-Item -LiteralPath $saves[0].FullName -NewName $SaveName }
            $haveSave = Test-Path $savePath
            if ($PlaySave -ne $SaveName) {
                $playPath = Join-Path $prep.RunDir "saves/$PlaySave"
                # Only with its seed in hand: a mistyped seed must not cost the save it was meant to replace.
                if ($WantFresh -and (Test-Path -LiteralPath $playPath) -and $PlaySeed -and $PlaySeed -ne $PlaySave -and (Test-Path -LiteralPath (Join-Path $prep.RunDir "saves/$PlaySeed"))) {
                    Remove-TreeWithRetry $playPath
                } elseif ($WantFresh -and (Test-Path -LiteralPath $playPath)) {
                    throw "a fresh '$PlaySave' needs its seed save$(if ($PlaySeed) { " '$PlaySeed'" }), which $node's run-smoke/saves does not have; '$PlaySave' is left as it is"
                }
                if (-not (Test-Path $playPath)) {
                    $seedPath = if ($PlaySeed) { Join-Path $prep.RunDir "saves/$PlaySeed" } else { $null }
                    if (-not $seedPath -or -not (Test-Path $seedPath)) {
                        throw "the scenario plays save '$PlaySave', which $node's run-smoke/saves does not have$(if ($PlaySeed) { " (nor its seed '$PlaySeed': run any scenario once first)" } else { ': copy it in first' })"
                    }
                    Copy-Item -Recurse -LiteralPath $seedPath -Destination $playPath
                }
            }
            $quickPlay = $haveSave -or ($PlaySave -ne $SaveName)
            # The first run creates the world by clicking the English menu buttons.
            if (-not $quickPlay -and $Lang -ne 'en_us') { throw "$node has no '$SaveName' save yet: run once without -Lang to create it" }
            # A scenario that visits more than one world names the extra saves; each is a copy of the first one, made
            # (once) while no client is running. A first run has nothing to copy yet: run it once more afterwards.
            if ($haveSave -and $ScenarioData.PSObject.Properties['extraSaves']) {
                foreach ($extra in @($ScenarioData.extraSaves)) {
                    $extraPath = Join-Path $prep.RunDir "saves/$extra"
                    if (-not (Test-Path $extraPath)) { Copy-Item -Recurse -LiteralPath $savePath -Destination $extraPath }
                }
            }
            Write-Host "[$node] booting client ($(if ($quickPlay) { "quick-play $PlaySave" } else { 'first run: creating the world' }))"
            $menuPlay = $quickPlay -and (Test-QuickPlayDefective $node)
            $client = Start-Client $node $prep.RunDir ($quickPlay -and -not $menuPlay) $nodeOut
            $report.bridgeVersion = $client.BridgeVersion
            if ($menuPlay) { Open-SaveFromMenu $PlaySave }
            if ($quickPlay) { Wait-InWorld 600 } else { New-SmokeWorld }
            $logCursor = (Invoke-Bridge POST '/log' @{ level = 'ERROR'; since = 0; limit = 1 }).nextCursor

            Write-Host "[$node] running $(@($ScenarioData.steps).Count) steps"
            $report.steps = @(Invoke-Scenario $node $nodeOut)
            if ($script:PinViolation) { throw $script:PinViolation }

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
            if (-not (Test-Path (Join-Path $prep.RunDir "saves/$SaveName")) -and $saves.Count -eq 1 -and $saves[0].Name -ne $PlaySave) {
                Rename-Item -LiteralPath $saves[0].FullName -NewName $SaveName
            }
        }
        $report.finished = (Get-Date).ToString('s')
        $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $nodeOut 'report.json') -Encoding utf8
        $failed = @($report.steps | Where-Object { -not $_.ok }).Count
        $summary += [pscustomobject]@{ node = $node; steps = @($report.steps).Count; failed = $failed; bcErrors = @($report.buildcraftErrors).Count; fatal = $report.fatal; skipped = $null; report = $report }
        Write-Host "[$node] done: $(@($report.steps).Count) steps, $failed failed, $(@($report.buildcraftErrors).Count) BuildCraft log errors$(if ($report.fatal) { ", FATAL: $($report.fatal)" })"
    }
} finally {
    Exit-SmokeLock
}

# summary.md: one table per node, screenshots linked relative to the run folder.
$md = @("# In-client smoke: $($ScenarioData.name)$(if ($Lang -ne 'en_us') { " ($Lang)" })", '', "Run folder: ``$OutDir``", '', '| Node | Steps | Failed | BuildCraft log errors | Fatal |', '|---|---|---|---|---|')
foreach ($s in $summary) { $md += "| $($s.node) | $($s.steps) | $($s.failed) | $($s.bcErrors) | $(if ($s.skipped) { "skipped: $($s.skipped)" } else { $s.fatal }) |" }
foreach ($s in $summary) {
    if ($s.skipped) { continue }
    $md += @('', "## $($s.node)", '', '| # | Step | OK | Detail | Screenshot |', '|---|---|---|---|---|')
    foreach ($st in $s.report.steps) {
        $d = ([string]$st.detail) -replace '\|', '\|' -replace "`r?`n", ' '
        if ($d.Length -gt 160) { $d = $d.Substring(0, 157) + '...' }
        $img = if ($st.shot) { "[$($st.shot)]($($s.node)/$($st.shot))" } else { '' }
        $md += "| $($st.n) | $($st.step) | $(if ($st.skipped) { 'skip' } elseif ($st.ok) { 'yes' } else { '**NO**' }) | $d | $img |"
    }
    foreach ($e in $s.report.buildcraftErrors) { $md += "- log: $((($e | ConvertTo-Json -Compress -Depth 4)) -replace "`r?`n", ' ')" }
}
$md | Set-Content -LiteralPath (Join-Path $OutDir 'summary.md') -Encoding utf8
Write-Host "summary: $(Join-Path $OutDir 'summary.md')"
if (@($summary | Where-Object { $_.failed -or $_.fatal -or $_.bcErrors }).Count) { exit 1 }
