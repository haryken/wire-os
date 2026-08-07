# Fix `self.audio_speaker.set_volume` (% → Vector levels) — plan

**Ngày:** 2026-07-31  
**Trạng thái:** ✅ Implemented — `%` → `mute` (0) + `VOLUME_1`…`5`. Engine bật lại MUTE cho voice/Xiaozhi. Deploy `.56`.

| % | Level |
|---|--------|
| **0** | **mute** (MUTE=0) |
| 1–20 | VOLUME_1 |
| 21–40 | VOLUME_2 |
| 41–60 | VOLUME_3 |
| 61–80 | VOLUME_4 |
| 81–100 | VOLUME_5 |  
**Mục tiêu:** Kêu tăng/giảm âm lượng → **master_volume robot đổi thật** (TTS Xiaozhi cũng to/nhỏ theo loa hệ thống).

---

## Vấn đề (đã xác nhận bằng log)

| Ai | Làm gì |
|----|--------|
| Xiaozhi MCP | `self.audio_speaker.set_volume` volume=`0`…`100` |
| vic-cloudless | Gửi `intent_imperative_volumelevel_extend` + `volume_level: "100"` (số %) |
| Engine `BehaviorVolume` | Chỉ hiểu **chuỗi bậc**: `VOLUME_1`…`VOLUME_5`, `min`/`low`/`medium`/`high`/`max`, … |
| Kết quả | `ComputeDesiredVolumeFromLevelIntent.invalid: volume_level: 100` → **không đổi volume**; LLM vẫn nói “đã tăng” |

**Đây là volume hệ thống robot** (`RobotSetting::master_volume`), không phải gain riêng file Opus Xiaozhi. Sửa mapping là đủ để TTS nghe khác.

---

## Engine nhận gì? (5 bậc)

Từ `behaviorVolume.cpp` `kVolumeLevelMap`:

| Bậc (enum) | Chuỗi hợp lệ (ví dụ) | Ý nghĩa |
|------------|----------------------|---------|
| MIN (1) | `VOLUME_1`, `min`, `minimum` | Nhỏ nhất (không mute) |
| MEDLOW (2) | `VOLUME_2`, `low` | Thấp |
| MED (3) | `VOLUME_3`, `medium` | Vừa |
| MEDHIGH (4) | `VOLUME_4`, `high` | Cao |
| MAX (5) | `VOLUME_5`, `max`, `maximum` | Lớn nhất |

Mute không dùng trong map (comment stock).

Đã có sẵn (không cần sửa): `self.vector.action` → `volume_up` / `volume_down` → increment intents — khác tool `set_volume`.

---

## Hai hướng sửa

### A — Map % → `VOLUME_N` trong vic-cloudless (**khuyến nghị**)

Chỉ sửa MCP handler; không đụng engine C++.

**File:** `anki/vic-cloudless/internal/xiaozhi/mcp.go`  
(`case "self.audio_speaker.set_volume"`)

1. Đọc `volume` 0–100 (giữ API Xiaozhi/ESP32-style).
2. Map sang chuỗi engine, ví dụ:

| % (input) | `volume_level` gửi engine |
|-----------|---------------------------|
| 0–20 | `VOLUME_1` |
| 21–40 | `VOLUME_2` |
| 41–60 | `VOLUME_3` |
| 61–80 | `VOLUME_4` |
| 81–100 | `VOLUME_5` |

(Hoặc chia đều: `level = clamp((vol+19)/20, 1, 5)` → `VOLUME_{level}`.)

3. `params["volume_level"] = "VOLUME_3"` (string), **không** `"50"`.
4. Response JSON có thể trả cả `%` lẫn bậc: `{"status":"ok","volume":80,"level":"VOLUME_4"}`.
5. (Tuỳ chọn) Tool description: *“0–100 percent; robot has 5 steps.”*

**Pros:** Đúng format ESP/Xiaozhi; 1 file Go; deploy `vic-cloud` nhanh.  
**Cons:** Chỉ 5 mức thật — 55% và 60% có thể cùng bậc.

### B — Engine nhận thêm số % / 1–5

Sửa `ComputeDesiredVolumeFromLevelIntent` parse `"100"` hoặc `"3"`.

**Pros:** Mọi client gửi số đều OK.  
**Cons:** C++ + rebuild `libcozmo_engine` / OTA; không cần nếu A đủ.

### C — Không khuyến nghị

Gọi thẳng Settings API / tinymix từ cloudless — lệch kiến trúc, bỏ qua BehaviorVolume anim/feedback.

---

## Plan triển khai (Phase 1 = Option A)

### Bước 1 — Helper map

```text
percentToVolumeLevel(vol int) string
  // 0..100 → "VOLUME_1" .. "VOLUME_5"
```

Unit test nhỏ (table-driven) trong `mcp_match_test.go` hoặc file test cạnh MCP.

### Bước 2 — Đổi handler `set_volume`

- `volume_level` = output helper (không `fmt.Sprintf("%d", vol)`).
- Log: `[Xiaozhi] MCP set_volume %d%% → %s`.

### Bước 3 — Deploy + test trên robot

1. `make vic-cloud` + deploy `.56`.
2. Kêu “to hết cỡ” / “nhỏ nhất” / “âm lượng vừa”.
3. Log kỳ vọng:
   - `volume_level:VOLUME_5` (không còn `100`)
   - **Không** còn `ComputeDesiredVolumeFromLevelIntent.invalid`
   - (Nếu bật) `BehaviorVolume.SetVolume.Success`
4. Tai: TTS sau đó to/nhỏ rõ; hoặc kiểm tra setting `master_volume` trên robot.

### Bước 4 — (Tuỳ chọn) Đồng bộ `volume_up` / `volume_down`

Đảm bảo LLM ưu tiên `set_volume` khi user nói mức tuyệt đối (“50%”, “to hết”); dùng `volume_up`/`down` khi nói “to hơn một chút”. Chỉ mô tả tool — không bắt buộc code.

### Không làm trong plan này

- Gain riêng pipeline ALSA Xiaozhi độc lập master_volume.
- Thêm mute 0% trừ khi product muốn map 0 → MIN (stock không mute qua map này).

---

## Checklist

- [x] Helper `%` → `VOLUME_1`…`5` (`percentToVolumeLevel` in `mcp.go`)
- [x] Sửa `self.audio_speaker.set_volume` trong `mcp.go`
- [ ] Test trên robot: log `MCP set_volume N% → VOLUME_x`, không còn `invalid`
- [x] Deploy `vic-cloud` → `.56`
- [ ] Xác nhận tai nghe / `master_volume`
- [x] Cập nhật tool description (5 steps)

---

## Quyết định

| Muốn | Chọn |
|------|------|
| Sửa nhanh, đúng bug log | **A** (map trong `mcp.go`) |
| Engine chấp nhận mọi client gửi số | Thêm **B** sau nếu cần |

**Default:** làm **A** khi bạn bảo implement.
