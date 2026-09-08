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

## Incident 2026-09-08: AP không bật nhưng vẫn fault 917

### Dấu hiệu và cách phân biệt

Robot đã nối WiFi nhà (`State = online`, có IP LAN trên `wlan0`), không có
`192.168.4.1`, không có `/run/wireos-setup-ap`, `hostapd` hay `dnsmasq`, nhưng
vẫn có:

```text
HAL.RadioSendPacket.FailedToSend
CozmoBot.Radio.Disconnected
AnimProcessMessages.Update.RobotStateTimeout
DisplayFaultCode: 917
```

Tên `Radio` ở đây là Unix datagram IPC nội bộ giữa `vic-robot` và `vic-anim`
(`/dev/socket/_anim_robot_*`), không phải radio WiFi.

### Nguyên nhân hồi quy

Phần menu WiFi/hotspot đã gọi `RebuildMainMenu()` từ mỗi tick của
`FaceInfoScreenManager::Update()`. Dù có cache một giây, đường này vẫn định kỳ:

- đọc mode từ `/run`, `/data` và `/persist`;
- hỏi trạng thái SSID/IP;
- chạy trên thread realtime của `vic-anim`.

Bản không có hotspot không đưa I/O này vào vòng animation. Khi flash hoặc truy
cập trạng thái bị chậm lúc boot, `vic-anim` không xử lý `RobotState` kịp. Timeout
hai giây cũ ngắt IPC và lập tức phát fault 917.

### Fix 2026-09-08

1. Không gọi `RebuildMainMenu()` mỗi animation tick. Menu được dựng khi vào màn
   hình Main và trong nhịp redraw 20 giây đã có sẵn.
2. `wired` đọc mode bền vững từ `/data`/`persist`, rồi mirror sang
   `/run/wireos-wifi-setup-mode` (tmpfs).
3. `vic-anim` chỉ đọc mirror `/run`; không đọc flash trong đường render.
4. Timeout `RobotState` tăng từ 2 lên 5 giây. Nếu socket mất, `vic-anim` thử
   reconnect mỗi 0,5 giây; chỉ phát 917 sau 15 giây thất bại liên tục.
5. `fault-code-handler` chờ FIFO 5 giây thay vì 1 giây và chấp nhận dữ liệu đã
   đọc trước EOF. Điều này tránh handler bỏ mã 917 rồi để robot kẹt.

### Gate bật hotspot khi boot

Không bật AP chỉ vì mode đã lưu là `hotspot`:

- Có WiFi nhà và IP LAN: luôn giữ AP tắt.
- Đã association nhưng chờ DHCP: chờ tối đa 75 giây, không bật AP.
- Có profile WiFi đã lưu: cho ConnMan tối đa 120 giây tự nối.
- Chỉ sau các kiểm tra trên, khi thực sự offline, mới gọi `vic-setup-ap on`.

### Quy tắc realtime bắt buộc

- Không đọc `/data`, `/persist`, chạy `curl`, `system()` đồng bộ hoặc gọi
  ConnMan từ hàm chạy mỗi tick của `vic-anim`.
- Dữ liệu từ service khác phải mirror qua `/run` hoặc truyền bằng IPC; phần
  persist thuộc process nền như `wired`.
- Mọi thay đổi WiFi/menu mặt phải smoke test lâu hơn cửa sổ lỗi cũ (ít nhất
  3 phút), không chỉ kiểm tra service vừa lên.
- Khi thấy `HAL.RadioSendPacket`, kiểm tra đường IPC trước; không mặc định kết
  luận đó là WiFi/hotspot.

## Smoke commands bổ sung

```bash
connmanctl state
ip -4 addr show wlan0
test -f /run/wireos-setup-ap && echo AP_ON || echo AP_OFF
cat /run/wireos-wifi-setup-mode

journalctl -b --no-pager | \
  grep -E 'RadioSendPacket.Failed|RobotStateTimeout|RobotReconnected|DisplayFaultCode: 917'

systemctl is-active wired anki-robot.target vic-robot vic-anim vic-engine vic-cloud
```

Khi WiFi nhà đã kết nối: kỳ vọng `AP_OFF`, tất cả service `active`, và không có
`RobotStateTimeout`/917. `RobotReconnected` chỉ được phép xuất hiện khi IPC thật
sự bị gián đoạn và phải tự phục hồi mà không restart toàn stack.
