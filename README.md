# sbt-js

Collection of [sbt](https://scala-sbt.org) plugins for uniform configuration of Shuwari Africa Ltd. sbt projects, as well
as CI and Release related functionality.

_NB: Unless specified otherwise, all plugins listed below are sbt `AutoPlugins`, and will be enabled automatically upon enabling the required plugin dependencies for each._

## Core Plugins

The following core plugins are available:

__________________________________

### sbt-js-core

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-js" % "0.12.0")
```

Preconfigures projects with opinionated project defaults for ScalaJS libraries and/or applications. Provides a foundation for incremental assembly using external Javascript ecosystem
tools.

Introduces additional sbt `SettingKeys` and `TaskKeys`, specifically relevant:

| Key                                  | Description                                                                                                                                            | Default                                                                                                                                             |
|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `jsPrepare`                          | Task: Compiles, links, and prepares project for packaging and/or processing with external tools."                                                      | [Refer to implementation of `jsPrepare`](modules/sbt-js/src/main/scala/africa/shuwari/sbt/plugin.scala)                                             |
| `jsFullLink`                         | Setting: Defines whether _"fullLink"_ or _"fastLink\"_ ScalaJS Linker output is used.                                                                  | `true` where `NODE_ENV` environment variable is defined with a value of `peoduction`. and `false` otherwise.                                        |
| `js`                                 | Task: Process and/or package assembled project with external tools.                                                                                    | Unimplemented. To be customised by end-user.                                                                                                        |
| `js / sourceDirectory`               | Setting: Default directory containing sources and resources to be copied as-is to `jsPrepare / target` during `jsPrepare` execution.                   | `(Compile / sourceDirectory) / js`                                                                                                                  |
| `js / sourceDirectories`             | Setting: List of all directories containing sources and resources to be copied as-is to `jsPrepare / target` during `jsPrepare` execution.             | `(Compile / sourceDirectory) / js`                                                                                                                  |
| `js / target`                        | Setting: Defines a target directory for the `js` task. Usable if required.                                                                             | `(Compile / crossTarget) / (js.key.label.toLowerCase + "-" + normalizedName + "-" + (if (jsFullLink.value) "full" else "fast") + "-linked"`         |
| `jsPrepare / target`                 | Setting: Defines a default target directory for the `jsPrepare` task.                                                                                  | `(Compile / crossTarget) / (jsPrepare.key.label.toLowerCase + "-" + normalizedName + "-" + (if (jsFullLink.value) "full" else "fast") + "-linked"`  |
| `jsPrepare / fileInputIncludeFilter` | Setting: An sbt `sbt.nio.file.PathFilter` inclusion filter to apply to the input sources and resources copied to the prepared assembly by `jsPrepare`. | `RecursiveGlob`                                                                                                                                     |
| `jsPrepare / fileInputExcludeFilter` | Setting: An sbt `sbt.nio.file.PathFilter` exclusion filter to apply to the input sources and resources copied to the prepared assembly by `jsPrepare`. | `HiddenFileFilter || DirectoryFilter`                                                                                                               |
__________________________________

### sbt-vite

```scala
addSbtPlugin("africa.shuwari.sbt" % "sbt-vite" % "0.12.0")
```

|Depends On:                                                                                   |
|-----------------------------------|
|[sbt-js](#sbt-js-core)             |

Preconfigures projects with opinionated project defaults for ScalaJS libraries and/or applications. Uses [Vite](https://vitejs.dev/) for bundling, and postprocessing.

Introduces additional sbt `SettingKeys` and `TaskKeys`, specifically:

| Key        | Description                                                                                       | Default                                                                                                 |
|------------|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `vite`     | Task: Compiles, links, and prepares project for packaging and/or processing with external tools." | [Refer to implementation of `jsPrepare`](modules/sbt-js/src/main/scala/africa/shuwari/sbt/plugin.scala) |
| `viteBuild`| Executes `vite build` using the options specified in _sbt-vite_ plugin settings.                  | _N/A_                                                                                                   |
| `viteStop` | Shuts down any running instances of Vite's development server.                                    | _N/A_                                                                                                   |

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
