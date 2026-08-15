# Syscon DFU, sạc, và quá nhiệt — đọc trước khi đụng body firmware

**Ngày ghi:** 2026-08-15  
**Đối tượng:** Vector production / OSKR kiểu **C2Y1** (SWD nhà máy đã tắt).  
**Nhánh làm việc:** `xiaozhi-wireos-save-2026-07-14`

Tài liệu này giải thích **vì sao robot ngừng sạc khi ấm**, **chỗ nào phải sửa**, **cách build/flash DFU đúng**, và **những việc không được làm** vì có thể **brick thân máy** (syscon). Không phải hướng dẫn crack key, không phải hướng dẫn vá binary.

---

## 1. Mục tiêu / không phải mục tiêu

Mục tiêu WireOS đã thống nhất:

| Muốn | Không muốn |
|------|------------|
| Đặt lên dock → **sạc ngay** | Cắt sạc chỉ vì thân ấm ~41°C |
| Pin đầy vẫn **ngừng sạc** | Sạc vô hạn sau khi đã no |
| Chỉ **nóng cực đoan** mới tắt máy | Tắt hẳn bảo vệ nhiệt 60/70°C |
| Không mặt **nhiệt kế** / ngủ chờ nguội | “Luôn sạc” nghĩa là bỏ luôn pin-full |

**Không** biến robot thành cục sạc không ngắt. Pin Li-ion đầy (~4.075 V + thêm ~5 phút) vẫn phải cắt. `handleTemperature()` 47°C dài / 60°C ~30 s / 70°C tắt ngay **phải giữ**.

---

## 2. Hai lớp riêng — đây là chỗ dễ hiểu nhầm

Vector có **hai máy tính**:

```
Đầu (AP / Linux)                         Thân (STM32 / syscon)
vic-engine, wired, vic-cloud             analog.cpp, sạc, motor, nhiệt thân
deploy vic-engine / OTA userland         CHỈ đổi bằng DFU syscon đã ký
```

| Việc | Chạy ở đâu | Deploy bằng gì | Có cắt mạch sạc không |
|------|------------|----------------|------------------------|
| Mặt nhiệt kế, `ConditionTooHotToCharge`, ngủ chờ nguội | **Engine (đầu)** | `vic-engine` / OTA | **Không.** Chỉ UI + hành vi. |
| Cắt MOSFET sạc khi “too hot” ~41°C | **Syscon (thân)** | `syscon.dfu` đã **ký production** | **Có.** Đây mới là sạc thật. |
| Tắt máy khi 60/70°C, pin thấp | **Syscon** | cùng DFU | Có (an toàn, giữ lại). |
| Pin đầy 4.075 V + 5 phút | **Syscon** | cùng DFU | Có (an toàn, giữ lại). |

Kết luận: **sửa engine không làm robot sạc lại** nếu thân vẫn cắt ở 41°C. Deploy `vic-engine` xong mà vẫn không sạc → đúng như thiết kế, không phải “deploy hỏng”.

Yocto **không biên dịch** `analog.cpp`. Image OTA chỉ **nhét sẵn** file `syscon.dfu` vào initramfs rồi `rampost` đẩy sang thân lúc boot.

---

## 3. Stock Anki: vì sao dock mà không sạc

File: `anki/victor/robot/syscon/src/analog.cpp` (bản **HEAD git**, chưa tính patch local).

Trong giây đầu tiên trên dock (`on_charger_time < 1 s`):

- Nếu nhiệt thân **> 41°C** (Xray: 41+5 = **46°C**) → `too_hot = true`
- `prevent_charge = too_hot || disable_charger` → **cắt mạch sạc**
- Khi đang `prevent_charge`, bộ đếm `on_charger_time` **không tăng** → điều kiện “giây đầu” **kẹt mãi** cho đến khi nhiệt **≤ 41°C**

Đó là latch: robot ấm (rất dễ sau khi chạy) → đặt lên dock → không sạc → nằm chờ nguội. Đầu thấy flag `POWER_CHARGER_OVERHEAT` → mặt nhiệt kế / `IsChargingStalledBecauseTooHot()`.

