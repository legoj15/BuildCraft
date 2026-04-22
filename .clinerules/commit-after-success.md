---
trigger: always_on
---

Upon a confirmed success, commit the code changes to Git, and update the local changelog.md. Cleanup any temporary files or add them to the gitignore if they may be needed again. If the terminal hangs, assume that the commit was successful, but verify that it went through. Squash related commits where applicable.