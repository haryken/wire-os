# WireOS Build Guide

Single reference for AI agents and humans building WireOS services.

**Rules for this document**

- Commands marked **VERIFIED** were found in source/scripts/recipes or confirmed against artifacts in this checkout.
- Commands/paths marked **UNVERIFIED** were not confirmed end-to-end in this inspection.
- Do not invent alternate build commands. Prefer scripts listed here.

**Project root:** `~/Projects/wire-os`  
**Inspected branch (parent):** `xiaozhi-wireos-save-2026-07-14`

Related docs:

- Git/submodule workflow: [`docs/GIT_SUBMODULE_WORKFLOW.md`](GIT_SUBMODULE_WORKFLOW.md)

---

## 1. Build architecture

WireOS has **two layers**:

| Layer | What it builds | Entry point |
|-------|----------------|-------------|
| **Full OS / OTA (Yocto)** | Entire rootfs + Anki stack + packaging into `.ota` | `./build/build.sh` |
| **Standalone component builds** | One tree at a time (victor `/anki`, wired, vic-cloudless) | Per-repo `make` / `build-*.sh` |

### Submodules involved in build

| Path | Role |
|------|------|
| `anki/victor` | C++/CMake “personality” stack → `/anki` |
| `anki/wired` | Go web/settings service → `/usr/bin/wired` |
| `anki/vic-cloudless` | Go cloudless/Xiaozhi cloud → `/anki/bin/vic-cloud` |
| `poky/*` | OpenEmbedded/Yocto (community) |
| `external/purplpkg` | Packaging helper (community) |

**VERIFIED** — mapping from root `.gitmodules` and Yocto `EXTERNALSRC` in recipes under `poky/victor/meta-anki/recipes/`.

### Full OTA pipeline (high level)

**VERIFIED** from `build/build.sh` + `ota/Makefile` + `poky/build/conf/set_bb_env.sh`:

1. Enter Yocto env (`poky` + `set_bb_env.sh`).
2. `clean-<bottype>` then `build-<bottype>` → BitBake `machine-robot-image`.
3. Package OTA in `ota/` (`devsign` / `oskrsign` / `prodsign` + `make`).
4. Artifact under `_build/` (see naming notes below).

When to use full OTA vs hot-deploy:

| Need | Prefer |
|------|--------|
| New base OS, rootfs, many packages, version bump | Full Yocto OTA |
| Iterate `wired` UI/API only | Standalone wired + hot-deploy |
| Iterate Xiaozhi/cloudless only | Standalone vic-cloudless + hot-deploy |
| Iterate anim/engine/robot under `/anki` on an existing good OTA | Standalone victor build + **§8.1 binary scp** when possible; full deploy-v only if many files — then `vic-cloudless/deploy.sh` if cloudless |

Root README states victor standalone deploy is the usual day-to-day path when a modern base WireOS OTA is already on the robot (**VERIFIED** `README.md` “Development path”).

---

## 2. Full OS / OTA build

### Function

Builds the complete robot image and packages a flashable/OTA `.ota`.

### Build system

Yocto/OpenEmbedded (Poky) inside Docker image `vic-yocto-builder-7`, or bare metal with `-nd`.

### Can it be built alone?

Yes — this **is** the top-level build. Component recipes still compile `victor`, `wired`, `vic-cloud`/`vic-cloudless`, etc. as part of the image.

### Commands (canonical for this project)

**Đây là lệnh OTA mặc định cần dùng.** Không cần thêm flag khác trừ khi user yêu cầu rõ.

**VERIFIED** — `build/build.sh` accepts `-bt devcloudless`; sets `CLOUDLESS=1` via `set_bb_env.sh` `build-devcloudless`; installs `vic-cloudless` instead of C++ `vic-cloud` (`apq8009-anki-robot-image.inc`).

Từ root `wire-os`:

```bash
./build/build.sh -bt devcloudless -v 100
```

| Phần | Ý nghĩa |
|------|---------|
| `-bt devcloudless` | Image cloudless (Xiaozhi / `vic-cloudless`) |
| `-v 100` | Increment phiên bản → artifact `vicos-3.0.1.100d.ota` |

Đổi số sau `-v` khi muốn increment khác (0–9999). Ví dụ `-v 101` → `vicos-3.0.1.101d.ota`.

