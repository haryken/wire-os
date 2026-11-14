# Vector AI tiếng Việt — Tài liệu tính năng (dùng làm brief website)

> Tài liệu này mô tả **tính năng người dùng** của robot **Vector** bản tùy biến (nói tiếng Việt, AI trên robot).  
> Dùng để đưa cho AI / designer làm trang giới thiệu sản phẩm.  
> Ưu tiên nhấn mạnh: **tiếng Việt**, **AI dùng miễn phí**, **vận hành trực tiếp trên robot**.

---

## Brief nhanh cho AI làm web

### Sản phẩm là gì
Robot **Vector** nói chuyện được bằng **tiếng Việt**, cấu hình qua **trang web trên chính robot** (không bắt buộc app điện thoại để chat). AI chạy **trên / với robot**: key API lưu trên robot, robot gọi thẳng nhà cung cấp hoặc cloud Xiaozhi — không phụ thuộc cloud Anki gốc.

### Ba thông điệp phải nổi trên hero / first viewport
1. **Tiếng Việt** — giao diện mặc định tiếng Việt; nói chuyện AI tiếng Việt; lệnh điều khiển bằng lời Việt.
2. **AI miễn phí** — có nhiều lộ trình chat AI **không mất tiền** (Xiaozhi, Gemini Live, Tự chọn AI Groq+Gemini+Edge, Vosk offline).
3. **Chạy trực tiếp trên robot** — cấu hình trên web robot; key chỉ lưu trên robot; không cần server riêng cho chế độ Direct API.

### Tone
Thân thiện, rõ ràng, hướng người dùng Việt Nam / gia đình / trẻ em học tiếng Anh. Tránh jargon kỹ thuật nặng; khi cần thì giải thích ngắn.

### Caveat nên ghi nhỏ (footer / FAQ)
- “Miễn phí” của Gemini / Groq vẫn phụ thuộc quota nhà cung cấp.
- OpenAI / Grok / Qwen là trả phí.
- Nhạc YouTube chỉ khi dùng Xiaozhi và cần server nhạc riêng.
- Vosk offline **không** phải chatbot — chỉ lệnh cứng (thường tiếng Anh).

---

## 1. Điểm khác Vector gốc (selling points)

| # | Điểm nổi | Mô tả ngắn |
|---|----------|------------|
| 1 | Chat AI trên robot | Nhiều backend; có lộ trình miễn phí |
| 2 | Tiếng Việt first-class | UI + giọng nói + lệnh thoại |
| 3 | Điều khiển bằng ngôn ngữ tự nhiên | Không chỉ cụm tiếng Anh cứng |
| 4 | Hội thoại liên tục | Nhiều chế độ mở mic (Liên tục, Kéo dài, Mở sẵn…) |
| 5 | Dạy tiếng Anh | Khóa học Explorers → IELTS/TOEIC qua Xiaozhi |
| 6 | Nhạc YouTube | Nói “mở bài…” (khi dùng Xiaozhi) |
| 7 | Chào khi thấy người | Tự bắt chuyện, không cần Hey Vector |
| 8 | Web UI đầy đủ | OTA, khuôn mặt, ảnh, game, điều khiển từ xa, hotspot |
| 9 | Cloudless / BYO-key | Không phụ thuộc Anki cloud gốc |
| 10 | Show vui | Pháo hoa, xe cứu hỏa, hát pool lớn… bằng lời Việt |

---

## 2. AI giao tiếp (trái tim sản phẩm)

Người dùng chọn **thẻ chế độ** trên trang web robot → bấm **Áp dụng**. Robot kết nối lại và có thể tự chào.

### 2.1 Lộ trình miễn phí (nhấn mạnh trên web)

#### Xiaozhi — ✅ Miễn phí, không cần key API
- Nói chuyện tự nhiên **tiếng Việt / tiếng Anh** với cloud Xiaozhi.
- AI hiểu ý và **điều khiển robot** (cube, blackjack, pháo hoa…).
- Preset sẵn:
  - **Cấu hình có sẵn (tiếng Việt)** — dùng ngay.
  - **Dạy tiếng Anh (khóa học)** — Explorers, Young Innovators, Future Leaders, IELTS, TOEIC; giọng song ngữ Anh–Việt hoặc chỉ Anh; chọn sách / unit / tên học sinh.
  - **Tự cấu hình** trên xiaozhi.me (tài khoản riêng).

