# Xiaozhi UI: token field & unpair cleanup — 2026-08-01

## Verdict (ESP32 + WireOS)

| Item | Needed? | Where |
|------|---------|--------|
| **Token in config JSON** | **Yes** | WSS `Authorization: Bearer …` (ESP32 `websocket_protocol.cc`; WireOS same via OTA `websocket.token`) |
| **Token text field on :8080** | **No** | User never types it; OTA / Get Code / Refresh fill it |
| **Unpair on :8080** | **No** | Real unbind is on [xiaozhi.me](https://xiaozhi.me); local unpair only cleared JSON token |

ESP32 stores `websocket.token` from OTA into NVS automatically — no settings UI to paste a token.

## Done this change

- Remove Unpair button + help that pushed local unpair.
- Hide Token input; keep `token` in `xiaozhi.json` (Get Code / auto-apply OTA / boot `OtaCheck` still write it).
- Remove **Refresh** button from UI (redundant with Get Code + boot OTA; API `refresh` kept for debug).
- Mode = radio Xiaozhi ↔ Vosk; apply immediately (`set_enabled` + restart `vic-cloud`).
- Save only updates Xiaozhi config fields (not mode).

## Optional later (not required for UX)

1. Drop `unpair` / `refresh` HTTP endpoints in `wired/mods/xiaozhi.go` (or leave unused).
2. Stop accepting `token=` on `save` (only OTA paths write token).
3. Mask token in `get` JSON for the web (`token_set: true` instead of raw value) if we ever show status.
4. Do **not** delete the JSON field until cloud stops sending Authorization.
