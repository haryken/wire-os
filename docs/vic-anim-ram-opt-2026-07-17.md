# vic-anim RAM opt (2026-07-17) — REVERTED 2026-07-20

**Status: reverted on robot + source.** A1/A2/A3/B1 rolled back.

Robot restored from:

```text
/anki/bin/vic-anim.bak-pre-ram-opt
```

Still kept (WireOS / Xiaozhi):

- `xiaozhi-relisten` → full `FakeTriggerWordDetection()` (có ting + mây)
- ignore while `xiaozhi-busy`
- `ShouldSimulateStreaming() == false`
- DEV_CHEATS mic WAV capture forced off

---

## What was reverted

| ID | Change |
|----|--------|
| **A1** | Trim mic chunks after take |
| **A2** | Picovoice consume-on-error + frame reuse |
| **A3** | `malloc_trim` on clear streaming |
| **B1** | Quiet Xiaozhi relisten (no engine clouds) |

## Re-apply note

Do not redeploy a RAM-opt `vic-anim` unless re-validating MemProbe / UX separately.
