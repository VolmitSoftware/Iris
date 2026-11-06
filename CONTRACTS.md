# Contracts

[DESIGN.md](DESIGN.md) is the authoritative description. This file maps requirements to the
types that carry them and lists what the Iris adapter still owes.

## Adopted seams

| Requirement | Contract |
| --- | --- |
| Module identity and composition | `IrisModule`, `ModuleInfo`, `StageGraph` |
| Loading and unloading a pack | host `LoadedPack`, `ModuleHost` |
| Module jars in a pack | `META-INF/services/com.volmit.IrisModule`, host `PackModules` (jar copies), `DiscoveredModule`, `MissingClasses` |
| Platform tooling parts of a jar | manifest `Iris-Tooling-Parts`, host `ToolingPart`, `ToolingPlatform`, `PlatformApi`, `ModuleHost.runningOn`, `ToolingPartLoader`, `ModuleJarCheck --platform` |
| Namespace ownership | module-prefixed stage, feature and mantle ids, `ModuleInfo.ownsStage`/`ownsId`; no reserved namespaces |
| Randomness independent of other modules | `StageContext.random()`, host `StageStreams`, invocation seed passed to the runners |
| Random values identical on every JDK | `WorldRandom`, pinned by `WorldRandomTest` |
| Module entry point | `IrisModule.info()`, `IrisModule.setup(ModuleSetup)` (`stages()`, `registry()`, `log()`), host `ModuleSetups.run` |
| Module-owned work and ordering | `Stage`, `StageRef`, one stage definition type per pipeline, `requires`/`softAfter`/`softBefore` |
| Activation by configuration | `when(settingsType)`, `StageContext.configured(settingsType)`, host `ConfiguredSettings` (entries per declared `@Feature` type; any column of a chunk) |
| Stacked settings levels, read per column | `Level`, `FeatureSettings.at(type, x, z)` and `declaredInChunk(type, chunkX, chunkZ)`, `SettingsStack` (`values`, `dimension`, `level`, `mostSpecific`), `FeatureSettings.Entry` (level, source, value) |
| Chunk coordinates in stages | `ChunkContext` (`chunkX`, `chunkZ`, `centreX`, `centreZ`, `area`), shared by `GenerationContext` and `MantleGenerationContext` |
| Typed results between modules | stage outputs, `StageContext.output` |
| Per-block work with single-pass cost | `BlockStage`, fused block passes |
| Pack-load validation and diagnostics | host `StageGraphBuilder`, `ResolvedGraph`, `LoadedModules` |
| Reference execution | host `PlacementRunner` (returns the API `PlacementOutcome`), `ChunkRunner`, `MantleRunner`, each timed through host `StageTimings` |
| Work across chunks and mantle writes | `Stage.mantle`, `MantleStage`, `MantleStageContext`, `MantleGenerationContext`; chunk stages write registered layers in their own chunk only and read the neighbors |
| Annotated settings | `IrisFeature`, `@Feature` (id, short JSON `key`, optional `root`), config annotations, host `OptionsMetadata`, `LinkedBiomes` (`treeBiomes`, `usedBiomes`: pack biomes the settings bring into the layout) |
| Module state that survives a restart | `RunningWorld.saved(SavedKey)`, `FeatureRegistry.registerSaved`, `SavedKey` (`ResourceId` id, type, `StorageCodec`, `initial`), `com.volmit.data.StorageCodec` (also the codec of mantle layers) |
| Host facades for the host's own modules | `PackWorld.extension(type)`, `InvocationContext.extension(type)`, `PackSettings.extension(type)` (each an `Optional`, empty by default) |
| Area a stage writes | `reach(blocks)`, `reach(PackReach)`, `writesMantle(...)` on stage definitions, `PackSettings` (with `resources()`, for a reach that depends on the size of the pack's objects), host `StageReach`, `ResolvedStage.reachBlocks`, `PipelineReach` |
| Settings described for studio | `@Desc`, `@Required`, `@MinNumber`, `@MaxNumber`, `@MinItems`, `@Snippet`, `@Ref`/`Reference`, `@ResourceRef`, `FeatureRegistry.registerSettings`, host `LoadedPack.schema()` (`PackSchema`, `FeatureSchema`, `ObjectType`, `SchemaOption`, `ValueKind` with `HostKind`), `ModuleHost.describingHostTypesIn` |
| Geometry and mantle registration | `FeatureRegistry`, host `ModuleRegistry`, `FeatureCatalog` |
| Block states and palettes | `BlockState`, `BlockRegistry` (`resolve(BlockSpec)`, `find(key)`, `blockEntity`, `hasTrait`, `oreFor`, `rotate(state, BlockTurn)`, `merge`, `withPropertyIfAccepted`, `matches`), `BlockTurn`, `BlockTrait` (with `STORAGE`), `BlockSpec`, `PaletteSpec` and `PaletteSampler` (`BlockRegistry.palette`) |
| A pack's objects | `PlaceableObject` (`name`, size, `center`, `geometry`), `PackObjects` (`object`, `sizeOf`, `scaled` with `ScaleMethod`, `smartBored`, `build`), `PackResources.objects()` |
| Placing through the pack's placement graph | `Placements.place(PlacementRun)` (`PackWorld.placements()`), `PlacementRun` (request, name, settings JSON, area, terrain, input, seed), `PlacementTerrain` (`recordPlacement` / `placementAt` with a `PlacementRecord`), `StructureVolumes`, `PlacedMarker`, `PlacementOutcome`; stages read `PlacementContext.placementTerrain()` and `input(type)`; mantle stages get a terrain from `MantleGenerationContext.placementTerrain()`; `RunningWorld.placementAt` reads what a placement recorded; `FeatureRegistry.registerPlacementType` names the type packs write placements as (host `FeatureCatalog.placementType()`, `PackSchema.placementType()`) |
| Noise from pack-described styles | `NoiseStyle`, `StyledRange`, `PackResources.noise()`, `NoiseFactory`, `NoiseSampler` |
| Values in settings | `BlockSpec`, `PaletteSpec`, `NoiseStyle`, `StyledRange`, `Range`, `BlockPosition`, `JigsawOverrides`, `Object` as `ValueKind.AnyKind` |
| Decisions over and fit of the platform's own structures | service `VanillaStructures` (`rule`, `starts`, `shift`, `reach`, `beforePieces`, `afterPieces`, `setup`), `VanillaStructureRule`, `VanillaStructureContext`, `VanillaStructureStart`, `VanillaStructureStartContext`, `VanillaStructureSetup`, `PlatformStart`, `StartMemo`, `StartReach`, `StructureFit` (`Start`, `Piece`, `ColumnVisitor`), `DecorationStep`, `TerrainAdaptation`, `PieceKind`, `TemplateCells`, `world.BlockAccess`, host `FeatureCatalog.registrations(service)` |
| Pack-scoped resolution | `ResourceId` (`ofPackKey`, `ofPlatformKey`), `ContentKey`, `PackResources` (`find(kind, id)`, `require`, `ids(kind)`, `idOf`; kinds `BIOMES`, `REGIONS`, `OBJECTS`, `EXPRESSIONS`, `IMAGE_MAPS`), `PackBiome`, `PackRegion`, `PackExpression`, `PackImageMap` |
| Module-owned pack resources | `ResourceKind`, `FeatureRegistry.registerResourceKind`, `@ResourceRef`, host `ModuleHost.reservingFolders`, `FeatureCatalog.resourceKinds()`, `ResourceKindSchema`, `ValueKind.ResourceReferenceKind` |
| Module-owned pack checks | `Validator`, `ValidationContext`, `Problem`, `Severity`, `Subject`, `FeatureRegistry.registerValidator`, host `FeatureCatalog.validators()` |
| Per-world module state | `PackWorld` (`state`), `InvocationContext.world()`, host `WorldStates` |
| Module lifecycle across loads, hotloads and reloads | `ModuleLifecycle`, `PackContext`, `FeatureRegistry.registerLifecycle`, host `FeatureCatalog.lifecycles()`, `LifecycleRunner` |
| Host questions answered by modules | `@Service`, `Providers`, `FeatureRegistry.registerService`, host `FeatureCatalog.services(type)` / `service(type)`; services `ObjectsAt`, `StructureLocator` (`LocateResult`, `startedStructures`, `locatableStarts`, `locateStart`, `ChunkFilter`), `SaplingGrowth`, `Loot` (with `shuffleSeed`), `BlockDrops`, `EntitySpawns`, `CaveBiomes`, `ProtectedFloor`, `AmbientEffects`, `VanillaFeatures` (`DecorationStep`); values `ItemDrop`, `EntitySpec`, `ItemRegistry` (`PackResources.items()`) |
| Terrain and settings sampling | `TerrainSampler`, `BiomeSample`, `TerrainSample`, `FeatureSettings` |
| Dense chunk output | `Hunk<T>`, `GenerationContext.blocks`, reference `ArrayHunk<T>` |
| Positions, boxes and faces | `com.volmit.geometry`: `BlockPosition`, `BlockBox`, `BlockFace` |
| Sparse geometry | `GeneratedObject` |
| Studio views of what a module builds | `com.volmit.tooling`: `ModuleSetup.tooling()` (`ToolingRegistry.registerView`), sealed `StudioView` (`SceneView` with `SceneEntry`, `PatternView` with `PatternEntry`, `PatternArea`, `BlockPattern`), `EditableSceneView` (`SceneFile`, `NodeGraph`, `GraphNode` with `Link`, `NodeSettings`, `Edits`, `Addition`), `JsonText`, `JsonPath` (`Key`, `Index`), host `StudioViewRegistration` |
| Commands of a module | `com.volmit.tooling`: `ToolingRegistry.registerCommand`, immutable `Command` (`named`, `alias`, `describe`, `child`, `argument`, `runs`, `openToEveryone`), typed `Argument` (`word`, `text`, `integer`, `decimal`, `flag`, `choice`, `resource`, `registered`, `orElse`, `suggesting`), `CommandAction`, `CommandContext` (`get`, `sender`, `pack`, `world`, `platform`), plain-text `Sender`, `ToolPack` (`edit` with `PackEdit`, `outputFolder`, `objects` with `ObjectCodec`), host `CommandRegistration` in `FeatureCatalog.commands()` |
| Buffered placement writes | `PlacementRequest`, `PlacementAccess`, host-owned `PlacementEdit` |
| Typed generation and persistent storage | `mantle` package |

Stage bodies hold no invocation state. Everything per placement or chunk travels through the
context, outputs and accumulators. Each stage draws only from its own stream, derived from the
invocation seed and its id, so other modules never change its draws. Every value a stream produces is
defined by IrisApi (`WorldRandom`), not by the JDK, so a JDK update never changes a world.

A module owns its id (`ns:name`, no `/`) and the ids below it (`ns:name/...`). Its stage ids are
below it; feature ids, mantle keys and resource kind ids it registers are its id or below it; a settings type in
`when(...)` is owned in that sense by a module that names it. Pack modules cannot use the
namespaces the host reserves.

A pack load works on copies of the module jars, so the originals are never held open.

Modules describe their settings only through their settings classes; jars ship no schema files. The
pack load turns every catalogued settings type into a `PackSchema` and fails, naming field, settings
and module, on a field type it cannot describe, a `@Ref` or `@ResourceRef` on a field that is not text, both on
one field, a class without
a working no-argument constructor (a `@JsonForms` class needs none: it is described as any JSON value), a bad or repeated snippet name, or a pack class's snippet name
outside its module's folder. Defaults come from an instance made by that constructor. Options are in
declaration order.

Packs write settings under the short key the feature declares, as the settings object, or as the value of the
one option its `root` names; there is no second form. A key is unique per target across modules and the host's own
JSON fields. The description is in a fixed order and holds module entries only.

Modules compile against the API (`com.volmit:iris-api`) `compileOnly` and never ship it. At runtime they can use the
API (`com.volmit.*` except the host side `com.volmit.host`, artifact `com.volmit:iris-host`), the host packages the host chooses to expose, the JDK and the classes in their
own pack's jars. A host may expose libraries it shares with modules (Iris exposes VolmLib and Gson); any other
library a module ships in the pack, never borrowing the host's copy. JDK modules
outside the platform class loader, such as `jdk.compiler`, are not available, and neither are JDK
packages not exported to unnamed modules, such as `jdk.internal.misc`, unless the server is started
with `--add-exports <module>/<package>=ALL-UNNAMED`. `sun.misc.Unsafe` from `jdk.unsupported` is the
supported way for modules to use Unsafe. A class Iris has but hides, a class of an unavailable JDK
module or non-exported JDK package, or one missing from an exposed package fails the pack load,
naming the jar and the classes. A class nobody has (an optional dependency) only warns, unless
its package is one the jar's manifest declares optional (`Iris-Optional-Packages`, read by the host's
`ModuleJar`). That declaration never excuses a load error.