Agent/AI: khi user nói “build OTA” / “build full” / “build image” mà không chỉ định type khác → **chỉ chạy lệnh trên** (có thể đổi `-v` theo user).

### Output

| Item | Path / name |
|------|-------------|
| BitBake deploy images | `poky/build/tmp-glibc/deploy/images/apq8009-robot-robot-perf/` (**VERIFIED** `ota/Makefile`) |
| OTA output dir | `_build/` (**VERIFIED** `ota/Makefile`) |
| OTA với lệnh trên | `_build/vicos-3.0.1.100d.ota` (**VERIFIED** naming + on-disk example) |

**Discrepancy (document as found):** root `README.md` says `./_build/3.0.1.<increment>.ota`. Actual packaging + `upload.sh` + on-disk artifacts use `vicos-$(os-version).ota` with a `d` / `oskr` suffix from `anki-version.bb`. Prefer `vicos-*.ota` when looking for artifacts.

### Install on robot

Flash/update via OTA tooling (`update-engine` / Web Setup / `update-os`). Exact flash UX is outside these build scripts (**UNVERIFIED** end-user-end flash steps here).

### Hot-deploy?

No — full image is an OTA. Use component hot-deploy below for iteration.

### Helpers

| Script | Role | Status |
|--------|------|--------|
| `build/clean.sh` | BitBake cleanall helper | **VERIFIED** exists |
| `build/run.sh` / `build/shell.sh` | Docker one-shot / shell | **VERIFIED** exist; both source `build/deps.sh` which is **missing** in this checkout (**VERIFIED** absent) → treat run/shell as fragile until `deps.sh` restored |
| `build/upload.sh` | SCP OTAs to a server | **VERIFIED** usage in script header |
| `build/set.sh` | Bare-metal env exports | **VERIFIED** |

Clean rebuild advice (**VERIFIED** `README.md`):

```bash
sudo rm -rf poky/build/tmp-glibc poky/build/cache poky/build/sstate-cache poky/build/downloads
```

---

## 3. Victor (`anki/victor`)

### Function

Builds the Anki/WireOS `/anki` tree: animation, engine, robot process, switchboard, boot anim, update-engine, (optional C++ cloud), libs, resources.

### Build system

CMake + Ninja via `project/victor/build-victor.sh`, wrapped by:

- `./build/build-v.sh` (Docker / entry)
- `source setenv.sh` → alias `vbuild` → `victor_build_release` (bare metal)

Yocto recipe: `poky/victor/meta-anki/recipes/anki-robot/victor.bb` runs the same family of `victor_build_*.sh` scripts.

### Can it be built separately?

**Yes** — designed for standalone build + deploy onto a robot that already has a compatible base OTA (**VERIFIED** `anki/victor/README.md`).

Also built inside full Yocto when packaging OTA.

### Build commands

**VERIFIED** — `anki/victor/README.md`:

```bash
cd anki/victor
./build/build-v.sh          # Docker path (also used from macOS flow in README)

# Bare metal Linux
source setenv.sh
vbuild                      # alias → victor_build_release
```

**VERIFIED (WSL / no Docker, 2026-08-08):** `./build/build-v.sh` and `./build/deploy-v.sh` use `docker … -it` and fail when Docker Desktop WSL integration is off. Prefer bare-metal scripts **by path** (aliases from `setenv.sh` do **not** expand in non-interactive agent shells):

```bash
cd anki/victor
echo '192.168.x.x' > robot_ip.txt   # required for deploy; one IP per line
export PATH="$HOME/.local/bin:$PATH"  # ninja + ccache shim live here on this machine

# Fix stale absolute paths if CMake was configured when /usr/bin/{ninja,ccache} existed
sed -i "s|/usr/bin/ninja|$HOME/.local/bin/ninja|g" _build/vicos/Release/CMakeCache.txt 2>/dev/null || true
sed -i "s|/usr/bin/ccache|$HOME/.local/bin/ccache|g" \
  _build/vicos/Release/CMakeCache.txt \
  _build/vicos/Release/launch-c \
  _build/vicos/Release/launch-cxx 2>/dev/null || true

./project/victor/scripts/victor_build_release.sh
```

Notes (**VERIFIED** this workspace):

