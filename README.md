# Iris

Iris is a world generation engine for Minecraft servers and mod loaders. It generates terrain,
biomes, caves, structures, objects, and entities from editable JSON packs, with a full in-game
studio authoring workflow. The same engine runs as a Bukkit-family plugin and as a Fabric, Forge,
or NeoForge server mod. Cross-platform generation targets deterministic parity for identical
artifacts, pack bytes, seeds, and test areas. The master branch targets Minecraft 26.2.

# [Support](https://discord.gg/3xxPTpT) **|** [Documentation](https://github.com/VolmitSoftware/docs/blob/master/iris/00-overview.md) **|** [Git](https://github.com/IrisDimensions)

Authoritative docs live in the central [VolmitSoftware/docs](https://github.com/VolmitSoftware/docs/tree/master/iris) repository, which is the source for the hosted wiki.

Consider supporting development by buying Iris on Spigot.

## Language and localization

Canonical English is defined in the typed Java catalogs under `core/src/main/java/art/arcane/iris/localization`, next to the command, Studio, runtime, and UI surfaces that use it. Iris does not ship an English server translation file; the required Minecraft client asset remains at `assets/irisworldgen/lang/en_us.json`. Complete server bundles and matching client assets are included for German, Spanish, Finnish, French, Hebrew, Italian, Japanese, Korean, Lithuanian, Dutch, Polish, Portuguese, Russian, Turkish, Vietnamese, Simplified Chinese, and Traditional Chinese. Set `language` in Iris settings to select one. A JSON file at `languages/overrides/<locale>.json` can override only selected server messages; omitted entries resolve from the bundle and then code-owned English.

## Platforms

| Platform | Artifact | Minecraft | Notes |
|---|---|---|---|
| Paper / Purpur / Leaf / Canvas | plugin jar | 26.1.2 - 26.2 | Full feature set |
| Folia | plugin jar | 26.1.2 - 26.2 | Region-safe scheduling throughout |
| Spigot / CraftBukkit | plugin jar | 26.1.2 - 26.2 | Managed `iris:*` creation and generation; exact vanilla-slot `/iris replace` is unavailable |
| Fabric | mod jar | 26.2 | Server worldgen + client HUD; requires Fabric Loader 0.19.3+ |
| Forge | mod jar | 26.2 | Server worldgen + client HUD; current target is Forge 26.2-65.1.1 |
| NeoForge | mod jar | 26.2 | Server worldgen + client HUD; current target is NeoForge 26.2.0.59 |

Java 25 is required on every platform.

The modded feature set matches the plugin wherever the operation is not Bukkit-bound: worldgen
from Iris packs, authoring with mod blocks/items/entities (with validation suggestions), studio
workspaces with schema autocomplete over the server's live registries, entity spawning parity
including death loot, pregeneration with a boss bar or client HUD, the `/iris` command tree, and
the goldenhash determinism gate, which is interchangeable across all four platforms.

### Native worldgen over Iris terrain

Iris replaces the chunk generator, so vanilla and mod worldgen only runs where Iris runs it. This
is identical on every platform.

| Vanilla / mod worldgen | Over Iris terrain | Control |
|---|---|---|
| Structures (vanilla, datapack, mod) | Yes, on by default | `importedStructures.disabled` denies individual keys |
| Placed features: ores, trees, plants, springs, geodes | Yes, **off by default** | `importedFeatures.enabled` per dimension, with per-step and per-key filters |
| Carvers (caves, canyons, mod carvers) | Never - architectural | Iris has no `NoiseGeneratorSettings` for a carver to sample; use pack `caves`/`carvings` |
| Surface builders and surface rules | Never | Iris builds its surface from pack palettes |
| Mod biomes | Only as a `derivative`, `vanillaDerivative`, `biomeScatter` or `biomeSkyScatter` target | Iris chooses biomes from the pack, not from a biome source |
| Mob spawning, including mod mobs | Yes | Biome spawn tables are merged with the vanilla derivative's |

With `importedFeatures` off - the default - chunk output is byte-for-byte what Iris has always
produced. See [94 - API - Modded](https://github.com/VolmitSoftware/docs/blob/master/iris/94-api-modded.md) and
[01 - Installation & Platforms](https://github.com/VolmitSoftware/docs/blob/master/iris/01-installation-platforms.md) for the full control reference, including
which `pointed_dripstone` keys the 26.2 `speleothem` rename does and does not affect.

Independently of that flag, Iris custom biomes now inherit the biome tags of their vanilla
derivative on every platform, so the emitted datapack tag files change. Anything driven by biome
tags therefore applies to Iris custom biomes: mob variants, spawn rules, and any vanilla or mod
content selecting on `#minecraft:is_overworld` and friends.

## Install

**Plugin (Paper/Purpur/Leaf/Canvas/Folia/Spigot):** drop the plugin jar into `plugins/` and start
the server. First boot performs no pack download. Run `/iris download pack=overworld`,
`/iris download pack=underworld`, or `/iris download link=https://host/path/pack.zip`, waiting for
each download to finish before starting another. The current built-in packs declare no external
datapack imports. Restart after download so Minecraft loads their dimension types and biomes.
Custom packs that declare `datapackImports` must complete their external-datapack installation and
registry restart first. Bukkit accepts Modrinth, direct HTTP(S), and absolute local `file:` ZIP URLs;
ZIPs under `plugins/Iris/datapacks/imports/` are discovered for every Iris dimension. Plain Spigot
supports ordinary managed `/iris create`, but not
the early-bootstrap `/iris replace` path for canonical Overworld, Nether, or End slots.

**Mod (Fabric/Forge/NeoForge):** drop the mod jar into `mods/` and start the server. The jar is
self-contained (core, SPI, and required Fabric API modules are bundled). First boot compiles only
packs already on disk and never accesses the network. `/iris download` installs a pack atomically
without stopping the server. The current built-in packs declare no external datapack imports.
Custom packs that declare them require compatible archives in that save's `datapacks/` directory;
modded `/iris datapack ingest` is an explanatory stub and does not install them. Restart only after
the Iris pack and all of its inputs are present. Packs register their custom dimension types,
height ranges, biomes, and external structure keys during that boot; worlds created
before it run with fallback registry data.

**Singleplayer (modded clients):** installed Iris packs appear as selectable World Types on the
Create New World screen; the integrated server runs the same engine.

## The client mod

Installing the mod jar on a client adds a native pregeneration HUD: a top-left panel with a
progress bar, chunks done/total, percent, chunks per second, and ETA, turning yellow while the
pregen is paused. The `H` key (rebindable, under the "Iris" controls category) toggles it.

The HUD works against modded Iris servers and against Bukkit/Paper Iris servers, both over the
`irisworldgen:main` channel (custom payloads on modded, plugin messaging on Bukkit). Vanilla
clients are unaffected and get the server-side boss bar instead; on non-Iris servers the client
mod is inert.

## Quickstart

Complete the platform's pack, external-datapack, and registry-restart workflow above first. Then
create and enter an Iris world.

Plugin (optional arguments are keyed):

```
/iris create myworld type=overworld seed=1337
/iris tp myworld
```

Mod (positional arguments):

```
/iris create myworld overworld 1337
/iris tp irisworldgen:myworld
```

On Paper-family servers with early bootstrap (not plain Spigot), the shipping pair can instead
replace the canonical portal-linked slots after their registry workflow is complete:

```
/iris replace minecraft:overworld type=overworld seed=123456789
/iris replace minecraft:the_nether type=underworld seed=-987654321
```

Restart once after both commands report staged. The independent seeds apply to their respective
slots, and vanilla portals keep routing between the canonical Overworld and Nether identities.

Pregeneration requires a radius in blocks. On the plugin, optional arguments are keyed; on modded
servers they are positional and composable:

```
/iris pregen start 352 world=myworld center=0,0 gui=false
/iris pregen start 352 irisworldgen:myworld at 0 0 sync
```

Players with the client mod see the native HUD; everyone else gets a boss bar (modded) or
console/status output. `/iris pregen status` reports progress on the plugin.

## Studio and VSCode workspace

The studio is the pack authoring environment, available on all platforms. Studio worlds are
transient - they are deleted on close and purged at startup.

Plugin:

```
/iris studio create name=<name> [template=<pack>]
/iris studio open <pack> [seed=1337]
/iris studio vscode [dimension=<pack>]
/iris studio update [dimension=<pack>]
/iris studio close
```

Mod:

```
/iris studio create [name] [template]
/iris studio open <pack> [seed]
/iris studio vscode [pack]
/iris studio update [pack]
/iris studio close
```

The generated VSCode workspace wires per-type JSON schemas (dimensions, biomes, regions, objects,
loot, entities, snippets) for full autocomplete. Schemas are generated from the server's live
registries, so on modded servers block, item, entity, enchantment, and potion-effect completion
includes installed mod content (for example `create:brass_ingot`). Editing an open studio's pack
files hotloads the changes and regenerates the schemas.

## PlaceholderAPI

Iris registers the `iris` expansion when PlaceholderAPI is enabled. Paths are dot-separated,
lowercase, and never contain an underscore. Every value is plain text: no colour codes, no unit
suffixes, no `%` character, `.` as the decimal separator, and no thousands grouping.

Three answers are possible. A path that is not in the list below returns nothing, so PlaceholderAPI
re-emits the literal `%iris_...%` and a typo stays visible. A known path with no value right now
returns `---`. A real zero returns `0`.

| Placeholder | Value |
|---|---|
| `%iris_available%` | `true` when the Iris terrain service is live |
| `%iris_world.available%` | `true` when the reading player is in an Iris world and a reading exists |
| `%iris_world.biome%` | Surface biome display name at the player, e.g. `Hot Desert Dunes` |
| `%iris_world.biome-key%` | Surface biome load key, e.g. `desert/hot-dunes` |
| `%iris_world.region%` | Region display name at the player |
| `%iris_world.region-key%` | Region load key |
| `%iris_world.dimension%` | Dimension (pack) load key of the player's world |
| `%iris_pregen.available%` | `true` while a pregeneration job is running |
| `%iris_pregen.world%` | World name the running job is pregenerating |
| `%iris_pregen.percent%` | Completion, `0.00` to `100.00`, no `%` character |
| `%iris_pregen.eta%` | Estimated seconds remaining, whole number |
| `%iris_pregen.eta-text%` | Same estimate as `2m 5s` or `1h 30m` |
| `%iris_pregen.chunks%` | Chunks generated so far |
| `%iris_pregen.total%` | Chunks in the job |
| `%iris_pregen.chunks-per-second%` | Current rate |
| `%iris_pregen.paused%` | `true` while the job is paused |

The world values are the surface reading at the player's block column. Walking refreshes them at most
once per second per player, so a whole board of `world.*` keys costs one refresh per player per
second no matter how many of them are on it, and a value may lag a sprinting player by up to a
second. A jump that is not walking - joining, respawning, changing worlds, stepping through a portal,
or any teleport including `/iris goto`, `/tp`, an ender pearl and a random teleport - is published
immediately, so a player who arrives somewhere and then stands still never keeps reading the biome,
region or dimension of where they came from. `pregen.*` is global: there is one pregeneration job per
server, and `%iris_pregen.world%` says which world it is.

### Migration from the pre-2.0 keys

The old underscore keys are gone. There is no alias and no dual-accept window; an old key now
renders literally so it is visible rather than silently wrong.

| Old key | New key | Why |
|---|---|---|
| `%iris_biome_name%` | `%iris_world.biome%` | Renamed onto the dot grammar |
| `%iris_biome_id%` | `%iris_world.biome-key%` | Renamed; `id` was always the load key |
| `%iris_region_name%` | `%iris_world.region%` | Renamed onto the dot grammar |
| `%iris_region_id%` | `%iris_world.region-key%` | Renamed; `id` was always the load key |
| `%iris_biome_file%` | removed | Rendered an absolute server path into player-visible text, and threw on packs with no backing file |
| `%iris_region_file%` | removed | Same as `biome_file` |
| `%iris_world_seed%` | removed | Handed the world seed to anyone who could read a scoreboard, and a placeholder has no permission context to gate on |
| `%iris_terrain_height%` | removed | Reported the *generated* height, before objects and player edits, so it disagreed with the block under the player's feet |
| `%iris_terrain_slope%` | removed | Three extra noise samples per read for an unformatted pack-authoring diagnostic |
| `%iris_world_mode%` | removed | Studio or Production; a studio world exists for seconds during authoring and is never on a live board |
| `%iris_world_speed%` | removed | Mutated engine rate-window state every time it was read. `%iris_pregen.chunks-per-second%` answers the same question from a snapshot |

The old keys also read the *cave* biome for a player standing under an overhang, because they
sampled two blocks above the player's feet. The new `world.biome` is always the surface biome,
which is what a board reader means.

## Building from source

Requirements: JDK 25 (set `JAVA_HOME` to it). The Gradle wrapper handles everything else.

```
./gradlew buildAllToOut
```

builds every platform artifact into the workspace-level `../PluginOuts/` directory:

```
Iris v<version> [CraftBukkit] <mc>.jar
Iris v<version> [Fabric] <mc>+<loader>.jar
Iris v<version> [Forge] <mc>+<loader>.jar
Iris v<version> [NeoForge] <mc>+<loader>.jar
```

Per-platform tasks: `./gradlew buildBukkit`, `buildFabric`, `buildForge`, `buildNeoforge`. The
SPI jar (the pure-JVM adapter/platform contract, not the stable plugin API) is built to `spi/build/libs/` by
`./gradlew :spi:jar`.

`./gradlew buildAll` also runs `buildAllToOut` and copies the jars into a consumer dropin tree
for a local test server. The consumer directory defaults to `../../[Minecraft Server]/consumers/`
when it exists, otherwise `build/consumers/` inside the repo. `-Plocation=/path/to/consumers`
changes only the consumer directory. Both tasks copy all four verified platform jars into
`../PluginOuts/`.

If you need help compiling as a developer or contributor, ask in the Discord.

## Adapters / modded development

`core/` and `spi/` are pure JVM. `adapters/bukkit/` is part of the root Gradle build; the three
modded adapters (`adapters/fabric`, `adapters/forge`, `adapters/neoforge`) are standalone builds
with their own `settings.gradle`, which is what keeps Loom, ForgeGradle, and ModDevGradle off one
plugin classpath. Drive them with `-p`:

```
./gradlew -p adapters/fabric   runServer      # or runClient
./gradlew -p adapters/forge    runServer
./gradlew -p adapters/neoforge runServer
./gradlew -p adapters/fabric   test           # shared adapters/modded-common test suite
```

Each `runServer` accepts determinism and world-integrity flags, forwarded to the game as system
properties:

| Flag | System property | Purpose |
|---|---|---|
| `-PirisParity=<pack>` | `iris.parity` | Run the cross-platform parity harness for a pack |
| `-PirisParityGolden=<file>` | `iris.parity.golden` | Compare against a captured golden-hash file |
| `-PirisParityDeep=true` | `iris.parity.deep` | Deep (per-block) parity instead of hash-only |
| `-PirisWorldCheck=<world>` | `iris.worldcheck` | Post-generation world integrity check |

Fabric additionally takes `-PirisClientRunDir=<dir>` to relocate the `runClient` working directory.
Shared code lives in `adapters/minecraft-common` (all adapters), `adapters/modded-common`
(loaders + the shared test suite), and `adapters/client-common` (client HUD and world-type
screens); every adapter adds those source directories, so one edit reaches all three loaders.

For IDE import you can surface the three adapter builds in the root composite with
`-PincludeModdedAdapters=true`. It is off by default: each adapter includes the root build back to
substitute `art.arcane:core` and `art.arcane:spi`, so including them from the root closes a
composite cycle. The build and release paths do not need it.

## Documentation

Full product docs live in the central [VolmitSoftware/docs](https://github.com/VolmitSoftware/docs/tree/master/iris) repository. Start with:

- [Overview and index](https://github.com/VolmitSoftware/docs/blob/master/iris/00-overview.md)
- [Installation and platforms](https://github.com/VolmitSoftware/docs/blob/master/iris/01-installation-platforms.md)
- [Getting started](https://github.com/VolmitSoftware/docs/blob/master/iris/02-getting-started.md)
- [Structures overview](https://github.com/VolmitSoftware/docs/blob/master/iris/18-structures-overview.md)
- [API — Getting Started](https://github.com/VolmitSoftware/docs/blob/master/iris/90-api-getting-started.md)
- [Maintainer — MC version bump](https://github.com/VolmitSoftware/docs/blob/master/iris/85-maintainer-mc-version-bump.md)
- [Maintainer — release checklist](https://github.com/VolmitSoftware/docs/blob/master/iris/86-maintainer-release-checklist.md)
