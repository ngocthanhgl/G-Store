# OpenCode Dev Environment Repair — Design

Date: 2026-10-22
Scope: global OpenCode config at `~/.config/opencode` (Windows: `C:\Users\material3\.config\opencode`)
Target install: OpenCode v2.1.2, Node 24.22.1, npm 12.0.2, git 2.54
Constraint from user: free only, no API keys / no paid services.

## Intent

Make the user's OpenCode install usable for everyday work on the `G Store` Android/Kotlin
project, plus documentation/knowledge work and Git/GitHub workflows. Current install has
four dead plugins and zero MCP servers; the goal is a working, verifiable baseline rather
than a maximal pile of packages.

## Findings (measured, not assumed)

1. `opencode plugin list` reports four plugins with no version:
   `caveman-opencode-plugin`, `exacontext7-opencode-plugins`, `opencode-acp`, `opencode-autotitle`.
   The log shows each failing with:
   `Plugin must export a default definition with an id and an effect or setup function`.
   Cause: these packages use the V1 plugin export (`export default async ({client}) => hooks`),
   while v2 plugins must default-export `{ id, setup | effect }`. This is an API break, not a
   config typo.
2. Working plugins: `opencode-mem` 2.33.0, `envsitter-guard` 0.1.0, `superpowers` (git).
3. `opencode mcp list` → `No MCP servers configured`. Zero MCP capability.
4. `~/.config/opencode/plugins` is empty; `~/.config/opencode/skills` does not exist.
   Skills currently come from built-ins plus `~/.claude/skills` (caveman family + cavecrew).
5. Toolchain present: `adb` at `E:\android\sdk\platform-tools\adb.exe`, `ANDROID_HOME=E:\android\sdk`,
   winget available. Missing: Python, uv, pnpm, bun, Chrome (Playwright will need Chromium download).
6. `~/.config/opencode/opencode.json` still uses the V1 `plugin` array and has no `mcp` block.

## Goals

- Every configured plugin loads without a warning.
- Useful, keyless MCP servers configured and connecting: filesystem, git, sequential-thinking,
  docs lookup, browser automation, Android/ADB control, GitHub.
- Android/Kotlin specific skills available to the agent.
- Reproducible verification command set and a documented rollback.

## Non-goals

- No Exa/Brave/Tavily/serena-style services that require a paid key.
- No rewriting of the four V1 plugins. Two are obsolete in V2, one needs a paid API, one
  (caveman) is already enforced by `AGENTS.md` + slash commands.
- No agent/model/provider changes, no theme changes, no session data migration.
- Not installing Python/uv in this pass (adds another runtime and a fragile `uvx` git dependency).

## Approaches considered

1. **V2-native cleanup + keyless MCP set** — drop dead V1 plugins, configure only keyless MCP
   servers, add Android skills. Chosen.
2. **Compatibility shim for all four V1 plugins** — possible only at the export boundary; V1
   hooks are shaped differently (`event`, `tool.execute.before`, `client.*`) and would need
   per-hook translation. High risk, low value: only `autotitle` has unique behavior and V2
   generates titles natively.
3. **Max package dump (Python MCPs, serena, pnpm, bun)** — largest surface, most failure modes,
   several servers needing tokens. Rejected; revisit on request.

User selected "Max: everything free" for capability breadth, and prioritised Android/Kotlin,
docs/memory, and Git/GitHub. This design satisfies that within free/keyless limits by choosing
the keyless maximum of *working* servers instead of the largest package count.

## Design

### 1. Config migration (`opencode.json`)

Backup to `opencode.json.bak-<timestamp>`, then:

- `plugin` → `plugins` (V2 field).
- Keep `caveman-opencode-plugin`? No: remove. Its behavior is duplicated by the global
  `AGENTS.md` hard rule and `commands/caveman*.md`. Removing it eliminates the load error.
- Remove `exacontext7-opencode-plugins` (requires Exa API key; violates no-paid-keys).
- Remove `opencode-acp` (V2 ships `opencode acp` as a CLI subcommand).
- Remove `opencode-autotitle` (V1-only; V2 writes session titles itself).
- Keep `opencode-mem`, `envsitter-guard`, `superpowers`.
- Keep `permission.external_directory: "allow"` untouched.

### 2. MCP servers (`mcp.servers`) — GLOBAL

User decision: everything global, so any repo opened anywhere benefits. Local servers via
`npx -y <pkg>@<pinned-version>`; pinned to avoid silent upgrades. All keyless.