- `~/.local/bin/ninja` exists; CMake may still cache `CMAKE_MAKE_PROGRAM=/usr/bin/ninja` → configure fails until patched.
- `~/.local/bin/ccache` is a **passthrough shim** (`exec "$@"`), not real ccache. Generated `launch-c` / `launch-cxx` may hardcode `/usr/bin/ccache` (missing) → every compile fails until patched to `$HOME/.local/bin/ccache`.
- SSH key file: `anki/victor/robot_sshkey` (**VERIFIED** present). Add to agent before deploy: `eval $(ssh-agent) && ssh-add robot_sshkey`.

Clean (**VERIFIED** README):

```bash
./build/clean.sh            # or vclean after setenv.sh
```

### Output

**VERIFIED** — default build dir from `build-victor.sh` / `victor.bb`:

```
anki/victor/_build/vicos/Release/
```

Notable binaries under `…/bin/` (from recipe canned_fs_config / CMake targets — **VERIFIED** list in `victor.bb` / CMake):

- `vic-anim`, `vic-engine`, `vic-robot`, `vic-switchboard`, `vic-bootAnim`
- `vic-cloud` (C++; removed from package when `CLOUDLESS=1`)
- `update-engine`, `vic-dasmgr`, `vic-crashuploader`, `vic-log-uploader`, helpers/scripts

Staging for deploy uses `install.sh` → `_build/staging/…/anki/{bin,lib,etc,data}` (**VERIFIED** scripts under `project/victor/scripts/`).

### Install paths on robot

**VERIFIED** — staged/deployed under:

```
/anki/bin/*
/anki/lib/*
/anki/etc/*
/anki/data/*
```

Systemd units for individual services are separate tiny recipes (`vic-anim.bb`, `vic-engine.bb`, …) that install `.service` files only; binaries come from `victor` package.

### OTA vs binary replace

| Goal | Approach |
|------|----------|
| Ship in official image | Full Yocto OTA |
| Dev iterate on existing OTA | Standalone build + `deploy-v` / `vdeploy` (rsync to `/anki`) |

### Hot-deploy workflow

**VERIFIED** — `anki/victor/README.md` + `build/deploy-v.sh` / `project/victor/scripts/deploy.sh`:

```bash
cd anki/victor
./build/build-v.sh
./build/deploy-v.sh        # or: source setenv.sh && vdeploy
```

**VERIFIED (bare metal / agent, 2026-08-08)** — when Docker is unavailable:

```bash
cd anki/victor
echo '192.168.x.x' > robot_ip.txt
eval $(ssh-agent) && ssh-add robot_sshkey
export ANKI_ROBOT_HOST=$(cat robot_ip.txt)
./project/victor/scripts/victor_deploy_run.sh
# = stop → stage → rsync → restart
```

Notes from scripts (**VERIFIED**):

- Expects `robot_ip.txt` + `robot_sshkey` in the victor tree (**VERIFIED** key present; write IP before deploy).
- Stops `anki-robot.target`, rsyncs staged tree to robot (`rsync://…:1873/anki_root/` in deploy script).
- Checks `/etc/os-version` and `/etc/victor-compat-version` compatibility.

**CRITICAL — cloudless wipe (**VERIFIED** 2026-08-08 on robot):**  
Victor staging/`deploy.sh` rsync **deletes** files under `/anki` that are not in the victor stage — including:

- `/anki/data/assets/cozmo_resources/cloudless/` (Vosk models + `en-US.json`)
- `/anki/bin/xiaozhi-play-bridge.sh` (and related cloudless extras)

After **any** full victor hot-deploy onto a cloudless robot, **always** re-run:

```bash
cd anki/vic-cloudless
./deploy.sh <robot-ip>    # restores cloudless + bridge; restarts anki-robot.target (~70MB, often slow)
```

**Prefer avoiding the wipe:** for engine-only C++ changes use binary scp (§8.1) instead of full `victor_deploy_run`.

Verify engine picked up C++ changes:

```bash
ssh -i ~/ssh_root_key root@<ip> \
  'strings /anki/lib/libcozmo_engine.so | grep -F /data/wired/mods/Petting/touch_enabled'
```

---

## 4. Wired (`anki/wired`)

### Function

Go service for robot web UI / settings / games / OTA helpers on ports documented by the app (historically `:80` / `:8080`). Runs as `wired.service`.

### Build system

Go cross-compile to ARM.

