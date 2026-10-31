# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/

<#
.SYNOPSIS
  Guards for the in-client smoke runners (issue #62): a machine-wide run lock and a pin on the McDevBridge client.
  Dot-source it (`. "$PSScriptRoot/SmokeGuard.ps1"`); it only defines functions.

.DESCRIPTION
  Why: every McDevBridge client overwrites the one ~/.mcdevbridge/bridge.json, so a second client (another session's
  runner, a hand-started game) booting beside a run can end up with the run reading, or answering for, the wrong world.

  THE LOCK (~/.mcdevbridge/smoke.lock, JSON). One run per machine, held from before the first boot until the run ends.
    Enter-SmokeLock  takes it, or throws naming the holder. The holder keeps the file open (read-shared, no delete), so a
                     live holder's file can be neither deleted nor replaced; a file whose pid is gone, or whose pid now
                     belongs to a process that started at another time (pid reuse), is stale and is taken over.
    Exit-SmokeLock   releases it (call it in `finally`). A run killed outright leaves a stale file; the next runner
                     recovers it, nobody has to delete it by hand.
  scripts/smoke/multiplayer/mp.py speaks the same file format: pid, start (the holder's start time as a FILETIME),
  owner, repo, since, detail; mp.py adds node. It cannot keep the file open, so there the pid test alone decides.

  THE PIN. A run reads bridge.json once at boot and then talks to that url and token only, so what it must prove is
  that the file it read names the client it spawned, and keep proving that nothing took the bridge over since.
    Get-SpawnLogBridgePort / Get-BridgeBootProblem   at boot: the port the spawned client logged to this run's own
                     Gradle output, the listener behind that port, the process and when it started.
    Get-BridgePinProblem   before every bridge call: the pinned process still runs and bridge.json still names it.
  McDevBridge exposes no pid over HTTP (bridge.json is the only place it is written), so the OS answers "who owns this
  port" instead (Get-NetTCPConnection).
#>

$script:SmokeLockStream = $null
$script:SmokeLockPath = $null

function Get-SmokeLockPath { Join-Path $HOME '.mcdevbridge/smoke.lock' }

# ── Processes ────────────────────────────────────────────────────────────────

function Get-ProcessStartFileTime([int]$ProcessId) {
    # When the process started, as a FILETIME (100 ns ticks since 1601 UTC; the number mp.py reads too).
    # $null: no such process (or it has exited). -1: it runs but will not say when it started (another user's).
    try { $p = [System.Diagnostics.Process]::GetProcessById($ProcessId) } catch { return $null }
    try {
        try { if ($p.HasExited) { return $null } } catch { }
        try { return $p.StartTime.ToFileTimeUtc() } catch { return -1L }
    } finally { $p.Dispose() }
}

function Test-SameProcess($StartFileTime, $Recorded) {
    # $StartFileTime is what Get-ProcessStartFileTime says now, $Recorded what was written when the process was noted
    # down. A different start time means the pid number has been handed to another process since.
    if ($null -eq $StartFileTime) { return $false }
    if ($StartFileTime -lt 0 -or -not $Recorded -or [int64]$Recorded -le 0) { return $true }
    [math]::Abs([int64]$StartFileTime - [int64]$Recorded) -lt 20000000   # 2 s
}

# ── The lock ─────────────────────────────────────────────────────────────────

function Get-JsonProperty($Object, [string]$Name, $Default = '') {
    if ($null -ne $Object -and $Object.PSObject.Properties[$Name]) { $Object.$Name } else { $Default }
}

function Read-SmokeLock([string]$Path) {
    # The lock file's content, or $null when it is absent, empty (its holder is mid-write) or not a lock.
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite, Delete')
        try { $text = [System.IO.StreamReader]::new($fs).ReadToEnd() } finally { $fs.Dispose() }
        $lock = $text | ConvertFrom-Json
        if ($lock -and $lock.PSObject.Properties['pid'] -and "$($lock.pid)" -match '^\d+$') { return $lock }
    } catch { }
    $null
}

function Test-SmokeLockHolderAlive($Lock) {
    Test-SameProcess (Get-ProcessStartFileTime ([int]$Lock.pid)) (Get-JsonProperty $Lock 'start' 0)
}

function Get-SmokeLockBusyMessage($Lock, [string]$Path) {
    $detail = Get-JsonProperty $Lock 'detail'
    $repo = Get-JsonProperty $Lock 'repo'
    $since = Get-JsonProperty $Lock 'since' '?'
    if ($since -is [datetime]) { $since = $since.ToString('s') }   # ConvertFrom-Json turns the ISO text into a DateTime
    "another smoke run holds $Path`: $(Get-JsonProperty $Lock 'owner' 'a runner') pid $($Lock.pid) since $since" +
        $(if ($detail) { ", $detail" } else { '' }) + $(if ($repo) { " (repo $repo)" } else { '' }) +
        '. Wait for it to finish; a lock whose process is gone is taken over automatically.'
}

function Enter-SmokeLock {
    # Returns @{ Path; Holder; Recovered } (Recovered: the stale lock that was taken over, else $null).
    # Throws when a live process holds the lock, or when this process already holds one.
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Owner, [string]$Repo = '', [string]$Detail = '', [string]$Path = (Get-SmokeLockPath))
    if ($script:SmokeLockStream) { throw "this process already holds the smoke lock ($script:SmokeLockPath)" }
    $dir = Split-Path -Parent $Path
    if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $holder = [ordered]@{
        pid = $PID; start = (Get-ProcessStartFileTime $PID); owner = $Owner; repo = $Repo; detail = $Detail
        since = (Get-Date).ToString('s')
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($holder | ConvertTo-Json -Compress))
    $stale = $null
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        $fs = $null
        # CreateNew is the atomic step: of two runners racing for the lock exactly one creates the file.
        try { $fs = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::Read) }
        catch [System.IO.IOException], [System.UnauthorizedAccessException] { }
        if ($fs) {
            try { $fs.Write($bytes, 0, $bytes.Length); $fs.Flush() }
            catch { $fs.Dispose(); Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue; throw }
            $script:SmokeLockStream = $fs
            $script:SmokeLockPath = $Path
            return [pscustomobject]@{ Path = $Path; Holder = [pscustomobject]$holder; Recovered = $stale }
        }
        $held = Read-SmokeLock $Path
        if ($held) {
            if (Test-SmokeLockHolderAlive $held) { throw (Get-SmokeLockBusyMessage $held $Path) }
            $stale = $held   # its process is gone: take the file over
        } elseif (-not (Test-Path -LiteralPath $Path)) {
            continue         # released between our create and our read
        } elseif ($attempt -le 4) {
            Start-Sleep -Milliseconds 150   # a holder between creating the file and writing it
            continue
        }
        # Stale (or never completed). A live holder's open handle makes this fail, and the next pass reads and refuses.
        try { Remove-Item -LiteralPath $Path -Force -ErrorAction Stop } catch { Start-Sleep -Milliseconds 150 }
    }
    throw "could not take the smoke lock at $Path (a lock file that is neither readable nor removable; delete it by hand if no smoke run is going)"
}

