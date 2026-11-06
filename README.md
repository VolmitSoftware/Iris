# Iris default module

This project owns the built-in Iris feature and setting sources under
`src/main/java/art/arcane/iris`. The files keep the packages and pack fields they had in Iris. They build against
the Iris API alone (see "Building" below); Iris depends on this project, never the reverse.

Ported features are IrisApi modules that every pack loads before its own: object placement
(`iris:prepare`, `iris:anchor`, `iris:validate`, `iris:bore`, `iris:blocks`, `iris:stilt`,
`iris:vacuum`, `iris:snow`, shipped in the built-ins jar), the mantle, and chunk generation. Every mantle stage is a pass of Iris's
mantle of its own, in the stage graph's order: `iris:carve/caves`, `iris:objects/place`,
`iris:floating/objects`, `iris:structure/place`, so a pack's mantle stage can order itself after any of
them. Chunk generation then runs, in this order: `iris:biome`, `iris:terrain`, `iris:carve` (cutting the
planned caves), `iris:post`, `iris:floating` (solids), `iris:deposit`, `iris:matter`, `iris:decorant`,
`iris:floating` (decoration), `iris:perfection` and `iris:custom`. There are no world modes and no Iris
mantle components outside the stage graph any more. Their stage constants, and the settings and output
types packs may read, live in `src/main/java/art/arcane/iris/builtin`, the package Iris exposes to pack
modules; their stage bodies live beside the features they replace. Settings and helper types that only the jar's modules read,
which Iris itself never touches, live in `art.arcane.iris.modules` instead, which Iris does not expose: the jar loads its own copy.
Every built-in snippet is named under its module's id, `iris/<module>/<name>` (folder `snippet/iris/carve/cave-profile/`), so a
snippet type may live in either package. A type stays in `art.arcane.iris.builtin` only when Iris reads it or an exposed type holds it.

