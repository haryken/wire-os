# Voice show-actions (Firetruck-style) — quy trình thêm / revert

**Ngày:** 2026-08-01  
**Mục tiêu:** Thêm hành động thoại / Xiaozhi kiểu “show” (1 anim group + SFX), giống `firetruck` / `xe cứu hỏa`, **không** cần behavior C++ phức tạp.  
**Mẫu đã làm:** `intent_play_firetruck` → `ag_voice_firetruck` → `anim_petdetection_dog_02` + `Missing_sfx.bnk`.

Dùng doc này để:

1. Thêm action tương tự (siren khác, Codelab show, …)  
2. Revert sạch nếu không ổn  

**Checklist ngắn (đủ 8 bước):** anim (+ manifest) → audio (+ SoundbankBundleInfo) → `user_intent_map` → Vosk overlay/`en-US` → MCP `mcp.go` → **web `:8080` `index.html`** → deploy → test.

---

## Luồng runtime (giống action khác)

```
Vosk phrase  OR  Xiaozhi MCP action id
        ↓
cloud intent string  (vd. intent_play_firetruck)
        ↓
user_intent_map.json  →  simple_voice_responses
        ↓
SimpleVoiceResponse  →  play anim_group
        ↓
anim JSON + Wwise event (Missing_sfx / bank khác)
```

**Không đụng CLAD** nếu chỉ play 1 animation group (đường `simple_voice_responses`).

---

## Checklist thêm 1 action mới (copy Firetruck)

Gọi action mới là `<NAME>` (vd. `firetruck`, `siren2`). Cloud intent: `intent_play_<NAME>`.

### 1) Anim

| Việc | Path |
|------|------|
| Anim JSON (hoặc `.bin` trong EXTERNALS) | `anki/victor/resources/config/engine/animations/anim_<…>.json` **và/hoặc** `EXTERNALS/animation-assets/animations/*` |
| **Bắt buộc:** đăng ký tên trong manifest | `anki/victor/EXTERNALS/animation-assets/anim_manifest.json` — thêm `{"name":"anim_<…>","length_ms":N}` |
| Anim group **chỉ** show này | `anki/victor/EXTERNALS/animation-assets/animationGroups/VoiceActions/ag_voice_<NAME>.json` |

**Quan trọng:** Engine chỉ cho `PlayAnimByName` nếu tên có trong `assets/anim_manifest.json`. Thiếu entry → log `AnimationComponent.PlayAnimByName.AnimNotFound` (đúng lỗi Firetruck lần đầu).

**Git:** `EXTERNALS/.gitignore` có `animation-assets/anim_manifest.json` — file **không** vào git mặc định. Sau khi sửa, deploy thẳng lên robot (`/anki/data/assets/cozmo_resources/assets/anim_manifest.json`) **hoặc** `git add -f` nếu muốn giữ trong fork WireOS.

Engine load anim từ cả `assets/animations/` và `config/engine/animations/` (`RobotDataLoader`).  
OTA package: `config/**/*` + `externals/animation-assets/animations/**/*.bin` + `animationGroups/**/*.json` + `anim_manifest.json` (`resources/BUILD.in`).

Mẫu group:

```json
{
  "Animations": [
    {
      "Name": "anim_petdetection_dog_02",
      "Weight": 1.0,
      "CooldownTime_Sec": 0.0,
      "Mood": "Default"
    }
  ]
}
```

`Name` phải khớp key trong file anim / tên canned anim đã load.

### 2) Audio (nếu anim gọi Wwise event)

| Việc | Path |
|------|------|
| Sound bank + zip | `…/victor_linux/Missing_sfx.bnk` **và** `Missing_sfx.zip` (+ `.wem` nếu unpack) |
| **Bắt buộc:** đăng ký bank | `…/victor_linux/SoundbankBundleInfo.json` — thêm entry `soundbank_name: Missing_sfx` |
| (tuỳ) ghi chú event | `…/Missing_sfx.txt` |

`SoundbankLoader` **không** tự load mọi `.bnk` — chỉ load bank có trong `SoundbankBundleInfo.json`. Thiếu entry → anim chạy nhưng **câm** (đúng lỗi Firetruck lần 2).

`SoundbankLoader` cũng add `bundle_name.zip` vào Wwise search path — nên ship cả `.zip`.

Kiểm tra anim có `RobotAudioKeyFrame` / `audioName` / `audioEventId` khớp bank (Firetruck: `Play__Robot_Vo__Codelab_Firetruck` / id `3855902783`).

Entry mẫu trong `SoundbankBundleInfo.json`:

```json
{
  "bundle_name": "Missing_sfx",
  "language": "English(US)",
  "path": "Missing_sfx.bnk",
  "soundbank_name": "Missing_sfx"
}
```

### 3) Engine map — simple voice

File: `anki/victor/resources/config/engine/behaviorComponent/user_intent_map.json`

Thêm vào mảng `simple_voice_responses`:

```json
{
  "cloud_intent": "intent_play_<NAME>",
  "response": {
    "anim_group": "ag_voice_<NAME>",
    "emotion_event": "RespondToShortVoiceCommand",
    "active_feature": "BasicVoiceCommand",
    "disable_wakeword_turn": true
  }
}
```

