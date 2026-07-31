# Kêu nhảy là nhảy (không cần nhạc) — plan dễ hiểu

**Ngày:** 2026-07-31  
**Trạng thái Phase 1:** ✅ Code + deploy `.56`.  
**Follow-up 2026-07-31 ~23:42:** Intent Xiaozhi bị `ForceClear` sau 3 tick → robot không nhảy. Đã nới timeout **3→90 ticks** + bỏ `DriveOffChargerStraight` khỏi voice dance queue. Deploy lại lib + JSON.  
**Phase 2:** ❌ Chưa làm.  
**Test:** ⏳ Thử lại “nhảy đi” (nên để robot **dưới đất / off sạc** để thấy rõ thân nhảy).  
**Mục tiêu sản phẩm:** Bạn nói “nhảy đi” / Xiaozhi bảo nhảy → robot **nhảy ngay**, **không cần bật loa nhạc**.

---

## Tách phase (đọc cái này trước)

| Phase | Làm gì? | Sửa code? | Trạng thái |
|-------|---------|-----------|------------|
| **Phase 0** | Test trên robot để xác nhận bug | Không | (tuỳ user) |
| Phase 1 | **Kêu nhảy → nhảy luôn, không cần nhạc** | JSON + C++ engine | ✅ **Done + deployed .56** |
| **Phase 2** | Làm đẹp + nút chọn trên web wired | wired + polish | ❌ Chưa |

```text
Phase 0  →  chứng minh bug
Phase 1  →  sửa để “nhảy!” là nhảy   ← DONE (source)
Phase 2  →  polish + toggle UI        ← chưa
```

### Phase 1 — đã đụng file nào?

| File | Việc | Status |
|------|------|--------|
| `.../danceToTheBeatVoiceCommand.json` | Bỏ `ListenForBeatsVoiceCommand`; feature → `DanceToTheBeat` | ✅ |
| `.../danceToTheBeat.json` | Bỏ điều kiện `BeatDetected` → `OffTreads` (ambient vẫn gate ở Coordinator) | ✅ |
| `behaviorDanceToTheBeat.h` | Thêm `freeDanceMode` | ✅ |
| `behaviorDanceToTheBeat.cpp` | Không beat → 120 BPM free-dance; không cancel mất beat; không bật listen mic khi free | ✅ |
| Xiaozhi `mcp.go` | Không cần | — |
| Wired UI | Phase 2 | ❌ |

**Deploy còn lại:** ✅ đã đẩy lên `.56` (`vic-engine`, `libcozmo_engine.so`, 2 JSON). Máy khác / OTA: build lại hoặc copy tương tự. Test tay còn ⏳.

---

## Phần 1 — Cho người không tech

### Bạn muốn gì?

Hiện nay Vector kiểu: *“Ok nhảy… để tao nghe nhạc đã”* → im lặng 10–15 giây → không nghe thấy nhịp → **lắc đầu / không nhảy**.

Bạn muốn: *“Nhảy!”* → **nhảy luôn**.

### “Nhạc” trong máy nghĩa là gì? (không phải Spotify)

Robot **không biết** bạn đang phát YouTube/Bluetooth. Nó chỉ dùng **micro** nghe xem trong phòng có **nhịp đập ổn định** (kick drum, beat rõ) không.

| Trong đầu bạn | Trong đầu robot |
|---------------|-----------------|
| Có nhạc đang phát | Có **nhịp mic** đủ mạnh, đều, kéo dài vài giây |
| Bài ballad / vocal êm | Thường = **không có nhịp** → không nhảy |
| Bài EDM kick rõ, loa to gần | Có thể nhảy (vẫn hay fail nếu nhịp loạn / loa xa) |

Nên “bật nhạc mà không nhảy” ở **code gốc Anki** là chuyện thường — không phải bug Xiaozhi.

### Có hai kiểu nhảy khác nhau

| Kiểu | Người dùng thấy | Cần nhạc? |
|------|-----------------|-----------|
| **A. Kêu nhảy** (nói / Xiaozhi) | “Nhảy đi” | **Hiện tại: CÓ** → ta sẽ sửa thành **KHÔNG** |
| **B. Tự nhảy khi nghe nhạc** (ambient) | Đang chơi → tự bắt đầu nghe rồi nhảy | Vẫn cần nhịp mic — **không đụng** trong Phase 1 |

### Sửa xong sẽ như thế nào?