| Path | Toolchain notes |
|------|-----------------|
| `Makefile` (used by Yocto `wired.bb`) | Go `~/.anki/go/dist/1.24.4`, SDK `5.3.0-r07`, tag `vicos` → `build/wired` |
| `build.sh` | SDK `4.0.0-r05`, system `go`, then `upx` |

**VERIFIED** both scripts/files as above. Prefer `make` for Yocto alignment.

**Caveat:** `Makefile` references `vector-gobot` (`libvector-gobot` target / CGO paths). That directory may be absent in a thin checkout (**VERIFIED** missing in this workspace).

**VERIFIED workaround (2026-08-08):** current `anki/wired` Go sources do **not** import `C` / gobot. When `vector-gobot/` is missing, cross-compile without CGO (static ARM binary still runs on robot):

```bash
cd anki/wired
mkdir -p build
CGO_ENABLED=0 GOARM=7 GOARCH=arm GOOS=linux \
  "$HOME/.anki/go/dist/1.24.4/go/bin/go" build -tags vicos -ldflags '-w -s' -o build/wired main.go
./send_to_bot.sh <robot-ip>
```

Prefer `make` when `vector-gobot` is restored (Yocto-aligned). Do not invent other official scripts.

### Can it be built separately?

**Yes.**

### Build commands

**VERIFIED** — `anki/wired/Makefile` (needs `vector-gobot`):

```bash
cd anki/wired
make
# output: build/wired
```

**VERIFIED** alternate script (different SDK; also needs `vector-gobot`):

```bash
cd anki/wired
./build.sh
```

**VERIFIED** no-gobot path: see CGO_ENABLED=0 command above.

### Output

```
anki/wired/build/wired
anki/wired/webroot/     # static UI (not produced by go build; source tree)
```

### Install paths on robot

**VERIFIED** — `poky/.../recipes/wired/wired.bb` + `wired.service`:

| Host | Robot |
|------|-------|
| `build/wired` | `/usr/bin/wired` |
| `webroot/` | `/etc/wired/webroot/` |
| unit | `systemd` → `wired.service` (`ExecStart=/usr/bin/logwrapper /usr/bin/wired`) |

### OTA vs binary replace

Binary + webroot hot-replace is enough for most wired work. Include in full OTA when shipping a complete image.

### Hot-deploy workflow

**VERIFIED** — `anki/wired/send_to_bot.sh`:

```bash
cd anki/wired
make                    # or ./build.sh
./send_to_bot.sh <robot-ip>
```

Script steps:

1. `systemctl stop wired` + `mount -o rw,remount /`
2. `scp build/wired` → `/usr/bin/`
3. `scp -r webroot/*` → `/etc/wired/webroot/`
4. `systemctl start wired`

SSH key: `~/ssh_root_key` (**VERIFIED** in script; on this machine it is a symlink to `anki/vic-cloudless/ssh_root_key`).

Quick check after deploy:

```bash
ssh -i ~/ssh_root_key root@<ip> \
  'systemctl is-active wired; curl -s http://127.0.0.1:8080/api/mods/Petting/get'
```

---

## 5. Vic-cloudless (`anki/vic-cloudless`)

### Function

Go “cloudless” stack providing `/anki/bin/vic-cloud` (Xiaozhi / local voice path, etc.), replacing the C++ `vic-cloud` when `CLOUDLESS=1`.

### Build system

`Makefile` → target `vic-cloud`: toolchain `5.3.0-r07`, voskopus, Go tags `nolibopusfile,vicos` → `build/vic-cloud`.  
Deps: `./get-deps.sh` (downloads SDK into `~/.anki/vicos-sdk/…`).

### Can it be built separately?

**Yes** (Linux only per README).

### Build commands

**VERIFIED** — `anki/vic-cloudless/README.md` + `Makefile`:

```bash
cd anki/vic-cloudless
./get-deps.sh           # if toolchain missing
make                    # builds build/vic-cloud (+ en-US assets as Makefile requires)
```

### Output

**VERIFIED** — `Makefile` / `deploy.sh` / `vic-cloudless.bb`:

```
anki/vic-cloudless/build/vic-cloud
anki/vic-cloudless/build/lib*          # e.g. vosk/opus libs when present
anki/vic-cloudless/build/en-US/        # Vosk model + intent JSON (Makefile downloads)
```

### Install paths on robot

**VERIFIED** — `vic-cloudless.bb` `do_install` + `deploy.sh`:

