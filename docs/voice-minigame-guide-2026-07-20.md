# Voice mini-game guide (Blackjack-style) — 2026-07-20

Cách Vector chạy game thoại kiểu **robot hỏi → mở mic → yes/no / hit / số**, và checklist khi **tạo game mới** hoặc **copy từ source khác**.

Related:

- `anki/vic-cloudless/docs/XIAOZHI-BLACKJACK-GAMEMODE-2026-07-13.md` — gameMode Xiaozhi ↔ Vosk
- `anki/vic-cloudless/docs/XIAOZHI-SELF-CONTROL-NO-RELISTEN-2026-07-13.md` — MCP xong không auto-mic

---

## 1. Blackjack nằm ở đâu

Chỉ **blackjack** là mini-game thoại nhiều vòng (hỏi → mic → chọn). Fistbump / cube / v.v. không cùng pattern.

### Hai tầng bắt buộc

| Tầng | Repo / path | Vai trò |
|------|-------------|---------|
| **Cloud (WireOS)** | `anki/vic-cloudless/` | MCP start game, tắt Xiaozhi WSS, load **Vosk** local, map STT → `intent_*` |
| **Engine (Victor)** | `anki/victor/` | Behavior FSM, TTS Acapela, face cards, `PromptUserForVoiceCommand` mở mic |

Không thể chỉ copy một bên: thiếu cloud thì không vào Vosk; thiếu engine thì không có vòng hỏi/điểm/UI.

### Map file (blackjack hiện tại)

#### Cloud — `anki/vic-cloudless/`

| File | Việc |
|------|------|
| `internal/xiaozhi/mcp.go` | MCP action `"blackjack"` → `intent_play_blackjack` |
| `internal/xiaozhi/gamemode.go` | ENTER/EXIT gameMode, `PrepareBlackjackSTT`, RAM gate Vosk |
| `internal/voice/stream/init.go` | Nhánh `StreamType_Blackjack` → Vosk (không fallback Xiaozhi) |
| `internal/voice/vtr/vosk.go` | `EnsureVosk` / `UnloadVosk` |
| `internal/voice/vtr/grammar.go` | Grammar nhỏ (hit/stand/yes/no) — tiết kiệm RAM |
| `internal/voice/vtr/intents.go` / `process.go` | Khớp phrase → intent |
| Robot: `/anki/data/.../cloudless/en-US/en-US.json` | Keyphrase → `intent_*` (build/deploy kèm cloudless) |

#### Engine — `anki/victor/`

| File / thư mục | Việc |
|----------------|------|
| `engine/.../behaviors/blackjack/behaviorBlackJack.{h,cpp}` | Vòng chơi, consume hit/stand/yes/no |
| `engine/.../behaviors/blackjack/blackJackSimulation.*` | Bộ bài / điểm |
| `engine/.../behaviors/blackjack/blackJackVisualizer.*` | Bài trên mặt |
| `engine/.../robotDrivenDialog/behaviorPromptUserForVoiceCommand.*` | TTS prompt → mic **wakewordless** |
| `resources/.../victorBehaviorTree/blackjack/*.json` | Prompt JSON (`streamType: "Blackjack"`, TTS, hit/stand, play again) |
| `resources/.../highLevelDelegates/voiceCommands/blackJackVoiceCommand.json` | Voice/MCP vào behavior `BlackJack` |
| `resources/.../user_intent_map.json` | `intent_play_blackjack`, `intent_blackjack_hit`, … → engine tags |
| `resources/assets/LocalizedStrings/*/BlackJackStrings.json` | Câu TTS trong game |
| CLAD mic (nếu thêm mode mới) | `StreamType` trong `mic.clad` / generated |

#### Anim (thường không sửa cho game mới)

| File | Việc |
|------|------|
| `animProcess/.../micData/micDataSystem.cpp` | FakeTrigger / `xiaozhi-relisten`; gameMode cloud đã no-op relisten |

---

## 2. Luồng chạy (tóm tắt)

