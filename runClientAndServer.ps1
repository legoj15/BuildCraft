# Starts both the Client and Server in a new side-by-side Windows Terminal window.
# This prevents their logs from mixing together while giving you a visually clean single space.
#
# Targets the :26.1.2 Stonecutter node (the active/primary MC version). An unqualified
# `gradlew runClient` would run across EVERY node — change the :26.1.2: prefix to pick another.

Write-Output "Launching Client and Server (node :26.1.2) in a new split-pane Windows Terminal window..."

# Launching using wt.exe, specifying --title to name the tabs, and passing the commands unquoted to prevent wt from misinterpreting them.
wt --title Server -d . pwsh.exe -NoExit -Command .\gradlew :26.1.2:runServer `; split-pane --title Client -d . pwsh.exe -NoExit -Command .\gradlew :26.1.2:runClient

