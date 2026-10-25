<#
.SYNOPSIS
    Presses, releases or taps a mouse button through SendInput, for the real-mouse smoke scenarios.

.DESCRIPTION
    A desktop-control agent drives zone-planner-drag's strokes with its own left_mouse_down / mouse_move /
    left_mouse_up, but its right click lets go of a held left button, so a "tap right mid-stroke" made with it ends the
    stroke at the tap point and looks exactly like the bug the scenario hunts. Hold left with the agent's tool and send
    the right tap (or a right-drag's press and release) through this script instead: SendInput leaves the left button
    alone, and the script reports whether left was down before and after so the run's evidence carries it.
    The input goes to the foreground window, so bring the game to the front first.

.EXAMPLE
    pwsh -File scripts/smoke/Send-MouseButton.ps1 -Action tap            # right tap, mid-stroke
    pwsh -File scripts/smoke/Send-MouseButton.ps1 -Action down           # start a right-drag erase
    pwsh -File scripts/smoke/Send-MouseButton.ps1 -Action up             # finish it
#>
param(
    [Parameter(Mandatory)][ValidateSet('down', 'up', 'tap')][string]$Action,
    [ValidateSet('right', 'left', 'middle')][string]$Button = 'right'
)

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class BcSmokeMouse {
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public MOUSEINPUT mi; }
    [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern short GetAsyncKeyState(int vk);
    public static bool LeftDown() { return (GetAsyncKeyState(0x01) & 0x8000) != 0; }
    public static uint Send(uint[] flags) {
        var inputs = new INPUT[flags.Length];
        for (int i = 0; i < flags.Length; i++) { inputs[i].type = 0; inputs[i].mi.dwFlags = flags[i]; }
        return SendInput((uint) inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
    }
}
'@

# MOUSEEVENTF_*DOWN / *UP
$down = @{ left = 0x0002; right = 0x0008; middle = 0x0020 }[$Button]
$up = $down * 2
$flags = switch ($Action) { 'down' { , [uint32[]]@($down) } 'up' { , [uint32[]]@($up) } 'tap' { , [uint32[]]@($down, $up) } }

$before = [BcSmokeMouse]::LeftDown()
$sent = [BcSmokeMouse]::Send($flags)
Start-Sleep -Milliseconds 150
$after = [BcSmokeMouse]::LeftDown()
"$Button $Action`: sent $sent of $($flags.Count) event(s); left held before=$before after=$after"
if ($sent -ne $flags.Count) { exit 1 }