Two of those modules are not in this project: `iris:biome` (writes the layout's platform biomes into the chunk) and
`iris:custom` (records the platform's custom blocks of content plugins) are Iris itself, so their classes live in Iris
core (`art.arcane.iris.core.modules.BiomeModule` and `CustomModule`) and Iris registers them (`IrisModuleHost`), not
the built-ins jar. Their stage constants stay in `art.arcane.iris.builtin.IrisStages`, so packs can still order after them.

Built-in modules log through the `ModuleLog` IrisApi hands them (`context.log()` in a stage body, `world.log()` in a
service or hook, `setup.log()` during setup), which labels each line with the module's id, so these sources do not
use `IrisLogging`. Classes of the host's object model (the host-classes list) and code that only has an engine or pack
data still log as Iris itself.

Modules also own pack resources and checks of their own. `iris:structure` (`StructureModule`) reads structures, jigsaw
pools and jigsaw pieces as resource kinds (`StructureResources`, `iris:structure/structure`, `/pool`, `/piece`), from the
folders and files packs have always used, and declares the structure placement settings and the mantle stage. Its
definitions (`IrisStructure`, `IrisJigsawPiece`, `IrisJigsawPool` and their parts in `engine/object/structure`) live
here and build against the API. It is not in the built-ins jar: Iris registers it with its own modules, because Iris's
jigsaw studio, importers and exporters read the structures it registers as the same classes, which a jar's own copy of
a class is not. Iris's own module `iris:structure-model` (core `StructureModelModule`) checks them with its validator
(`StructureValidator`: assembly samples and vanilla structure replacement) wherever Iris validates a pack. The
assembler, locator, fitting and placing are still Iris's, and `iris:structure` places through the `StructureHost`
facade (`art.arcane.iris.builtin.structure`).

Built-in modules answer Iris through IrisApi services instead of Iris reading their features: `iris:objects`
provides `ObjectsAt` (the objects a chunk would get, for the object search and studio tools) and `iris:structure-model`
provides `StructureLocator` (`StructureLocating`: the pack's structure keys for Iris's structure index, the ones a
world places, and their nearest start for the find commands). A world's structure index lives in the world's state
(`engine.getGeneration().state(...)`), so every reload of the pack's structure files, which starts a new world, builds
it again.
`iris:structure-model` also provides `VanillaStructures` (`StructureVanillaStructures`) for the platform's own structures:
which ones its placements replace or the dimension's `importedStructures` disables, the starts its `nativeStructures`
placements make (`startedStructures`, `locatableStarts`, `locateStart`), the shift and band of the platform's starts, and the whole terrain fit of every start (SOURCE,
PRESERVE, VACUUM, ENCASE, BORE, FORCE_CARVE, stilts) in `engine/framework/structure/fit`, written against IrisApi's
`StructureFit` and `BlockAccess` only. A start carries its fit as a memo (its terrain and stilts JSON), so every
chunk it reaches and every restart fits it the same way. `iris:vanilla-features` (`VanillaFeaturesModule`, no stages)
reads the dimension's `importedFeatures` (`ImportedFeaturesSettings`) and provides `VanillaFeatures` (`ImportedFeatures`):
whether the platform's own placed features run over the terrain and which decoration steps and features they leave out;
the family patterns of `disabled` and of `importedStructures` share one rule (`art.arcane.iris.builtin.RegistryKeyFamily`).
Object place modes are data (`PlaceMode`: height sampling,
stilt, `VacuumProfile`, `PlaceTrait`s) that the placement stages ask; the pack JSON keeps the mode names.
`iris:objects` also provides `SaplingGrowth` (`TreeGrowing`: the tree a sapling grows into, as placed blocks, and which
objects are trees) and says which biomes and regions place an object (`ObjectLocating`, for the object search).
`iris:objects` ships in the built-ins jar. It declares the settings `objects` (region, biome: the list of `ObjectPlacement`s),
`proceduralObjects` (region, biome), `treeSettings` (dimension), `requireObjectSurfaceSupport`, `objectSurfaceSupportBuffer`,
`upperDimensionObjects` and `upperObjectsForcePlace` (dimension), all written as they always were. `ObjectPlacement` is the
placement itself, read by the module decoder like any settings class, and the one type Iris places with. What a jar cannot do, Iris does through the facades `ObjectHost` and
`ObjectChunk` in `art.arcane.iris.builtin.objects`: loading and scaling the pack's objects, placing one with the placement
stages into the chunk's mantle (on the surface, in a cave inside the cave's bounds, or hung from the terrain above), reading the
carved space the cave carver planned, and the loot an object placed at a block defines; the layout, the settings of a column and
the upper dimension's objects come from IrisApi (`TerrainSampler`, `FeatureSettings`, `UpperDimension`). `ObjectPlacing` and its helpers (`ChunkObjectPlacing`,
`CaveAnchors`, `PlacementReach`) decide what to place and where. Procedural objects (`engine/procedural`, resource kind
`iris:objects/procedural`, folder `procedural`) are definitions of parts that bake into variants; a placement names them as
`procedural/<path>`, or `procedural/<path>#<index>` for one variant, like any object.
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
`iris:decorant`, `iris:terrain` and `iris:deposit` ship in the built-ins jar and keep their per-world workers (`BiomeDecorators`, `TerrainShaper`,
`DepositPlacer`) in the world's state store (`context.world().state(...)`); the modules still compiled into Iris reach the same store through
the `IrisChunk` their invocation offers (`context.extension(IrisChunk.class)`, then `state(...)`) or `engine.getGeneration().state(...)`.