```text
User (Xiaozhi chat)
  → LLM MCP self.vector.action action=blackjack
  → cloud: intent_play_blackjack + MaybeEnterBlackjackFromIntent
  → engine: play_blackjack → behavior BlackJack
  → Acapela hỏi ("Another card?")
  → StartWakeWordlessStreaming(StreamType_Blackjack)
  → cloud: CloseSession Xiaozhi + EnsureVosk (sau TTS / first blackjack mic)
  → Vosk STT → intent_blackjack_hit / stand / affirmative / negative
  → engine cập nhật tay bài / TTS / viz
  → hết ván: "Play again?" → yes reset / no quit
  → EXIT: Hey Vector | idle ~30s | max age | OOM → UnloadVosk, về Xiaozhi
```

**Trong game:** TTS = **Acapela engine**, STT = **Vosk**, không dùng Xiaozhi WSS.  
**Ngoài game:** Xiaozhi continuous như cũ.

---

## 3. Tạo game mới (copy từ blackjack trong repo này)

Khuyến nghị: **reuse `StreamType_Blackjack` + gameMode** lần đầu (MeetVictor cũng piggyback yes/no vậy). Thêm `StreamType` mới chỉ khi cần tách grammar/RAM riêng.

### Checklist file phải đụng

| # | Việc | File / chỗ |
|---|------|------------|
| 1 | Copy behavior C++ | `engine/.../behaviors/blackjack/` → `behaviors/<YourGame>/` (đổi tên class, logic) |
| 2 | Copy / sửa JSON tree | `resources/.../victorBehaviorTree/blackjack/` → `<yourGame>/` — giữ `PromptUserForVoiceCommand` + `streamType: "Blackjack"` (hoặc stream mới) |
| 3 | Strings TTS | `LocalizedStrings/*/…Strings.json` |
| 4 | Intent map | `user_intent_map.json` (+ clad `userIntent` nếu tag mới) |
| 5 | Voice command / activate | JSON voiceCommands + behavior ID trong tree |
| 6 | MCP action | `vic-cloudless/internal/xiaozhi/mcp.go` → `vectorActions` |
| 7 | Vào gameMode | `gamemode.go` — generalize tên hoặc tạm gọi chung blackjack stream |
| 8 | Nhánh mic | `stream/init.go` — nếu reuse Blackjack thì thường **không** cần nhánh mới |
| 9 | Từ khóa STT | `en-US.json` + `grammar.go` (thêm `one\|two\|three`, `higher\|lower`, …) |
| 10 | Build / deploy | `vic-anim` thường không; **vic-engine** + **vic-cloud** (+ copy `en-US.json` lên robot nếu đổi phrase) |

### Không quên

- Defer load Vosk **sau TTS** (tránh crash CloseSession mid-TTS).
- Không fallback Xiaozhi khi Vosk fail trên stream game.
- `TriggerRelisten` / continuous Xiaozhi **tắt** trong gameMode.
- RAM: Vosk ~90MB; refuse nếu MemAvailable &lt; ~150MB.

### Ý tưởng game cùng khuôn

Higher/lower · Đỏ/đen · Trivia đúng/sai · Chọn 1–2–3 · Would you rather · Simon says (yes → action).

---

## 4. Copy code từ **source khác** (repo / fork / WirePod / game ngoài)

“Source khác” = behavior/JSON/intent không phải blackjack trong tree WireOS hiện tại. Làm theo thứ tự:

### Bước A — Phân loại mang sang gì

| Mang từ ngoài | Có dùng được trực tiếp? | Cần làm thêm trên WireOS |
|---------------|-------------------------|---------------------------|
| Engine behavior C++ + JSON prompts | Có (port vào `anki/victor`) | Intent map, strings, build engine |
| Chỉ logic game (Python/JS/server) | Không chạy trên bot | Viết lại behavior C++ hoặc gọi MCP từ Xiaozhi (không phải mic Vosk loop) |
| Chipper / wire-pod intents JSON | Một phần | Merge vào `cloudless/en-US/en-US.json` + grammar |
| Xiaozhi-only (WSS STT cả game) | Tránh lúc đầu | Phải gắn `StreamType` + gameMode kiểu blackjack nếu muốn ổn định RAM/mic |
| Anim / face assets | Có | Copy resources + đăng ký anim nếu cần |
| MCP tool name | Đổi cho khớp | `mcp.go` `vectorActions` |

