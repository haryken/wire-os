# WireOS Git Submodule Workflow

This document is the source of truth for how AI agents and humans must commit/push WireOS code. Read it before any commit/push work in this project.

## Project root

```
~/Projects/wire-os
```

## Required development branch

All WireOS-managed repositories use:

```
xiaozhi-wireos-save-2026-07-14
```

Do **not** checkout `main`/`master`, merge into them, rebase onto them, reset the branch, create PRs against them, or force-push — unless the user explicitly asks.

Git stores submodule pointers as **commit SHAs**, not branch names. Using the same branch name across repos is for human/agent workflow clarity.

---

## 1. Repository structure (actual)

Inspected from the live checkout. Prefer this over assumptions.

### Parent

| Repo | Path | Origin |
|------|------|--------|
| wire-os | `.` (repo root) | `git@github.com:haryken/wire-os.git` |

### WireOS / Vector repos (owned by haryken)

```
wire-os
├── anki/victor
│   └── EXTERNALS          (nested submodule)
├── anki/wired
└── anki/vic-cloudless
```

| Path | Origin |
|------|--------|
| `anki/victor` | `git@github.com:haryken/wire-os-victor.git` |
| `anki/victor/EXTERNALS` | `git@github.com:haryken/wire-os-externals.git` |
| `anki/wired` | `git@github.com:haryken/wired.git` |
| `anki/vic-cloudless` | `git@github.com:haryken/vic-cloudless.git` |

Configured in:

- Root `.gitmodules` — `anki/victor`, `anki/wired`, `anki/vic-cloudless`, plus community deps
- `anki/victor/.gitmodules` — nested `EXTERNALS`

### Community / upstream dependencies

| Path | Origin (keep as-is) |
|------|---------------------|
| `external/purplpkg` | `https://github.com/purpl-org/purplpkg` |
| `poky/bitbake` | `https://github.com/openembedded/bitbake.git` |
| `poky/meta-openembedded` | `https://github.com/openembedded/meta-openembedded` |
| `poky/openembedded-core` | `https://github.com/openembedded/openembedded-core.git` |

Rules for community deps:

- Do **not** change URL, remote, branch, or commit when the user only asked to commit/push WireOS.
- If they have local changes: **report first**. Do not discard, reset, clean, or commit unless explicitly requested.

Note: community submodules often sit on detached SHAs (no local branch name). That is normal.

### Untracked top-level dirs (not part of this submodule map)

The parent may show untracked directories such as `wirepodxiaozhi/` or `xiaozhi-esp32/`. Do not treat them as WireOS submodules unless the user asks to add them.

---

## 2. How submodules work

The parent repo does **not** store the full source tree of a submodule.

It stores only a **gitlink**: a commit SHA (pointer) for that path.

Examples:

```
wire-os
  anki/victor      -> SHA of wire-os-victor

wire-os-victor
  EXTERNALS       -> SHA of wire-os-externals
```

Therefore:

1. Commit and **push** the child repo first.
2. Then update and commit the parent pointer.
3. Then push the parent.

If the parent points at a child SHA that was never pushed, `git submodule update` on another machine will fail.

When the parent shows:

```
modified: anki/victor (new commits)
```

that usually means the checked-out victor SHA differs from the pointer recorded in wire-os — **not** that victor’s files are uncommitted. After victor is pushed, stage the pointer with `git add anki/victor`.

---

## 3. Mandatory commit / push order (inside → outside)

### If `EXTERNALS` changed

1. `anki/victor/EXTERNALS` — status → commit → push  
2. `anki/victor` — `git add EXTERNALS` → commit → push  
3. `wire-os` — `git add anki/victor` → commit → push  

### If `wired` changed

1. `anki/wired` — commit → push  
2. `wire-os` — `git add anki/wired` → commit → push  

### If `vic-cloudless` changed

1. `anki/vic-cloudless` — commit → push  
2. `wire-os` — `git add anki/vic-cloudless` → commit → push  

### If several WireOS children changed

```
EXTERNALS (if any)
  → victor
  → wired
  → vic-cloudless
  → wire-os
```

