# Xiaozhi WSS idle force-close (2026-07-15)

Trial on **Vector-G9Y6**. Goal: match ESP32 — after `session_idle_sec` (~60s) with no new listen, **always** close the Xiaozhi WebSocket, even if busy/playing/turn flags were left dirty by `analyze_photo`.

## Behavior

| Before | After |
|--------|--------|
| Idle timer saw busy/`playing`/`turnCancel` → **reschedule forever** (WSS never closed) | Idle → clear stuck flags + playback files → **`closeSessionLocked("idle")`** |

Log when force-clearing:

```text
[Xiaozhi] idle force-close — clearing stuck state (turn=… playing=… busy=…)
[Xiaozhi] closed WSS session: … reason: idle
```

Config (unchanged defaults in `internal/xiaozhi/config.go`):

- `session_idle_sec`: **60**
- `idle_timeout_sec`: **20** (used if session_idle unset; floor 30s in code)

## Files touched

- `anki/vic-cloudless/internal/xiaozhi/session.go` — `resetIdleTimerLocked` / new `onSessionIdleTimeout`

## Quick check

```bash
journalctl -u vic-cloud -f
# After ~60s quiet (no Hey Vector / no relisten):
# expect: closed WSS session: … reason: idle
# optional: idle force-close — clearing stuck state …
```

## Revert on robot (.48)

```bash
IP=192.168.100.48
KEY=/path/to/anki/vic-cloudless/ssh_root_key

# If you kept a pre-trial binary:
ssh -i "$KEY" -o PubkeyAcceptedAlgorithms=+ssh-rsa -o StrictHostKeyChecking=no root@$IP '
  mount -o remount,rw /
  systemctl stop vic-cloud
  # restore prior binary you saved, e.g.:
  # cp -a /anki/bin/vic-cloud.bak-pre-idle-force /anki/bin/vic-cloud
  chmod 755 /anki/bin/vic-cloud
  systemctl start vic-cloud
'
```

## Revert in source

Restore the previous idle callback in `session.go` that only closed when **not** stuck:

```go
sess.idleTimer = time.AfterFunc(idle, func() {
	sess.mu.Lock()
	defer sess.mu.Unlock()
	if sess.turnCancel != nil || sess.playing {
		resetIdleTimerLocked()
		return
	}
	if _, err := os.Stat(BusyPath); err == nil {
		resetIdleTimerLocked()
		return
	}
	closeSessionLocked("idle")
})
```

Remove `onSessionIdleTimeout`, rebuild/deploy `make vic-cloud`.

## Note

While continuous **relisten** is running, `BindTurn` **stops** the idle timer (expected). Idle only counts **between** listens. Force-close helps when a listen already ended but flags never cleared.
