# sbt-js

Collection of [sbt](https://scala-sbt.org) plugins for uniform configuration of Shuwari Africa Ltd. sbt projects, as well
as CI and Release related functionality.

_NB: Unless specified otherwise, all plugins listed below are sbt `AutoPlugins`, and will be enabled automatically upon meeting the required plugin dependencies for each._

The following plugins are available:
__________________________________

### sbt-vite

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-vite" % "@VERSION@")
```

| Depends On:            |
|------------------------|
| [sbt-js](#sbt-js-core) |

Preconfigures projects with opinionated project defaults for ScalaJS libraries and/or applications. Uses [Vite](https://vitejs.dev/) for bundling, and postprocessing.

Introduces additional sbt `SettingKeys` and `TaskKeys`, specifically:

| Key                      | Description                                                                                                   | Default                                                                                                             |
|--------------------------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `vite`                   | Task: Compiles, links, and prepares project for packaging and/or processing with external tools."             | _N/A_                                                                                                               |
| `viteBuild`              | Task: Executes `vite build` using the options specified in _sbt-vite_ plugin settings.                        | _N/A_                                                                                                               |
| `viteStop`               | Task: Shuts down any running instances of Vite's development server.                                          | _N/A_                                                                                                               |
| `vite.base`              | Setting: Option corresponding to Vite's `--base` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).              | `None`                                                                                                              |
| `vite.config`            | Setting: Option corresponding to Vite's `--config` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).            | `None`                                                                                                              |
| `vite.force`             | Setting: Option corresponding to Vite's `--force` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).             | `None`                                                                                                              |
| `vite.logLevel`          | Setting: Option corresponding to Vite's `--logLevel` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).          | `Level.Info`                                                                                                        |
| `vite.mode`              | Setting: Option corresponding to Vite's `--mode` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).              | ```if (JSBundlerPlugin.autoImport.jsFullLink.value) ViteImport.Mode.Production else ViteImport.Mode.Development ``` |
| `vite.host`              | Setting: Option corresponding to Vite's `--host` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).              | `None`                                                                                                              |
| `vite.port`              | Setting: Option corresponding to Vite's `--port` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).              | `None`                                                                                                              |
| `vite.strictPort`        | Setting: Option corresponding to Vite's `--strictPort` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).        | `None`                                                                                                              |
| `vite.cors`              | Setting: Option corresponding to Vite's `--cors` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).              | `None`                                                                                                              |
| `vite.assetsDir`         | Setting: Option corresponding to Vite's `--assetsDir` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).         | `None`                                                                                                              |
| `vite.assetsInlineLimit` | Setting: Option corresponding to Vite's `--assetsInlineLimit` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html). | `None`                                                                                                              |
| `vite.ssr`               | Setting: Option corresponding to Vite's `--ssr` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).               | `None`                                                                                                              |
| `vite.sourcemap`         | Setting: Option corresponding to Vite's `--sourcemap` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).         | `None`                                                                                                              |
| `vite.minify`            | Setting: Option corresponding to Vite's `--minify` setting. See [Vite documentation](https://vitejs.dev/guide/cli.html).            | `None`                                                                                                              |

__________________________________

### sbt-js-core

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-js" % "@VERSION@")
```

Preconfigures projects with opinionated project defaults for ScalaJS libraries and/or applications. Provides a foundation for incremental assembly using external Javascript ecosystem
tools.

Introduces additional sbt `SettingKeys` and `TaskKeys`, specifically relevant:

| Key                                  | Description                                                                                                                                            | Default                                                                                                      |
|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `jsPrepare`                          | Task: Compiles, links, and prepares project for packaging and/or processing with external tools."                                                      | _N/A_                                                                                                        |
| `jsFullLink`                         | Setting: Defines whether _"fullLink"_ or _"fastLink\"_ ScalaJS Linker output is used.                                                                  | `true` where `NODE_ENV` environment variable is defined with a value of `peoduction`. and `false` otherwise. |
| `js`                                 | Task: Process and/or package assembled project with external tools.                                                                                    | Unimplemented. To be customised by end-user.                                                                 |
| `js / sourceDirectory`               | Setting: Default directory containing sources and resources to be copied as-is to `jsPrepare / target` during `jsPrepare` execution.                   | `(Compile / sourceDirectory) / js`                                                                           |
| `js / sourceDirectories`             | Setting: List of all directories containing sources and resources to be copied as-is to `jsPrepare / target` during `jsPrepare` execution.             | `(Compile / sourceDirectory) / js`                                                                           |
| `js / target`                        | Setting: Defines a target directory for the `js` task. Usable if required.                                                                             | _N/A_                                                                                                        |
| `jsPrepare / target`                 | Setting: Defines a default target directory for the `jsPrepare` task.                                                                                  | _N/A_                                                                                                        |
| `jsPrepare / fileInputIncludeFilter` | Setting: An sbt `sbt.nio.file.PathFilter` inclusion filter to apply to the input sources and resources copied to the prepared assembly by `jsPrepare`. | `RecursiveGlob`                                                                                              |
| `jsPrepare / fileInputExcludeFilter` | Setting: An sbt `sbt.nio.file.PathFilter` exclusion filter to apply to the input sources and resources copied to the prepared assembly by `jsPrepare`. | `HiddenFileFilter`                                                                                           |

__________________________________

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