Nhiệt “thật sự nguy hiểm” nằm chỗ khác, **không** dùng 41°C:

| Ngưỡng syscon (`handleTemperature`) | Việc |
|-------------------------------------|------|
| &lt; 47°C | An toàn, giảm bộ đếm nóng |
| ≥ 47°C lâu (~4 h) / ≥ 50°C (~2 h) | Báo alarm; có thể hẹn tắt |
| ≥ 60°C | Coi như cháy, tắt máy ~30 s |
| ≥ 70°C (không phải Whiskey) | Tắt **ngay** |

CPU đầu ≥ ~90°C (`ConditionHighTemperature`) là **lớp engine khác**, không cắt MOSFET sạc.

Pin đầy (giữ nguyên, kể cả khi bỏ 41°C):

- `BATTERY_FULL_VOLTAGE` = ADC ~ **4.075 V**
- `CHARGE_FULL_TIME` = **5 phút** sau khi đã no
- `charge_cutoff = prevent_charge || max_charge_time_expired`

---

## 4. Patch trên git (nhánh `xiaozhi-wireos-save-2026-07-14`) — chưa phải firmware trên robot

Nguồn đã commit; **máy thật vẫn stock** cho đến khi flash/deploy:

1. `anki/victor/robot/syscon/src/analog.cpp`  
   - `too_hot = false`  
   - `prevent_charge = disable_charger` thôi  
   - **Vẫn** gọi `handleTemperature()` (60/70°C)  
   - **Vẫn** cắt khi pin đầy  
   - **Chưa chạy trên robot** cho đến DFU production đã ký  

2. `anki/victor/engine/components/battery/batteryComponent.h`  
   - `IsChargingStalledBecauseTooHot()` → `return false`  
   - Chỉ tắt mặt nhiệt kế / vòng sleep-until-cool  
   - **Không** khôi phục sạc nếu syscon vẫn cắt  
   - **Chưa chạy trên robot** cho đến khi build + deploy `vic-engine`  

Cho đến khi có **DFU ký production** và flash thành công, robot C2Y1 vẫn chạy syscon **stock trong blob OTA**. `vic-engine` trên máy cũng vẫn bản cũ nếu chưa deploy.

---

## 5. File DFU trên firmware / OTA

| Vai trò | Đường dẫn |
|---------|-----------|
| Nguồn C++ thân | `anki/victor/robot/syscon/src/analog.cpp` |
| Bootloader thân (xác thực chữ ký) | `anki/victor/robot/syscon/boot/main.cpp`, `boot/comms.cpp` |
| Ký image | `anki/victor/robot/syscon/tools/sign.py` + `cert.py` |
| Key production (AES, **cần password**) | `anki/victor/robot/syscon/tools/victorSysConSigningKeyPvt.pem` |
| Key **dev** (không password, **C2Y1 từ chối**) | `anki/victor/robot/syscon/tools/development.pem` |
| Build Keil | `anki/victor/robot/vmake.sh` → VM `anki-vm-keil`, `UV4.exe` |
| AfterMake ký | `syscon.uvprojx`: `sign.py -k ./tools/victorSysConSigningKeyPvt.pem -b build/syscon.dfu …` |
| Flash qua mạng (sau khi đã có DFU **đúng chữ ký**) | `anki/victor/robot/dfu.sh -s ROBOT_IP` |
| Host DFU trên robot | `anki/victor/robot/hal/dfu/dfu.c` |
| Blob OTA / initramfs | Recipe: `poky/victor/meta-vicos/recipes-core/initscript-anki/` |
| Boot đầu đẩy DFU | `files/init-boot.sh` → `rampost -d syscon.dfu` |
| File blob | `…/initscript-anki/files/syscon.dfu` (recipe `SRC_URI`; checkout này **có thể không có file** — LFS / chưa copy) |

Bản OTA Anki gốc từng dùng version kiểu `Ws00000009c263e` (~2017-10-20). **Không** suy ra “sửa analog.cpp rồi bitbake là ra DFU mới”.