| Artifact | Robot path |
|----------|------------|
| `build/vic-*` | `/anki/bin/` (primarily `vic-cloud`) |
| `build/lib*` | `/anki/lib/` |
| `build/en-US` | `/anki/data/assets/cozmo_resources/cloudless/` |
| `extra/cloud.sudoers` | `/etc/sudoers.d/cloud` |
| `extra/setfreq` | `/usr/sbin/setfreq` |
| `vic-cloud.service` | systemd (`ExecStart=… /anki/bin/vic-cloud`) |

**Image selection** (**VERIFIED** `apq8009-anki-robot-image.inc`):

- `CLOUDLESS=1` → package `vic-cloudless`
- else → package `vic-cloud` (C++ from victor)

When cloudless, `victor.bb` removes `/anki/bin/vic-cloud` from the victor package so the Go binary wins (**VERIFIED** `victor.bb`).

### OTA vs binary replace

Hot-deploy is enough for cloud/Xiaozhi iteration on a cloudless-capable base. Full OTA needed to change whether the image is cloudless by default.

### Hot-deploy workflow

**VERIFIED** — `README.md` + `deploy.sh`:

```bash
cd anki/vic-cloudless
make
./deploy.sh <robot-ip>
```

`deploy.sh` also deploys `xiaozhi-play-bridge` unit/script and restarts `anki-robot.target`.  
Note: bridge files are in `deploy.sh` but **not** in `vic-cloudless.bb` `do_install` (**VERIFIED** gap) — OTA image may lack bridge unless added elsewhere (**UNVERIFIED** whether another recipe installs it).

**Must re-run after victor hot-deploy** — see §3 “cloudless wipe”. Existing `build/vic-cloud` + `build/en-US/` can be redeployed without rebuilding if artifacts are already present (**VERIFIED** 2026-08-08).

---

## 6. Other services / packages

These are mostly **Yocto unit wrappers** or small utilities. Binaries for the Anki stack usually come from `victor` unless noted.

| Component | Role | Build alone? | Notes |
|-----------|------|--------------|-------|
| `vic-anim` / `vic-engine` / `vic-robot` / `vic-switchboard` / `vic-bootAnim` | systemd units | No separate Go/CMake project | Units from `*.bb`; bins from `victor` → `/anki/bin/…` (**VERIFIED**) |
| `vic-cloud` (C++) | Classic cloud binary | Via `victor` or image without CLOUDLESS | `/anki/bin/vic-cloud` |
| `update-engine` | OTA client | Via `victor` | `/anki/bin/update-engine` + `anki-update-engine.bb` units |
| `vic-dasmgr` / crash / log uploaders | Support daemons | Via `victor` | `/anki/bin/…` |
| `fault-code` | Fault handler | Separate recipe `fault-code_git.bb` | Installs `/usr/bin/fault-code-handler`; unit text mentions `/bin/…` (**VERIFIED** path mismatch in sources) |
| `update-os` | Helper script | `update-os.bb` | `/usr/sbin/update-os` |
| `anki-version` | `/etc/os-version*` | Recipe only | Version string + `d`/`oskr` suffix (**VERIFIED** `anki-version.bb`) |
| `wired` | See §4 | Yes | |
| `vic-cloudless` | See §5 | Yes | |

Building a single `vic-anim` binary without victor’s CMake graph is **UNVERIFIED** / not supported by a dedicated script — use victor build.

---

## 7. When Yocto is required

Use full Yocto/OTA when you need any of:

- Rootfs, kernel/boot packaging, or non-`/anki` system packages
- Changing default cloud vs cloudless image composition
- Shipping a versioned `vicos-*.ota` for Web Setup / update-engine
- Ensuring service units + permissions + `canned_fs_config` match production image

Reasons victor/wired/cloudless still appear in Yocto even though they have standalone builds:

- Image composition, users/groups, sudoers, and install paths are defined in `.bb` files
- OTA signing/packaging only exists in `ota/Makefile` after BitBake deploy images

---

## 8. Practical hot-deploy cheat sheet

Assuming robot reachable by SSH (key `~/ssh_root_key` for wired/cloudless scripts) and a compatible base OTA.

**Example robot used 2026-08-08:** `192.168.100.46` (**VERIFIED** end-to-end).

### 8.0 Choose the lightest path first (**VERIFIED** lesson 2026-08-08)