| Name | Type | Package / URL | Why |
| --- | --- | --- | --- |
| `context7` | remote | `https://mcp.context7.com/mcp` | Latest library/API docs, version-specific snippets |
| `deepwiki` | remote | `https://mcp.deepwiki.com/mcp` | Repo-level knowledge: wiki, ask-about-codebase |
| `filesystem` | local | `@modelcontextprotocol/server-filesystem` 2.0.0, roots `C:\Users\material3` + `E:\` | File access for any project |
| `git` | local | `@cyanheads/git-mcp-server` 2.15.3, `MCP_TRANSPORT_TYPE=stdio`, no `GIT_BASE_DIR` | Repo ops in whatever cwd the session uses |
| `thinking` | local | `@modelcontextprotocol/server-sequential-thinking` 2.0.0 | Structured multi-step reasoning |
| `playwright` | local | `@playwright/mcp` 0.0.83, `--headless` | Browser automation; needs Chromium install |
| `android` | local | `android-mcp-server` 1.3.0, `ANDROID_HOME=E:\android\sdk` | Device/emulator screenshots, taps, logcat |

Notes:
- `GIT_BASE_DIR` is intentionally omitted: setting it to one project would break every other repo.
- `filesystem` keeps explicit roots (home + E:) instead of the whole disk.
- Skip `github` MCP: `gh` is installed but not authenticated, and `api.githubcopilot.com/mcp/`
  may require a Copilot entitlement. Git workflow uses `git` MCP + shell + `gh` once the user logs in.
- Skip `server-memory`: `opencode-mem` plus the built-in memory tool already cover it.
- Skip `chrome-devtools-mcp`: no Chrome installed, and Playwright already covers browser work.
- Skip Python-based MCPs (`mcp-server-fetch`, `mcp-server-time`, serena): no Python/uv on this
  machine; built-in `webfetch`/`websearch` cover the fetch role.
- `codemode` stays default (`true`) to keep the tool list small.
- `mcp.timeout.startup` raised to 60s because `npx` cold start on Windows is slow.

### 3. Skills (`~/.config/opencode/skills/`)

Create three project-tailored skills, all free, no external fetch:

- `android-build` — Gradle wrapper usage, SDK/API level checks, assemble/lint/test, common
  AGP/Kotlin failures on this machine.
- `apk-inspect` — `aapt2`/`apkanalyzer`/`unzip` inspection: manifest, ABIs, signing cert,
  min/target SDK; never trusts filenames.
- `android-emulator` — list/create/boot AVDs, `adb` logcat, install/launch flows via the
  `android` MCP server, plus manual fallback.

Existing caveman, superpowers, archify, graphify, impeccable, ponytail remain untouched.

### 4. Browser runtime

Install Chromium once for Playwright: `npx -y playwright@latest install chromium`.
If download is blocked, disable the `playwright` server instead of leaving a broken one.

### 5. Verification

```powershell
opencode plugin list                 # no "-" versions, no warnings
opencode mcp list                    # every server connected or explicitly pending auth
opencode api get /api/info           # service healthy
Select-String $env:LOCALAPPDATA\..\.local\share\opencode\log\opencode.log -Pattern 'failed to load plugin|MCP'
```

Plus one real call per critical server (filesystem list, git status, android device list) to
prove tools execute, not merely connect.

### 6. Rollback

Keep `opencode.json.bak-<timestamp>`; restore it and `opencode service restart`.
Skill folders and Chromium download are additive and safe to delete.

## Risks

- `npx` cold start on Windows can exceed the 30s startup timeout; raise `mcp.timeout.startup`
  to 60s for local servers if `mcp list` shows startup failures.
- `android-mcp-server` depends on `sharp` native binaries; if it fails to start, drop it and
  keep manual `adb` via the shell.
- `github` remote MCP may require interactive OAuth; it stays config-only until the user signs in.
- MCP tools consume context. `codemode: true` (default) keeps the native tool list small.
- Config field renames (`plugin` → `plugins`) must not drop unrelated settings; the backup and
  a diff review guard this.

## Acceptance criteria

1. `opencode plugin list` shows no load failures.
2. `opencode mcp list` shows the configured servers, each `connected` or awaiting user sign-in.
3. At least filesystem, git, and android tools execute a real call successfully.
4. New skills are discoverable by ID.
5. Rollback file exists and is documented.

## Verification results (2026-10-22)

```
opencode mcp list
✓ android     connected
✓ context7    connected
✓ deepwiki    connected
✓ filesystem  connected
✓ git         connected
✓ playwright  connected
✓ thinking    connected

opencode plugin list
envsitter-guard  0.1.0   envsitter-guard
opencode-mem     2.33.0  opencode-mem
superpowers      2ce8aa1  superpowers@git+https://github.com/obra/superpowers.git
```

Real calls executed, not just connections:

- `android.list_devices` → "No devices connected." (correct: no device/AVD attached)
- `filesystem.list_allowed_directories` → workspace root (server honors client MCP roots)
- `git.git_status` → branch `main`, upstream `origin/main`, untracked files listed
- `context7.resolve-library-id` → Jetpack Compose library IDs returned
- `deepwiki.read_wiki_structure` → `google/accompanist` page tree returned
- `playwright.browser_navigate` → `https://example.com`, title "Example Domain"
- `memory add` + `memory search` → memory persisted, retrieved with similarity 84

Deviations found during build:

1. `playwright` could not find Chrome. Fixed by installing Chromium and pinning
   `--executable-path C:\Users\material3\AppData\Local\ms-playwright\chromium-1248\chrome-win64\chrome.exe`.
2. `android` first connect hit the 60s startup cap during cold `npx` install; once cached it
   connects in ~7s. `mcp.timeout.startup` raised to 90s to survive future cold starts.
3. `filesystem` reports only the workspace root although two roots are configured: the server
   follows the client's MCP roots. Expected client behavior, not a config error. Native read/write
   still reach outside paths because `permission.external_directory` is `allow`.
4. Global servers start per location; the log shows connect attempts for every opened directory.
   Acceptable, but move `android`/`playwright` to project config if startup cost matters.
5. Added `.playwright-mcp/` to the project `.gitignore` (snapshot output).

## Rollback

```powershell
Copy-Item C:\Users\material3\.config\opencode\opencode.json.bak-20261022-101657 C:\Users\material3\.config\opencode\opencode.json -Force
Copy-Item C:\Users\material3\.config\opencode\opencode-mem.jsonc.bak-20261022-101657 C:\Users\material3\.config\opencode\opencode-mem.jsonc -Force
opencode reload
```