Settings move off Iris's classes one feature at a time. `iris:deposit` is the first whose settings are IrisApi types
only: `DepositSettings` (`art.arcane.iris.modules.deposit`) holds `DepositGenerator`s (blocks as `BlockSpec`, its enums beside it; packs write generators as
`snippet/iris/deposit/deposit/...`), and packs write `"deposits": [...]`, as they always have (`@Feature(key = "deposits", root = "generators")`: the key holds the one option's list). What needs Iris (resolving the
palette, ore checks, clump shapes per world seed) lives in the module's own code, `ResolvedDeposit`. The deposits run one generator after the other, each
with a random of its own; they used to spread over threads on a multicore pregen, which no module can do (stages run on the host's threads).

An object placement is `iris:objects` settings (`objects.ObjectPlacement`, under a biome's or region's `objects`), and the
settings inside it, such as its clamp, stilt, vacuum and edit settings, its slope condition or a procedural coral, are
module-owned classes too (`art.arcane.iris.builtin.validate`, `.stilt`, `.vacuum`, `.blocks`, `.prepare`, `.carve`,
`.floating`, `.objects.*`), so studio describes them from the module's own annotations. Iris reads a placement's module
settings (the keys pack modules add at `Level.PLACEMENT`) from the JSON the placement was written as (`json()`), and keeps
what it makes of the placement in `hostForm()`; a placement made in code uses `ObjectPlacement.builder()` or
`toBuilder()`. Packs write the same JSON as before. Behaviour
that needs Iris (noise built from the styles, blocks resolved through the pack, baked object variants) lives in `Resolved*` classes beside the
worker (`ResolvedReplace`, `ResolvedCaveProfile`, `ResolvedFloatingChild`, `ResolvedProceduralPlacement`).

`iris:decorant` is the second: `DecorantSettings` (biome level) holds `Decorator`s in `art.arcane.iris.builtin.decorant`,
written `"decorators": [...]` as always (`@Feature(key = "decorators", root = "decorators")`).
`ResolvedDecorator` resolves one decorator's blocks and noise for a world; `BiomeDecorators`, the world's state, keeps them
per biome and place. A chunk's decorators place into a `DecorationScene` (its blocks, the planned blocks around it, the terrain and the pack's block
rules); other modules that decorate (the caves' floors and ceilings, floating islands) build one from their chunk's stage context. Whether a
dimension decorates at all is its `decorate` key (`DecorateSettings`). A decorator's noise
seed is the world's decorator seed, its biome's key and its place in the biome's list. The module's `warmUp` builds
them ahead of generation; `iris:terrain` and `iris:objects` warm their ore generators, placements and procedural
objects the same way.

`iris:terrain` and `iris:post` own the fields that shape a column's blocks. Per biome: `layers`, `seaLayers`, `caveCeilingLayers` (lists of
`PaletteLayer`, the old biome palette layer, in `art.arcane.iris.builtin.terrain`), `slab` and `wall` (one `PaletteLayer` each), `lockLayers` (a
boolean) and `lockLayersMax` (a number). Per dimension: `rockPalette` and `fluidPalette` (a `PaletteSpec` each) in `art.arcane.iris.modules.terrain`, and
`postProcessing`, `postProcessingSlabs`, `postProcessingWalls` (booleans) in `art.arcane.iris.modules.post`; `bedrock`, `explodeBiomePalettes` and
`explodeBiomePaletteSize` are dimension keys of `iris:terrain` too. A key a pack leaves out has the
default Iris always had (one grass layer, no slab or wall, stone and water, post processing on). `ResolvedLayer` resolves one layer's blocks and noise
for a pack, `BiomeLayers`, the world's state, keeps them and stacks a biome's layers into the blocks of a column, and `TerrainPalettes` is the world's
rock and fluid as a block for every column; the terrain, cave, floating, post and vacuum stages ask them. All of them are the jar's own
(`art.arcane.iris.modules.terrain`), and every stage that asks is in the jar, so one copy serves them all: a biome's layer thickness noise is built once,
from the random of whoever asks first, as it always was. The slope limit of a layer, a decorator and an
object placement is the one `SlopeClip`.

