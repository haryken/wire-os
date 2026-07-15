# Xiaozhi stability mitigate pack (2026-07-15)

Applied on **Vector-G9Y6 @ 192.168.100.48**. Goal: reduce LMK *and* ALSA/anim crashes after long music, without trying to patch closed Wwise.

## What this pack does

| # | Change | Where |
|---|---|---|
| 1 | **Balanced PCM window** (~6s ahead, live hardcap 1MB, punch-fail 3MB) | `vic-cloudless/.../playback.go` |
| 2 | **Wwise portal** `STREAM_MAX_PORTAL_FRAMES=40000` (~2.5s; was trial 28k — starved after underrun) | `victor/.../sdkAudioComponent.cpp` |
| 3 | **Soft-cap TTS/music ~250s** (wall + PCM bytes) then abort stream cleanly | `vic-cloudless/.../turn.go` |
| 4 | ~~Auto-release / refuse Control during TTS~~ **reverted** (user request) | — |

Not included: patching Anki Wwise internals.

## Expected UX

- Dialogue + music **up to ~250s** should play fully (soft abort after that; say “continue” for more).
- Less likely wake-block from Control/camera tab during TTS.
- Middle buffer sizes vs prior extremes (8s/40k vs trial 4s/20k).
- PCM punch playhead lag **2.0s** (was 0.6s) so punch-hole does not zero unread TTS.
- After binary overwrite: **force-restart** `vic-anim` (`killall -9` if `systemctl stop` leaves old PID) — otherwise silent TTS with old code still in RAM.

## Robot backups (`.48`)

Created (or kept) before overwrite:

- `/anki/bin/vic-cloud.bak-pre-mitigate`
- `/anki/bin/vic-anim.bak-pre-mitigate`
- `/usr/bin/wired.bak-pre-mitigate`

(Older RAM-trial backups may still exist as `*.bak-pre-ram-trial`.)

## Revert on robot

```bash
IP=192.168.100.48
KEY=/path/to/ssh_root_key

ssh -i "$KEY" -o PubkeyAcceptedAlgorithms=+ssh-rsa -o HostKeyAlgorithms=+ssh-rsa root@$IP '
  mount -o remount,rw /
  systemctl stop anki-robot.target wired
  cp -a /anki/bin/vic-cloud.bak-pre-mitigate /anki/bin/vic-cloud
  cp -a /anki/bin/vic-anim.bak-pre-mitigate /anki/bin/vic-anim
  cp -a /usr/bin/wired.bak-pre-mitigate /usr/bin/wired
  chmod 755 /anki/bin/vic-cloud /usr/bin/wired
  chmod 550 /anki/bin/vic-anim
  chown cloud:anki /anki/bin/vic-cloud 2>/dev/null || true
  chown engine:anki /anki/bin/vic-anim
  systemctl start wired anki-robot.target
  sleep 5
  systemctl is-active wired vic-cloud vic-anim vic-engine
'
```

## Revert source (git)

```bash
cd anki/vic-cloudless && git checkout -- \
  internal/xiaozhi/playback.go \
  internal/xiaozhi/turn.go \
  internal/xiaozhi/session.go

cd ../victor && git checkout -- \
  animProcess/src/cozmoAnim/audio/sdkAudioComponent.cpp

cd ../wired && git checkout -- mods/control.go
```

## Quick checks after deploy

```bash
journalctl -u vic-cloud -f
# expect: soft-cap log if music >~250s
# expect: no long "assuming" during TTS; wake works after music

dmesg | grep -i lowmemorykiller | tail
systemctl is-active vic-cloud vic-anim vic-engine vic-robot
free -m
```
