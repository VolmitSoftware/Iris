# Migration requirements

The authoritative design is [DESIGN.md](DESIGN.md). This replaces the earlier deferred-contract
backlog after the decision to establish the API first and modularize Iris afterwards.

## Adopted seams

| Requirement | Current contract |
| --- | --- |
| Annotated configuration and construction | `IrisFeature`, `FeatureRegistry`, typed factories, `OptionsMetadata` |
| Registered feature catalog and ordering | `ModuleRegistry`, `FeatureCatalog`, `GenerationPlan`, `PlacementPipeline` |
| Block states, predicates and palettes | `BlockState`, `BlockRegistry`, `BlockPalette` |
| Pack-scoped resolution | `ResourceId`, `ResourceKey<T>`, `PackResources` |
| Terrain, biome and effective feature sampling | `TerrainSampler`, `BiomeSample`, `TerrainSample`, `FeatureSettings` |
| Dense local output | `Hunk<T>`, reference `ArrayHunk<T>` |
| Sparse procedural geometry | `ObjectGenerator`, `GeneratedObject`, `ObjectBlock`, `ObjectMarker` |
| Host-owned transforms | `ObjectTransform`, `ObjectTransformer` |
| World-space placement and hooks | `PlacementRequest`, `PlacementPlan`, `PlacementModifier` |
| Buffered placement writes | `PlacementAccess`, host-owned `PlacementEdit` |
| Typed persistent/generation storage | existing `mantle/` contracts |

Dense output, sparse geometry and world-space placement remain distinct. Configured modifiers
hold read-only snapshots; temporary state is carried by contexts and immutable results.

## Requirements for the Iris adapter

- Preserve pack field names/defaults and explicit target-specific inheritance/combination rules.
- Preserve seed derivation, random consumption, coordinate conventions and iteration order.
- Bridge current engine ordering, including planned Mantle work, insertion and finishing steps.
- Prepare full read/write areas and predecessor work; never silently clip requested reach.
- Provide atomic generation-storage edits across blocks, block entities, markers and mantle.
- Preserve unknown persisted layers or fail explicitly; provide codecs for platform values.
- Handle chunk publication, native structure integration, cleanup and reload lifetimes in the host.

## Later extension families

Terrain/biome providers, pack transformations, structure assembly/import controls and runtime
spawning/effects/drop/tree/lifecycle contracts are designed as separate extension families in
DESIGN.md. They require feature ports to settle their exact Java methods. They must not be
implemented by exposing Engine, IrisData, Bukkit or native mutable handles.
