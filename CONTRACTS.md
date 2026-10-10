# Contracts

[DESIGN.md](DESIGN.md) is the authoritative description. This file maps requirements to the
types that carry them and lists what the Iris adapter still owes.

## Adopted seams

| Requirement | Contract |
| --- | --- |
| Module identity and composition | `IrisModule`, `ModuleInfo` (with optional packages), `StageGraph` |
| Loading and unloading a pack | host `LoadedPack`, `ModuleHost` |
| Module jars in a pack | `META-INF/services/com.volmit.IrisModule`, host `PackModules` (jar copies), `DiscoveredModule`, `MissingClasses` |
| Namespace ownership | module-prefixed stage, feature and mantle ids, `ModuleInfo.ownsStage`/`ownsId`; no reserved namespaces |
| Randomness independent of other modules | `StageContext.random()`, host `StageStreams`, invocation seed passed to the runners |
| Random values identical on every JDK | `WorldRandom`, pinned by `WorldRandomTest` |
| Module-owned work and ordering | `Stage`, `StageRef`, stage definitions, `requires`/`softAfter`/`softBefore` |
| Activation by configuration | `when(settingsType)`, host `ConfiguredSettings` (entries per type; any column of a chunk) |
| Stacked settings levels, read per column | `FeatureSettings.at(type, x, z)`, `FeatureSettings.Entry` (level, source, value) |
| Typed results between modules | stage outputs, `StageContext.output` |
| Per-block work with single-pass cost | `BlockStage`, fused block passes |
| Pack-load validation and diagnostics | host `StageGraphBuilder`, `ResolvedGraph`, `LoadedModules` |
| Reference execution | host `PlacementRunner`, `ChunkRunner`, `MantleRunner` |
| Work across chunks and mantle writes | `Stage.mantle`, `MantleStage`, `MantleStageContext`, `MantleGenerationContext`; chunk stages read the mantle only |
| Annotated settings | `IrisFeature`, `@Feature` (id, short JSON `key`, optional `root`), config annotations, `OptionsMetadata` |
| Area a stage writes | `reach(blocks)`, `reach(PackReach)`, `writesMantle(...)` on stage definitions, `PackSettings`, host `StageReach`, `ResolvedStage.reachBlocks`, `PipelineReach` |
| Settings described for studio | `@Desc`, `@Required`, `@MinNumber`, `@MaxNumber`, `@MinItems`, `@Snippet`, `@Ref`/`Reference`, `FeatureRegistry.registerSettings`, `FeatureRegistry.registerHeldType`, host `FeatureCatalog.heldTypes()`, host `LoadedPack.schema()` (`PackSchema`, `FeatureSchema`, `ObjectType`, `SchemaOption`, `ValueKind` with `HostKind`), `ModuleHost.describingHostTypesIn` |
| Geometry and mantle registration | `FeatureRegistry`, host `ModuleRegistry`, `FeatureCatalog` |
| Block states and palettes | `BlockState`, `BlockRegistry`, `BlockPalette`, `BlockSpec` (`BlockRegistry.resolve(BlockSpec)`, `blockEntity`), `PaletteSpec` and `PaletteSampler` (`BlockRegistry.palette`) |
| Noise from pack-described styles | `NoiseStyle`, `StyledRange`, `PackResources.noise()`, `NoiseFactory`, `NoiseSampler` |
| Values in settings | `BlockSpec`, `PaletteSpec`, `NoiseStyle`, `StyledRange`, `Range`, `BlockPosition`, `JigsawOverrides`, `Object` as `ValueKind.AnyKind` |
| Decisions over and fit of the platform's own structures | service `VanillaStructures` (`rule`, `starts`, `shift`, `reach`, `beforePieces`, `afterPieces`, `setup`), `VanillaStructureRule`, `VanillaStructureContext`, `VanillaStructureStart`, `VanillaStructureStartContext`, `VanillaStructureSetup`, `PlatformStart`, `StartMemo`, `StartReach`, `StructureFit` (`Start`, `Piece`, `ColumnVisitor`), `StructureStep`, `TerrainAdaptation`, `PieceKind`, `TemplateCells`, `world.BlockAccess`, host `FeatureCatalog.registrations(service)` |
| Pack-scoped resolution | `ResourceId`, `ResourceKey<T>`, `PackResources` (`find`, `ids`), `PackBiome`, `PackRegion` |
| Module-owned pack resources | `ResourceKind`, `FeatureRegistry.registerResourceKind`, `@ResourceRef`, host `ModuleHost.reservingFolders`, `FeatureCatalog.resourceKinds()`, `ResourceKindSchema`, `ValueKind.ResourceReferenceKind` |
| Module-owned pack checks | `Validator`, `ValidationContext`, `Problem`, `Severity`, `Subject`, `FeatureRegistry.registerValidator`, host `FeatureCatalog.validators()` |
| Per-world module state | `PackWorld` (`state`), `InvocationContext.world()`, host `WorldStates` |
| Module lifecycle across loads, hotloads and reloads | `ModuleLifecycle`, `FeatureRegistry.registerLifecycle`, host `FeatureCatalog.lifecycles()`, `LifecycleRunner` |
| Host questions answered by modules | `@Service`, `Providers`, `FeatureRegistry.registerService`, host `FeatureCatalog.services(type)` / `service(type)`; services `ObjectsAt`, `StructureLocator` (`LocateResult`), `SaplingGrowth`, `Loot`, `BlockDrops`, `EntitySpawns`, `AmbientEffects`; values `ItemDrop`, `EntitySpec`, `ItemRegistry` (`PackResources.items()`) |
| Terrain and settings sampling | `TerrainSampler`, `BiomeSample`, `TerrainSample`, `FeatureSettings` |
| Dense chunk output | `Hunk<T>`, `GenerationContext.hunk`, reference `ArrayHunk<T>` |
| Sparse geometry and transforms | `ObjectGenerator`, `GeneratedObject`, `ObjectTransform`, `ObjectTransformer` |
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
and module, on a field type it cannot describe, a `@Ref` on a field that is not text, a class without
a working no-argument constructor, a bad or repeated snippet name, or a pack class's snippet name
outside its module's folder. Defaults come from an instance made by that constructor. Options are in
declaration order.