1. Bạn: “Nhảy đi” (hoặc Xiaozhi `dance`).
2. Robot xuống sạc nếu cần, có thể làm ngắn anim “chuẩn bị”.
3. **Nhảy luôn** với nhịp nội bộ cố định (ví dụ 120 BPM — như metronome trong đầu, không cần loa).
4. Bật nhạc trong phòng mà **không** kêu: vẫn có thể tự nhảy như cũ (không phá).

### Không làm kiểu “lừa”

- **Cách đúng (Phase 1/2):** Đổi quy trình lệnh nhảy — bỏ bước “bắt buộc nghe nhạc”.
- **Cách cheat (không dùng làm chính):** Giả vờ có beat trong detector (`kFakeBeat_bpm`) — chỉ prototype nhanh.

---

## Phần 2 — Sơ đồ dễ nhìn

### Hiện tại (kêu nhảy)

```text
Bạn: "Nhảy!"
    ↓
Robot xuống sạc (nếu đang trên đế)
    ↓
Đứng nghe mic 10–15 giây   ← 🛑 CHẶN Ở ĐÂY nếu không có nhịp
    ↓
Có nhịp? → Nhảy theo tempo nhạc
Không?   → Anim "không làm được" / bỏ cuộc
```

### Sau Phase 1 (kêu nhảy)

```text
Bạn: "Nhảy!"
    ↓
Robot xuống sạc (nếu cần)
    ↓
(Tuỳ chọn) Anim chuẩn bị ngắn
    ↓
Nhảy luôn với tempo cố định (~120 BPM)   ← không cần loa
```

### Tự nhảy khi có nhạc (giữ nguyên)

```text
Mic nghe thấy nhịp ổn định
    ↓
Coordinator mở Listen → xác nhận beat
    ↓
Nhảy sync theo nhạc (như gốc)
```

---

## Phần 3 — Từ ngữ kỹ thuật ↔ tiếng thường

| Tech | Tiếng thường |
|------|----------------|
| **Intent** `imperative_dance` / `intent_imperative_dance` | Lệnh nội bộ “hãy nhảy” |
| **Behavior** | Một “cảnh hành động” robot chạy (nghe / nhảy / xuống sạc…) |
| **DispatcherQueue** | Hàng đợi: làm bước 1 rồi 2 rồi 3 |
| **ListenForBeats** | Cảnh “đứng nghe nhịp bằng mic” |
| **DanceToTheBeat** | Cảnh “nhảy sync theo nhịp đã bắt được” |
| **BeatDetector / aubio** | Phần mềm phân tích mic tìm nhịp |
| **IsBeatDetected()** | “Engine đã tin là có nhịp thật” (đủ beat, tempo đều, confidence đủ) |
| **IsPossibleBeatDetected()** | “Có vẻ có nhịp” (lỏng hơn — chỉ để bắt đầu nghe) |
| **BeatDetected condition** | Điều kiện JSON: behavior chỉ chạy khi đã có nhịp |
| **Coordinator** | Behavior tự động khi nghe nhạc môi trường (không phải lệnh nói) |
| **Xiaozhi MCP `action=dance`** | AI bảo robot nhảy → vẫn map vào cùng intent nhảy |
| **BPM** | Nhịp mỗi phút (120 = khá nhanh, phổ biến để nhảy) |
| **Free-dance / voice-free** | Nhảy theo lệnh, không phụ thuộc mic nhạc |
| **Fake beat** | Cheat: giả beat trong detector |

---

## Phần 4 — PHASE 1: Sửa ở đâu? (checklist)

> **Phase 1 source: DONE.** Deploy binary/JSON lên robot vẫn cần (xem bảng trên đầu file).

Chỉ đụng **đường “kêu nhảy”**. Không sửa Xiaozhi MCP.

### Phase 1 — follow-up fix (intent drop) ✅

| File | Việc | Status |
|------|------|--------|
| `userIntentComponent.cpp` | `kMaxTicksToClear` 3 → **90** (Xiaozhi MCP kịp kích VoiceFeatures) | ✅ deployed |
| `danceToTheBeatVoiceCommand.json` | Chỉ còn `DanceToTheBeat` (bỏ DriveOff — tránh kẹt trên sạc) | ✅ deployed |

**Triệu chứng log:** `PendingIntentNotCleared.ForceClear` + `imperative_dance` → behavior không kịp nhận.  
**Không phải** thiếu free-dance binary (FreeDance đã có trên bot).

---

### File 1 — Hàng đợi lệnh nhảy (JSON) ✅

**File:** `.../danceToTheBeatVoiceCommand.json`

**Đã sửa:** bỏ `ListenForBeatsVoiceCommand` → chỉ còn `DriveOffChargerStraight` + `DanceToTheBeat`.

### File 2 — Dispatcher dance (JSON) ✅