function Exit-SmokeLock {
    # Releases the lock this process holds; harmless when it holds none, so `finally` can always call it.
    $fs = $script:SmokeLockStream
    $path = $script:SmokeLockPath
    $script:SmokeLockStream = $null
    $script:SmokeLockPath = $null
    if (-not $fs) { return }
    $fs.Dispose()
    # While the handle was open nobody could replace the file, but never delete one that names somebody else.
    $current = Read-SmokeLock $path
    if ($current -and [int]$current.pid -ne $PID) { return }
    try { Remove-Item -LiteralPath $path -Force -ErrorAction Stop } catch { }
}

# ── The bridge pin ───────────────────────────────────────────────────────────

function Get-SpawnLogBridgePort([string]$Path) {
    # The port the client this run spawned announced in the Gradle output the run captured ("[McDevBridge] control
    # bridge listening on http://127.0.0.1:PORT"), $null while it has not. The file belongs to this run alone, so a
    # bridge.json naming any other port was written by somebody else's client.
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return $null }
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite, Delete')
        try { $text = [System.IO.StreamReader]::new($fs).ReadToEnd() } finally { $fs.Dispose() }
    } catch { return $null }
    $hits = [regex]::Matches($text, 'control bridge listening on http://127\.0\.0\.1:(\d+)')
    if ($hits.Count) { [int]$hits[$hits.Count - 1].Groups[1].Value } else { $null }
}