Caves, floating islands and ores are settings of their modules too, written under the keys they always had. `iris:carve` owns
`caveProfile` (dimension, region, biome; an object, or a `snippet/iris/carve/cave-profile/...` reference), `carving` (dimension; a list of bands) and
`carvingEnabled` (dimension; a boolean), `carvingBiome` (biome; a biome key) and `caveMinDepthBelowSurface` (biome; a number). `iris:floating`
owns `floatingChildBiomes` (biome; a list) and `mergeFloatingChildBiomes` (biome; a boolean). `iris:terrain` owns `ores` (dimension, region,
biome; a list of generators) and `hideOresForHiddenOre` (dimension; a boolean); `iris:deposit` owns `depositVariants` (dimension, region, biome; a list)
and `oreDepositFrequencyMultiplier` and `oreDepositSizeMultiplier` (biome; numbers). Behaviour that needs Iris lives in the `Resolved*` classes beside the
workers: `ResolvedOre`/`ResolvedOres` (blocks and chance noise per world, `TerrainOres` the world's state), `ResolvedDepositVariant`,
`ResolvedCarvingEntry`/`CarvingBands`/`CarvingResolver` (the band for a position) and `ResolvedFloatingChild`. The settings that name biomes
(`carvingBiome`, a band's biome, a floating child's target and carving) implement `LinkedBiomes`, which is how Iris's biome tree and the biomes a
dimension reaches learn of them without reading the settings classes; and `iris:carve` provides the `CaveBiomes` service
(`CaveBiomeBands`), which Iris asks for the dimension's carving band at a position and for a biome's minimum cave depth.
`iris:floating` ships in the built-ins jar: `FloatingWorld` holds a world's floating children, noise and seeds. It asks the
API alone: the running world's layout for a column's surface biome and a biome's height for the `BIOME` top shape
(`TerrainSampler.biomeHeight`), the chunk's terrain for a child's sky biome (`skyBiome`), which it writes with
`Mantle.setPlatformBiome`, and `PackWorld.placements()` to place an object floating, on an island's top or hung under it,
where the jar's `IslandAnchor` says where the island face is.

Packs write each settings feature under the short key it declares: `deposits` (dimension, region, biome; a list), `decorators`
(biome; a list), `importedStructures` (dimension; an object of `disabled`, `adjustments` and the rest) and `structures`
(dimension, region, biome; a list of `StructurePlacement`s, `StructurePlacementSettings`). The feature ids
(`iris:deposit`, `iris:decorant`, `iris:structure`) stay the modules' identity in the stage graph and logs. A key is unique
per target across all modules and Iris's own fields, so a new built-in or pack module picks a key no field of the target uses.

Every built-in stage is a full port: the module owns its logic and per-engine state, and the mantle stages
work out their reach from the pack when it loads. A dimension's `disabledComponents` turns a mantle stage off
by its stage id, such as `iris:carve/caves`; a name that matches no stage fails the world.

## Building

This is a Gradle build of its own (`art.arcane:iris-module`). Its compile classpath is the Iris API
(`com.volmit:iris-api`, from the sibling `../IrisApi` checkout; never `iris-host`), VolmLib, Gson, Lombok and the JDK,
all `compileOnly`: a host provides them at runtime. VolmLib comes from a sibling `VolmLib` checkout when there is one,
found the way Iris finds it (`-PuseLocalVolmLib=false`, `-PlocalVolmLibDirectory=<path>` or `VOLMLIB_DIR`), otherwise
from the published `volmLibCoordinate` in `gradle.properties`, which matches Iris's. Classes compile with Iris's options
(`-parameters`, no debug info) and `lombok.config` repeats Iris's, so they are the classes Iris shipped when it
compiled these sources itself.

Iris includes this build (`includeBuild('../IrisModule')` in its `settings.gradle`) and depends on its classes: Iris
core and its adapters still name many of them (the cut-over that ends this comes later). Classes that need Iris itself
are Iris's and live in Iris core: the layout model (`IrisBiome`, `IrisRegion`, `IrisDimension`, generators, noise
styles, expressions, images), the pack's objects and blocks, the structure assembler, locator, fitting and placer
(behind `StructureHost`), and the placement service behind `PackWorld.placements()`. A module this build holds
but the jar does not, such as `iris:structure`, is one Iris registers with its own modules (`IrisModuleHost`): its
classes are then the ones Iris's own code sees.

`./gradlew check` compiles every source and builds the built-ins jar below, which runs the pack class checks.

## Built-in modules as a pack module jar