Full `victor_deploy_run` + `vic-cloudless/deploy.sh` is **slow** (often many minutes): rsync walks a huge `/anki` tree, **deletes cloudless**, then re-uploads ~70 MB Vosk models. Agents must **not** use that path for small edits.

| What changed | Preferred deploy | Avoid |
|--------------|------------------|--------|
| Only `anki/wired/webroot/**` (HTML/JS/CSS/i18n) | `scp` files → `/etc/wired/webroot/` (no binary rebuild) | `send_to_bot.sh`, victor, cloudless |
| Wired Go API/mod only | Build `build/wired` + `./send_to_bot.sh <ip>` | victor / cloudless |
| Only engine C++ → `libcozmo_engine.so` (and/or `vic-engine`) | Build victor, then **binary scp** + restart service (§8.1) | Full `victor_deploy_run` (wipes cloudless) |
| Only `vic-anim` binary | scp `bin/vic-anim` + `systemctl restart vic-anim` | Full victor rsync unless resources also changed |
| Many `/anki` files, resources, or unsure | Full `victor_deploy_run` **then** `vic-cloudless/deploy.sh` | Skipping cloudless restore on cloudless robots |
| Only Xiaozhi / `vic-cloud` / Vosk JSON | `vic-cloudless` `make` (if needed) + `./deploy.sh <ip>` | victor |

**Rule of thumb:** if cloudless on the robot is already healthy and you only need one/two binaries under `/anki/bin` or `/anki/lib`, use **scp + restart** — do **not** full-rsync victor.

### 8.1 Fast engine-only deploy (no cloudless wipe)

**VERIFIED** pattern after `victor_build_release` (from repo root or adjust paths):

```bash
IP=192.168.x.x
KEY=~/ssh_root_key
ENG=anki/victor/_build/vicos/Release

ssh -i "$KEY" root@$IP 'mount -o rw,remount /'
# Typical engine C++ change:
scp -i "$KEY" "$ENG/lib/libcozmo_engine.so" root@$IP:/anki/lib/
scp -i "$KEY" "$ENG/bin/vic-engine" root@$IP:/anki/bin/   # if the executable changed
ssh -i "$KEY" root@$IP 'systemctl restart vic-engine'
# If anim also rebuilt: scp bin/vic-anim + systemctl restart vic-anim
```

Optional lib after cmake install step: `_build/vicos/Release/dist/lib/libcozmo_engine.so` (**VERIFIED**).

Verify without full deploy:

```bash
ssh -i "$KEY" root@$IP \
  'strings /anki/lib/libcozmo_engine.so | grep -F /data/wired/mods/Petting/touch_enabled'
```

### 8.2 Fast wired UI-only deploy

```bash
IP=192.168.x.x
KEY=~/ssh_root_key
ssh -i "$KEY" root@$IP 'mount -o rw,remount /'
scp -i "$KEY" -r anki/wired/webroot/* root@$IP:/etc/wired/webroot/
# No wired restart required for static files (browser hard-refresh).
# Restart wired only if the Go binary/API changed (`send_to_bot.sh`).
```

### 8.3 Full stack commands (when actually needed)

```bash
# --- Wired UI + binary ---
cd anki/wired
CGO_ENABLED=0 GOARM=7 GOARCH=arm GOOS=linux \
  "$HOME/.anki/go/dist/1.24.4/go/bin/go" build -tags vicos -ldflags '-w -s' -o build/wired main.go
./send_to_bot.sh 192.168.x.x

# --- Victor full /anki (wipes cloudless!) — bare metal ---
cd anki/victor
echo '192.168.x.x' > robot_ip.txt
export PATH="$HOME/.local/bin:$PATH"
./project/victor/scripts/victor_build_release.sh
eval $(ssh-agent) && ssh-add robot_sshkey
export ANKI_ROBOT_HOST=$(cat robot_ip.txt)
./project/victor/scripts/victor_deploy_run.sh

# --- REQUIRED after full victor on cloudless robots (~70MB, often slow) ---
cd anki/vic-cloudless
./deploy.sh 192.168.x.x
```

Docker wrappers (when Docker WSL integration works):

```bash
cd anki/victor && ./build/build-v.sh && ./build/deploy-v.sh
# still re-run vic-cloudless deploy.sh afterward on cloudless bots
```

After **full** victor+cloudless deploy, typical checks:

