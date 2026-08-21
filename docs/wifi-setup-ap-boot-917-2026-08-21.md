# WireOS WiFi: fault 917 khi boot (vic-setup-ap)

**Date:** 2026-08-21  
**Branch:** `xiaozhi-wireos-save-2026-07-14`  
**Symptom:** Sau khi thêm tính năng WiFi hotspot, mỗi lần tắt/mở nguồn robot “crash” liên tục (nhìn như `vic-robot` chết). **Không** liên quan join WiFi trên web.

## Root cause (code, không phải “build lệch ngẫu nhiên”)

File: `anki/victor/platform/switchboard/anki-wifi/wifi.cpp`  
Hàm: `DisableAccessPointMode()`

Patch WiFi từng gọi **không điều kiện**:

```cpp
ExecCommand({"/usr/bin/vic-setup-ap", "off"});
ExecCommand({"/anki/bin/vic-setup-ap", "off"});
```

Lúc boot, ConnMan/switchboard đi đường tắt AP → spam `vic-setup-ap off` (thấy 4 lần trong ~1s) đúng lúc `vic-anim` ↔ `vic-robot` mới nối radio → `HAL.RadioSendPacket.FailedToSend` → `RobotStateTimeout` → **fault 917** → `fault-code-handler` kill cả stack.

Overlay icon WiFi trên mặt (`faceDisplay.cpp`) **không** phải nguyên nhân lúc boot (chỉ vẽ khi có `/run/wireos-wifi-face`).

## Fix đã áp dụng

1. **Code:** chỉ gọi `vic-setup-ap off` khi WireOS AP đang bật:

```cpp
if (access("/run/wireos-setup-ap", F_OK) == 0) {
  (void)ExecCommand({"/usr/bin/vic-setup-ap", "off"});
  (void)ExecCommand({"/anki/bin/vic-setup-ap", "off"});
}
```

2. **Robot (deploy):** `vic-switchboard` bản đã vá + `vic-anim` stock (chưa bật lại overlay mặt). Join WiFi web vẫn qua **wired** + `vic-setup-ap` có flag.

## Quy tắc để không phạm lại

- **Không** gọi `vic-setup-ap on/off` từ đường ConnMan generic (`EnableAccessPointMode` / `DisableAccessPointMode`) trừ khi flag WireOS tồn tại (`/run/wireos-setup-ap`, hoặc tương đương rõ ràng).
- Open AP / hotspot setup: chỉ **wired** hoặc đường có chủ đích (`wifiWatcher` sau boot-wait), không xen vào boot “tắt AP mặc định”.
- Deploy WiFi switchboard: luôn build từ cây đã có gate trên; **không** scp lại binary WiFi cũ spam `vic-setup-ap off`.
- Overlay `vic-anim` (icon WiFi): deploy **tách** và chỉ sau khi smoke boot sạch.
- **Smoke sau mọi đổi WiFi binary:** cold reboot → 60s không spam `vic-setup-ap`, không `DisplayFaultCode: 917`, `vic-anim`/`vic-robot`/`vic-switchboard` active — rồi mới thử join web.

## Smoke commands

```bash
# trên robot sau reboot
journalctl -b --no-pager | grep -c 'vic-setup-ap'
journalctl -b --no-pager | grep -c 'DisplayFaultCode: 917'
systemctl is-active vic-anim vic-robot vic-switchboard
```

Kỳ vọng: `vic-setup-ap` = 0 (hoặc chỉ khi đang join/AP có chủ đích), `917` = 0 lúc boot idle.
