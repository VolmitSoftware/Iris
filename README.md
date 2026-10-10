# Iris default module

This project owns the built-in Iris feature and setting sources under
`src/main/java/art/arcane/iris/engine`. The files retain their existing packages, pack fields and
direct dependencies on Iris internals.

Ported features are IrisApi modules that every pack loads before its own: object placement
(`iris:prepare`, `iris:anchor`, `iris:validate`, `iris:bore`, `iris:blocks`, `iris:stilt`,
`iris:vacuum`, `iris:snow`), the mantle, and chunk generation. Every mantle stage is a pass of Iris's
mantle of its own, in the stage graph's order: `iris:carve/caves`, `iris:objects/place`,
`iris:floating/objects`, `iris:structure/place`, so a pack's mantle stage can order itself after any of
them. Chunk generation then runs, in this order: `iris:biome`, `iris:terrain`, `iris:carve` (cutting the
planned caves), `iris:post`, `iris:floating` (solids), `iris:deposit`, `iris:matter`, `iris:decorant`,
`iris:floating` (decoration), `iris:perfection` and `iris:custom`. There are no world modes and no Iris
mantle components outside the stage graph any more. Their stage constants, and the settings and output
types packs may read, live in `src/main/java/art/arcane/iris/builtin`, the package Iris exposes to pack
modules; their stage bodies live beside the features they replace.

Built-in modules also own pack resources and checks of their own. `iris:structure` reads structures, jigsaw
pools and jigsaw pieces as resource kinds (`StructureResources`), from the folders and files packs have always
used, and checks them with its validator (`StructureValidator`: assembly samples and vanilla structure
replacement) wherever Iris validates a pack; Iris core has no loader or validator of its own for them any more.