`wired` and `vic-cloudless` do not depend on each other; order between them does not matter.  
**`wire-os` is always last** when any submodule pointer changes.

---

## 4. When the user says “commit push” (or similar)

Phrases such as: *commit push*, *push code*, *save code lên git*, *commit tất cả*, *đẩy code lên github*.

**Do not** immediately `git add .` in the parent.

### Inspect first

Parent:

```bash
git status
git branch --show-current
git remote -v
```

All submodules:

```bash
git submodule foreach --recursive 'echo "===== $displaypath ====="; git status --short; echo'
git submodule foreach --recursive 'echo "===== $displaypath ====="; git branch --show-current; echo'
git submodule foreach --recursive 'echo "$displaypath -> $(git remote get-url origin 2>/dev/null)"'
```

Then decide which repos actually need commits.

---

## 5. Staging rules

Do not blindly use `git add .` without inspecting files.

Classify changes:

| Category | Action |
|----------|--------|
| Source / config / needed assets | May stage if part of the work |
| Submodule pointer | Stage only after child is committed **and** pushed |
| Binary / build artifact | Report; only stage if the project intentionally tracks it |
| Temp / debug / secrets / large unknowns | Ask the user |

Never delete files just because they look like build artifacts.

---

## 6. Branch rules

Required branch for WireOS-managed repos:

```
xiaozhi-wireos-save-2026-07-14
```

Applies to: `wire-os`, `anki/victor`, `anki/victor/EXTERNALS`, `anki/wired`, `anki/vic-cloudless`.

If any WireOS repo is on a different branch: **stop and report**. Do not auto-checkout.

---

## 7. Push target and safety

Always push:

```
xiaozhi-wireos-save-2026-07-14
```

```bash
# first time / no upstream
git push -u origin xiaozhi-wireos-save-2026-07-14

# already tracking
git push
```

**Never:** push `main`/`master`, merge into them, open a PR (unless asked), or force-push.

Before pushing a parent pointer commit, confirm the child’s new SHA exists on its remote.

Order of safety:

```
EXTERNALS remote has new commit
  → then victor may commit EXTERNALS pointer
victor remote has new commit
  → then wire-os may commit victor pointer
(same for wired / vic-cloudless)
```

Avoid parent commits that point at SHAs that exist only locally.

---

## 8. `.gitmodules`

WireOS-owned submodule URLs must remain the haryken forks listed above.  
Community deps keep their upstream URLs.

Do **not** edit `.gitmodules` during a normal commit/push request unless:

- the change is already in the working tree and belongs in that commit, or  
- the user explicitly asked to change it.

Never change submodule `path` values.

---

## 9. Large files / LFS

If push shows `Uploading LFS objects` or involves large binaries:

- Do not interrupt unless it is clearly stuck/failed.
- Report which files use LFS.
- Do not remove LFS or rewrite history on your own.
- On file-size failures: report the error and propose options before any history rewrite.

---

## 10. Final verification checklist

After each commit/push workflow:

```bash
git status -sb
git submodule foreach --recursive 'echo "===== $displaypath ====="; git status -sb; echo'
```

Report:

- which repos were committed (with SHAs)
- which repos were pushed (branch + remote)
- which submodule pointers were updated
- remaining uncommitted files
- repos still ahead of remote
- push/LFS errors
- repos that are not clean

---

## 11. Forbidden unless explicitly requested

- `checkout` main/master  
- merge / rebase / `reset --hard`  
- `git clean` / restore / discard code  
- force push  
- deleting files  
- changing community dependency remotes/commits  
- changing remotes without request  
- committing secrets/tokens/passwords  
- staging files outside the inspected scope  
- editing application source when the user only asked for Git workflow  

---

## 12. Agent procedure summary

On every WireOS commit/push request:

1. Read this file and `.cursor/rules/wire-os-git-workflow.mdc`.  
2. Inspect parent + all submodules.  
3. Identify changed WireOS repos (and report community dirtiness).  
4. Commit/push deepest child → outward → parent last.  
5. Always use branch `xiaozhi-wireos-save-2026-07-14`.  
6. Never push main/master; never force-push.  
7. Report the full result.