- `disable_wakeword_turn: true` nếu show có **drive** (như firetruck / forward).  
- `anim_group` phải tồn tại — engine verify lúc init.

### 4) Vosk phrases

File: `anki/vic-cloudless/build/en-US/en-US.json`  
**Git:** cả thư mục `build/` bị ignore — **không** commit được. Sửa local + copy lên robot, và giữ bản overlay tracked (xem dưới).

```json
{
  "name": "intent_play_<NAME>",
  "keyphrases": ["english phrase", "cụm tiếng Việt", "asr typo…"],
  "requiresexact": false
}
```

Overlay đã track (Firetruck): `anki/vic-cloudless/wireos-intents/show-actions-en-US.json` — merge vào `build/en-US/en-US.json` trước khi deploy.

Deploy: copy `build/en-US` → `/anki/data/assets/cozmo_resources/cloudless/`.

### 5) Xiaozhi MCP

File: `anki/vic-cloudless/internal/xiaozhi/mcp.go` — mảng `vectorActions`:

```go
{ID: "<NAME>", Intent: "intent_play_<NAME>", Hint: "mô tả / tiếng Việt"},
```

Cập nhật `vectorActionToolDescription()` nếu muốn LLM ưu tiên action mới.

Build + deploy `vic-cloud`.

### 6) Hướng dẫn web `:8080` (**bắt buộc**)

Tab **Lệnh thoại (Voice)** trên wired UI phải liệt kê phrase mới — user đọc từ đây, không chỉ từ repo docs.

| Việc | Path |
|------|------|
| Thêm phrase Xiaozhi + Vosk | `anki/wired/webroot/index.html` — section `#voice` (`voice-mode-xz` và `voice-mode-vosk`) |
| Deploy UI | copy `webroot/` → `/etc/wired/webroot` (hoặc OTA wired) rồi soft-refresh trình duyệt |

Mẫu Firetruck: nhóm **🚒 Show ngắn** / **Short shows** + dòng trong mục chào / chat ngắn.

**Mỗi lần thêm show-action mới → cập nhật `index.html` cùng commit** (đừng chỉ sửa MD repo).

### 7) Deploy / test

1. Build/deploy **victor** resources (anim + group + sound) — hoặc copy thủ công lên robot rồi restart `vic-anim` / `anki-robot.target`.  
2. Deploy **cloudless** (en-US + binary nếu đổi MCP).  
3. Deploy **wired webroot** (bước 6) — kiểm tra tab Lệnh thoại `:8080`.  
4. Test Vosk: wake → nói phrase.  
5. Test Xiaozhi: “làm xe cứu hỏa” → tool `firetruck`.  
6. Tab Nhật ký `:8080` → `vic-anim` / `vic-cloud`: không missing anim / Wwise event.

**Checklist deploy Firetruck (robot):**

```text
/anki/data/assets/cozmo_resources/assets/anim_manifest.json          # có anim_petdetection_dog_02
/anki/data/assets/cozmo_resources/assets/animations/anim_petdetection_dog_02.json
/anki/data/assets/cozmo_resources/assets/animationGroups/VoiceActions/ag_voice_firetruck.json
/anki/data/assets/cozmo_resources/sound/Missing_sfx.bnk
/anki/data/assets/cozmo_resources/sound/Missing_sfx.zip
/anki/data/assets/cozmo_resources/sound/SoundbankBundleInfo.json     # có Missing_sfx
/anki/data/assets/cozmo_resources/config/engine/behaviorComponent/user_intent_map.json
/anki/data/assets/cozmo_resources/cloudless/en-US/en-US.json
```

---

## Lỗi hay gặp (Firetruck đã dính)

| Triệu chứng | Nguyên nhân | Fix |
|-------------|-------------|-----|
| MCP/intent ok, **không cử động** | Thiếu `anim_manifest.json` entry | Thêm `name` + `length_ms`, deploy manifest, restart anim |
| Cử động ok, **câm** | Bank chưa trong `SoundbankBundleInfo.json` | Thêm entry + ship `.bnk`/`.zip`, restart anim |
| Xiaozhi TTS **rè** khi chat thường, log `gap keepalive ~350ms` | ALSA pad silence giữa câu TTS | `alsa_player.go`: `minGapBeforePad` ≥ 1s, buffer ~1.5s (đã fix 2026-08-01) |

---

## Firetruck — file đã thêm (inventory revert)

### Victor (`anki/victor`)

