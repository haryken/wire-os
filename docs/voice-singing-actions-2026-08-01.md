# Voice singing (Hát) — quy trình thêm bài / revert

**Ngày:** 2026-08-01  
**Mục tiêu:** Lệnh thoại / Xiaozhi **“hát đi” / `sing`** → Vector hát 1 bài Cozmo (random trong pool), giống UX dance / firetruck.  
**Mẫu đã làm (P1→P2):** `intent_imperative_sing` → `SingingVoiceCommand` → random `Singing_Bingo` / `Singing_TwinkleTwinkle` / `Singing_AbaDaba`.

Dùng doc này để:

1. Thêm bài hát mới vào pool (P3 từng bài)  
2. Revert sạch nếu không ổn  

**Khác firetruck:** Singing **không** dùng `simple_voice_responses`. Cần `BehaviorSinging` (C++) + Wwise **switch** chọn bài + rebuild **`vic-engine`**.

**Checklist ngắn:** CLAD audio/anim/behavior/intent → `BehaviorSinging` + factory → song JSON → anim/group/manifest → `Cozmo_singing` bank + SoundbankBundleInfo → `SingingVoiceCommand` + `voiceFeatures` + `user_intent_map` → Vosk/MCP → **web `:8080`** → build/deploy engine → test.

Tham chiếu show đơn giản (1 anim, không C++): `docs/voice-show-actions-firetruck-2026-08-01.md`.

---

## Luồng runtime

```
Vosk phrase  OR  Xiaozhi MCP id "sing"
        ↓
intent_imperative_sing
        ↓
user_intent_map → imperative_sing
        ↓
VoiceFeatures → SingingVoiceCommand (DispatcherRandom)
        ↓
Singing_<Song> (BehaviorClass Singing)
        ↓
PostSwitchState(Cozmo_Sings_*Bpm, Cozmo_Sings_<Song>)
        ↓
BPM song → GetOut  +  bank Cozmo_singing
(GetIn skipped for snappy voice UX)
```

---

## Cách dùng (sau P2)

| Mode | Ví dụ |
|------|--------|
| Xiaozhi | “hát đi”, “hát bài”, “sing a song” → tool `sing` |
| Vosk | Hey Vector → “sing” / “hát” / “hát đi” |
| `:8080` Lệnh thoại | Nhóm Show / Move có phrase hát |

Pool mặc định (random đều): **39 bài** Cozmo (toàn bộ `Singing_*.json` từ Viccyware) — 80/100/120 BPM.

---

## Checklist thêm 1 bài mới (P3 từng bài)

Gọi bài `<Song>` (vd. `RowYourBoat`). Cần khớp **Viccyware** `Singing_<Song>.json` + switch Wwise.

### 1) CLAD BehaviorID (bắt buộc rebuild engine)

File: `anki/victor/clad/src/clad/types/behaviorComponent/behaviorIDs.clad`

Thêm:

```text
Singing_RowYourBoat,
```

(Chỉ cần nếu ID chưa có. Các switch audio `Cozmo_Sings_*` thường **đã đủ** từ port P1 — không thêm CLAD audio trừ khi bài mới nằm ngoài 3 enum BPM.)

### 2) Song behavior JSON

File: `anki/victor/resources/config/engine/behaviorComponent/behaviors/victorBehaviorTree/singing/Singing_<Song>.json`

```json
{
  "behaviorClass": "Singing",
  "behaviorID": "Singing_RowYourBoat",
  "audioSwitchGroup": "Cozmo_Sings_100Bpm",
  "audioSwitch": "Cozmo_Sings_Row_Your_Boat"
}
```

- **Không** cần `requiredUnlockId` (dead trên Vector).  
- `audioSwitchGroup` / `audioSwitch` phải khớp enum trong `robot/clad/.../audioSwitchTypes.clad` (string đúng tên enum).  
- Copy từ Viccyware `…/singing/Singing_*.json` rồi xóa unlock/displayName.

### 3) Cho vào pool voice

File: `…/voiceCommands/singingVoiceCommand.json` — thêm vào `behaviors`:

```json
{ "behavior": "Singing_RowYourBoat", "weight": 1.0 }
```

### 4) (Tuỳ) MCP / Vosk theo tên bài — P3

P2 chỉ có `sing` = random. P3: thêm intent riêng hoặc param — ngoài scope mặc định.

### 5) Deploy

Rebuild **không** bắt buộc nếu chỉ thêm JSON + BehaviorID đã có trong binary cũ…  
**Nhưng** BehaviorID mới **chưa** có trong binary cũ → **phải rebuild** `vic-engine` sau khi sửa `behaviorIDs.clad`.

Nếu BehaviorID đã có sẵn trong clad/binary (port đủ 39 ID), chỉ cần copy JSON + sửa `singingVoiceCommand.json` + deploy resources.

### 6) Web `:8080` (**bắt buộc** nếu đổi phrase user thấy)

`anki/wired/webroot/index.html` — tab Lệnh thoại. Deploy `/etc/wired/webroot`.

---

## Inventory P1/P2 (file đã đụng)

### CLAD (rebuild engine)