function Get-ListenerOwnerPid([int]$Port) {
    # Pid of the process listening on the port; $null when the OS will not say (nothing listening yet, no NetTCPIP).
    # No -State Listen filter: for a loopback listener the CIM State comes back empty and the filter finds nothing, so
    # a listener is taken to be the entry with no remote end.
    try {
        $listeners = @(Get-NetTCPConnection -LocalPort $Port -ErrorAction Stop | Where-Object { $_.RemotePort -eq 0 })
        if ($listeners.Count) { return [int]$listeners[0].OwningProcess }
    } catch { }
    $null
}

function Get-BridgeBootProblem {
    # Null when a freshly read bridge.json is the one the client this run spawned wrote, else what is wrong with it.
    # -SpawnLogPort is Get-SpawnLogBridgePort's answer: the one fact only the spawned client could have produced.
    # -Since is when the client was spawned, -JavaBefore the java pids that already ran then.
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Info, [Parameter(Mandatory)][datetime]$Since, [int[]]$JavaBefore = @(),
        [Parameter(Mandatory)][int]$SpawnLogPort, [string[]]$ProcessNames = @('java', 'javaw'))
    $bridgePid = [int]$Info.pid
    if ($SpawnLogPort -ne [int]$Info.port) {
        return "bridge.json is port $($Info.port) (pid $bridgePid), but the client this run spawned logged port ${SpawnLogPort}: the file is another client's"
    }
    if ($bridgePid -in $JavaBefore) {
        return "bridge.json names pid $bridgePid, which was already running before this run spawned its client (a stale file, or another client's)"
    }
    try { $p = [System.Diagnostics.Process]::GetProcessById($bridgePid) }
    catch { return "bridge.json names pid $bridgePid, which is not running (a stale file)" }
    try {
        try { if ($p.HasExited) { return "bridge.json names pid $bridgePid, which has exited (a stale file)" } } catch { }
        if ($p.ProcessName -notin $ProcessNames) { return "bridge.json names pid $bridgePid, which is '$($p.ProcessName)', not a Minecraft client" }
        try {
            if ($p.StartTime -lt $Since.AddSeconds(-2)) {
                return "bridge.json names pid $bridgePid, started $($p.StartTime.ToString('s')), before this run spawned its client at $($Since.ToString('s'))"
            }
        } catch { }
    } finally { $p.Dispose() }
    $owner = Get-ListenerOwnerPid ([int]$Info.port)
    if ($null -ne $owner -and $owner -ne $bridgePid) {
        return "bridge.json says pid $bridgePid owns port $($Info.port), but the OS says pid $owner listens there"
    }
    $null
}

function Get-BridgePinProblem {
    # Null while the pin holds, else why it no longer does. $Pin is the bridge.json content read at boot plus a
    # startFileTime property (the pinned process's Get-ProcessStartFileTime). A bridge.json that is missing or caught
    # mid-write is not a violation (a client exiting deletes it, a reader can meet a write in flight); one that names
    # another client is: that client now owns the bridge, and the run's results can no longer be trusted.
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Pin, [Parameter(Mandatory)][string]$BridgeFile)
    $start = Get-ProcessStartFileTime ([int]$Pin.pid)
    if (-not (Test-SameProcess $start (Get-JsonProperty $Pin 'startFileTime' 0))) {
        return "the pinned client (pid $($Pin.pid), port $($Pin.port)) is no longer running"
    }
    try { $now = Get-Content -Raw -LiteralPath $BridgeFile -ErrorAction Stop | ConvertFrom-Json } catch { return $null }
    if ($null -eq $now) { return $null }
    foreach ($field in 'pid', 'port', 'url', 'token') {
        if (-not $now.PSObject.Properties[$field] -or "$($now.$field)" -ne "$($Pin.$field)") {
            return "bridge.json no longer names the client this run pinned (its $field changed: it now says pid $(Get-JsonProperty $now 'pid' '?'), port $(Get-JsonProperty $now 'port' '?'); the run pinned pid $($Pin.pid), port $($Pin.port)). Another Minecraft client has taken over the bridge"
        }
    }
    $null
}