### Bước B — Checklist “port vào WireOS”

1. **Engine**
   - Paste/adapt vào `anki/victor/engine/.../behaviors/<Game>/`
   - JSON dưới `resources/.../victorBehaviorTree/<Game>/`
   - Mọi chỗ mở mic phải dùng `PromptUserForVoiceCommand` (hoặc tương đương) với `streamType` đã hỗ trợ (`Blackjack` hoặc CLAD mới)
   - Đăng ký behavior trong behavior config / factory (đúng convention victor — tìm chỗ BlackJack được register rồi làm giống)
   - `user_intent_map.json` + strings

2. **Cloud**
   - Thêm MCP ID trong `internal/xiaozhi/mcp.go`
   - Nối `intent_play_<game>` → `gamemode.go` (reuse blackjack enter/exit **hoặc** copy pattern `MaybeEnter…` / `Prepare…STT`)
   - `stream/init.go`: nếu stream type mới → thêm branch Vosk giống Blackjack
   - Phrase + grammar: `en-US.json`, `grammar.go`

3. **Không copy mù**
   - Socket / Chipper gRPC cũ → bỏ; WireOS dùng cloudless + Xiaozhi MCP
   - Full Vosk FST lớn → không; dùng grammar nhỏ
   - Auto FakeTrigger sau MCP → xem NO-RELISTEN doc (mic đóng đến khi wake / FakeTrigger có chủ đích)

4. **Deploy test**
   - `vic-engine` + `vic-cloud` (+ resources)
   - Log: `blackjack gameMode ON` / `[Vosk] matched:` / EXIT unload
   - MemProbe: Vosk không kẹt sau khi thoát game

### Bước C — File “vào đâu” khi copy từ ngoài (cheat sheet)

```text
Source ngoài                    →  Đích WireOS
─────────────────────────────────────────────────────────────
Game FSM / scoring              →  anki/victor/engine/.../behaviors/<Game>/
Prompt “Ask then listen” JSON   →  anki/victor/resources/.../victorBehaviorTree/<Game>/
TTS strings                     →  anki/victor/resources/.../LocalizedStrings/
Intent names                    →  user_intent_map.json (+ clad nếu cần)
“Play X” from assistant         →  anki/vic-cloudless/internal/xiaozhi/mcp.go
STT vocab / yes-no phrases      →  cloudless en-US.json + grammar.go
Enter/exit local STT            →  gamemode.go + stream/init.go
Wakewordless mic type           →  JSON streamType (+ mic.clad nếu type mới)
Face / anim                     →  victor resources / anim data
```

---

## 5. Khi nào cần `StreamType` mới?

| Tái sử dụng `Blackjack` | Thêm StreamType mới |
|-------------------------|---------------------|
| Yes/no / 2–4 từ khóa nhỏ | Grammar/RAM profile khác hẳn |
| Prototype nhanh | Nhiều game song song cần tách idle timeout |
| Ít đụng CLAD / cloud | Sẵn sàng sửa `mic.clad`, anim, init.go, gamemode |

Lần đầu: **reuse Blackjack stream**.

---

## 6. Test plan tối thiểu (mọi game mới)

1. Chat Xiaozhi bình thường (continuous OK).
2. Gọi MCP / nói mở game → log enter gameMode, Vosk load (sau TTS).
3. Robot hỏi → mic mở không cần Hey Vector → trả lời đúng từ khóa.
4. Sai/silence → behavior xử lý (reprompt / stand / quit) không treo.
5. Thoát game → Vosk unload, Hey Vector lại ra Xiaozhi.
6. RSS: không giữ Vosk sau EXIT; anim không zombie mic job (`docs/vic-anim-micjob-zombie-2026-07-20.md`).

---

## 7. Gợi ý implement tiếp

1. Chọn 1 game (Higher/Lower hoặc Trivia true/false).
2. Copy blackjack tree + behavior skeleton, đổi string/logic.
3. Reuse `StreamType_Blackjack` + MCP action mới.
4. Thêm 4–8 phrase vào grammar/`en-US.json`.
5. Ship engine + cloud; chỉnh UX sau.