| File | Thay đổi |
|------|----------|
| `robot/clad/.../audioSwitchTypes.clad` | `Cozmo_Sings_{80,100,120}Bpm` + SwitchGroupType |
| `robot/clad/.../audioEventTypes.clad` | `Stop__Robot_Singing` |
| `robot/clad/.../audioParameterTypes.clad` | `Cozmo_Singing_Vibrato` |
| `robot/clad/.../audioSoundbanks.clad` | `Cozmo_Singing` |
| `clad/.../animationTrigger.clad` | `DEPRECATED_Singing_*` |
| `clad/.../behaviorClasses.clad` | `Singing` |
| `clad/.../behaviorIDs.clad` | `Singing_{Bingo,TwinkleTwinkle,AbaDaba}`, `SingingVoiceCommand` |
| `clad/.../userIntent.clad` | `imperative_sing` |

### Engine C++

| File | Vai trò |
|------|---------|
| `engine/.../oneShots/behaviorSinging.{h,cpp}` | PostSwitch + GetIn/Song/GetOut |
| `engine/.../behaviorFactory.cpp` | `BehaviorClass::Singing` |

### Resources / EXTERNALS

| File | Vai trò |
|------|---------|
| `resources/.../animations/anim_cozmosings_*.json` | 7 anim |
| `EXTERNALS/.../animationGroups/CozmoSing/ag_cozmosings_*.json` | 5 groups |
| `EXTERNALS/.../anim_manifest.json` | entries `anim_cozmosings_*` (gitignore — `git add -f` / deploy tay) |
| `resources/.../AnimationTriggerMap.json` | map `DEPRECATED_Singing_*` → `ag_cozmosings_*` |
| `resources/.../singing/Singing_{Bingo,TwinkleTwinkle,AbaDaba}.json` | 3 bài |
| `resources/.../voiceCommands/singingVoiceCommand.json` | random pool |
| `resources/.../reactions/voiceFeatures.json` | thêm `SingingVoiceCommand` |
| `resources/.../user_intent_map.json` | `intent_imperative_sing` → `imperative_sing` |
| `EXTERNALS/.../Cozmo_singing.{bnk,zip,txt}` | Wwise bank |
| `EXTERNALS/.../SoundbankBundleInfo.json` | entry `Cozmo_singing` |

### Cloudless / Wired

| File | Vai trò |
|------|---------|
| `vic-cloudless/internal/xiaozhi/mcp.go` | `sing` → `intent_imperative_sing` |
| `vic-cloudless/wireos-intents/show-actions-en-US.json` | overlay Vosk (+ merge `build/en-US`) |
| `wired/webroot/index.html` | hướng dẫn `:8080` |

### Docs

| File | Vai trò |
|------|---------|
| `docs/voice-singing-actions-2026-08-01.md` | Doc này |

---

## Deploy checklist (robot)

```text
# Engine binary (sau rebuild)
/anki/bin/vic-engine

# Resources
.../config/engine/behaviorComponent/behaviors/victorBehaviorTree/singing/
.../voiceCommands/singingVoiceCommand.json
.../reactions/voiceFeatures.json
.../user_intent_map.json
.../animations/anim_cozmosings_*.json
.../assets/animationGroups/CozmoSing/
.../assets/cladToFileMaps/AnimationTriggerMap.json
.../assets/anim_manifest.json
.../sound/Cozmo_singing.bnk + .zip
.../sound/SoundbankBundleInfo.json   # có Cozmo_singing

# Cloud / UI
/anki/bin/vic-cloud
.../cloudless/en-US/en-US.json
/etc/wired/webroot/index.html
```

Restart: `systemctl restart anki-robot.target` (hoặc ít nhất `vic-engine` + `vic-anim` + `vic-cloud`).

---

## Lỗi hay gặp

| Triệu chứng | Nguyên nhân | Fix |
|-------------|-------------|-----|
| Intent ok, không hát | Thiếu `SingingVoiceCommand` trong `voiceFeatures` / map sai | Kiểm tra JSON + intent map |
| Behavior fail / assert | Thiếu CLAD switch / factory | Rebuild engine đủ clad |
| Cử động không có tiếng | Thiếu `Cozmo_singing` trong `SoundbankBundleInfo` hoặc chưa deploy `.bnk`/`.zip` | Giống firetruck silent |
| AnimNotFound | Thiếu `anim_manifest` / group / TriggerMap | Deploy manifest + CozmoSing groups + map |
| Chỉ JSON, quên rebuild | BehaviorID/Class mới không có trong binary | **Bắt buộc** rebuild `vic-engine` |

---

## Revert nhanh

1. Xóa `SingingVoiceCommand` khỏi `voiceFeatures.json`.  
2. Xóa block `intent_imperative_sing` trong `user_intent_map.json`.  
3. Xóa MCP `sing` + Vosk overlay entry.  
4. Gỡ phrase trên `:8080`.  
5. Deploy cloudless + resources (engine cũ vẫn boot được; song JSON thừa vô hại).

Revert sạch: gỡ CLAD/C++/assets + rebuild (nặng hơn).

---

## P3 (sau này)

- Thêm BehaviorID + JSON + weight trong `singingVoiceCommand.json` (cả ~39 bài Viccyware).  
- Intent theo tên bài (“hát bingo”).  
- Freeplay socialize tự hát — không bắt buộc cho voice.

---

## Ghi chú

- Nguồn: Viccyware `BehaviorSinging` + `Cozmo_singing` + `anim_cozmosings_*`.  
- Cube-shake vibrato Cozmo **không** port (P1/P2).  
- **Luôn** cập nhật hướng dẫn tab Lệnh thoại `:8080` khi đổi phrase.  
- OTA: `Cozmo_singing.bnk` ~2.8MB (nhẹ hơn Missing_sfx).
