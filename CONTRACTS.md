# Contracts

[DESIGN.md](DESIGN.md) is the authoritative description. This file maps requirements to the
types that carry them and lists what the Iris adapter still owes.

## Adopted seams

| Requirement | Contract |
| --- | --- |
| Module identity and composition | `IrisModule`, `ModuleInfo` (with optional packages), `StageGraph` |
| Loading and unloading a pack | host `LoadedPack`, `ModuleHost` |
| Module jars in a pack | `META-INF/services/com.volmit.IrisModule`, host `PackModules` (jar copies), `DiscoveredModule`, `MissingClasses` |
| Namespace ownership | module-prefixed stage, feature and mantle ids, `ModuleInfo.ownsStage`/`ownsId`, `ModuleHost.reservingNamespaces` |
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
| Annotated settings | `IrisFeature`, `@Feature` (id as JSON key, built-in alias), config annotations, `OptionsMetadata` |
| Area a stage writes | `reach(blocks)`, `reach(PackReach)`, `writesMantle(...)` on stage definitions, `PackSettings`, host `StageReach`, `ResolvedStage.reachBlocks`, `PipelineReach` |
| Settings described for studio | `@Desc`, `@Required`, `@MinNumber`, `@MaxNumber`, `@Snippet`, `@Ref`/`Reference`, `FeatureRegistry.registerSettings`, host `LoadedPack.schema()` (`PackSchema`, `FeatureSchema` with `shorthand()`, `ObjectType`, `SchemaOption`, `ValueKind` with `HostKind`), `ModuleHost.describingHostTypesIn` |
| Geometry and mantle registration | `FeatureRegistry`, host `ModuleRegistry`, `FeatureCatalog` |
| Block states and palettes | `BlockState`, `BlockRegistry`, `BlockPalette`, `BlockSpec` (`BlockRegistry.resolve(BlockSpec)`, `blockEntity`) |
| Noise from pack-described styles | `NoiseStyle`, `StyledRange`, `PackResources.noise()`, `NoiseFactory`, `NoiseSampler` |
| Values in settings | `BlockSpec`, `NoiseStyle`, `StyledRange`, `Range`, `BlockPosition`, `Object` as `ValueKind.AnyKind` |
| Decisions over the platform's own structures | `VanillaStructureHook`, `VanillaStructureRule`, `VanillaStructureContext`, `FeatureRegistry.registerVanillaStructureHook`, host `FeatureCatalog.vanillaStructureHooks()` |
| Pack-scoped resolution | `ResourceId`, `ResourceKey<T>`, `PackResources` (`find`, `ids`), `PackBiome`, `PackRegion` |
| Module-owned pack resources | `ResourceKind`, `FeatureRegistry.registerResourceKind`, `@ResourceRef`, host `ModuleHost.reservingFolders`, `FeatureCatalog.resourceKinds()`, `ResourceKindSchema`, `ValueKind.ResourceReferenceKind` |
| Module-owned pack checks | `Validator`, `ValidationContext`, `Problem`, `Severity`, `Subject`, `FeatureRegistry.registerValidator`, host `FeatureCatalog.validators()` |
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

Packs write settings under the feature id. Only built-in modules may set an alias, a short key that
existing pack JSON uses; a pack module setting one fails the load. Aliases are unique per target and
may not equal a reserved host field. The description is in a fixed order and holds module entries only.

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

A resource kind reads one folder directly below the pack root, with a class no other kind uses; a folder two
kinds claim, or one the host reads itself, fails the pack load naming both owners. Resources of a kind are
values, read once per pack snapshot. Validators only report: an `ERROR` blocks the pack, a `WARNING` is shown.

IrisApi's value types are values: the host decodes them like settings, and nothing changes them afterwards.
Noise belongs to the host; modules describe it and get samplers built from the description. A vanilla structure
hook answers per structure and world, never per position.

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
- Decode a value that is not a JSON object into settings with exactly one option
  (`FeatureSchema.shorthand()`), and render the schema to accept both forms.
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
  fields and `reservingNamespaces("iris")`. Its methods accumulate, so the configuration can be
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
- Decode module settings under the feature id and, for built-ins, the alias. Fail a JSON object that
  holds both, naming the two keys.
- Render `pack.schema()` into studio's files: merge each target's `fields(target)` into the schema
  of the matching Iris file type (dimension, region, biome, object placement) under the key and the
  alias, write `types()` as definitions, a `/snippet/<name>` schema per entry of `snippets()`, and
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

- Decode IrisApi's value types like settings: `BlockSpec`, `NoiseStyle`, `StyledRange`, `Range` and
  `BlockPosition` from the JSON shapes in their docs, `Object` as maps, lists, `Double`s, strings and booleans.
  Read a `"snippet/<name>"` or `"snippet/<folder>/<name>"` string of a `@Snippet` type from
  `snippet/<folder>/<name>.json`. Complete `NOISE_STYLE`, `PACK_EXPRESSION` and `PACK_IMAGE_MAP` references;
  rendering a value type with the host's own description of the same JSON is allowed.
- Serve `PackResources.noise()`: build each style's noise from the host's generators, expressions and image maps,
  the same values for the same style and seed, cached per pack snapshot; an unknown style name is flat.
- Resolve `BlockSpec`s in `BlockRegistry` as packs expect (pack block, platform block, backup, air) and turn their
  `tileData` into the host's block entity format.
- Ask every vanilla structure hook (`catalog().vanillaStructureHooks()`) before the platform starts one of its
  structures and when it locates one, combine the rules with `VanillaStructureRule.and`, keep the result per world,
  structure and pack snapshot, and apply it on every platform: block the platform's starts of a blocked
  structure, and report a structure a module places as that module's. A hook that throws fails naming its module.

## Open

- Placing the platform's structures where a module chooses (Iris's native structure planner): a hook method
  returning starts per chunk, with the terrain fitting and foundations the host builds around them.

- Terrain/biome providers, pack transformations, structure assembly and the runtime family.