Built-in modules answer Iris through IrisApi services instead of Iris reading their features: `iris:objects`
provides `ObjectsAt` (the objects a chunk would get, for the object search and studio tools) and `iris:structure`
provides `StructureLocator` (`StructureLocating`: the pack's structure keys for Iris's structure index, the ones a
world places, and their nearest start for the find commands). `iris:structure` also has a lifecycle hook that drops a
world's structure index when the world stops, which includes every reload of the pack's structure files.
`iris:structure` also provides `VanillaStructures` (`StructureVanillaStructures`) for the platform's own structures:
which ones its placements replace or the dimension's `importedStructures` disables, the starts its `nativeStructures`
placements make, the shift and band of the platform's starts, and the whole terrain fit of every start (SOURCE,
PRESERVE, VACUUM, ENCASE, BORE, FORCE_CARVE, stilts) in `engine/framework/structure/fit`, written against IrisApi's
`StructureFit` and `BlockAccess` only. A start carries its fit as a memo (its terrain and stilts JSON), so every
chunk it reaches and every restart fits it the same way. Object place modes are data (`PlaceMode`: height sampling,
stilt, `VacuumProfile`, `PlaceTrait`s) that the placement stages ask; the pack JSON keeps the mode names.
`iris:objects` also provides `SaplingGrowth` (`TreeGrowing`: the tree a sapling grows into, as placed blocks, and which
objects are trees) and says which biomes and regions place an object (`ObjectLocating`, for the object search).
`iris:loot` owns the loot table resource kind (`LootResources`, folder `loot`), checks the tables and the references to
them (`LootValidator`), and provides `Loot` (`LootGiving`: which tables a container draws from and the `ItemDrop`s a
table gives) and `BlockDrops` (`BlockDropping`). `iris:spawn` owns spawners, entities and markers (`SpawnResources`,
folders `spawners`, `entities`, `markers`) and provides `EntitySpawns` (`EntitySpawning`: what to spawn in a chunk or at
a marker, as `EntitySpec`s; `EntityResolution` makes the entity's random choices) and `AmbientEffects` (`AmbientEffecting`:
the effects due around a player, with their random values). Iris's Bukkit code creates the items and entities from those
values (`BukkitItemStacks`, `BukkitEntitySpawner`, `BukkitAmbientEffects`); the modded adapters, which cannot name IrisApi
types, ask through the host-typed `ContentServices`.
The settings of both modules are `builtin.loot` and `builtin.spawn` types of IrisApi annotations only: `iris:loot` declares
`loot` (object, also the shape of an entity's `loot`) and `blockDrops` (list) for dimensions, regions and biomes;
`iris:spawn` declares `entitySpawners` (list of spawner keys) for the same and `effects` (list, `iris:spawn/effects`) for
regions and biomes. The loot tables, spawners, entities and markers are plain module types too (`LootTable`, `Spawner`,
`EntityDefinition`, `SpawnMarker`). `PlacementLoot` picks the table an object placement gives one of its chests.
`iris:decorant` keeps its per-world `Decorating` in the world's state store (`context.world().state(...)`); the other
built-ins still reach the same store through `IrisChunk.state(...)` / `engine.getGeneration().state(...)`.

Settings move off Iris's classes one feature at a time. `iris:deposit` is the first whose settings are IrisApi types
only: `DepositSettings` holds `DepositGenerator`s (blocks as `BlockSpec`, its enums beside it in
`art.arcane.iris.builtin.deposit`), and packs write `"deposits": [...]`, as they always have (`@Feature(key = "deposits", root = "generators")`: the key holds the one option's list). What needs Iris (resolving the
palette, ore checks, clump shapes per world seed) lives in the module's own code, `ResolvedDeposit`.

Settings that live inside a class of Iris's own, such as an object placement's clamp, stilt, vacuum and edit settings, its slope condition
or a procedural coral, are module-owned classes too (`art.arcane.iris.builtin.validate`,
`.stilt`, `.vacuum`, `.blocks`, `.prepare`, `.carve`, `.floating`, `.objects.*`). The module registers each with
`registry.registerHeldType(...)` so studio describes it from the module's own annotations; packs write the same JSON as before. Behaviour
that needs Iris (noise built from the styles, blocks resolved through the pack, baked object variants) lives in `Resolved*` classes beside the
worker (`ResolvedReplace`, `ResolvedCaveProfile`, `ResolvedFloatingChild`, `ResolvedProceduralPlacement`).

`iris:decorant` is the second: `DecorantSettings` (biome level) holds `Decorator`s in `art.arcane.iris.builtin.decorant`,
written `"decorators": [...]` as always (`@Feature(key = "decorators", root = "decorators")`).
`ResolvedDecorator` resolves one decorator's blocks and noise for a world; `BiomeDecorators`, the world's state, keeps them
per biome and place. Other modules that decorate (`iris:cave`'s ceilings, floating islands) ask it. A decorator's noise
seed is the world's decorator seed, its biome's key and its place in the biome's list. The module's `warmUp` builds
them ahead of generation; `iris:terrain` and `iris:objects` warm their ore generators, placements and procedural
objects the same way.

`iris:terrain` and `iris:post` own the fields that shape a column's blocks. Per biome: `layers`, `seaLayers`, `caveCeilingLayers` (lists of
`PaletteLayer`, the old biome palette layer, in `art.arcane.iris.builtin.terrain`), `slab` and `wall` (one `PaletteLayer` each), `lockLayers` (a
boolean) and `lockLayersMax` (a number). Per dimension: `rockPalette` and `fluidPalette` (a `PaletteSpec` each) in `art.arcane.iris.builtin.terrain`, and
`postProcessing`, `postProcessingSlabs`, `postProcessingWalls` (booleans) in `art.arcane.iris.builtin.post`. A key a pack leaves out has the
default Iris always had (one grass layer, no slab or wall, stone and water, post processing on). `ResolvedLayer` resolves one layer's blocks and noise
for a pack, `BiomeLayers`, the world's state, keeps them and stacks a biome's layers into the blocks of a column, and `TerrainPalettes` is the world's
rock and fluid as a block for every column; the terrain, cave, floating and post stages ask them. The slope limit of a layer, a decorator and an
object placement is the one `SlopeClip`.

Caves, floating islands and ores are settings of their modules too, written under the keys they always had. `iris:carve` owns
`caveProfile` (dimension, region, biome; an object, or a `snippet/cave-profile/...` reference), `carving` (dimension; a list of bands) and
`carvingEnabled` (dimension; a boolean), `carvingBiome` (biome; a biome key) and `caveMinDepthBelowSurface` (biome; a number). `iris:floating`
owns `floatingChildBiomes` (biome; a list) and `mergeFloatingChildBiomes` (biome; a boolean). `iris:terrain` owns `ores` (dimension, region,
biome; a list of generators) and `hideOresForHiddenOre` (dimension; a boolean); `iris:deposit` owns `depositVariants` (dimension, region, biome; a list)
and `oreDepositFrequencyMultiplier` and `oreDepositSizeMultiplier` (biome; numbers). Behaviour that needs Iris lives in the `Resolved*` classes beside the
workers: `ResolvedOre`/`ResolvedOres` (blocks and chance noise per world, `TerrainOres` the world's state), `ResolvedDepositVariant`,
`ResolvedCarvingEntry`/`CarvingBands`/`CarvingResolver` (the band for a position) and `ResolvedFloatingChild`. The settings that name biomes
(`carvingBiome`, a band's biome, a floating child's target and carving) implement `LinkedBiomes`, which is how Iris's biome tree and the biomes a
dimension reaches learn of them without reading the settings classes; and `iris:carve` provides the `CaveBiomes` service
(`CaveBiomeBands`), which Iris asks for the dimension's carving band at a position and for a biome's minimum cave depth.

Packs write each settings feature under the short key it declares: `deposits` (dimension, region, biome; a list), `decorators`
(biome; a list) and `importedStructures` (dimension; an object of `disabled`, `adjustments` and the rest). The feature ids
(`iris:deposit`, `iris:decorant`, `iris:structure`) stay the modules' identity in the stage graph and logs. A key is unique
per target across all modules and Iris's own fields, so a new built-in or pack module picks a key no field of the target uses.

Every built-in stage is a full port: the module owns its logic and per-engine state, and the mantle stages
work out their reach from the pack when it loads. A dimension's `disabledComponents` turns a mantle stage off
by its flag (`CARVED`, `OBJECT`, `FLOATING_OBJECT`, `JIGSAW`) or by its stage id.

Iris core and these sources still depend on each other. The Iris host build therefore compiles them
together as one source set. This keeps the existing class names and platform artifacts while the
features remain in a single physical source tree. It does not create an independent plugin artifact.

`source-patterns.txt` records the module's source ownership. The host's
`:core:verifyDefaultModuleOwnership` task rejects declared feature sources in core, undeclared Java
files here, and missing source families. `:core:bukkitPurityRatchet` checks this tree alongside
core against the existing allowlist.

From this directory, run `./gradlew check` to invoke the Iris host's joint compilation and purity
check and build the module sources archive. Run `./gradlew builtinSourcesJar` to build just the
archive. The host project also provides `:core:defaultModuleSourcesJar` for the same source tree.

## Built-in modules as a pack module jar

`./gradlew builtinModulesJar` builds `build/libs/iris-builtins.jar`, the built-in modules as an ordinary pack
module jar for a pack's `modules/` folder. `builtin-modules.txt` lists what goes in: `module <class>` lines
(the jar's entry points, written to `META-INF/services/com.volmit.IrisModule`) and `include <pattern>` lines
for further classes. The classes come from Iris core's compiled output, which the task builds first.
Before the jar lands in `build/libs`, `ModuleJarCheck` of IrisApi runs the class checks of a pack load on it,
with the packages Iris exposes (`art.arcane.iris.builtin`, VolmLib's `art.arcane.volmlib` and Gson's
`com.google.gson`, all three on the check's classpath); a listed class that uses Iris internals fails the task
naming the class and what it uses. `check` runs the task.

The jar holds `iris:post`, `iris:perfection` and `iris:matter`. They are written against IrisApi and
`art.arcane.iris.builtin` alone: the chunk's `BlockState` hunk, the block registry's traits and `canPlaceOn`, the terrain
sampler (heights around the chunk, slope, fluid height, where the upper dimension begins) and the mantle's planned blocks.
Iris no longer compiles them in (`IrisModuleHost.generationModules()`), so a pack gets them by putting
`iris-builtins.jar` into its `modules` folder; the built-ins that order after their stages (`iris:floating`,
`iris:decorant`, `iris:custom`) only order, so a pack without the jar still loads.

`art.arcane.iris.builtin` also holds what the compiled-in built-ins and the jar must share, because each sees its own copy of
every other class: `IrisSeed` (Iris's per-feature world seeds, a facade Iris's worlds implement, so existing worlds generate the
same) and `terrain.LayerBlocks` (the world's picks from the biomes' palette layers: terrain, caves and post pick from the same
layers, and a layer's noise is built once per world by whichever asks first). `iris:post` reads its switches
(`postProcessingWalls`, `postProcessingSlabs`) and the biome's `wall` and `slab` as module settings through its context.