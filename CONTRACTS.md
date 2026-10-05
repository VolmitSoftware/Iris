# Contracts

[DESIGN.md](DESIGN.md) is the authoritative description. This file maps requirements to the
types that carry them and lists what the Iris adapter still owes.

## Adopted seams

| Requirement | Contract |
| --- | --- |
| Module identity and composition | `IrisModule`, `ModuleInfo`, `StageGraph` |
| Module jars in a pack | `META-INF/services/com.volmit.IrisModule`, host `PackModules`, `DiscoveredModule` |
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
- Make built-in modules visible to the loader that defines the API, or pack jars cannot require
  their stages.

## Open

- Per-stage reach declarations, replacing the removed modifier radii.
- Per-stage RNG streams, so adding a module does not shift other modules' random draws.
- Terrain/biome providers, pack transformations, structure assembly and the runtime family.
- Version gating: modules do not declare which API version they target, so an incompatible jar
  fails with a linkage error at load or later at install or run time.