A jar can carry tooling for one platform, such as Bukkit, as a part named in its manifest (`Iris-Tooling-Parts:
<platform>=<entry class> ...`): the entry class's package and subpackages. Parts are not generation code. Generation code
that uses a part class fails the pack load ("Generation code cannot use the bukkit part; move the call into the part").
A host that runs on a platform (`ModuleHost.runningOn`) checks that platform's parts at pack load: they may use their own
packages, the platform's api and what modules can use, and not Iris internals, the server's own classes
(`net.minecraft`, `org.bukkit.craftbukkit`, `com.mojang`), `com.volmit.host` or another part. Other platforms' parts are
skipped unread. The host opens a part through its own class loader (`ToolingPartLoader`, a child of the pack's), which
the adapter creates and closes; the generation loader never sees `com.volmit.tooling.<platform>`. Enabling a part,
and what it mounts, is the adapter's. `ModuleJarCheck --platform=<name>:<platform api jar>` makes a build check the parts.

Stage bodies and service providers keep no state; what a module keeps for a world lives in that world's state store,
created once per type and world. A world's state lives while the world runs with one pack snapshot: a hotload or a
reload of the pack's resources starts the world again with empty state; what must outlive that is a
`RunningWorld.saved` value, which the host reads on first use, writes when it saves the world and when the world stops,
and replaces by its `initial` when the codec rejects the stored payload. A service names how its providers combine
(`ALL`, `FIRST`, `ONE`); a second provider of a `ONE` service fails the pack load, naming both modules. Services
return values and never act on the platform.

A resource kind reads one folder directly below the pack root, with a class no other kind uses; a folder two
kinds claim, or one the host reads itself, fails the pack load naming both owners. Resources of a kind are
values, read once per pack snapshot. Validators only report: an `ERROR` blocks the pack, a `WARNING` is shown.

IrisApi's value types are values: the host decodes them like settings, and nothing changes them afterwards.
Noise belongs to the host; modules describe it and get samplers built from the description. A
`VanillaStructures` rule answers per structure and world, never per position.

There is no API version gating. Packs ship their module jars, so their compatibility with the
running Iris is the pack publisher's responsibility.

## Requirements for the Iris adapter

- Port the engine as ordinary modules. Keep `iris:*` stage ids stable once packs or addons
  depend on them, because soft references and diagnostics use them.
- Answer `FeatureSettings.at(type, x, z)` with one entry per level that declares the type at that
  column: the dimension, the column's region, its surface biome and, in a placement, the placement, in
  that order. Never merge or override levels. Pass each placement's own declarations as
  `ConfiguredSettings.placement(...)`; the chunk and mantle runners collect a chunk's settings
  themselves.
- Name the packages of the classes Iris describes itself with `ModuleHost.describingHostTypesIn(...)`,
  decode them as Iris does, and render each `HostKind` with Iris's own description of the class. These are
  deprecated for removal: transitional, until the cut-over.
- Back each module's log with the host's own: `ModuleHost.loggingTo(logger)` where `logger` writes `DEBUG` only when the
  host's debug setting is on and writes an `ERROR` with a cause as the host reports its own failures. Implement
  `PackWorld.log()` (the host's own log) and hand services a world scoped to the provider's module
  (`ServiceRegistration.scope`); the runners do it for stages and hooks.
- Offer the facades of the host's own modules through `extension(type)` on the world, the invocation inputs and the pack
  settings, and nowhere else: no thread-bound state and no interfaces for modules to cast to.
- Hand modules heights above the dimension's bottom (0 = bottom block, whatever the platform's minimum
  height is) in every value that carries Y: samples, bounds, block views, placement edits, the mantle,
  hunks. X and Z stay world coordinates. Convert in one place at the boundary.
- Preserve pack field names and defaults, seed derivation, RNG draw order, coordinate
  conventions and iteration order where existing worlds must not change. Per-stage streams do not
  reproduce Iris's current shared draws; matching them where worlds must stay the same is adapter
  work inside the ported stages.
- Derive one seed per placement (world seed and the placement's stable identity) and per chunk
  (world seed and chunk position) and pass it to `PlacementRunner.place` / `ChunkRunner.generate`.
- Pass every runner a `StageTimings` that records into the host's metrics (or `StageTimings.NONE`), so
  placement, mantle and chunk stages are all timed by the host rather than by the modules.
- Prepare the area a placement may read or write, including neighbors: the placement pipeline's
  `reach()` around the placed object's bounds, each mantle layer by its own maximum. Never clip.
- Work out each mantle stage's reach once per pack load (`ResolvedStage.reachBlocks(PackSettings)`),
  answering `PackSettings.declared(type)` with every entry of the dimension and of the regions and
  biomes it can reach.
- Run each mantle stage (`MantleRunner.generate(stage, ...)`) as its own pass, in resolved order: for
  every chunk within that stage's reach plus the reach of every later stage of a chunk, each chunk once,
  before the next stage and before that chunk generates, with the mantle widened by the stage's reach.
  Give chunk stages that chunk's mantle; `ChunkRunner` limits writes to the chunk's own columns. Give chunk stages a `TerrainSampler` over the
  chunk and the 16 blocks around it, and mantle stages one over the chunk and the larger of their reach and 16 blocks.
- Serve `Mantle.blocks()` in every invocation: the blocks placements wrote into the mantle, read-only, none where the
  dimension keeps no mantle or the chunk generates without it. A block a stage reads and writes back unchanged keeps
  the host's own identity of it (a custom block of a content plugin stays one).
- Serve `Mantle.carved()` in every invocation, keeping the voids in the host's own form: a mantle stage's view writes them, a chunk
  stage's view only reads them (its writes throw UnsupportedOperationException), and a dimension that keeps no mantle holds none. Answer
  `Mantle.isCarved(x, y, z)` (false where it holds none) and cover the chunk and the 16 blocks around it in a chunk stage's mantle, as the
  sampler does.
- Name pack resources to services by `ResourceId` and convert to the host's keys at the host's edge; read a `ResourceId`
  setting written as a bare pack key as the pack's id of that key.
- Answer `TerrainSampler.surfaceBiome(x, z)` (the id `biome(x, y, z).surfaceBiome()` gives), `surfaceKind`, `minWorldY()`, `surfaceBlock` (empty
  where no image map gives one) and `upperDimension()` (empty when nothing sits above); `FeatureSettings.declaredBy(biome, type)` answers the
  settings the biome declares itself (empty when it declares none or is unknown); `BlockRegistry.oreFor` answers from the platform.
- Answer `TerrainSampler.biomeHeight(biome, x, z, seed)` (the height above the bottom the biome's terrain generators give the
  column when shaped with `seed`) and `skyBiome(biome, x, z)` (the platform biome the biome shows as in the sky; empty when it
  names none) for any biome of the pack, throwing IllegalArgumentException for one it does not have. Store
  `Mantle.setPlatformBiome(x, y, z, biome)` where the biome pass that writes the chunk's biomes reads it; a chunk stage's
  mantle accepts it inside the chunk's own columns only, and a placement's mantle or an edit may refuse it.
- Answer `TerrainSampler.fluidHeight()` (the height the host fills with fluid below) and `upperDimensionY(x, z)` (where a
  dimension stacked above begins; empty when none does). `surfaceY(x, z)` equals `column(x, z).surfaceY()`; a host
  overrides it when the height costs less than the whole column. `surfaceHeight(x, z)` must equal
  `(int) Math.round(surfaceY(x, z))`.
- Answer `FeatureSettings.at` in level order with no allocation a column read can avoid, and `declaredInChunk` with
  exactly the distinct entries (by level and source) of the chunk's 16 by 16 columns, in the order the default gives:
  level order, then the order the first column holding an entry was found, columns by X and then Z. Answer it from the
  regions and biomes the chunk holds where the host knows them.
- Make placement edits atomic across blocks, block entities, markers and mantle in generation
  storage. Abort chunk publication when any stage fails.
- Preserve unknown persisted mantle layers or fail explicitly.
- Build one `ModuleHost` at startup with the built-in modules, exposed packages, reserved JSON
  fields. Its methods accumulate, so the configuration can be
  built in steps. Load each pack with `LoadedPack.load(packRoot, host)`
  and close it only after nothing built from it is in use. Catch `ModuleLoadException`, the one type of every load
  error, not `RuntimeException`.
- Depend on both `com.volmit:iris-api` and `com.volmit:iris-host` and ship both; expose neither the host side nor
  anything below it to packs (the loader refuses to).
- Implement both `PlacementEdit.set` overloads; buffer a plain block (`set(x, y, z, state)`) without building a
  `PlacedBlock` for it.
- Pass a `System.Logger` that writes to the server log to `ModuleHost.loggingTo(...)`, so the
  per-pack module summary and missing-classes warning are visible, and a cache directory of the host
  to `ModuleHost.copyingJarsTo(...)` for the jar copies.
- Provide the API at runtime from the plugin's class loader, for example by shading it into the
  Iris plugin jar. Pack jars must not ship it.
- Pass the packages of built-in modules whose stages packs may require, and of libraries the host shares with
  modules, to `ModuleHost.exposing(...)`. They must be on the loader that defines the
  API. Keep them narrow: everything in an exposed package and its subpackages becomes visible to
  packs and is part of what packs can break on. Stage output types and settings types that pack
  code uses through those stages must be in exposed packages too. The API is always exposed.
- Consider showing `PackModules.missingClasses()` in studio.
- Pass the host's own JSON fields per target to `ModuleHost.reservingFields(...)`, so a settings key can never
  shadow one; a clash refuses the pack load.
- Decode module settings under the declared key, as a JSON object (or a snippet reference for a `@Snippet` type), or,
  for a feature with a `root`, as exactly that option's value. Render one shape for each.
- Render `pack.schema()` into studio's files: merge each target's `fields(target)` into the schema
  of the matching Iris file type (dimension, region, biome, object placement) under the key, write `types()` as definitions, a `/snippet/<name>` schema per entry of `snippets()`, and
  completion lists per `Reference` from the server registries and the pack's biome, region and
  object folders. `PACK_*` references are pack paths without a namespace, as Iris writes them. Pack
  snippet names are under their module's folder, so they cannot collide with Iris's own; built-in
  names are Iris's to keep distinct.
- Refuse to open a pack in studio when its load fails, showing the error, rather than writing a
  schema without the modules.
- Load the pack again and rebuild the schema files after a module jar in `<pack>/modules` changes.
- Create settings with their no-argument constructor and decode JSON into that instance, so an
  omitted field keeps the default studio shows.
- Read every module resource kind (`catalog().resourceKinds()`) from `<pack>/<folder>/**/*.json` the way settings
  are decoded, keyed by path without `.json`, id `<pack namespace>:<key>`, and serve it through
  `PackResources.find` by type and `PackResources.ids`. Read each resource once per pack snapshot, fail `find` for a
  file that does not read, and read the files again on every reload.
- Reserve the folders the host reads itself with `ModuleHost.reservingFolders(...)`.
- Write a studio schema per kind for the files below its folder (the host's own description for a `HostKind`), and
  complete `ResourceReferenceKind` options, and the host's own references to a kind's class, with the kind's keys.
- Run every validator (`catalog().validators()`) wherever the host validates packs, after its own checks: an
  `ERROR` blocks the pack, a `WARNING` is shown; show each problem with its subject. A validator that throws is an
  error naming its module, and the others still run.

- Keep one `PackWorld` per world and pack snapshot and pass it to every invocation of the world
  (`InvocationContext.world()`), to service calls and to hooks. Use `WorldStates` (or the same rules) for its store.
  A placement a tool makes outside those worlds (a studio paste) gets a `PackWorld` of its own: the pack's resources
  and settings, a seed the host picks, a store closed when the placement ends, and no hooks. `world()` never throws.
- Don't override `PackWorld.seed(purpose)`: its default, `WorldRandom.purposeSeed(seed(), purpose)`, is the rule for
  every purpose, the host's own built-ins included.
- Answer `PackWorld.running()` for a world the host runs, empty for a tool's world. Its `layout()` covers every column of
  the world (not one chunk), with the same values chunk stages read; its `settings()` are the settings at a column and a
  biome's declarations as chunk stages see them; `highest` is the platform's highest block now; `caveFloors` are
  the cave floor markers of a chunk, in heights above the bottom; `sky()` is empty until the platform has the world.
- Call the hooks with `LifecycleRunner`: `packLoaded` once per pack snapshot before its first world starts,
  `worldStarted` before the world generates anything, `warmUp` after it (on a thread of your choice, and wait for it before
  the world's first generation; report a failure naming the module and carry on), `worldStopped` when it stops. On a hotload, and when the pack's
  resources are read again while worlds run, stop each world, load the new snapshot and start the world again with
  a new store. Close a stopped world's store only once no invocation uses it any more.
- A failed `packLoaded` or `worldStarted` keeps the world from starting and names the module; report `worldStopped`
  failures without skipping the other modules.
- Ask modules through services instead of reading their features: combine `ALL` providers, ask `FIRST` providers in
  catalog order until one answers, and use `service(type)` for `ONE`. Call providers from any thread, but long
  searches off the server's main thread, and carry out what they return on the thread the platform needs.
- Read a `StructureLocator` position's Y as a height above the dimension's bottom and convert it to the platform's Y
  at the boundary, like every other IrisApi height; the same goes for the positions of `Loot.Site`, `BlockDrops.Broken`,
  `EntitySpawns` and `SaplingGrowth`.
- Create what `ItemDrop` and `EntitySpec` describe on the platform, skip what the platform lacks (an unknown enchantment,
  attribute or item flag) with a warning, route a `ContentKey.Provided` (and a `Registered` id of a namespace another
  plugin owns) to that plugin, and run the checks that come with an `EntitySpawns.Attempt` (light, surface,
  free space) before spawning. Report how many entities appeared with `EntitySpawns.spawned`. Apply `Arrival.reason`
  (the platform's own name for the spawn reason, natural when it is empty or unknown), `Arrival.risesFromGround`, a
  panda's `Appearance` genes, and `adjustsProvidedMob` (a `ContentKey.Provided` mob is otherwise left as its provider set it up).
- Shuffle a filled container's slots with `Loot.shuffleSeed` (the first seed in module order; leave the order alone when
  no module gives one), and discard the blocks written at height 0 when a `ProtectedFloor` provider says the floor is
  protected (ask once per world).
- Keep a `PlacementRecord` beside the block a placement recorded it at and return it from `placementAt` (the host chooses
  the form it stores); read the settings of `PlacementRun.settings` from the JSON at `Level.PLACEMENT`.
- `AmbientEffects.due`: ask for the player's last sampled position (not every tick's), carry out an `Effect` in the order
  sound, particles, commands, potion, skip a part the platform lacks, and give no potion to a player who already has the
  same one with a higher amplifier.
- Decode IrisApi's value types like settings: `BlockSpec`, `PaletteSpec`, `NoiseStyle`, `StyledRange`, `Range` and
  `BlockPosition` from the JSON shapes in their docs, `Object` as maps, lists, `Double`s, strings and booleans.
  Read and write a `@JsonForms` class through its codec, created once with its no-argument constructor, handing it
  those same plain values; a member written as `null` stays left out and never reaches the codec.
  Read a `"snippet/<name>"` or `"snippet/<folder>/<name>"` string of a `@Snippet` type from
  `snippet/<folder>/<name>.json`. Complete `NOISE_STYLE` references and `@ResourceRef` of the host kinds
  (`EXPRESSIONS`, `IMAGE_MAPS` among them);
  rendering a value type with the host's own description of the same JSON is allowed.
- Serve `PackResources.noise()`: build each style's noise from the host's generators, expressions and image maps,
  the same values for the same style and seed, cached per pack snapshot; an unknown style name, a missing expression and a
  missing image map fail.
  Answer `NoiseSampler.signed(...)` with the generator's own signed read (and its per-thread coordinate memo) when it
  has one; the default is `sample * 2 - 1`.
  Answer `NoiseSampler.scaled(factor)` by building the generator again with the factor folded into its scale, leaving the sampler it
  is called on untouched (the default multiplies the coordinates, which may round differently).
- Answer `PackWorld.minWorldY()` with the platform's Y of the dimension's bottom, the same number `TerrainSampler.minWorldY()`,
  `PlatformStart.minWorldY()` and `StructureFit.minWorldY()` give. Answer `BlockRegistry.find(key)` with the platform's block at
  its default properties, empty for a block or property the platform lacks.
- Answer `PackResources.idOf(key)` (the ids the host gives
  its own resources, characters an id cannot hold replaced) and `TerrainSampler.caveBiome(x, y, z)` (the cave biome of a block,
  without the work of the rest of the sample) when the defaults do not fit the host.
- Answer `BlockRegistry.hasTrait` (`VINE` included) and `canPlaceOn` (the support rule decorations need) as the platform
  does; stages ask per block, so answer from memory once a state was seen.
- Resolve `BlockSpec`s in `BlockRegistry` as packs expect (pack block, platform block, backup, air) and turn their
  `tileData` into the host's block entity format.
- Build `BlockRegistry.palette(spec, seed)` pickers: blocks expanded by weight, the style's noise at (x, y, z) over
  the zoom picks the entry, resolved as above, cached per pack snapshot; refuse a palette without a block to pick.
- Ask every `VanillaStructures` provider (`catalog().registrations(VanillaStructures.class)`, module id order) before
  the platform starts one of its structures and when it locates one, combine the rules with `VanillaStructureRule.and`,
  keep the result per world, structure and pack snapshot, and apply it on every platform: block the platform's starts
  of a blocked structure, and report a structure a module places as that module's. A provider that throws or answers
  nothing fails naming its module.
- Before publishing a start the platform made, add up every provider's `shift` for it (after the host's own
  alignment of monuments and temples) and move the start by it, less where it would leave the world. Before
  publishing any start, cover every provider's `reach` in its references (`PLATFORM`: the platform's own terrain
  adaptation envelope), within Minecraft's reference range; a recorded envelope only grows.
- Per chunk, call every provider's `beforePieces` once before the platform places any piece of the chunk's starts,
  then per start in placing order the platform's placement and `afterPieces`. Give `StructureFit` in the API's heights
  (above the bottom, maxima exclusive): the processed template cells of a piece, its template columns, its legacy
  template air, the surface per column without fluids and the dimension's natural block. `BlockAccess.is` answers
  `AIR`, `SOLID`, `FLUID`, `LOG` and `LEAVES` as the platform's world does and refuses other traits.
- Persist every start's `StartMemo` with its ownership record, unchanged, and hand it back in `reach` and
  `StructureFit`; a start without a memo stays without one.
- Ask `starts` per chunk, in module id order, refuse a start outside the chunk asked for, keep the first start per
  structure, let a start take the chunk from a platform start only when it says so or the structure is `REPLACE`d,
  apply its jigsaw overrides, keep its memo, and ask again (same answer) when locating or verifying a start found in
  the world.
- Send the platform's locate of a structure some `StructureLocator` provider lists in `startedStructures` to that
  provider's `locateStart` (module id order, the first that lists it in `locatableStarts`), convert the start's Y to the platform's,
  check the start the platform made there before reporting it, and search again refusing that chunk when the check fails.
  Route the host's own structure commands by `startedStructures` the same way.
- Ask `setup` per dimension before its worlds build their structure state and when installing data packs; multiply
  frequencies per structure set (0.01 to 16), protect the platform's definitions in every installed data pack once
  any dimension of any loaded pack asks for it.
- Run the platform's own placed features over a world only when some `VanillaFeatures` provider's `generates` says so;
  then skip a decoration step any provider refuses (`generatesStep`, empty for a step the API does not name) and a
  feature any provider refuses (`generatesFeature`). Ask once per world, step and feature, and keep the answers while
  the world runs. Keep the platform's own pass otherwise unchanged: its biome tables, order and seeds.

## Open

- Terrain/biome providers, pack transformations, structure assembly and the runtime family.