`winbuild.bat` gọi `sign.py` **không** `-k` → mặc định `development.pem` → **không** dùng cho C2Y1.

---

## 6. DFU layout và chữ ký (để hiểu vì sao không được vá file)

Bộ nhớ app syscon: `0x08002000`, header `0x110` byte, vùng ký `COZMO_APPLICATION_SIZE - 0x110` = **`0xDEF0` byte** bắt đầu tại `APP->signedStart`.

Bootloader (`boot/main.cpp`):

```text
verify_cert(&APP->signedStart, 0xDEF0, APP->certificate, 256)
```

File `syscon.dfu` (do `sign.py`):

| Offset file | Nội dung | Ghi chú |
|-------------|----------|---------|
| `0x00`–`0x0F` | Version ASCII (`Ws…`) | Host đọc để so version; **không** phải fingerprint trên flash |
| `0x10`–`0x10F` | RSA-PSS certificate 256 byte | Khớp key **production** trên bootloader C2Y1 |
| từ `signedStart` | Payload (gồm code `analog.cpp`) | **Nằm trong vùng ký** |

Host `dfu.c`: đọc 16 byte version → `PAYLOAD_ERASE` → ghi phần còn lại từ `APP->certificate` → `PAYLOAD_VALIDATE`.

`PAYLOAD_VALIDATE` (`boot/comms.cpp`):

1. Nếu cert **sai** → **`Flash::eraseApplication()`** → NACK  
2. Nếu cert **đúng** → mới ghi fingerprint `C2MO` và chạy app  

Sửa một byte trong payload (ví dụ “vá 41°C” trong DFU có sẵn) **làm hỏng RSA**. Lần flash đó **xóa app đang chạy rồi mới kiểm tra** → cert fail → app bị xóa. Thân không còn firmware ứng dụng.

---

## 7. Cách làm ĐÚNG (khi đã có password)

Thứ tự bắt buộc:

1. **Giữ** cắt pin đầy và `handleTemperature()` 60/70°C. Chỉ bỏ latch 41°C.  
2. Build syscon bằng **Keil** (`vmake.sh` / `UV4.exe`), không dùng gcc Yocto.  
3. AfterMake: `sign.py -k victorSysConSigningKeyPvt.pem` → hộp thoại wx hỏi **password**.  
4. Kiểm tra output `anki/victor/robot/syscon/build/syscon.dfu` (và bản `syscon-Ws….dfu`).  
5. **Cất bản DFU stock đang chạy** (copy từ robot / OTA cũ) trước khi flash — đó là đường lui.  
6. Flash **một** robot thử: `./dfu.sh -s ROBOT_IP` (script stop `anki-robot.target`, scp, chạy `dfu`).  
7. Xác nhận: dock khi thân ấm vẫn sạc; pin đầy vẫn ngắt; không brick.  
8. Mới được thay `initscript-anki/files/syscon.dfu` rồi build OTA. File sai chữ ký trong initramfs = **mỗi lần boot** `rampost` có thể xóa app thân.

Cần có:

- Máy/VM Keil (`anki-vm-keil`, user `nathan` trong `vmake.sh`)  
- `HOSTNAME` của máy dev (rsync path)  
- Password của `victorSysConSigningKeyPvt.pem` (người giữ key Anki/WireOS)  
- GUI cho `wx.TextEntryDialog` trên máy chạy `sign.py`

**Chưa có password / chưa có Keil → dừng. Không flash.**

---

## 8. CẤM — dễ brick, không làm

Production C2Y1: **SWD tắt**. Không JTAG để cứu bootloader. Bootloader còn sống thì vẫn DFU được image **ký đúng**. Bootloader chết / sai → hết đường.

### 8.1 Không bao giờ

