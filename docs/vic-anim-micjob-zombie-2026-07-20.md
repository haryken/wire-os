# vic-anim MicDataInfo zombie job RSS leak (2026-07-20)

**Status: fixed** on source + deploy to Vector `.45`.

Related: A1/A2 (`docs/vic-anim-a1-a2-2026-07-20.md`), MemProbe + MicJobProbe.

---

## Symptom

After Xiaozhi chat / listen / music:

- `vic-anim` RSS climbs steadily (~**+0.3–1 MB / 10s**), even when MemProbe `phase=idle`
- Does **not** climb right after boot idle (plateau ~63 MB)
- Does **not** drop again without process restart

---

## How we found it (logging)

### 1. MemProbe (`vic-cloud`)

Temporary 10s sampler: `anki/vic-cloudless/internal/memprobe/memprobe.go`  
Logs RSS for `vic-anim` / cloud / engine + phase flags (`tts` / `idle` / …).

```bash
journalctl -f | grep MemProbe
```

Showed: early idle stable; after Xiaozhi, idle still climbed.

### 2. MicJobProbe (`vic-anim` → file → MemProbe)

Anim cannot always be seen in `journalctl`, so every ~10s it writes:

```text
/run/vic-anim-micjob.txt
```

Example:

```text
nJobs=1 stream=0 done=1 sent=608 procKB=4430 rawKB=0
```

MemProbe appends that as `micjob={...}` on each line.

| Field | Meaning |
|-------|---------|
| `nJobs` | Entries in `_micProcessingJobs` |
| `stream` | `_currentlyStreaming` (actively uplinking) |
| `done` | `_streamingComplete` (may be stale after clear) |
| `sent` | Chunks sent this stream |
| `procKB` | Approx size of **processed** PCM still held in jobs |
| `rawKB` | Approx size of **raw** 4ch held in jobs |

### 3. Smoking-gun timeline

| Phase | anim RSS | micjob |
|-------|----------|--------|
| Boot idle | ~63 MB flat | `nJobs=0 procKB=0` |
| During listen | stable-ish | `nJobs=1 stream=1 procKB=0` (A1 frees while streaming) |
| **After stream, “idle”** | **climbs** | **`nJobs=1 stream=0 procKB` +~300 KB / 10s** |

`procKB` rate ≈ mono 16 kHz PCM → leftover job still calling `CollectProcessedAudio` with nobody consuming.

---

## Root cause

Xiaozhi stream jobs:

```cpp
EnableDataCollect(Processed, /*saveToFile=*/false);
SetTimeToRecord(kMaxRecordTime_ms);
```

On stream end, `ClearCurrentStreamingJob()` used to only `SetTimeToRecord(0)`.

Then `MicDataInfo::UpdateForNextChunk()` always tried `ChooseNextFileNameBase()` (even when **not** saving). If the name was empty it did:

```cpp
_typesToSave.ClearFlags();
return;   // BUG: did NOT clear _typesToCollect or buffers
```

Job stayed in the deque, kept collecting forever → RSS leak.

---

## Fix

Files:

- `anki/victor/animProcess/src/cozmoAnim/micData/micDataInfo.cpp` / `.h`
- `anki/victor/animProcess/src/cozmoAnim/micData/micDataSystem.cpp`

1. **`UpdateForNextChunk`**: if nothing to save (`typesToSave` empty and no FFT), drop buffers + clear collect immediately. If `ChooseNextFileNameBase` fails, still drop buffers + clear collect (log warning).
2. **`ForceEndCollection()`**: clear flags + free processed/raw buffers + zero timers.
3. **`ClearCurrentStreamingJob`**: `ForceEndCollection()` on **all** jobs in `_micProcessingJobs`, then clear the deque (not only `_currentStreamingJob`). Reset `_streamingComplete`.
4. **`AddMicDataJob(streaming)`**: force-end any leftover jobs before starting a new listen (overlapping FakeTrigger).
5. **Orphan safety in `Update`**: if not streaming and `_currentStreamingJob == nullptr` but jobs remain → force-end them.

UX impact: none for Xiaozhi — leftover jobs were unused after `stream=0`; clearing only stops the leak.

### Follow-up (same day)

First fix only ended `_currentStreamingJob`. Logs still showed `nJobs=2..3 stream=0 procKB` climbing in idle → extended to clear **all** jobs + orphan sweep.

---

## Verify after deploy

1. Restart robot / `anki-robot.target`.
2. Confirm boot idle: `micjob={nJobs=0 … procKB=0}`, anim flat.
3. Do a few Xiaozhi turns (listen + TTS).
4. After mic closes, watch MemProbe:

```bash
journalctl -f | grep MemProbe
# or
cat /run/vic-anim-micjob.txt
```

**Expect:** after stream ends, `nJobs=0` (or `procKB` not climbing). Anim RSS should plateau, not +~300 KB/10s forever.

---

## Revert

Restore anim binary backup if needed:

```bash
cp /anki/bin/vic-anim.bak-pre-a1a2 /anki/bin/vic-anim   # or bak-pre-ram-opt
# then restart anki-robot.target
```

Source: revert the three changes above in `micDataInfo.*` / `ClearCurrentStreamingJob`.

Remove TEMP probes later: MicJobProbe block + `/run/vic-anim-micjob.txt`, MemProbe `micjob=` field, `memprobe` package when profiling is done.