This build packages the built-in modules as an ordinary pack module jar, `build/libs/iris-builtins.jar`, from its own
classes, and Iris bundles it into the plugin: every pack loads it as if it were in its `modules/` folder (IrisApi's
`ModuleHost.bundlingJars`). A pack must not hold a copy of it: its classes would be in two jars, so the pack fails to load
naming both. `./gradlew builtinModulesJar` builds it. `builtin-modules.txt` lists what goes in: `module <class>` lines
(the jar's entry points, written to `META-INF/services/com.volmit.IrisModule`) and `include <pattern>` lines for further
classes. Iris asks for the jar by the capability `art.arcane:iris-builtins`; its plugin leaves the jar's classes out of
its own, and its Bukkit artifact check fails the build if Iris still uses one of them.
Before the jar is written, `ModuleJarCheck` of IrisApi runs the class checks of a pack load on it, with the packages Iris
exposes (`art.arcane.iris.builtin`, VolmLib's `art.arcane.volmlib` and Gson's `com.google.gson`, all three on the check's
classpath, with this build's other classes as the host's); a listed class that uses a class outside the jar and those
packages fails the task naming the class and what it uses. Iris checks the jar once more before bundling it, against the
VolmLib classes its Bukkit jar strips (`core/volmlib-left-out-of-bukkit-jar.txt`), which only Iris knows.

The jar holds `iris:post`, `iris:perfection`, `iris:matter`, `iris:loot`, `iris:spawn`, `iris:vanilla-features` and
`iris:carve`, and the object placement stages `iris:prepare`, `iris:anchor`, `iris:validate`, `iris:bore`, `iris:blocks`, `iris:stilt`, `iris:vacuum` and `iris:snow`. They are
written against IrisApi and `art.arcane.iris.builtin` alone: the chunk's `BlockState` hunk, the block registry's traits and
`canPlaceOn`, the terrain sampler (heights around the chunk, slope, fluid height, where the upper dimension begins) and the
mantle's planned blocks; the placement stages read the placement being run through the `ObjectPlacement` facade, and the loot and
spawn services, which have no chunk to ask, ask the running world (`world.running()`, IrisApi's `RunningWorld`). Without the jar a pack's world has no caves, vanilla
placed features, loot, block drops, entity spawns or ambient effects, and its biomes' `loot`, `blockDrops`, `entitySpawners` and
`effects` are not read.
Iris no longer compiles them in (`IrisModuleHost.generationModules()`); every pack gets them from the bundled jar. A pack
still loads without them, as with an Iris build that bundles no jar, and Iris warns once per pack folder when a pack loads
none of the jar's modules (`IrisModuleHost.JAR_MODULE_IDS`, which a test keeps equal to
`builtin-modules.txt`): add a module to the jar and to that list together.

`art.arcane.iris.builtin` also holds what the compiled-in built-ins and the jar must share, because each sees its own copy of
every other class. The facades among them are interfaces Iris implements and offers through IrisApi's extension point, never
through the thread or a cast: a module asks `world.extension(X.class)` for what belongs to a world (`LegacyStreams`,
`StructureHost`, `spawn.SpawnCooldowns`), `context.extension(X.class)` for what belongs to one invocation (`MatterMantle`
in a chunk or mantle stage) and `settings.extension(X.class)` for a reach worked out
before any world exists, each with `.orElseThrow()`, since a built-in runs only where Iris offers them. Iris's per-feature world seeds are
IrisApi purpose seeds: `world.seed(IrisSeedPurposes.CARVE)` (`iris:seed/carve`), by the API's rule like any module's;
`spawn.SpawnCooldowns` (the spawner cooldowns
Iris saves with the world's data, so they survive a restart),
`loot.LootResolver` (the random streams loot is drawn from, shared with the hosts that fill containers) and the loot and
marker lists of an object placement (`loot.ObjectLoot`, `loot.ObjectVanillaLoot`, `spawn.PlacementMarker`). The placement stages need more of the object being
placed than IrisApi describes, so `placement.ObjectPlacement` is the facade Iris implements for each placement and offers with
its invocation (`context.extension(ObjectPlacement.class)`): the object's size (`ObjectShape`), the placement's settings (`PlacementSettings`, read from
the `objects.ObjectPlacement` being placed), the terrain questions of its placer and the world around it (`PlacementTerrain`), the surface warp,
and the block operations only the platform can do (turning a state with the object, merging two states, matching, traits of the
placement's own states). Its types answer with IrisApi, VolmLib and built-in values only. `prepare.PreparedPlacement`, the output of
`iris:prepare/transforms`, lives in the exposed package too, because `IrisStages` names it. `iris:post` reads its switches
(`postProcessingWalls`, `postProcessingSlabs`) and the biome's `wall` and `slab` as module settings through its context.

`iris:carve` also reads the mantle's matter slices through `MatterMantle` (the chunk of `MatterCavern`s and markers it plans and
applies, which the platform and the rest of Iris read as slices, not as module layers). A mantle stage gets the whole mantle; a
chunk stage gets its own chunk and the neighbors the world already holds in memory, which is all the cave apply stage ever read,
so it writes nothing outside its chunk. What a
cave's walls, floor and ceiling get comes from the terrain and decorant built-ins of the same jar (`modules.terrain.BiomeLayers`,
`modules.terrain.TerrainPalettes`, the biome decorators), through carve's own `CaveDressing`, which tests replace with fixed blocks.
A cave's dimension, region and biome settings are found by the id the layout's biome samples carry (`DeclaredByLevel`), its
absolute world Y bands convert with `PackWorld.minWorldY()`, and its noise reads `NoiseSampler.signed(...)` (Iris's memoised signed read)
and `scaled(...)`. `caveLavaHeight` and `upperDimensionCarving` are its dimension settings.