- Hex-edit / patch `syscon.dfu` có sẵn (đổi analog, version, cert, padding).  
- Ký bằng `development.pem` rồi flash C2Y1.  
- Flash DFU **DVT / Whiskey / skip-verify / unsigned**. Header `whiskeyCompatible` sai → `boot_test()` fail → **xóa app** rồi ngồi recovery.  
- Flash **sysboot** (bootloader) trừ khi có quy trình nhà máy + đường lui SWD — **C2Y1 không có**.  
- Nhét DFU chưa kiểm tra vào `initscript-anki/files/syscon.dfu` rồi OTA hàng loạt.  
- Tắt hẳn `handleTemperature()` / sạc vượt 4.075 V “cho luôn luôn sạc”.  
- Brute-force / crack password PEM. Key AES-128-CBC; không có password thì **không ký được production**.  
- `dfu.sh` khi chưa chắc file là bản **production-signed** vừa tự build.

### 8.2 Brick theo tầng

| Làm gì | Thường gặp | Có cứu được không |
|--------|------------|-------------------|
| DFU cert sai | App bị xóa, bootloader chờ DFU | **Có**, flash lại DFU **ký đúng** (`dfu.sh` / `rampost`) |
| Image whiskey / fingerprint / cert không khớp lúc boot | `boot_test()` xóa app, `Comms::run()` recovery | **Có**, cùng cách |
| DFU **đúng chữ ký** nhưng logic sạc/nhiệt sai | Robot chạy, hành vi nguy hiểm | Cứu bằng DFU stock đã cất |
| Ghi đè **bootloader** / SWD skipverify lên prod | Không vào DFU app được | **C2Y1: coi như mất thân** |
| OTA chứa DFU xấu | Mỗi boot `rampost -d syscon.dfu` | Đừng để robot reboot vòng DFU xấu; cần image OTA cũ / DFU tốt từ LAN |

Dấu hiệu recovery: `dfu -v` / version `-----Erased-----`, thân không sạc / không motor, đầu Linux vẫn SSH được.

### 8.3 Việc “an toàn giả”

| Việc | Kết quả thật |
|------|----------------|
| Chỉ `return false` ở engine | Mất mặt nhiệt kế; **vẫn không sạc** nếu thân cắt |
| Sửa `analog.cpp` rồi `victor_deploy` / scp engine | **Không** vào STM32 |
| Bitbake không thay `syscon.dfu` | Robot vẫn blob 2017 |
| Vá DFU rồi “thử một con” | Đúng lúc nguy hiểm nhất: erase-then-validate |

---

## 9. Checklist trước khi gõ `dfu.sh`

- [ ] Robot là C2Y1/OSKR production, không nhầm DVT.  
- [ ] File là `syscon.dfu` **vừa ký** bằng `victorSysConSigningKeyPvt.pem` + password đúng, không phải `development.pem`.  
- [ ] Đã copy DFU **đang chạy** ra chỗ an toàn.  
- [ ] Diff `analog.cpp` chỉ bỏ latch 41°C; còn pin-full + 60/70°C.  
- [ ] Không flash `sysboot`.  
- [ ] Chỉ một robot, người đứng cạnh, SSH còn sống.  
- [ ] Sau flash: `dfu -v` version mới; dock ấm vẫn sạc; pin đầy ngắt.

Không đủ ô trên → **không flash**.

---

## 10. Việc được làm ngay (không cần DFU)

- Engine: tắt mặt nhiệt kế (`IsChargingStalledBecauseTooHot` = false) — chỉ UI.  
- Quan sát: dock khi ấm, log `POWER_CHARGER_OVERHEAT`, điện áp, `IsCharging()`.  
- Giữ patch `analog.cpp` trên git **nhưng không flash** cho đến khi có DFU ký.  
- Không full `victor_deploy_run` / `vic-cloudless/deploy.sh` chỉ vì việc này (wipe unrelated).

---

## 11. Công thức nhớ

```text
Sạc MOSFET     = syscon DFU đã ký production
Mặt nhiệt kế   = vic-engine
41°C latch     = analog.cpp stock (thân)
60/70°C tắt máy = analog.cpp handleTemperature (giữ)
Pin đầy        = 4.075 V + 5 phút (giữ)
Password PEM   = bắt buộc để ký C2Y1
development.pem / vá DFU / whiskey / sysboot = brick
```

Hết password hoặc hết Keil thì **dừng ở engine + tài liệu này**. Không improvisation trên flash thân.
