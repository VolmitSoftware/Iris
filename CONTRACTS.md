# Contracts

[DESIGN.md](DESIGN.md) is the authoritative description. This file maps requirements to the
types that carry them and lists what the Iris adapter still owes.

## Adopted seams

| Requirement | Contract |
| --- | --- |
| Module identity and composition | `IrisModule`, `ModuleInfo` (with optional packages), `StageGraph` |
| Module jars in a pack | `META-INF/services/com.volmit.IrisModule`, host `PackModules`, `DiscoveredModule`, `MissingClasses` |
| Module-owned work and ordering | `Stage`, `StageRef`, stage definitions, `requires`/`softAfter`/`softBefore` |
| Activation by configuration | `when(settingsType)`, host `ConfiguredSettings` |
| Typed results between modules | stage outputs, `StageContext.output` |
| Per-block work with single-pass cost | `BlockStage`, fused block passes |
| Pack-load validation and diagnostics | host `StageGraphBuilder`, `ResolvedGraph`, `LoadedModules` |
| Reference execution | host `PlacementRunner`, `ChunkRunner` |
| Annotated settings | `IrisFeature`, `@Feature`, config annotations, `OptionsMetadata` |
| Geometry and mantle registration | `FeatureRegistry`, host `ModuleRegistry`, `FeatureCatalog` |
| Block states and palettes | `BlockState`, `BlockRegistry`, `BlockPalette` |
| Pack-scoped resolution | `ResourceId`, `ResourceKey<T>`, `PackResources` |
| Terrain and settings sampling | `TerrainSampler`, `BiomeSample`, `TerrainSample`, `FeatureSettings` |
| Dense chunk output | `Hunk<T>`, `GenerationContext.hunk`, reference `ArrayHunk<T>` |
| Sparse geometry and transforms | `ObjectGenerator`, `GeneratedObject`, `ObjectTransform`, `ObjectTransformer` |
| Buffered placement writes | `PlacementRequest`, `PlacementAccess`, host-owned `PlacementEdit` |
| Typed generation and persistent storage | `mantle` package |

Stage bodies hold no invocation state. Everything per placement or chunk travels through the
context, outputs and accumulators.

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

There is no API version gating. Packs ship their module jars, so their compatibility with the
running Iris is the pack publisher's responsibility.

## Requirements for the Iris adapter

- Port the engine as ordinary modules. Keep `iris:*` stage ids stable once packs or addons
  depend on them, because soft references and diagnostics use them.
- Build `ConfiguredSettings` per placement and per chunk from decoded pack JSON, with explicit
  precedence between dimension, region, biome and placement declarations.
- Preserve pack field names and defaults, seed derivation, RNG draw order, coordinate
  conventions and iteration order where existing worlds must not change.
- Prepare the full area a placement or chunk may read or write, including neighbors. Never clip.
- Make placement edits atomic across blocks, block entities, markers and mantle in generation
  storage. Abort chunk publication when any stage fails.
- Preserve unknown persisted mantle layers or fail explicitly.
- Load each pack's jars with `PackModules`, pass them with the built-in modules to
  `LoadedModules.load`, and close `PackModules` only after nothing built from it is in use.
- Route `System.Logger` output (logger `com.volmit.host.PackModules`) to the server log, so the
  per-pack module summary and missing-classes warning are visible.
- Provide the API at runtime from the plugin's class loader, for example by shading it into the
  Iris plugin jar. Pack jars must not ship it.
- Pass the packages of built-in modules whose stages packs may require to
  `PackModules.load(packRoot, exposedHostPackages)`. They must be on the loader that defines the
  API. Keep them narrow: everything in an exposed package and its subpackages becomes visible to
  packs and is part of what packs can break on. Stage output types and settings types that pack
  code uses through those stages must be in exposed packages too. The API is always exposed.
- Consider showing `PackModules.missingClasses()` in studio.

## Open

- Per-stage reach declarations, replacing the removed modifier radii.
- Per-stage RNG streams, so adding a module does not shift other modules' random draws.
- Terrain/biome providers, pack transformations, structure assembly and the runtime family.