#### Gemini Live — ✅ Miễn phí (key Gemini miễn phí, có giới hạn lượt/ngày)
- Nói chuyện **giọng nói ↔ giọng nói** trực tiếp với Google Gemini.
- Phản hồi dưới ~1 giây; mic tự dừng khi nói xong; nhớ hội thoại.
- Đa ngôn ngữ (Việt, Anh, Trung…).
- Điều khiển robot bằng lời nói.
- **Chạy trực tiếp trên robot** với key Gemini lưu trên robot.

#### Tự chọn AI (3 bước: Nghe → Suy nghĩ → Nói) — ✅ Có thể miễn phí hoàn toàn
- Chạy **thẳng trên robot**, không cần server riêng.
- Combo miễn phí khuyến nghị: **Groq (nghe) + Gemini (suy nghĩ) + Microsoft Edge (nói)**.
- Nhớ hội thoại + điều khiển robot bằng lời.
- Có thể đổi từng bước sang OpenAI (khi đó tính phí) hoặc máy chủ tự host (Ollama…).
- Giọng Edge: Emma đa ngôn ngữ; **Hoài My / Nam Minh** tiếng Việt chuẩn.

#### Vosk — ✅ Miễn phí, offline, không mạng
- Nhận lệnh thoại **trên robot**, không cần key.
- **Không** trò chuyện AI; chỉ cụm lệnh có sẵn (thường tiếng Anh).
- Phù hợp khi mất mạng hoặc muốn lệnh cứng.

### 2.2 Lộ trình trả phí (tuỳ chọn nâng cao)

| Chế độ | Chi phí (theo UI) | Điểm nổi |
|--------|-------------------|----------|
| **OpenAI Realtime** | Key OpenAI có credit, theo phút | Voice realtime, hiểu ý điều khiển robot |
| **Grok Voice** | ~0,08 USD/phút + phí tra web | Voice realtime, tự tra tin tức / thời tiết |
| **Qwen Live** | Key DashScope; tài khoản mới thường có quota tạm | Voice realtime, mạnh Việt / Trung / Anh |

### 2.3 “Vận hành trực tiếp trên robot” nghĩa là gì

- Mở trình duyệt → IP robot (cổng 80 / 8080; mic trình duyệt cần HTTPS 8443).
- Nhập API key trên web; key **chỉ lưu trên robot** (UI chỉ hiện vài ký tự cuối).
- Chế độ Direct API (Tự chọn AI / Gemini / OpenAI / Grok / Qwen): robot gọi thẳng nhà cung cấp — **không cần máy chủ trung gian**.
- Không bắt buộc app điện thoại để chat AI.

---

## 3. Tiếng Việt & đa ngôn ngữ

- **Giao diện web mặc định tiếng Việt**; thêm English, Italiano, 中文.
- Chat AI tiếng Việt (Xiaozhi, Gemini Live, pipeline Edge/Gemini, Grok, Qwen…).
- Lệnh thoại ví dụ: “lăn khối”, “về sạc”, “hát đi”, “xe cứu hỏa”, “chơi blackjack”, “bắn pháo hoa”…
- TTS Edge: Hoài My / Nam Minh (Việt chuẩn).
- Game Wordle VI, Treo cổ, Đố vui; bình luận bàn cờ bằng **Giọng Google tiếng Việt**.
- Khóa học tiếng Anh với tùy chọn **song ngữ Anh–Việt**.

---

## 4. Cách mở micro khi nói chuyện

Áp dụng mọi AI trừ Vosk:

| Chế độ | Ý nghĩa |
|--------|---------|
| **Liên tục** | Trả lời xong mở mic lại — chat không cần “Hey Vector” mỗi câu |
| **Đứng nghe** | Như Liên tục nhưng đứng yên lâu hơn |
| **Kéo dài** | Giữ mic cả khi robot đi lại |
| **Mở sẵn** | Ting + lưng xanh; mic sẵn trong khi robot vẫn đi |
| **Ngưỡng** | Như Mở sẵn + lọc ồn theo mức RMS |
| **Một lượt** | Một hỏi → một đáp → tắt; phải gọi lại |

