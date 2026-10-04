# Default Iris module

This module owns the existing built-in feature sources extracted from `core`. This is the
behavior-preserving extraction stage: packages, pack fields, seeds, algorithms and engine
dependencies are retained. These implementations have not yet been ported to IrisApi.

## Feature inventory

All paths below are relative to `src/main/java/art/arcane/iris/engine`.

| Family | Sources |
| --- | --- |
| Terrain, biome and decoration actuators | `actuator/` |
| Surface, ceiling, shoreline, sea-floor, sea-surface and floating decoration | `decorator/` |
| Cave application, deposits, post-processing, floating terrain, perfection and custom blocks | `modifier/` |
| Overworld, islands, enclosure and superflat composition | `mode/` |
| Cave generation, object distribution, floating objects and structure foundations/carving | `mantle/components/` |
| Trees, crystals, fungi, coral, formations and ruins | `object/procedural/` |
| Height modes, stilts, ceiling hanging, vacuum, paint, floating, slope, support, collision, boring and snow | `object/placement/` |
| Terrain generators, ores, deposit variants, cave profiles and floating child biomes | `object/terrain/` |
| Structure distribution, jigsaw settings, graph compilation and assembly | `object/structure/`, `framework/structure/`, selected `framework/*.java` |
| Prefabs, transforms, tile data and matter objects | `object/prefab/` |
| Block palettes, decorator settings and noise/expressions | `object/material/`, `object/decoration/`, `object/noise/` |
| Images, maps, bindings, masks and compiled sampling | `object/image/`, `image/` |
| Entities, ambient effects, spawning, loot, markers and pack transformations | `object/entity/`, `object/loot/`, `object/marker/`, `object/mod/` |
| Dimension, region and biome configuration, annotations and shared settings values | `object/world/`, `object/annotations/`, `object/value/`, `object/compatibility/` |

Core retains engine lifecycle, pack loading, compiled invocation data, storage, scheduling,
platform interfaces/adapters, native Minecraft generation coordination, and runtime integrations.
For example, entity/loot/tree definitions live here; the platform services that apply them remain
with the host. Moving those services to the public runtime interfaces is later work.

## Build ownership during extraction

Core and these sources refer to each other. `gradle/default-module.gradle` adds this source root
to the host compilation so existing callers and every platform distribution keep the same class
names and bundled bytecode. There is one source copy and one compilation; no dependency cycle is
introduced between Gradle projects. The binary still ships with the host.

`source-patterns.txt` defines source ownership. `:core:verifyDefaultModuleOwnership` rejects
feature sources moved back into core, undeclared additions, and missing feature families. It runs
before compilation. The existing Bukkit purity ceiling covers both roots; extraction does not
reset or raise it. Source-based integration checks also inspect both roots.

The sibling workspace project exposes this tree through `IrisModule/src/builtin/java`. That
directory is a relative symbolic link, so editing it edits these tracked sources. Keeping the
canonical files in the Iris repository makes the extraction a complete, reviewable commit and
keeps a fresh Iris checkout buildable. API examples remain in `IrisModule/src/main/java/com/volmit/example`.

From the Iris repository:

```sh
./gradlew :core:compileJava :core:bukkitPurityRatchet :core:defaultModuleSourcesJar
./gradlew :core:test
```

Feature tests stay in `core/src/test/java` because they exercise the legacy host integration.
The source archive contains only this module. The existing host source archive contains both
roots, matching the combined binary.

The next stage replaces engine dependencies family by family with public registration,
generation, geometry, placement, resource, storage and runtime interfaces. Independent addon
compilation and a separate feature binary belong to that stage.
