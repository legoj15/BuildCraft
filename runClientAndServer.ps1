# Starts both the Client and Server in a new side-by-side Windows Terminal window.
# This prevents their logs from mixing together while giving you a visually clean single space.

Write-Output "Launching Client and Server in a new split-pane Windows Terminal window..."

# Launching using wt.exe, specifying --title to name the tabs, and passing the commands unquoted to prevent wt from misinterpreting them.
wt --title Server -d . pwsh.exe -NoExit -Command .\gradlew runServer `; split-pane --title Client -d . pwsh.exe -NoExit -Command .\gradlew runClient

