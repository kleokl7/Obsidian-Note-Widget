# Test vault

Fixture for `scripts/device-check.sh`, which pushes it to
`/sdcard/Documents/TestVault` and adds today's daily note under `Daily/`.
`Today.md` covers every block type, task state and fold case the widget
renders; keep its first task ("Tap my checkbox…") because the script taps it
and checks that the file changed.
