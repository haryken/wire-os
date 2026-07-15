# Xiaozhi PCM/portal RAM trial (2026-07-15)

Trial on **Vector @ 192.168.100.48** — narrower PCM window + Wwise portal to reduce LMK pressure on ~426 MB RAM.

## What changed

| Knob | File | Before | Trial |
|---|---|---|---|
| `pcmWindowAheadBytes` | `anki/vic-cloudless/internal/xiaozhi/playback.go` | 256 KB (~8 s) | **128 KB (~4 s)** |
| `pcmPunchKeepBytes` | same | 64 KB | **32 KB** |
| punch batch min | same | 64 KB | **32 KB** |
| `pcmHardCapBytes` (live window) | same | 1536 KB | **768 KB** |
| `pcmHardCapPunchFailBytes` | same | 8 MB | **2 MB** |
| `STREAM_MAX_PORTAL_FRAMES` | `anki/victor/animProcess/.../sdkAudioComponent.cpp` | 40000 (~2.5 s) | **20000 (~1.25 s)** |

## Expected behavior

- Long TTS/music still streams (sliding window); **not** the old hard cut at ~48 s of logical file size.
- Lower peak `/run` + `vic-anim` portal residency → less likely `lowmemorykiller` kills `vic-anim`.
- Trade-off: slightly higher chance of short underruns/glitches if Wi‑Fi or CPU stalls.

## Binaries deployed (`.48`)

On robot, backups taken before overwrite (if present):

- `/anki/bin/vic-cloud.bak-pre-ram-trial`
- `/anki/bin/vic-anim.bak-pre-ram-trial`

Live:

- `/anki/bin/vic-cloud`
- `/anki/bin/vic-anim`

## Revert on robot (SSH)

```bash
IP=192.168.100.48
KEY=/path/to/ssh_root_key

ssh -i "$KEY" -o PubkeyAcceptedAlgorithms=+ssh-rsa -o HostKeyAlgorithms=+ssh-rsa root@$IP '
  mount -o remount,rw /
  systemctl stop anki-robot.target
  cp -a /anki/bin/vic-cloud.bak-pre-ram-trial /anki/bin/vic-cloud
  cp -a /anki/bin/vic-anim.bak-pre-ram-trial /anki/bin/vic-anim
  chmod 755 /anki/bin/vic-cloud
  chmod 550 /anki/bin/vic-anim
  chown cloud:anki /anki/bin/vic-cloud 2>/dev/null || true
  chown engine:anki /anki/bin/vic-anim
  systemctl start anki-robot.target
  sleep 4
  systemctl is-active vic-cloud vic-anim vic-engine
'
```

## Revert source (git)

```bash
cd anki/vic-cloudless
git checkout -- internal/xiaozhi/playback.go

cd ../victor
git checkout -- animProcess/src/cozmoAnim/audio/sdkAudioComponent.cpp
```

Then rebuild/deploy, or use the `.bak-pre-ram-trial` binaries above.

## Quick test

1. Hey Vector → short reply (should speak fully).
2. Ask for longer speech / music (~1 min).
3. Watch for underruns; if robot freezes check:

```bash
dmesg | grep -i lowmemorykiller | tail
systemctl is-active vic-cloud vic-anim vic-engine
free -m
```
