# sbt-js

Plugins providing a consistent Scala.js + JS toolchain baseline (assembly, dev workflow with Vite, build & release helpers).

All listed plugins are `AutoPlugin`s unless noted; enable by adding them with a normal `addSbtPlugin` line (they trigger automatically when prerequisites are present).

## Modules

### sbt-vite

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-vite" % "@VERSION@")
```

Depends on: [`sbt-js`](#sbt-js-core)

Integrates [Vite](https://vitejs.dev/) for local development and production builds. Provides deterministic, fingerprinted dev server management (auto‑restart on config change), safe single-instance concurrency, robust shutdown, and build failure propagation.

#### Dev server lifecycle & fingerprint

The `vite` task maintains a single long‑running dev server process. A SHA‑1 fingerprint is computed from:

* Resolved dev server CLI parameters (only keys you explicitly set)
* The assembled asset staging directory path
* The resolved Vite mode (`development` / `production`)

If any component changes between invocations the existing process is terminated and a new one is started transparently. Re‑invoking `vite` with no effective configuration change is a cheap no‑op (log message: "already running").

#### PATH augmentation

The effective `PATH` (or `Path` on Windows) passed to the Vite process is augmented with each discovered `node_modules/.bin` directory from:

* The assembled JS staging directory
* The current project base directory
* The root project base directory

The augmentation is de‑duplicated and memoized for the lifetime of the sbt session to avoid repeated string processing.

Key tasks & settings:

| Key | Type | Description | Notes |
|-----|------|-------------|-------|
| `vite` | Task[Unit] | Starts (or reuses) the Vite dev server; configuration changes trigger transparent restart. | Single instance via task tag. |
| `viteStop` | Task[Unit] | Stops the running dev server (if any). | Gracefully terminates process tree. |
| `viteBuild` | Task[File] | Runs `vite build` (production) and fails fast on non‑zero exit. | Output: `jsAssemble/target/dist`. |
| `vite.base` / `vite.config` / `vite.force` ... | Setting | Direct mappings to Vite CLI flags (e.g. `--base`, `--config`). | Only flags you set are passed. |
| `vite.mode` | Setting | Vite mode (`development` / `production`). | Derived from `jsFullLink` (`NODE_ENV=production` => production). |
| `vite.port`, `vite.host`, `vite.strictPort`, `vite.cors` | Setting | Dev server networking flags. | Changes included in restart fingerprint. |
| `vite.assetsDir`, `vite.assetsInlineLimit`, `vite.ssr`, `vite.sourcemap`, `vite.minify` | Setting | Build‑only flags. | Applied to `viteBuild`. |

### sbt-js-core

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-js" % "<version>")
```

Provides Scala.js stage selection plus incremental assembly of static resources and linker output (fast or full link chosen automatically from environment). Assembly copies only changed files and prunes stale ones.

Key tasks & settings:

| Key | Type | Description | Notes |
|-----|------|-------------|-------|
| `jsAssemble` | Task[File] | Incrementally assembles static JS/resources + Scala.js linker output into a staging directory. | Auto depends on correct Scala.js stage. |
| `jsFullLink` | Setting[Boolean] | Chooses full (optimized) or fast linker output. | `true` iff `NODE_ENV=production` (case‑insensitive). |
| `jsSource` | Setting[File] | Primary static resource root. | Defaults: `(Compile / sourceDirectory)/js`. |
| `js / sourceDirectories` | Setting[Seq[File]] | All static resource roots. | You can append additional directories. |
| `js / target` | Setting[File] | Generic target for any user `js` task chaining. | Provided; not modified by plugin code. |
| `jsAssemble / target` | Setting[File] | Staging directory for assembled assets. | Includes full/fast link output + static files. |
| `jsAssemble / fileInputIncludeFilter` / `fileInputExcludeFilter` | Setting[PathFilter] | Filters applied to static resource discovery. | Include defaults: recursive; exclude hidden + directories. |

Incremental assembly details:

* Uses `FileFunction.cached` to copy only changed or new files; preserves timestamps.
* Removes stale outputs no longer mapped (excluding any transient `node_modules` artifacts you might overlay separately).
* Dynamically selects correct linker output directory after forcing the appropriate Scala.js stage (`fastLinkJS` or `fullLinkJS`).
* Safe to run repeatedly; no redundant copying logged unless files change.

Environment influence:

* Set `NODE_ENV=production` before invoking sbt to trigger full optimization (`jsFullLink = true`).
* Otherwise fast link is used for faster dev cycles.

Typical workflow:

```text
sbt jsAssemble        # build staging assets (fast link by default)
sbt vite              # start dev server (auto restarts on setting changes)
sbt viteBuild         # production build (uses assembled staging dir)
```

Customization examples:

* Add more static roots: `js / sourceDirectories += (Compile / resourceDirectory).value / "web"`.
* Override assembly target: `jsAssemble / target := crossTarget.value / "custom-web"`.
* Force production locally: `jsFullLink := true` (overrides environment detection).

## License

Copyright © Shuwari Africa Ltd. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License at:

  [`http://www.apache.org/licenses/LICENSE-2.0`](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