Packs write settings under the short key the feature declares, as the settings object, or as the value of the
one option its `root` names; there is no second form. A key is unique per target across modules and the host's own
JSON fields. The description is in a fixed order and holds module entries only.

Modules compile against the API `compileOnly` and never ship it. At runtime they can use the API
(`com.volmit.*`), the host packages the host chooses to expose, the JDK and the classes in their
own pack's jars. They may use any library they ship in the pack, never Iris's copy. JDK modules
outside the platform class loader, such as `jdk.compiler`, are not available, and neither are JDK
packages not exported to unnamed modules, such as `jdk.internal.misc`, unless the server is started
with `--add-exports <module>/<package>=ALL-UNNAMED`. `sun.misc.Unsafe` from `jdk.unsupported` is the
supported way for modules to use Unsafe. A class Iris has but hides, a class of an unavailable JDK
module or non-exported JDK package, or one missing from an exposed package fails the pack load,
naming the jar and the classes. A class nobody has (an optional dependency) only warns, unless
its package is one the module declares optional with `ModuleInfo.withOptionalPackages(...)`
(`PackageName`). That declaration never excuses a load error.

Stage bodies and service providers keep no state; what a module keeps for a world lives in that world's state store,
created once per type and world. A world's state lives while the world runs with one pack snapshot: a hotload or a
reload of the pack's resources starts the world again with empty state. A service names how its providers combine
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
  decode them as Iris does, and render each `HostKind` with Iris's own description of the class.
- Hand modules heights above the dimension's bottom (0 = bottom block, whatever the platform's minimum
  height is) in every value that carries Y: samples, bounds, block views, placement edits, the mantle,
  hunks. X and Z stay world coordinates. Convert in one place at the boundary.