Thêm tùy chọn **Gọi khi im lặng**: im một lúc thì robot tự hỏi han.  
Mic theo **giọng nói** (hết câu mới dừng) — nói dài không bị cắt cứng.

---

## 5. Điều khiển robot bằng lời nói

Wake: **Hey Vector** (hoặc từ tùy chỉnh Picovoice) / nút lưng.

### Di chuyển & tương tác
Lại đây, nhìn tôi, tiến / lùi / trái / phải / quay lại, cụng tay, nhảy (dance), hát (sing — nhiều bài), đi dạo, về sạc, đi ngủ.

### Khối (cube)
Lăn khối, bốc đầu (wheelie), nhặt / tìm / mang khối, keep away, làm trick.

### Ảnh & nhìn
Chụp ảnh lưu album; hỏi “bạn thấy gì” / phân tích ảnh (vision).

### Âm lượng · mắt · cảm xúc
To / nhỏ / đặt % / im; đổi màu mắt (teal, vàng, cầu vồng…); xin chào, tạm biệt, khen, bad robot, I love you; hỏi giờ, tuổi, tên.

### Show vui
- Pháo hoa / fireworks  
- Happy holidays  
- Xe cứu hỏa / còi xe (~12 giây + hiệu ứng)

### Pin
Hỏi pin / đang sạc chưa.

### Blackjack thoại
Chơi blackjack nhiều vòng bằng lời; hết game quay lại chat AI.

### Nhạc (Xiaozhi)
Nói **“mở bài …”** → tìm YouTube (qua server nhạc) → phát trên loa robot; Hey Vector để dừng.

---

## 6. Khuôn mặt & chào chủ động

- **Ghi / đổi tên / xóa** khuôn mặt trên web (nhìn vào Vector khi ghi; đèn lưng báo hiệu).
- **Chào khi thấy người**: thấy người → tự chào + bắt chuyện AI — **không cần Hey Vector**.
- Không chen khi đang chat / nhạc / game; khoảng cách chào cấu hình được.
- Mọi AI trừ Vosk; mặc định tắt (bật khi muốn).

---

## 7. WiFi · Hotspot · Cập nhật

### Mạng
- Khi mất WiFi: **Bluetooth (BLE)** setup hoặc **Hotspot mở** trên robot → vào `http://192.168.4.1/` chọn WiFi nhà.
- Portal hotspot vẫn mở được một phần cài đặt / AI / điều khiển.

### OTA (cập nhật OS)
- Kiểm tra bản cập nhật chính thức.
- Cập nhật từ **link** hoặc **file `.ota`**.
- Có tiến trình + log; lỗi giữ OS cũ; xong tự khởi động lại.
- Chỉ dùng OTA từ nhà phát triển.

---

## 8. Trang web trên robot — các khu vực chính

| Tab / khu vực | Nội dung |
|---------------|----------|
| **Cài đặt bot** | Âm lượng, màu mắt, hành vi mic, khuôn mặt, ảnh, vị trí/múi giờ, hiệu năng, wake engine, Alexa, pin trên trang, nhật ký… |
| **AI giao tiếp** | Chọn chế độ AI + cấu hình key / preset |
| **Lệnh thoại** | Danh sách cụm lệnh (đặc biệt hữu ích với Vosk) |
| **Điều khiển** | Pad lái, tay, đầu, camera, gương, micro 2 chiều; link điều khiển ngoài mạng (4G) |
| **Thời gian tự do** | Xem camera / cảm xúc khi robot freeplay (không chiếm quyền) |
| **Trò chơi** | Cờ vua, cờ tướng, caro, Othello, đam, vây, Sudoku, 2048, dò mìn, Wordle VI, Uno, Blackjack web… |
| **Cập nhật** | OTA chính thức / link / file |

### Cài đặt bot đáng chú ý
- Âm lượng hệ thống vs **âm lượng giao tiếp AI** (30% → 100% → boost).
- Màu mắt preset + tùy chỉnh.
- Engine đánh thức: Hey Vector mặc định hoặc **Picovoice** (từ tùy chỉnh).
- Hiệu năng CPU: Thường / Cân bằng / Cao.
- Liên kết **Alexa** (tùy chọn).

---

## 9. Trò chơi trên web