| File | Vai trò |
|------|---------|
| `resources/config/engine/animations/anim_petdetection_dog_02.json` | Show ~12s + firetruck VO |
| `EXTERNALS/animation-assets/animations/anim_petdetection_dog_02.json` | Bản copy dưới assets/ (anim process) |
| `EXTERNALS/animation-assets/anim_manifest.json` | Entry `anim_petdetection_dog_02` / `length_ms: 13200` (**bắt buộc**, gitignored — `git add -f` nếu cần) |
| `EXTERNALS/animation-assets/animationGroups/VoiceActions/ag_voice_firetruck.json` | Group voice-only (**submodule EXTERNALS**) |
| `EXTERNALS/victor-audio-assets/…/Missing_sfx.bnk` + `Missing_sfx.zip` | Bank SFX |
| `EXTERNALS/victor-audio-assets/…/SoundbankBundleInfo.json` | **Phải** có entry `Missing_sfx` |
| `EXTERNALS/victor-audio-assets/…/Missing_sfx.txt` | Catalog event |
| `resources/config/engine/behaviorComponent/user_intent_map.json` | Entry `intent_play_firetruck` trong `simple_voice_responses` |

**Git note:** `EXTERNALS` là submodule. Commit asset trong EXTERNALS rồi bump tip trong `anki/victor` → root. Anim JSON + `user_intent_map.json` nằm trực tiếp trong `anki/victor`.

Nguồn anim/SFX: Viccyware (`resources/.../anim_petdetection_dog_02.json`, `Missing_sfx.zip`).

### Cloudless (`anki/vic-cloudless`)

| File | Vai trò |
|------|---------|
| `wireos-intents/show-actions-en-US.json` | Overlay keyphrases (tracked) — merge vào `build/en-US/en-US.json` |
| `build/en-US/en-US.json` | Runtime Vosk (gitignored) |
| `internal/xiaozhi/mcp.go` | `vectorActions` id `firetruck` + hint trong tool description |
| `internal/xiaozhi/alsa_player.go` | Gap keepalive TTS (không phải firetruck; cùng đợt fix rè) |

### Wired UI (`anki/wired`)

| File | Vai trò |
|------|---------|
| `webroot/index.html` (`#voice`) | Hướng dẫn user trên `:8080` — phrase firetruck / xe cứu hỏa (Xiaozhi + Vosk) |

### Docs (repo root)

| File | Vai trò |
|------|---------|
| `docs/voice-show-actions-firetruck-2026-08-01.md` | Doc này (dev); **không** thay thế bảng Lệnh thoại trên web |

---

## Revert (nếu không ổn)

### Nhanh (tắt hành vi, giữ asset)

1. Xóa block `intent_play_firetruck` trong `simple_voice_responses` (`user_intent_map.json`).  
2. Xóa entry `intent_play_firetruck` trong `en-US.json` (+ overlay).  
3. Xóa `{ID: "firetruck", ...}` (+ câu hint) trong `mcp.go`.  
4. Deploy cloudless + copy `user_intent_map.json` / restart anim.

→ Robot không còn nhận lệnh; file anim/audio vẫn trên disk (an toàn, không gãy boot).

### Sạch (gỡ hết asset)

Sau bước trên, xóa:

```text
anki/victor/resources/config/engine/animations/anim_petdetection_dog_02.json
anki/victor/EXTERNALS/animation-assets/animations/anim_petdetection_dog_02.json
# Xóa entry name=anim_petdetection_dog_02 trong EXTERNALS/animation-assets/anim_manifest.json
anki/victor/EXTERNALS/animation-assets/animationGroups/VoiceActions/ag_voice_firetruck.json
# nếu VoiceActions trống: rmdir VoiceActions
anki/victor/EXTERNALS/victor-audio-assets/victor_robot/victor_linux/Missing_sfx.bnk
anki/victor/EXTERNALS/victor-audio-assets/victor_robot/victor_linux/Missing_sfx.zip
anki/victor/EXTERNALS/victor-audio-assets/victor_robot/victor_linux/Missing_sfx.txt
# (tuỳ) gỡ entry Missing_sfx trong SoundbankBundleInfo.json
# wem chỉ thuộc Missing_sfx.zip — đối chiếu zip trước khi xóa loose .wem
```

Rồi rebuild OTA / sync resources + restart robot stack.

### Git revert

Nếu đã commit riêng:

```bash
# trong anki/victor/EXTERNALS, anki/victor, anki/vic-cloudless, root
git log --oneline --grep=firetruck
git revert <sha>   # hoặc reset nếu chưa push
```

---

## Khi nào KHÔNG dùng simple_voice (dùng path Singing / trick)

Dùng **UserIntent CLAD + behavior JSON** (`respondToUserIntents`) khi cần:

- Nhiều bước (tìm cube → trick)  
- Random nhiều behavior  
- Param (bài hát cụ thể, volume…)  
- Ưu tiên khác trong `VoiceFeatures` / `globalInterruptions`

Firetruck-style = **chỉ 1 show anim** → giữ simple_voice.

---

## Ghi chú

- Firetruck **không** gắn PetDetection vision; voice dùng group riêng `ag_voice_firetruck`.  
- Phrase Vosk substring match — tránh keyphrase quá ngắn dễ đụng nhầm.  
- Xiaozhi: LLM phải gọi MCP `firetruck`; nếu chỉ chat, kiểm tra tool description / prompt.  
- OTA: `Missing_sfx.bnk` ~15MB — cân nhắc size image.  
- **Luôn** cập nhật hướng dẫn tab Lệnh thoại `:8080` (`wired/webroot/index.html`) khi thêm/xóa show-action — MD repo chỉ cho dev.
