# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/

<#
.SYNOPSIS
  Plays the REAL-mouse half of the zone-planner-drag scenario on a machine nobody sits at (Server1): holds, drags and
  releases the physical mouse over the game window while the scenario waits for it.

.DESCRIPTION
  zone-planner-drag sets the Zone Planner up and then waits 45 s (pan) and 90 s (paint stroke) for a real drag, which the
  bridge cannot make (README, "Real-mouse checks"). This script is the "agent with desktop control": run it in the same
  interactive Windows session as the runner, beside it, pointing at the run's output folder. It waits for the shot that
  opens each window (10-zp-start.png for the pan, 17-zp-before.png for the stroke), brings the game window to the front
  and sends real input (SetCursorPos + mouse_event, so the window system sees held buttons):

    pan     left down on the map, drag right, left up.
    stroke  left down, drag, TAP RIGHT while left is still held, keep dragging, left up. (A left button that stays held
            through the right tap is the point of the check; the log line says whether it did.)

  Positions are framebuffer pixels of the scenario's 1600x900 window at GUI scale 3, mapped through the window's client
  origin. The window can be larger than the screen (Server1's is 1024 wide): the drags stay inside the part that shows.
  What the drag DID is judged from the runner's shots (13-zp-panned, 19-zp-after) and the layer check, not here.

  Each finished gesture leaves a marker file in -OutDir (real-mouse-zp-pan.done, real-mouse-zp-stroke.done,
  real-mouse-lib-scrollbar.done: the scenario's name and the UTC time). The scenarios' `expectFile` steps wait for it, so a
  run nobody drove fails instead of passing on its screenshots alone. A gesture that threw writes no marker.

.EXAMPLE
  pwsh scripts/smoke/Drive-RealMouse.ps1 -OutDir build/smoke/zpd/zone-planner-drag/26.3-fabric
#>
[CmdletBinding()]
param(
    # The runner's per-node output folder (<OutDir>/<node>) of the run being driven.
    [Parameter(Mandatory)][string]$OutDir,
    # Which real-mouse scenario is running: the Zone Planner's pan and stroke, or the Electronic Library's scrollbar drag.
    [ValidateSet('zone-planner-drag', 'library-scrollbar-drag')][string]$Scenario = 'zone-planner-drag',
    [int]$WaitMinutes = 120,
    # Leave out the right-button tap: a plain left drag, to tell what the stroke does without it.
    [switch]$NoRightTap,
    # Where the stroke starts and ends (framebuffer pixels of the 1600x900 window; the Zone Planner map is x 440-1082, y 133-435).
    # "x,y" each: `pwsh -File` cannot deliver an array, so they are strings.
    [string]$StrokeFrom = '560,200',
    [string]$StrokeTo = '900,380'
)

$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class BcRealMouse {
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern short GetAsyncKeyState(int vk);
}
'@
[BcRealMouse]::SetProcessDPIAware() | Out-Null

$notBefore = (Get-Date).AddSeconds(-60)
$LEFT_DOWN = 0x0002; $LEFT_UP = 0x0004; $RIGHT_DOWN = 0x0008; $RIGHT_UP = 0x0010
function Wait-ForFile([string]$Name) {
    $path = Join-Path $OutDir $Name
    $deadline = (Get-Date).AddMinutes($WaitMinutes)
    while ((Get-Date) -lt $deadline) {
        # A shot from an earlier run into the same folder is not the cue: only one written around or after this start.
        if ((Test-Path -LiteralPath $path) -and (Get-Item -LiteralPath $path).Length -gt 0 -and (Get-Item -LiteralPath $path).LastWriteTime -ge $notBefore) { Start-Sleep -Milliseconds 400; return }
        Start-Sleep -Milliseconds 500
    }
    throw "timed out waiting for $path"
}

function Write-DoneMarker([string]$Gesture) {
    # The runner's `expectFile` step waits for this file: the proof, for a check with no server-side state (a scrollbar),
    # that this script really sent its input. Written only after the gesture finished (never from a `finally`).
    $path = Join-Path $OutDir "real-mouse-$Gesture.done"
    "$Scenario $Gesture $([datetime]::UtcNow.ToString('o'))" | Set-Content -LiteralPath $path -Encoding utf8
    Write-Host "marker: $path"
}

function Get-GameWindow {
    $info = Get-Content -Raw (Join-Path $HOME '.mcdevbridge/bridge.json') | ConvertFrom-Json
    $h = (Get-Process -Id ([int]$info.pid)).MainWindowHandle
    if ($h -eq [IntPtr]::Zero) { throw 'the game window has no handle' }
    $h
}

function Set-GameFront($h) {
    # Windows only hands focus to the foreground owner's friends: an Alt tap lifts the lock, then SetForegroundWindow.
    foreach ($try in 1..3) {
        [BcRealMouse]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [BcRealMouse]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
        if ($try -eq 3) { [BcRealMouse]::ShowWindow($h, 6) | Out-Null; Start-Sleep -Milliseconds 200; [BcRealMouse]::ShowWindow($h, 9) | Out-Null }
        [BcRealMouse]::SetForegroundWindow($h) | Out-Null
        Start-Sleep -Milliseconds 400
        if ([BcRealMouse]::GetForegroundWindow() -eq $h) { return "front after try $try" }
    }
    # Real mouse input lands on whatever window is under the cursor: without the game in front, do nothing.
    throw 'could not bring the game window to the front; no mouse input was sent'
}

function Move-To($h, [int]$fx, [int]$fy) {
    # fx, fy: framebuffer pixels of the 1600x900 client area.
    $p = New-Object BcRealMouse+POINT
    [BcRealMouse]::ClientToScreen($h, [ref]$p) | Out-Null
    [BcRealMouse]::SetCursorPos($p.X + $fx, $p.Y + $fy) | Out-Null
}

function Drag-To($h, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [int]$steps = 24) {
    foreach ($i in 1..$steps) {
        Move-To $h ([int]($x0 + ($x1 - $x0) * $i / $steps)) ([int]($y0 + ($y1 - $y0) * $i / $steps))
        Start-Sleep -Milliseconds 40
    }
}

$rect = New-Object BcRealMouse+RECT

if ($Scenario -eq 'library-scrollbar-drag') {
    # The Library's list (framebuffer x 456-923, y 183-512 at the 1600x900 window, GUI scale 3) has its scrollbar in the
    # strip at the right edge; its thumb starts at the top. Hold left on the thumb, drag to the bottom, release.
    Write-Host "waiting for 10-lib-before.png in $OutDir"
    Wait-ForFile '10-lib-before.png'
    $h = Get-GameWindow
    $origin = New-Object BcRealMouse+POINT
    [BcRealMouse]::ClientToScreen($h, [ref]$origin) | Out-Null
    Write-Host "game window $h at screen ($($origin.X), $($origin.Y)); $(Set-GameFront $h)"
    try {
        Move-To $h 932 200; Start-Sleep -Milliseconds 400
        [BcRealMouse]::mouse_event($LEFT_DOWN, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 300
        Drag-To $h 932 200 932 500 30
        Start-Sleep -Milliseconds 300
        [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero)
        Write-Host 'scrollbar: dragged the thumb (932,200) -> (932,500), left released'
    } finally {
        if ([BcRealMouse]::GetAsyncKeyState(0x01) -band 0x8000) { [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero) }
    }
    Write-DoneMarker 'lib-scrollbar'
    return
}

Write-Host "waiting for 10-zp-start.png in $OutDir"
Wait-ForFile '10-zp-start.png'
$h = Get-GameWindow
[BcRealMouse]::GetClientRect($h, [ref]$rect) | Out-Null
$origin = New-Object BcRealMouse+POINT
[BcRealMouse]::ClientToScreen($h, [ref]$origin) | Out-Null
Write-Host "game window $h, client $($rect.Right)x$($rect.Bottom) at screen ($($origin.X), $($origin.Y)); $(Set-GameFront $h)"
try {
    # PAN: empty hand, hold left on the map (framebuffer x 440-1082, y 133-435), drag about a third of its width.
    Move-To $h 500 300; Start-Sleep -Milliseconds 400
    [BcRealMouse]::mouse_event($LEFT_DOWN, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 300
    Drag-To $h 500 300 760 300
    Start-Sleep -Milliseconds 300
    [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero)
    Write-Host 'pan: dragged (500,300) -> (760,300), left released'
} finally {
    if ([BcRealMouse]::GetAsyncKeyState(0x01) -band 0x8000) { [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero) }
}
Write-DoneMarker 'zp-pan'

Write-Host 'waiting for 17-zp-before.png'
Wait-ForFile '17-zp-before.png'
$h = Get-GameWindow
Write-Host "stroke: $(Set-GameFront $h)"
try {
    # STROKE: left down, drag, tap right with left still held, keep dragging, release left.
    $from = @($StrokeFrom -split ',' | ForEach-Object { [int]$_ }); $to = @($StrokeTo -split ',' | ForEach-Object { [int]$_ })
    $x0 = $from[0]; $y0 = $from[1]; $x1 = $to[0]; $y1 = $to[1]
    $mx = [int](($x0 + $x1) / 2); $my = [int](($y0 + $y1) / 2)
    Move-To $h $x0 $y0; Start-Sleep -Milliseconds 400
    [BcRealMouse]::mouse_event($LEFT_DOWN, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 300
    Drag-To $h $x0 $y0 $mx $my
    if (-not $NoRightTap) {
        $before = [bool]([BcRealMouse]::GetAsyncKeyState(0x01) -band 0x8000)
        [BcRealMouse]::mouse_event($RIGHT_DOWN, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 60
        [BcRealMouse]::mouse_event($RIGHT_UP, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 150
        $after = [bool]([BcRealMouse]::GetAsyncKeyState(0x01) -band 0x8000)
        Write-Host "stroke: right tapped mid-stroke; left held before=$before after=$after"
    } else {
        Write-Host 'stroke: no right tap (plain left drag)'
    }
    Drag-To $h $mx $my $x1 $y1
    Start-Sleep -Milliseconds 300
    [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero)
    Write-Host "stroke: dragged ($x0,$y0) -> ($x1,$y1), left released"
} finally {
    if ([BcRealMouse]::GetAsyncKeyState(0x01) -band 0x8000) { [BcRealMouse]::mouse_event($LEFT_UP, 0, 0, 0, [UIntPtr]::Zero) }
    if ([BcRealMouse]::GetAsyncKeyState(0x02) -band 0x8000) { [BcRealMouse]::mouse_event($RIGHT_UP, 0, 0, 0, [UIntPtr]::Zero) }
}
Write-DoneMarker 'zp-stroke'
Write-Host 'done'
