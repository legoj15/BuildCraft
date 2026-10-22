# Starts both the Client and Server in a new side-by-side Windows Terminal window.
# This prevents their logs from mixing together while giving you a visually clean single space.
#
# -Node picks the Stonecutter node (default: the primary, 26.2). An unqualified `gradlew runClient` would run
# across EVERY node, so the task is always qualified.
param([string]$Node = '26.2')

Write-Output "Launching Client and Server (node :$Node) in a new split-pane Windows Terminal window..."

# Launching using wt.exe, specifying --title to name the tabs, and passing the commands unquoted to prevent wt from misinterpreting them.
wt --title Server -d . pwsh.exe -NoExit -Command .\gradlew :${Node}:runServer `; split-pane --title Client -d . pwsh.exe -NoExit -Command .\gradlew :${Node}:runClient