**Bàn cờ / board** (có thể bình luận robot, AI đọc được bàn khi bật):
Cờ vua, Cờ tướng, Cờ caro, Cờ thả cột, Cờ lật (Othello), Cờ đam, Cờ vây 9×9 — cấp độ Dễ / Trung bình / Khó.

**Khác:** Sudoku, 2048, Dò mìn, Lật thẻ, Bắn tàu, Wordle VI, Treo cổ, Đố vui, Đoán số, Simon, Uno, Blackjack web, Poker…

Blackjack **thoại trên mắt robot** là trải nghiệm riêng (không chỉ bàn web).

---

## 10. Gợi ý cấu trúc trang web giới thiệu

1. **Hero** — ảnh/video Vector + tên thương hiệu sản phẩm + 1 câu: *“Robot Vector nói tiếng Việt, AI miễn phí, chạy ngay trên robot.”* + CTA (mua / liên hệ / xem demo).
2. **3 cột lợi ích** — Tiếng Việt · AI miễn phí · Trên robot.
3. **So sánh chế độ AI** — bảng Miễn phí vs Trả phí (mục 2).
4. **Điều khiển bằng lời** — grid icon lệnh vui (hát, pháo hoa, về sạc…).
5. **Dạy tiếng Anh** — block riêng cho phụ huynh.
6. **Chào khi thấy người + khuôn mặt** — cảm xúc “thú cưng AI”.
7. **Game & nhạc** — vui cho cả nhà.
8. **Cài đặt dễ** — web trên robot, hotspot bắt WiFi, OTA.
9. **FAQ** — miễn phí thế nào? cần app không? cần mạng không? Vosk khác gì chatbot?
10. **CTA cuối**.

### Từ khóa / SEO gợi ý
`Vector tiếng Việt`, `robot AI tiếng Việt`, `Vector chat GPT`, `Gemini Live Vector`, `robot dạy tiếng Anh`, `Vector không cần app`

### Visual gợi ý
- Ảnh/video thật của Vector (mắt, lưng đèn, đế sạc).
- Screenshot UI tiếng Việt (thẻ Xiaozhi / Gemini Live / Tự chọn AI).
- Tránh dashboard dày; hero một composition, brand rõ.

---

## 11. FAQ ngắn (copy sẵn)

**Có cần app điện thoại không?**  
Không bắt buộc để chat AI. Cấu hình qua trình duyệt vào IP robot trên WiFi nhà.

**AI miễn phí thật không?**  
Có: Xiaozhi (không key), Gemini Live (key Gemini free + giới hạn), Tự chọn AI với Groq+Gemini+Edge, Vosk offline. Gemini/Groq vẫn theo quota nhà cung cấp.

**AI có chạy trên robot không?**  
Có — cấu hình và key nằm trên robot; Direct API robot gọi thẳng nhà cung cấp. Không cần server riêng.

**Nói tiếng Việt được không?**  
Được — UI mặc định tiếng Việt; chat và lệnh thoại tiếng Việt trên các chế độ AI chính.

**Mất mạng thì sao?**  
Dùng Vosk offline cho lệnh cứng; hoặc bật hotspot robot để cấu hình lại WiFi.

**Có dạy tiếng Anh không?**  
Có preset khóa học Xiaozhi (Explorers → IELTS/TOEIC), có chế độ song ngữ Anh–Việt.

---

## 12. Checklist nội dung bắt buộc trên web

- [ ] Nhấn mạnh **tiếng Việt**
- [ ] Nhấn mạnh **AI miễn phí** (liệt kê rõ 3–4 lộ trình free)
- [ ] Nhấn mạnh **chạy trực tiếp trên robot** / key trên robot / không cần server riêng
- [ ] Phân biệt rõ free vs paid (OpenAI, Grok, Qwen)
- [ ] Có block điều khiển bằng lời + show vui
- [ ] Có block dạy tiếng Anh
- [ ] Có FAQ quota / mạng / Vosk
- [ ] CTA rõ ràng

---

*Tài liệu nội bộ — cập nhật theo UI hiện tại (AI giao tiếp gồm Xiaozhi, Tự chọn AI, Gemini Live, OpenAI Realtime, Grok Voice, Qwen Live, Vosk).*
