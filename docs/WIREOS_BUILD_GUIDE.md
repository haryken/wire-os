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
| Iterate anim/engine/robot under `/anki` on an existing good OTA | Standalone victor + deploy-v |

Root README states victor standalone deploy is the usual day-to-day path when a modern base WireOS OTA is already on the robot (**VERIFIED** `README.md` “Development path”).

---

## 2. Full OS / OTA build

### Function

Builds the complete robot image and packages a flashable/OTA `.ota`.

### Build system

Yocto/OpenEmbedded (Poky) inside Docker image `vic-yocto-builder-7`, or bare metal with `-nd`.

### Can it be built alone?

Yes — this **is** the top-level build. Component recipes still compile `victor`, `wired`, `vic-cloud`/`vic-cloudless`, etc. as part of the image.

### Commands

**VERIFIED** — `README.md` + `build/build.sh`:

```bash
# From wire-os root, Docker (recommended in README)
./build/build.sh -bt dev -v <build-increment>

# Bare metal
./build/build.sh -nd -bt dev -v <build-increment>
```

**VERIFIED** flags documented in `README.md`:

| Flag | Meaning |
|------|---------|
| `-bt <dev\|oskr>` | Build type (README). Script also accepts `prod` / `devcloudless` (**VERIFIED** in `build/build.sh`, not fully documented in README flags section). |
| `-v <0-9999>` | Version increment |
| `-bp <password>` | Boot signing password (not required for dev) |
| `-nd` | No Docker |
| `-ui <…>` | BitBake UI |

**VERIFIED** — cloudless image path via bot type:

```bash
./build/build.sh -bt devcloudless -v <N>
```

Sets `CLOUDLESS=1` so image installs `vic-cloudless` instead of C++ `vic-cloud` (**VERIFIED** `apq8009-anki-robot-image.inc` + `set_bb_env.sh` `build-devcloudless`).

### Output

| Item | Path / name |
|------|-------------|
| BitBake deploy images | `poky/build/tmp-glibc/deploy/images/apq8009-robot-robot-perf/` (**VERIFIED** `ota/Makefile`) |
| OTA output dir | `_build/` (**VERIFIED** `ota/Makefile`) |
| Observed OTA name | `_build/vicos-3.0.1.<N>d.ota` for dev (**VERIFIED** files present under `_build/`, e.g. `vicos-3.0.1.100d.ota`) |

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

Notes from scripts (**VERIFIED**):

- Expects robot IP/key files (`robot_ip.txt`, `robot_sshkey` in victor tree — exact local setup **UNVERIFIED** on this machine).
- Stops `anki-robot.target`, rsyncs staged tree to robot (`rsync://…:1873/anki_root/` in deploy script).
- Checks `/etc/os-version` and `/etc/victor-compat-version` compatibility.

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

**Caveat:** `Makefile` references `vector-gobot` (`libvector-gobot` target / CGO paths). That directory may be absent in a thin checkout (**VERIFIED** missing in this workspace at time of writing). If `make` fails for that reason, fix/restore `vector-gobot` rather than inventing a new official script.

### Can it be built separately?

**Yes.**

### Build commands

**VERIFIED** — `anki/wired/Makefile`:

```bash
cd anki/wired
make
# output: build/wired
```

**VERIFIED** alternate script (different SDK):

```bash
cd anki/wired
./build.sh
```

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

SSH key: `~/ssh_root_key` (**VERIFIED** in script).

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

Assuming robot reachable by SSH (key `~/ssh_root_key` for wired/cloudless scripts) and a compatible base OTA:

```bash
# Wired UI / games / settings
cd anki/wired && make && ./send_to_bot.sh 192.168.x.x          # VERIFIED script

# Vic-cloudless / Xiaozhi cloud binary
cd anki/vic-cloudless && make && ./deploy.sh 192.168.x.x       # VERIFIED script

# Full /anki personality stack
cd anki/victor && ./build/build-v.sh && ./build/deploy-v.sh    # VERIFIED README/scripts
```

After deploy, typical checks (**UNVERIFIED** as mandatory, but commonly used):

```bash
ssh -i ~/ssh_root_key root@<ip> 'systemctl is-active wired vic-cloud anki-robot.target'
```

---

## 9. Known discrepancies / traps

1. **OTA filename:** README `3.0.1.<N>.ota` vs actual `vicos-3.0.1.<N>d.ota` (**VERIFIED**).
2. **`build/deps.sh` missing** but referenced by `build/run.sh` / `shell.sh` (**VERIFIED**).
3. **wired toolchain split:** `Makefile` = SDK 5.3.0-r07; `build.sh` = 4.0.0-r05 (**VERIFIED**).
4. **wired `vector-gobot`:** required by `Makefile`; may be absent locally (**VERIFIED** absence in this tree).
5. **xiaozhi-play-bridge:** deployed by `deploy.sh`, not by `vic-cloudless.bb` install (**VERIFIED**).
6. **fault-code unit vs install path mismatch** (**VERIFIED** in sources).

---

## AI Quick Reference

| User asks… | Read section |
|------------|--------------|
| “build wired” / deploy wired / web UI | §4 Wired (+ §8 hot-deploy) |
| “build victor” / deploy `/anki` / anim/engine | §3 Victor (+ §8) |
| “build vic-cloudless” / Xiaozhi cloud / vic-cloud Go | §5 Vic-cloudless (+ §8) |
| “build OTA” / full image / Yocto / `vicos-*.ota` | §2 Full OS / OTA (+ §7) |
| “build service vic-anim / vic-engine / …” | §6 (bins from Victor) + §3 |
| “hot deploy” / replace binary only | §8 + matching component section |
| “cloudless image” | §2 (`-bt devcloudless`) + §5 |
| “where is the OTA file?” | §2 Output (`_build/vicos-*.ota`) |
| Git commit/push of build changes | `docs/GIT_SUBMODULE_WORKFLOW.md` |

**Agent checklist before running a build**

1. Confirm which component the user wants (wired / victor / cloudless / full OTA).  
2. Open the matching section; prefer **VERIFIED** commands.  
3. Check toolchain/SDK presence (`~/.anki/vicos-sdk`, Go dist) before `make`.  
4. For hot-deploy, confirm robot IP + SSH key; remount/rootfs implications are in the scripts.  
5. Do not claim a command works if marked **UNVERIFIED**.  
6. After code changes, follow `docs/GIT_SUBMODULE_WORKFLOW.md` for commit/push order.
