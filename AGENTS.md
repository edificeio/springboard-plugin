# AGENTS.md

Gradle plugin (id `springboard`) used by "springboard" projectsto build an environment of the ENT:
- generate config files
- download Vert.x fat-jar modules
- extract deployments/i18n/help
- wire docker-compose
- run JS/Gatling tests

Published to the ODE Nexus as `fr.wseduc:springboard-plugin`.

## Hard constraint

The plugin is built **and consumed** with Gradle 4.5.1. Write Groovy/Gradle code for that version only:

- Use `compile`/`shaded` configurations, never `implementation`/`api`.
- Tasks are declared with the legacy `project.task("name") << { ... }` and `project.task(dependsOn: [...], "name")` forms; follow that style.
- Old APIs are in use on purpose (`sourceSets.test.output.classesDir`, `project.javaexec { main = ... }`, `project.copy {}`). Do not "modernize" them (`mainClass`, `destinationDirectory`, `tasks.register`, lazy `Property` APIs, `plugins {}` DSL in consumers…).
- Check any Gradle API you use exists in 4.5 before adding it.
- The shadow plugin is pinned to `2.0.1` for the same reason; do not bump it.
- Always build with build.sh

## Build

```bash
./build.sh clean install   # runs `gradle clean` / `gradle install` in the docker-compose `gradle` service
./build.sh publish         # publish to Nexus (needs odeUsername/odePassword in ~/.gradle/gradle.properties); CI only
```

- `install` puts the plugin in `~/.m2` (mounted into the container); consumers resolve it via `mavenLocal()` first.
- Jenkins (`Jenkinsfile`) runs `./build.sh clean install publish`.
- Version lives in `gradle.properties` (`version=3.x-SNAPSHOT`). 
- Commit using conventionnal commit standards.
- No test suite exists. Verify changes by installing the plugin and running tasks in a consumer springboard (e.g. `/opt/springboards/ode6`, with its `springboardPluginVersion` pointing at the SNAPSHOT) such as `gradle generateConf`, `gradle listMods`.
- `lib/` (Groovy 5 jars), `bin/`, `build/`, `.idea/` are IDE/local artifacts and git-ignored; ignore them.

## Layout

- `build.gradle` – groovy plugin + shadowJar (bundles `snakeyaml` 1.33 via the `shaded` configuration) + maven publishing. `gradle/maven.gradle` is legacy `uploadArchives` config.
- `src/main/resources/META-INF/gradle-plugins/springboard.properties` – maps plugin id to `fr.wseduc.gradle.springboard.SpringboardPlugin`.
- `src/main/groovy/fr/wseduc/gradle/springboard/`
  - `SpringboardPlugin.groovy` – all tasks and helpers; also holds the `IndependentServiceConf`, `IndependentService`, `SpringboardService` model classes (ports of Go structs from another tool; keep field semantics aligned).
  - `FileUtils.groovy` – template rendering (`SimpleTemplateEngine`) and properties merge/copy helpers.
- `src/main/resources/` – files copied into consumer projects by `init`/`generateConf`: `docker-compose.yml` (becomes `docker-compose.yml.template`), `ent-core.json.template`, `main.json`, `conf.properties`, `default.properties`, `package.json`, `conf/`, `neo4j-conf/`, `docker-entrypoint-initdb.d/init.sql`, sample CSV/AAF data, Gatling scenarios.
- Root `docker-compose.yml` is only the build container for `build.sh`; it is **not** the template shipped to consumers (that is `src/main/resources/docker-compose.yml`).

## Tasks provided to consumers

| Task | Purpose |
|---|---|
| `init` | `extractDeployments` + `extractHelps` + `extractTranslations` + `initFiles` (copies resource files, merges `conf.properties`/`default.properties` from deployments) |
| `generateConf` | Renders `ent-core.json` and `docker-compose.yml` from templates, runs `extractMods`, adds independent services, copies `main.json` |
| `generateDockerConf` | Only regenerates `docker-compose.yml` |
| `extractMods` / `listMods` | Download (parallel, fail-fast) and unzip `-fat.jar` mods into `mods/`; `listMods` only prints them |
| `extractDeployments`, `extractHelps`, `extractTranslations`, `extractTheme` | Unpack `deployment`, `help`, `i18n` configurations / copy themes |
| `integrationTest` | Runs Gatling simulations (depends on `compileTestScala`) |
| `downloadTestsJS`, `unzip<Module>TestJsJar`, `prepareJsTests` | Auto-created in `afterEvaluate` from deps with classifier `testJs`/`tests` |

Consumer projects must define the `deployment`, `help`, `i18n` configurations and apply the `scala` plugin.

## Behaviors to know before editing

- **Template engine**: `ent-core.json.template` and `docker-compose.yml.template` use Groovy `SimpleTemplateEngine`; `$` and `\` must be escaped. Bindings priority: `conf.properties` > `gradle.properties` (minus `modowner`, `modname`, `version`, `produceJar`) > `default.properties` > `additionalBindings` (`isM1`, `vertxCliVersion`, `vertxServiceLauncherVersion`).
- **Mod versions**: `resolveMods` lists default mods; each version comes from a Gradle property (e.g. `entCoreVersion`, `modMongoPersistorVersion`), else is fetched from the `pom.xml` of the GitHub repo on branch `modsDefaultBranch` (default `dev`, per-mod override possible). Deployment-configuration deps are merged in. Adding a mod = add a spec line there.
- `vertxServiceLauncherVersion` follows the same property-then-GitHub fallback.
- **Independent services**: read from `services.yaml` / `services.yml` in the consumer root; services with `enabled && !local` are injected into the generated `docker-compose.yml` (user `$DEFAULT_DOCKER_USER`, image `<url>:<version>`, `nest` type env vars, `sql` → `DATABASE_URL`). `local` services get an nginx URL via `host.docker.internal` (Mac/M1) or `172.17.0.1`.
- `#REMOVE_BY_CI` markers in the docker-compose template are stripped by CI scripts in consumers; keep them on port mappings.
- `extractMods` skips jars/dirs already present in `mods/`, and forces SNAPSHOT re-resolution (`cacheChangingModulesFor(0)`) on detached configurations.
- `initFiles` overwrites most copied resources but only *appends* missing keys to `conf.properties`/`default.properties`.

## Conventions

- Tabs for indentation in Groovy sources; log with `project.logger.lifecycle/info/warn`, prefixed `[taskName]` as existing code does.
- Commit style: conventional (`feat:`, `fix:`, `chore:`, `release:`), often with a ticket prefix like `#RBACK-244`. Branches: `dev` (work) → `master` (releases).