**File:** `.../danceToTheBeat.json`

**Đã sửa:** `BeatDetected` → `OffTreadsState` (ambient vẫn check beat ở Coordinator trước khi gọi dance).

### File 3 — C++ free-dance ✅

**Files:** `behaviorDanceToTheBeat.h` / `.cpp`

**Đã sửa:**
- Không có mic beat → `freeDanceMode` + tempo **120 BPM**
- Không cancel khi mất beat ở free mode
- Không bật listen-for-beats mic khi free mode

### File 4 — Không đụng (Phase 1)

| File / phần | Vì sao |
|-------------|--------|
| Coordinator / aubio / MCP / Wired | Giữ ambient; Xiaozhi đã map đúng; UI = Phase 2 |

### Deploy

1. Build `vic-engine` (docker/`build-v` hoặc ninja Release).
2. Rsync 2 JSON + copy `/anki/bin/vic-engine`.
3. `systemctl restart anki-robot.target`
4. Test: im lặng + “nhảy” → nhảy; log có `FreeDance` nếu bật channel Behaviors.

---

## Phần 5 — PHASE 2: Làm sau (chưa chi tiết file)

> **Không phải Phase 1.** Chỉ làm khi “kêu nhảy là nhảy” đã chạy ổn.

| Việc | Người thường | Tech | Ghi chú |
|------|--------------|------|---------|
| Anim vào/ra nhảy gọn hơn | Không còn đứng “nghe” lâu | Get-in / get-out triggers | Polish UX |
| Nút trên web `:8080` | Chọn “Nhảy tự do” vs “Nhảy cần nhạc” | Wired ghi flag → engine đọc | Tuỳ chọn |
| Xiaozhi | Vẫn nói “nhảy” như cũ | Không đổi MCP nếu flag ở engine | — |

Phase 2 **chưa** liệt kê path file từng dòng — khi làm sẽ bổ sung (wired mod + persistent flag + đọc flag trong `DanceToTheBeatVoiceCommand` / behavior).

---

## Phần 6 — Thứ tự làm (tóm lại)

### Phase 0 — Xác nhận bug (5 phút)

1. Không nhạc, bảo nhảy → đứng nghe rồi thôi / cant-do-that.  
2. Nhạc kick rõ, bảo nhảy → có thể nhảy.  
→ Chứng minh: lệnh nhảy đang phụ thuộc mic.

### Phase 1 — Sửa thật ✅ source done

1. ✅ `danceToTheBeatVoiceCommand.json` — bỏ listen.  
2. ✅ `danceToTheBeat.json` — không còn BeatDetected trên dispatcher.  
3. ✅ `behaviorDanceToTheBeat` — free-dance 120 BPM, không cancel mất beat.  
4. ✅ Deploy resources + engine lên `.56` (`vic-engine` + `libcozmo_engine.so` + 2 JSON).  
5. ⏳ Test trên robot — user: im lặng + “nhảy đi”.

### Phase 2 — UX ← **làm sau**

1. Polish anim.  
2. (Tuỳ chọn) Toggle wired “tự do / cần nhạc”.

### Không làm trong plan này

- Làm ambient nhảy dễ hơn (nới ngưỡng detector) — **khác mục tiêu**.  
- Fake beat console làm sản phẩm chính.

---

## Phần 7 — Quyết định nhanh

| Muốn | Phase |
|------|-------|
| Kêu nhảy là nhảy, không cần nhạc | **Phase 1** |
| Vẫn muốn tự nhảy khi có nhạc hay | Giữ Coordinator (không phá khi làm Phase 1) |
| Có nút chọn trên web | **Phase 2** |
| Thử 5 phút không sửa JSON/C++ | Console `kFakeBeat_bpm=120` (cheat tạm, không phải phase) |

---

## Log hay gặp (đọc máy)

| Log / anim | Nghĩa thường |
|------------|----------------|
| `NoMoreBeat` | Đang nghe rồi mic mất nhịp → hủy (ambient) |
| `DanceBeatCantDoThat` | Lệnh nhảy: nghe mãi không ra nhịp |
| `dttb.cancel_beat_lost` | Đang nhảy bị mất nhịp → dừng |
| `dttb.coord_no_beat` | Tự nhảy: sau listen vẫn chưa đủ beat |

---

## Tóm một câu

**Sửa chỗ “kêu nhảy”:** bỏ bước nghe mic trong `danceToTheBeatVoiceCommand.json`, và cho `DanceToTheBeat` nhảy với nhịp cố định khi không có beat — **không** phải lừa detector, **không** đụng Xiaozhi trừ khi sau này làm nút Phase 2.