- Preserve pack field names and defaults, seed derivation, RNG draw order, coordinate
  conventions and iteration order where existing worlds must not change. Per-stage streams do not
  reproduce Iris's current shared draws; matching them where worlds must stay the same is adapter
  work inside the ported stages.
- Derive one seed per placement (world seed and the placement's stable identity) and per chunk
  (world seed and chunk position) and pass it to `PlacementRunner.place` / `ChunkRunner.generate`.
- Prepare the area a placement may read or write, including neighbors: the placement pipeline's
  `reach()` around the placed object's bounds, each mantle layer by its own maximum. Never clip.
- Work out each mantle stage's reach once per pack load (`ResolvedStage.reachBlocks(PackSettings)`),
  answering `PackSettings.declared(type)` with every entry of the dimension and of the regions and
  biomes it can reach.
- Run each mantle stage (`MantleRunner.generate(stage, ...)`) as its own pass, in resolved order: for
  every chunk within that stage's reach plus the reach of every later stage of a chunk, each chunk once,
  before the next stage and before that chunk generates, with the mantle widened by the stage's reach.
  Give chunk stages that chunk's mantle; `ChunkRunner` makes it read-only.
- Make placement edits atomic across blocks, block entities, markers and mantle in generation
  storage. Abort chunk publication when any stage fails.
- Preserve unknown persisted mantle layers or fail explicitly.
- Build one `ModuleHost` at startup with the built-in modules, exposed packages, reserved JSON
  fields. Its methods accumulate, so the configuration can be
  built in steps. Load each pack with `LoadedPack.load(packRoot, host)`
  and close it only after nothing built from it is in use.
- Pass a `System.Logger` that writes to the server log to `ModuleHost.loggingTo(...)`, so the
  per-pack module summary and missing-classes warning are visible, and a cache directory of the host
  to `ModuleHost.copyingJarsTo(...)` for the jar copies.
- Provide the API at runtime from the plugin's class loader, for example by shading it into the
  Iris plugin jar. Pack jars must not ship it.
- Pass the packages of built-in modules whose stages packs may require to
  `ModuleHost.exposing(...)`. They must be on the loader that defines the
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
  attribute or item flag) with a warning, and run the checks that come with an `EntitySpawns.Attempt` (light, surface,
  free space) before spawning. Report how many entities appeared with `EntitySpawns.spawned`.
- `AmbientEffects.due`: ask for the player's last sampled position (not every tick's), carry out an `Effect` in the order
  sound, particles, commands, potion, skip a part the platform lacks, and give no potion to a player who already has the
  same one with a higher amplifier.
- Decode IrisApi's value types like settings: `BlockSpec`, `PaletteSpec`, `NoiseStyle`, `StyledRange`, `Range` and
  `BlockPosition` from the JSON shapes in their docs, `Object` as maps, lists, `Double`s, strings and booleans.
  Read a `"snippet/<name>"` or `"snippet/<folder>/<name>"` string of a `@Snippet` type from
  `snippet/<folder>/<name>.json`. Complete `NOISE_STYLE`, `PACK_EXPRESSION` and `PACK_IMAGE_MAP` references;
  rendering a value type with the host's own description of the same JSON is allowed.
- Serve `PackResources.noise()`: build each style's noise from the host's generators, expressions and image maps,
  the same values for the same style and seed, cached per pack snapshot; an unknown style name is flat, a missing
  expression falls back to the base style, and a missing image map fails.
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
  apply its jigsaw overrides, keep its memo, record its identity with every start the host publishes, and ask again (same
  answer) when locating or verifying a start found in the world.
- Ask `setup` per dimension before its worlds build their structure state and when installing data packs; multiply
  frequencies per structure set (0.01 to 16), protect the platform's definitions in every installed data pack once
  any dimension of any loaded pack asks for it.

## Open

- Terrain/biome providers, pack transformations, structure assembly and the runtime family.
