# Dual wake engine (Picovoice + Sensory THF) — 2026-07-29

**Feature:** choose wake-word backend from wired `:8080` Bot Settings → Wake.

| Engine | Behavior |
|--------|----------|
| **picovoice** (default WireOS) | Custom keyword + `sensitivity2`; current path |
| **thf** | Stock Anki Sensory TrulyHandsFree; locale en-US/AU/GB/FR/DE via bot locale |

Config file on robot:

```text
/data/data/com.anki.victor/persistent/wake_engine
```

Contents: `picovoice` or `thf` (one line, no quotes). Missing file → **picovoice**.

Changing engine requires **restart `vic-anim`** (wired API does this via Save).

---

## Pre-change safety commits / SHAs

Taken **before** this feature was applied:

| Repo | SHA / note |
|------|------------|
| `wire-os` (parent) | `55e76a5ba` (includes voice-minigame guide) |
| `anki/victor` | `b6764fca` (micjob zombie + A1/A2) |
| `anki/wired` | `08d5c17` |
| `anki/vic-cloudless` | `54004bd` |

Stock THF source reference: `/home/linh/Projects/victor` branch **`master`**.

---

## What this change touches

### `anki/victor`

- Add `speechRecognizerTHFSimple.*`, `speechRecognizerTHFTypesSimple.*`
- Add `3rd/sensory` (+ `cmake/sensory.cmake` already present)
- Symlink `resources/assets/sensorySpeechRecModels` → `../../3rd/sensory/sensorySpeechRecModels`
- `animProcess/CMakeLists.txt` — `include(sensory)`, link `${SENSORY_LIBS}`
- `speechRecognizerSystem.h` / `.cpp` — `_victorTrigger` (PV) **or** `_victorTriggerThf`, read `wake_engine`
- Clang fix: name `std::lock_guard` locals (Wunused-value / nodiscard under `-Werror`)
- After pull: regenerate source lists if needed:
  `python3 tools/build/tools/metabuild/metabuild.py -o generated/cmake animProcess/BUILD.in`

### `anki/wired`

- Mod API: `/api/mods/WakeEngine/get`, `/api/mods/WakeEngine/set?engine=…`
- `webroot/index.html` + `js/wake-engine.js` — radio Picovoice / Hey Vector (THF)
- Picovoice keyword panel only when engine = picovoice; sensitivity section is Picovoice-only

### Docs

- This file

---

## Revert (full)

### Quick: restore Picovoice on robot only

```bash
# on robot
echo picovoice > /data/data/com.anki.victor/persistent/wake_engine
systemctl restart anki-robot.target
```

### Source revert

```bash
cd /home/linh/Projects/wire-os/anki/victor
git checkout b6764fca -- \
  animProcess/CMakeLists.txt \
  animProcess/src/cozmoAnim/speechRecognizer/speechRecognizerSystem.cpp \
  animProcess/src/cozmoAnim/speechRecognizer/speechRecognizerSystem.h
rm -f animProcess/src/cozmoAnim/speechRecognizer/speechRecognizerTHFSimple.* \
      animProcess/src/cozmoAnim/speechRecognizer/speechRecognizerTHFTypesSimple.*
rm -rf 3rd/sensory resources/assets/sensorySpeechRecModels
# re-run metabuild then rebuild vic-anim

cd /home/linh/Projects/wire-os/anki/wired
git checkout 08d5c17 -- main.go webroot/index.html
rm -f mods/wake-engine.go webroot/js/wake-engine.js

cd /home/linh/Projects/wire-os
rm -f docs/dual-wake-engine-2026-07-29.md
```

Or reset submodule tips to SHAs above, then rebuild/deploy `vic-anim` + `wired`.

---

## Verify

1. `:8080` → Cài đặt bot → Wake → **Picovoice** → Save → custom keyword / sensitivity vẫn chạy.
2. **Hey Vector (THF)** → Save → log anim: `Wake engine: Sensory THF`; say “Hey Vector”.
3. Đổi locale bot US ↔ AU → THF reload model; Picovoice không đổi theo AU/US model set.
4. MemProbe: anim ổn định (không regress micjob zombie).

---

## Notes

- THF needs Sensory models on device under trigger data dir (`sensorySpeechRecModels/...` as in `micTriggerConfig.json`). If missing on robot, THF init fails — stay on Picovoice or deploy models from `3rd/sensory/sensorySpeechRecModels`.
- Do not run both engines at once (RAM).
- Alexa remains PryonLite either way.
- Host build note (WSL): if `/usr/bin/ccache` missing, point `_build/.../launch-c` and `launch-cxx` at a passthrough `~/.local/bin/ccache`.