```bash
ssh -i ~/ssh_root_key root@<ip> '
  systemctl is-active wired vic-engine vic-anim vic-cloud anki-robot.target
  test -f /anki/data/assets/cozmo_resources/cloudless/en-US/en-US.json && echo cloudless_ok
'
```

---

## 9. Known discrepancies / traps

1. **OTA filename:** README `3.0.1.<N>.ota` vs actual `vicos-3.0.1.<N>d.ota` (**VERIFIED**).
2. **`build/deps.sh` missing** but referenced by `build/run.sh` / `shell.sh` (**VERIFIED**).
3. **wired toolchain split:** `Makefile` = SDK 5.3.0-r07; `build.sh` = 4.0.0-r05 (**VERIFIED**).
4. **wired `vector-gobot`:** required by `Makefile`; often absent locally — use `CGO_ENABLED=0` cross-build (**VERIFIED** 2026-08-08).
5. **xiaozhi-play-bridge:** deployed by `deploy.sh`, not by `vic-cloudless.bb` install (**VERIFIED**).
6. **fault-code unit vs install path mismatch** (**VERIFIED** in sources).
7. **Victor Docker scripts need TTY/Docker:** `build-v.sh` / `deploy-v.sh` fail on WSL without Docker; use bare-metal scripts in §3 (**VERIFIED**).
8. **`setenv.sh` aliases:** `vbuild` / `vdeploy` do not work in non-interactive shells — call `project/victor/scripts/*.sh` directly (**VERIFIED**).
9. **ninja / ccache paths:** CMake may cache `/usr/bin/ninja` and generate `launch-*` with `/usr/bin/ccache` while only `$HOME/.local/bin/{ninja,ccache}` exist; ccache there is a passthrough shim (**VERIFIED**).
10. **Victor rsync wipes cloudless:** full victor hot-deploy deletes `/anki/.../cloudless` and bridge extras — follow with `vic-cloudless/deploy.sh` (**VERIFIED** 2026-08-08). Prefer §8.1 binary scp when only engine/anim changed to **avoid** the wipe and the slow ~70 MB restore.
11. **Slow deploy anti-pattern:** running full victor + cloudless for a webroot typo or single `.so` change wastes minutes; use §8.0 table (**VERIFIED** lesson).

---

## AI Quick Reference

| User asks… | Read section |
|------------|--------------|
| “build wired” / deploy wired / web UI | §4 + **§8.0 / §8.2** (UI-only = scp webroot) |
| “build victor” / deploy `/anki` / anim/engine | §3 + **§8.0 / §8.1** first; full §8.3 only if needed |
| “build vic-cloudless” / Xiaozhi cloud / vic-cloud Go | §5 Vic-cloudless (+ §8) |
| “build OTA” / full image / Yocto / `vicos-*.ota` | §2 → chạy `./build/build.sh -bt devcloudless -v 100` |
| “build service vic-anim / vic-engine / …” | §6 (bins from Victor) + §3 + **§8.1** |
| “hot deploy” / replace binary only | **§8.0 decision table** |
| “cloudless image” | §2 (cùng lệnh OTA mặc định) + §5 |
| “where is the OTA file?” | §2 → `_build/vicos-3.0.1.100d.ota` (hoặc `.<N>d.ota` theo `-v`) |
| “deploy chậm / wipe cloudless” | §8.0–§8.1 + trap §9.10–11 |
| Git commit/push of build changes | `docs/GIT_SUBMODULE_WORKFLOW.md` |

**Agent checklist before running a build/deploy**

1. Confirm which component the user wants (wired / victor / cloudless / full OTA).  
2. **Pick the lightest deploy from §8.0** before starting any full rsync.  
3. Full OTA → **only** `./build/build.sh -bt devcloudless -v 100` (đổi `-v` nếu user chỉ định).  
4. Open the matching section; prefer **VERIFIED** commands.  
5. Check toolchain/SDK presence (`~/.anki/vicos-sdk`, Go dist) before `make`.  
6. If doing **full** victor deploy to a cloudless robot → plan `vic-cloudless/deploy.sh` (slow); if only `.so`/one binary → §8.1 instead.  
7. On WSL without Docker → bare-metal victor scripts + fix ninja/ccache paths (§3).  
8. Do not claim a command works if marked **UNVERIFIED**.  
9. After code changes, follow `docs/GIT_SUBMODULE_WORKFLOW.md` for commit/push order.
