# IrisApi

Java 25 contracts for Iris modules, with a small host-independent reference implementation.
Main code depends only on the JDK. No Bukkit, Iris implementation, Gson, or Lombok types appear
in the public contracts. The draft interfaces may change before a stable release.

The design-first migration plan is in [DESIGN.md](DESIGN.md). Existing Iris features have not
been modularized or wired into this project.

## Three kinds of executable feature

```java
registry.register(DeepStoneFeature.class, BlockState.class, DeepStoneModifier::new);
registry.registerObject(StoneColumnFeature.class, StoneColumnGenerator::new);
registry.registerPlacement(FoundationFeature.class, FoundationModifier::new);
```

Each settings class implements `IrisFeature` and declares `@Feature`: a namespaced feature ID,
a display name, a JSON field and its attachment targets. Constructor/field initializers retain
defaults. Factories accept only the configured settings and snapshot everything they need.

| Kind | Interface | Result |
| --- | --- | --- |
| Chunk generation | `GenerationModifier<T>` | Edits an invocation-owned local `Hunk<T>` |
| Procedural geometry | `ObjectGenerator` | Returns an immutable sparse `GeneratedObject` |
| Object placement | `PlacementModifier` | Prepares, validates and modifies one buffered world-space placement |

Placement features attach only to `OBJECT_PLACEMENT`. Chunk and geometry features may attach
to any declared supported target. Configuration identity is separate from modifier ordering
identity. Host settings resolution determines where each configured occurrence applies.

See the sibling [IrisModule sources and build entry](../IrisModule/README.md) for the current built-in module.

## Resources, blocks and sampling

- `ResourceId` requires an explicit namespace. `ResourceKey<T>` pairs it with a public type.
- `PackResources` exposes immutable resources and a block registry from one pack snapshot.
  Reload always changes `snapshotId`; caches must not survive across it.
- `BlockState` has value equality and canonical, sorted property keys. It describes a state;
  `BlockRegistry` checks platform availability/properties and supplies traits, tags and support.
- `BlockPalette` and `NoiseSampler` are reusable typed resources. The host owns their loading.
- `TerrainSampler` reads the prepared terrain field and pack biome identity, not current edits.
- `BlockView` reads prepared world-space generation blocks. Empty space is an air state.
- `FeatureSettings` looks up applicable configured settings by target and world position.
  Dimension, region, biome and placement declarations remain distinct; no implicit merging.

All invocation views expire when the invocation ends. No native platform object or live-world
handle is exposed. Modifiers must not retain views or start detached tasks using them.

## Reference registry and ordering

```java
var registry = new ModuleRegistry(reservedHostFields);
module.register(registry);
var catalog = registry.freeze();
var registration = catalog.generation(DeepStoneFeature.class, BlockState.class);
var configured = registration.configure(occurrenceId, decodedFeature);
var plan = GenerationPlan.resolve(configuredModifiersIncludingHostSteps);
plan.execute(host::execute);
```

`ModuleRegistry` checks annotated concrete feature types, metadata, duplicate IDs, JSON field
collisions across feature kinds, placement targets, output types and mantle registrations.
A failed individual registration leaves the registry unchanged. Registration ends at `freeze()`;
the resulting catalog is immutable. If a module callback fails, discard the whole registry build.

`GenerationPlan` and `PlacementPipeline` resolve immutable dependency declarations. Missing
requirements, self-edges, cycles, duplicate occurrences and conflicting same-ID implementations
fail at pack load. Ready identities use lexical order; repeated configured instances use lexical
occurrence order. Repeated instances must agree on dependencies and generation output type.
Reach may vary per instance. Dependencies and modifier IDs require namespaces.

```java
return Dependencies.requires("iris:terrain")
        .afterIfPresent("example:caves")
        .beforeIfPresent("iris:ores");
```

Required declarations do not activate features. Optional edges apply only when the identity
exists. Independent modifiers are not automatically safe to run in parallel. Chunk generation
and placement have separate dependency graphs. The host bridges its engine lifecycle into the
chunk graph and rejects edges that conflict with that lifecycle.

`GenerationPlan` orders across output types but delegates execution to the host, which prepares
and releases the correctly typed output/context for each applicable occurrence. It does not
implement chunk scheduling or rollback. A failed invocation must prevent chunk publication.
`ArrayHunk<T>` is a small checked dense-volume reference adapter.

## Geometry and placement

`GeneratedObject` contains local bounds, an anchor, sorted sparse blocks, optional immutable
block entity payloads and ordered markers. Missing sparse entries and explicit air blocks are
different. The host owns object transforms, property orientation, rasterization and variant caches.

`PlacementRequest` aligns the object's local anchor with a world anchor. Its arithmetic is checked;
maxima are exclusive. `PlacementPlan` contains proposed world-space writes after block mapping.
Its `writtenBounds()` covers those object block writes, excluding skipped blocks and markers.
It does not include additional writes made by finishing hooks.

`PlacementPipeline` executes this lifecycle:

1. Prepare all configured modifiers; any rejection stops the attempt.
2. Map or skip each candidate block; build the complete proposed plan.
3. Check bounds and run host/modifier validation before buffering mutations.
4. Run `beforePlace` hooks for boring or other preparation.
5. Buffer object blocks, block entity data and markers.
6. Run `afterPlace` hooks for foundations, vacuum, snow or other finishing.
7. Commit once; close on success, rejection or exception.

The host supplies a fresh `PlacementEdit`, correctly prepared bounds, serialized storage,
per-occurrence contexts/RNGs, and read-only intrinsic validation. Hook `PlacementAccess` exposes
an overlay bounded to the footprint plus declared horizontal reach, without commit/close rights.
Its mantle view has the same restricted bounds and forbids nested edits. All buffered mutations
must be validated by the host adapter, including writes from hooks.

Commit publishes block/entity/marker/mantle changes together or leaves parent generation storage
unchanged. This is a contract for a future storage adapter; the reference runner alone cannot
provide atomic storage. It is not a live-world, disk or crash-recovery guarantee.

## Mantle

The existing typed mantle contracts remain: namespaced keys, registered types, versioned codecs,
world-space bounds, ordered sparse visits, generation/persistent lifetimes and buffered edits.
Only layer owners register shared keys; consumers use their definitions. Generation-only layers
also require codecs. Missing reads must not allocate storage. Unknown persisted layers must be
preserved or cause an explicit failure, never silent loss.

`GenerationModifier.mantleRadius()` and `PlacementModifier.writeRadius()` declare horizontal reach.
The host prepares bounds and predecessor work and excludes competing writes. Generation cleanup
waits for all consumers, including neighboring chunks. Mantle writes do not implicitly edit a Hunk.

## Remaining host work

Module discovery/version compatibility, JSON decoding and value validation, schema generation,
resource loaders, effective settings/biome resolution, legacy random compatibility, native block
adapters, storage/codecs, cross-chunk scheduling, engine lifecycle bridging and Iris feature ports
are not implemented here. Terrain/biome provider and runtime extension designs are documented
in [DESIGN.md](DESIGN.md); they are not yet Java interfaces.

`OptionsMetadata` inspects inherited/private fields, skips static/transient state and validates
annotation bounds. It does not instantiate settings, extract defaults or validate supplied values.
A decoder must invoke constructors and reject unsupported metadata/types explicitly.

## Validation

```sh
./gradlew check jar sourcesJar
```

Tests cover real registry/factory binding, conflicts, deterministic ordering, graph errors,
immutable values, checked coordinates and placement rejection/failure/commit behavior. The
example module adds generated geometry and foundation composition tests against a test host.
There is no running Minecraft integration or production storage adapter in these tests.
