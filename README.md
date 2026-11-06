# IrisApi

Java 25 contracts for Iris modules, with a small host-independent reference implementation.
Main code depends only on the JDK. No Bukkit, Iris implementation, Gson or Lombok types appear
in the public contracts. Nothing here is stable yet.

This file is for module authors. [DESIGN.md](DESIGN.md) explains the model and its rules. [CONTRACTS.md](CONTRACTS.md) lists
what the Iris host adapter still has to provide. [HOST.md](HOST.md) is for hosts: loading packs and running stages. Existing Iris features are not ported or wired
into this project; the test sources contain example ports only.

The build has two subprojects, one artifact each:

| Subproject | Artifact | Packages | Who uses it |
| --- | --- | --- | --- |
| `api` | `com.volmit:iris-api` | `com.volmit` and its subpackages, except `com.volmit.host` | modules, `compileOnly`; the host |
| `host` | `com.volmit:iris-host` | `com.volmit.host` and `com.volmit.host.schema` (depends on `iris-api`) | the host only |

Pack jars never see the host side, though it lies inside `com.volmit`: the pack loader leaves it out, and a module
that references a class of it fails the load. Tests that need both (the loader, the runners, the example ports) live
in `host`; `api` tests the value types and `WorldRandom` alone.

## Modules own their stages

There are no host-defined hooks or phases. A module declares stages as constants and defines
them in `setup`. Other modules order themselves against those constants.

```java
public final class MossModule implements IrisModule {
    public static final PlacementStage<Void> SPREAD = Stage.placement("example:moss/spread");

    public ModuleInfo info() { return ModuleInfo.of("example:moss", "Moss", "1.0.0"); }

    public void setup(ModuleSetup setup) {
        setup.stages().define(SPREAD)
             .when(MossSettings.class)                      // only where a pack configures moss
             .requires(AnchorModule.HEIGHT, BlocksModule.COMPLETE)
             .softAfter(StageRef.of("iris:snow/finish"))    // ordering only, if snow is set up
             .run(ctx -> spread(ctx.output(AnchorModule.HEIGHT), ctx.configured(MossSettings.class).mostSpecific().orElseThrow()));
    }
}
```

`IrisModule` has two methods: `info()` and `setup(ModuleSetup)`. `setup` runs once while the pack
loads. `setup.stages()` defines stages, `setup.registry()` takes every other contribution and
`setup.log()` is the module's log.

- `Stage.placement`, `Stage.placementBlock`, `Stage.chunk` and `Stage.mantle` create stages for the
  placement pipeline (once per attempt), the placement pipeline (once per object block), the chunk
  pipeline (once per chunk, writing only that chunk) and the mantle pipeline (once per chunk, before
  the chunks it reaches generate, writing only the mantle). Pass an output class to give a placement
  or chunk stage a typed output; mantle stages have none.
- A stage id starts with the id of the module that defines it and `/`: module `example:moss`
  defines `example:moss/spread`, never `example:other/...`. Module ids therefore contain no `/`.
  Defining an id twice fails.
- `requires(...)` takes another module's stage constant. It orders, makes the output readable
  and deactivates this stage wherever the requirement is inactive.
- `softAfter` and `softBefore` only order. They take a stage constant, or a `StageRef` id when the
  other module may be absent and you cannot compile against it. There is no hard "before".
- `run(...)` ends a stage without output, `produce(...)` a stage with output and
  `accumulate(initial, collector)` a block stage whose accumulator becomes its output. A producer that
  rejects the placement attempt may return null; any other null output fails the attempt.
- `when(settingsType)` runs the stage only where those settings are configured.
  `ctx.configured(settingsType)` gives the body every configured entry that activated it (see Settings
  levels below); asking for any other type throws.
- `ctx.output(stage)` works only for stages the current stage transitively requires.
- `define` returns a definition for the stage's pipeline, so it offers only what that pipeline allows:
  `reach` and `writesMantle` exist on placement and mantle definitions, never on chunk ones.

Which edge to use:

| You need | Use |
| --- | --- |
| Another stage's output, or to run only where it runs | `requires(stage)` |
| To run after a stage that may be absent or inactive, without reading it | `softAfter(stage)` |
| Another module's stage to run after yours, without it naming you | `softBefore(stage)` |
| To order against a module you do not compile against | `softAfter(StageRef.of("id"))` / `softBefore(StageRef.of("id"))` |
| To run only where settings are configured | `when(Settings.class)` |
- `ctx.random()` is the stage's own random stream, derived from the invocation seed and the stage
  id. Installing or removing other modules, or how much they draw, never changes what a stage
  draws. A block stage has one stream per placement, drawn across the blocks of its pass.
  The stream is a `WorldRandom` (see Random streams below).

`setup.registry()` takes the non-stage contributions: geometry generators,
mantle layers, settings no stage names (see Settings for studio), pack resource kinds, validators,
lifecycle hooks and services (see below). Their feature ids, mantle keys and kind ids are the module id or start with it and `/`,
like stage ids: `iris:snow` or `iris:snow/depth` for module `iris:snow`. A settings type named in `when(...)` needs a feature id that at least one
module naming it owns in that sense, or its owner must have registered it, so another module may
still condition its stages on it, as moss does on `iris:snow`.

## Settings levels

Settings can be declared at several levels (`com.volmit.config.Level`): the dimension, a region, a biome
and, in a placement, the placement itself. Each level's declaration is its own entry, and levels stack:
nothing is merged and no level overrides another. A module decides what several entries mean, such as
running a deposit once per entry, or using the most specific one.

Every lookup answers a `SettingsStack`: the entries in level order, with what modules usually pick from them.

```java
var here = ctx.settings().at(DepositSettings.class, worldX, worldZ);   // every level at this column
here.values();        // every level's value: DIMENSION, REGION, BIOME, PLACEMENT, in that order
here.dimension();     // the dimension's value, if it declares one
here.level(Level.BIOME);
here.mostSpecific();  // the last level's value
for (var entry : here) {
    entry.level();    // where it is declared
    entry.source();   // the declaring resource, such as overworld:biome/desert/dunes
    entry.value();    // the decoded settings
}
```

Settings are read per column, at the column's surface biome. A chunk or mantle stage conditioned with
`when(...)` runs when any column of the chunk has an entry (`FeatureSettings.declaredInChunk`), so a
biome border inside a chunk never hides a feature, and `ctx.configured(type)` gives every distinct entry
of the chunk. It then reads `ctx.settings().at(...)` per column, or at the chunk's centre column
(`ctx.centreX()`, `ctx.centreZ()`) for something that happens once per chunk. Something that spans several columns, such as a placed object, uses the column
it treats as its origin. In a placement `ctx.configured(type)` gives the placement's own entry; a
placement declares each settings type once. An entry is found under the class whose `@Feature` declares
it, so a value of a subclass counts as its annotated superclass.

Each feature has one JSON shape: the object of its settings class's options, or, when `@Feature` declares
`root`, exactly the value of that one option (see "Settings for studio"). There is no shorthand that accepts
either form.

## Coordinates

X and Z are world coordinates. Y is always the height above the dimension's bottom: 0 is the bottom block
of the dimension, whatever its minimum height is in the platform, so Y is never negative. This holds for
every API value that carries a height: terrain and biome samples, `BlockBox` bounds of the terrain
sampler, block views, placement edits and the mantle, hunks, block palettes and noise samplers. The host
converts to and from the platform's own Y at its boundary. The one place a module meets the platform's Y is a
setting that names an absolute world Y, as packs write them (a cave biome band between Y -64 and 320): it converts
with `minWorldY()`, the world Y of the dimension's bottom (`heightAboveBottom = worldY - minWorldY()`). The world,
the terrain sampler, platform starts and structure fits all answer it under that one name, and none of them has a
default: a host that forgets it does not compile. `BlockBox`, `BlockPosition` and `BlockFace` live in
`com.volmit.geometry`.

## Random streams

Stages and object generators draw from `com.volmit.random.WorldRandom`, an IrisApi type whose every
method is defined and pinned by tests, so the same seed gives the same world on every JDK. It is
SplitMix64 with `nextInt`, `nextLong` and `nextDouble` in plain, bounded and `origin, bound` forms,
plus `nextBoolean`, `nextFloat`, `nextGaussian`, `chance(probability)`, `pick(list)` and
`shuffle(list)`. It does not implement `java.util.random.RandomGenerator`, whose default methods the
JDK may change, so use `pick` and `shuffle` instead of `Collections.shuffle`. Bounded integers are
unbiased, `nextDouble` never returns its bound, and invalid bounds throw. It is not thread-safe: a
stage uses its own stream within one invocation and must not share or keep it.

A module's own world-wide randoms and noise take `world.seed(purpose)`, the seed the world gives a purpose id such as
`example:moss/patches`: the same for the same world and purpose, independent across purposes, so two modules never
mix the world seed into the same value by hand. It is `WorldRandom.purposeSeed(world.seed(), purpose)` for every
purpose and host (a default method of `PackWorld`), pinned like every other `WorldRandom` value.

## The mantle pipeline and reach

A chunk stage writes only its own chunk: the host may already have generated its neighbors. Work that
writes the mantle or reaches beyond its chunk, such as a carver or a vein that runs on into the next
chunk, is a mantle stage. Every mantle stage is its own pass: before a chunk generates, the host runs
the first mantle stage for every chunk within that stage's reach plus the reach of every later stage,
then the next stage the same way, in resolved order. So a stage sees what earlier stages wrote into the
chunks around it, and the chunk's mantle is complete when its chunk stages read it. Mantle stages have
no outputs: they pass data on through mantle layers. The two pipelines meet only in the mantle.

```java
public static final MantleStage CARVE = Stage.mantle("example:caves/carve");
public static final ChunkStage<Void> APPLY = Stage.chunk("example:caves/apply");

graph.define(CARVE).when(CaveSettings.class).reach(32).writesMantle(CAVES).run(CaveModule::carve);
graph.define(APPLY).run(CaveModule::apply);   // reads its own chunk's CAVES and writes blocks
```

A chunk stage writes only its own chunk but may look around it: `ctx.terrain()` covers the chunk and the 16 blocks
around it, gives the dimension's `fluidHeight()` and, per column, `upperDimensionY(x, z)` where a dimension stacked above
begins (write nothing there). `surfaceY(x, z)` is a column's height alone, cheaper than `column(x, z)` when a stage needs
no slope, and `surfaceHeight(x, z)` the same height rounded to the block a stage places on. `surfaceBiome(x, z)` is the id of a column's surface biome alone and `surfaceKind(x, z)` what it was generated as (`BiomeKind`: land, sea,
shore); `surfaceBlock(x, z)` is the block an image map gives the ground there, `minWorldY()` the world Y of the dimension's bottom, and
`upperDimension()` the dimension stacked above (`UpperDimension`: its own settings, resources and image-map blocks), for a stage that fills in its ground.
`biomeHeight(biome, x, z, seed)` is the height a pack biome's terrain generators would give a column, whatever biome stands there, and
`skyBiome(biome, x, z)` the platform biome that biome shows as in the sky (a custom biome, or a pick from its sky scatter); a stage that builds
ground of a biome somewhere else, such as a floating island, shapes and paints it with them. `ctx.mantle().setPlatformBiome(x, y, z, biome)` makes a
block show that platform biome instead of its column's, inside the stage's own chunk.
`ctx.settings().declaredBy(biomeId, Type.class)` reads what a biome declares itself, such as a cave biome of a `BiomeSample`, which is no level of
`at(...)`. A mantle stage's `ctx.terrain()` covers the chunk and the larger of its reach and 16 blocks around it; a chunk stage's `ctx.mantle()` covers the same area as its terrain. `ctx.mantle().blocks()` holds the blocks placements wrote into the mantle for the chunks to
take over, such as objects and structures; a chunk stage copies them in (Iris's `iris:matter`). `ctx.mantle().carved()` is the layer of voids the cave planner carved (`CarvedVoid`: `OPEN`, `FLUID`, `LAVA` or `AIR`, optionally with a biome), and
`carved().get(x, y, z) != null` asks whether one is there, whatever the chunk's blocks show. A chunk stage only reads both; a mantle stage plans the voids
with `carved().set(...)`, and a chunk stage's write throws. A chunk stage may write the layers its module registered, inside
its own chunk's columns; around it, the mantle stays as the mantle stages left it. `blocks().oreFor(rock, ore)` gives the ore variant that suits the rock
(deepslate ores in deepslate), which the platform decides, and `PackBiome.vanillaBiome()` the biome vanilla's rules see a pack biome as.

```java
graph.define(INSERT).run(ctx -> {
    var blocks = ctx.blocks();
    ctx.mantle().blocks().visit(ctx.area(), (x, y, z, block) -> blocks.set(x - ctx.originX(), y, z - ctx.originZ(), block));
});
```

Chunk and mantle contexts share `ChunkContext`: `originX()`/`originZ()` (the chunk's first block), `chunkX()`/`chunkZ()`,
`centreX()`/`centreZ()` (the world X and Z of the centre column) and `area()`, the chunk's columns at the terrain's full
height.

`reach(blocks)` is the distance in blocks along X and Z beyond the stage's footprint: the chunk for a
mantle stage, the X/Z bounds of the placed object for a placement stage, whole columns in both cases.
It defaults to 0. `writesMantle(...)` names the mantle layers the stage writes; each must be
registered by some module. A chunk stage's definition has neither method, and its mantle is
read-only. `pack.placement().reach()` gives the host the largest reach of a pipeline and the largest
per mantle layer.

When how far a mantle stage writes depends on the pack, such as the longest vein or the largest object
any level configures, it declares a `PackReach` instead of a number. The host works it out from the
pack's settings (`PackSettings.declared(type)`: every entry of the dimension and of the regions and
biomes it can reach) when the pack loads, and asks each mantle stage with
`ResolvedStage.reachBlocks(settings)`. Only mantle definitions offer it.

```java
graph.define(PLAN).when(VeinSettings.class).reach(VeinModule::reach).writesMantle(ORE).run(VeinModule::plan);

static int reach(PackSettings settings) {
    int longest = settings.declared(VeinSettings.class).values().stream().mapToInt(VeinSettings::length).max().orElse(0);
    return Math.max(0, longest - 9);   // a vein starts at the chunk's centre column
}
```

A mantle stage may rely on what earlier stages wrote around its chunk, but not on what a stage of its
own pass wrote in other chunks: whether it has run there yet depends on which chunk the host generates
first.

## Pack resources of a module

A module can own a kind of pack file, the way Iris owns biomes and objects: every JSON file below one folder
of the pack, each read into a new instance of a class of the module.

```java
public static final ResourceKind<Keep> KEEPS = ResourceKind.of("example:castle/keep", "keeps", Keep.class);

public void setup(ModuleSetup setup) {
    setup.registry().registerResourceKind(KEEPS);   // reads <pack>/keeps/**/*.json
}

// anywhere the pack's resources are at hand, such as a stage or a validator
Keep keep = resources.require(KEEPS, resources.idOf("towers/north"));
List<ResourceId> all = resources.ids(KEEPS);  // every keep of the pack, in id order
PackBiome dunes = resources.require(PackResources.BIOMES, resources.idOf("desert/dunes"));  // the host's kinds alike
```

- A file's key is its path below the folder without `.json` (`towers/north`), and its id is
  `<pack namespace>:<key>`, like every pack resource (`resources.idOf("towers/north")` makes it, in lower case with
  characters outside `[a-z0-9_./-]` replaced by `_`: `ResourceId.ofPackKey`). JSON keys inside the file are the class's
  field names.
- The kind's id follows the module id. The folder is one folder name directly below the pack root; the module
  chooses it, so a kind that replaces one of Iris's own keeps the folder and file format packs already have.
- One folder holds one kind and one class backs one kind, which `@ResourceRef(...)` names by its class. Two kinds
  claiming one folder, or a folder the host reads itself (`ModuleHost.reservingFolders`), fail the pack load,
  naming both owners.
- The host creates each resource with the class's no-argument constructor and reads the JSON into it, as it does
  settings, so an omitted field keeps its default. It reads a resource once per pack load and hands every caller
  the same instance, so resources are values nobody may change. A reload (studio hotload) reads them again.
- Studio describes the files from the class like settings: `@Desc`, `@MinNumber`, `@Required` and the rest. An
  option that names a resource of a kind carries `@ResourceRef(Keep.class)` on a `String` or `ResourceId`, or a
  list, set or array of them, and studio completes the kind's keys. Packs write the key either way; a `ResourceId`
  field holds the pack's id of it. Built-in kinds may read into a class the host describes
  itself, as built-in settings may hold one.

```
Resource kind example:castle/keep of module example:castle from modules/castle.jar reads folder 'biomes', which the host reads itself; give the kind a folder of its own
```

## Placing objects

A module that places objects itself, such as the objects of a biome or a growing tree, takes them from the pack's
objects and places them through the pack's placement stages, so every module's placement stages (anchoring, stilts,
snow) run for them as they do for any placement:

```java
PackObjects objects = world.resources().objects();
PlaceableObject tower = objects.object(world.resources().idOf("towers/north")).orElseThrow();
PlaceableObject large = objects.scaled(tower, 2, ScaleMethod.TRILINEAR);       // kept per pack snapshot

PlacementTerrain terrain = context.placementTerrain();                          // in a mantle stage
PlacementRun run = new PlacementRun(new PlacementRequest(large.geometry(), anchor), "towers/north",
        Optional.of(placementJson), area, terrain, mySettings, seed);
PlacementOutcome outcome = world.placements().place(run);
if (!outcome.isPlaced()) {
    context.log().debug("tower rejected: " + outcome.rejection().orElseThrow());
}
```

- `PackObjects` gives an object of the pack by id, its size without loading it (`sizeOf`, for a reach), a scaled or
  smart-bored copy, and objects a module builds block by block (`build`). The host keeps them per pack snapshot.
  Only `PackObjects` makes a `PlaceableObject`; a module does not implement it, and the host rejects one it did not
  make. `scaled` and `smartBored` return other objects and leave the original as it was.
- `PlacementTerrain` is the world the placement reads and the host writes into once it commits: heights, blocks,
  carved space, fluid, the structures nearby and the placement recorded at a block (`recordPlacement`, `placementAt`;
  `RunningWorld.placementAt` reads it back later, such as for the loot of a placed object). A recorded placement is a
  `PlacementRecord`: the object's id, the module's number for this placement of it and, for a piece of a structure, the
  structure's id. A module may wrap the terrain to hold or filter writes; a mantle stage gets one for its invocation from
  `MantleGenerationContext.placementTerrain()`.
- `name` labels the placement (the object's key). `settings` is the placement as the pack wrote it, a JSON object: the
  host reads from it the settings other modules declare at `Level.PLACEMENT`, so their stages see them like those of any
  other level. The module that owns the placement type declares it once in `setup`, with the JSON names of its own
  options, so no settings key can take one: `registry.registerPlacementType(MyPlacement.class, names)`. Studio then adds
  the placement-level settings to that type's schema.
- `input` is what the module hands its own placement stages (`PlacementContext.input(type)`); other modules' stages do
  not know its type. The area bounds what the placement may write, widened by the reach of the pack's placement stages.
- A placement commits whole or not at all: blocks, then markers, and only when no stage rejected and none threw.
- A marker is one of the kinds the host keeps, a `PlacedMarker`: `PackMarker` (a marker the pack declares, by key),
  `TreeMaterial` (a tree block, for felling and growth) or `BuriedTreasure` (a chest treasure maps lead to).
- A rotated object turns its blocks with `BlockRegistry.rotate(state, turn)`: the `BlockTurn` says where the rotation
  moves a direction, and the platform turns the state's facing, axis or rotation to the nearest it has. `merge`,
  `withPropertyIfAccepted` and `matches` change and compare states the way the platform does (`BlockState.withProperty`
  only sets the text, unchecked), and the `STORAGE` trait tells a
  container that holds items, such as for loot.

## Validators

A module checks the pack for problems it cares about with a `Validator`. The host runs every validator of the
pack wherever it validates packs (when a pack loads, when studio opens it, on request) and reports their
problems with its own.

```java
registry.registerValidator(pack -> {
    for (ResourceId id : pack.resources().ids(KEEPS)) {
        Keep keep = pack.resources().require(KEEPS, id);
        if (keep.towers() > 8) {
            pack.report(Problem.error(Subject.file("keeps/" + id.path() + ".json"), "Keep has " + keep.towers() + " towers; at most 8 fit."));
        }
    }
});
```

A validator sees the pack's resources, module kinds included, and `pack.settings()`: every settings entry of
every dimension, region and biome of the pack. It reports each `Problem` with a severity and its subject, a
resource id or a file below the pack root. An `ERROR` stops the pack from loading; a `WARNING` is only shown.
A validator reports and returns; one that throws fails the pack's validation, naming its module.

## World state and lifecycle

Stage bodies and services are shared by every world and every invocation, so a module keeps nothing in fields. What
it builds for one world, such as a cache or a worker, it keeps in that world's state store:

```java
private static void plant(ChunkStageContext context) {
    planted(context.world()).add(context.chunkX(), context.chunkZ());
}

static PlantedChunks planted(PackWorld world) {
    return world.state(PlantedChunks.class, PlantedChunks::new);   // one per world, created on first use
}
```

- `ctx.world()` is the `PackWorld` an invocation generates: its seed and purpose seeds, the pack snapshot's resources, the settings of
  its dimension and the regions and biomes it can reach, and `state(type, create)`. Every stage context has it and it
  never throws: a placement outside the worlds the host generates, such as a studio paste, gets a world of its own with
  the pack's resources and settings, a seed the host picks and a state store that lives as long as the placement.
- `state` creates each type once per world, even when threads race, and hands every caller the same instance. A
  factory may ask for other state; asking for the type being created fails instead of recursing. A factory that throws
  keeps nothing. Use a class of the module as the type.
- A state that is `AutoCloseable` is closed after the world stops, once nothing generates with it.

The state store is in memory and ends with the world. What must outlive a restart, such as when a spawner last fired,
a module keeps with `RunningWorld.saved`. Only a running world has a folder to save in, so a tool's world has none.
The module registers the key in `setup` like a mantle layer, under its own id:

```java
private static final SavedKey<LastWaves> WAVES = new SavedKey<>(ResourceId.parse("example:waves/last"), LastWaves.class,
        new LastWavesCodec(), LastWaves::new);

registry.registerSaved(WAVES);                                  // in setup

LastWaves waves = world.running().orElseThrow().saved(WAVES);   // the world's one instance, read on first ask
waves.record(now);                                              // changed in place; the module owns its thread safety
```

- The host reads the value when it is first asked for (`initial` when none is stored), writes it when it saves the world
  and when the world stops, and hands a world started over the same storage, such as after a hotload, what the old
  one wrote.
- `StorageCodec.write` may run while module threads change the value, so it must read it safely. A stored payload the
  codec rejects is logged and replaced with `initial`.
- `saved` throws `IllegalArgumentException` for a key nobody registered, and for another key with the id of a
  registered one. The id is the module's id or starts with it and `/`, as a mantle key's does.
- The id names the stored file, so none of its segments is empty, `.` or `..`.

A module that wants to act when the host loads a pack or starts or stops a world registers a `ModuleLifecycle`. Every
hook does nothing by default, and a module class may implement it and register itself:

```java
public final class RuinsModule implements IrisModule, ModuleLifecycle {
    public void setup(ModuleSetup setup) {
        setup.registry().registerLifecycle(this);
    }

    public void worldStarted(PackWorld world) {
        planted(world);                       // build the state now instead of on the world's first chunk
    }
}
```

- `packLoaded(pack)` runs once per pack snapshot, before the first world using it starts: `pack.resources()`,
  `pack.settings()` (every entry of the whole pack) and `pack.log()`, the module's log.
- `worldStarted(world)` runs before the world generates anything, `worldStopped(world)` when it stops; its state is
  still readable there.
- `warmUp(world)` runs after every module's `worldStarted` and may be slow: build caches in the world's state there,
  not in `worldStarted`. The host may run it off the main thread and waits for it before the world's first
  generation. A failing `warmUp` does not stop the world; the host reports it naming the module and the cache is built
  when first needed, so never put anything there the world cannot do without.
- A hotload, or a reload of the pack's resources while worlds run (a tool rewrote files), stops each world, loads the
  new snapshot and starts the worlds again with empty state. State never outlives the resources it was built from.
- Hooks run in module id order, stops in reverse. A failing `packLoaded` or `worldStarted` stops the world from
  starting, naming the module (modules that had started get `worldStopped`); every `worldStopped` runs even when one
  fails.

## Logging

A module writes to its own `ModuleLog`, which the host labels with the module's id and writes to its own log:

```java
private static void decorate(ChunkStageContext context) {
    context.log().warnOnce("empty:" + biomeKey, "Empty block data for " + biomeKey);   // [Iris/WARN] [iris:decorant] Empty block data for plains
    context.log().debug("decorated chunk " + context.originX() + "," + context.originZ());
}
```

| Method | Use |
| --- | --- |
| `debug(message)` | Detail for people debugging the module. Written only when the host's debug setting is on. Building the message still costs, so keep it cheap or guard it with `debugging()`. |
| `debugging()` | Whether `debug` writes anything now: the guard for a message that is costly to build. |
| `info(message)` | What a server owner may want to see once, such as what the module did at startup. |
| `warn(message)` | Something is wrong or unusual and the module carries on. |
| `warnOnce(key, message)` | `warn` the first time the key is used on this module's log, `debug` every time after. For a condition met per block or per chunk. Keys belong to the module, so another module's identical key does not hide yours; keep the key space small (a biome name, never a coordinate). |
| `error(message, cause)` | A failure the module caught and cannot recover from. The host logs it and reports `cause` like its own failures. A null `cause` only logs. |

- A message is plain text, never a format string.
- `context.log()` (stage bodies, object generators), `fit.log()` (vanilla structure fits), `world.log()` (services and
  lifecycle hooks, which get a world as their argument), `pack.log()` (`packLoaded`) and `setup.log()` are the same
  module's log.
- Do not use `System.out`, `System.getLogger` or a logging library of your own: the host cannot label or report them.

## Services

The host asks modules for what only they know, such as which objects a chunk gets or where a structure is, through
services: small interfaces marked `@Service` in `com.volmit.service`. A module registers a provider; the host looks
them up in the pack's catalog instead of knowing the feature.

```java
public void setup(ModuleSetup setup) {
    setup.registry().registerService(ObjectsAt.class, new RuinsObjects());
}

final class RuinsObjects implements ObjectsAt {
    public Set<ResourceId> objectsAt(PackWorld world, int chunkX, int chunkZ) {
        return planted(world).contains(chunkX, chunkZ) ? Set.of(world.resources().idOf("ruins/arch")) : Set.of();
    }

    public PlacedBy placedBy(PackWorld world, ResourceId object) {
        return PlacedBy.NOTHING;
    }
}
```

- Providers hold no state: a provider serves every world and the host may call it from any thread, several at once.
  Service methods take the world, and the provider keeps what it needs in the world's state store.
- Services compute; they never act on the platform. What should happen in the world comes back as a value the host
  carries out on the right thread.
- Services name pack resources by `ResourceId` (`world.resources().idOf(key)` for a key the pack wrote), and absent
  values in what they return are `Optional`s.
- A service that answers about the world as it runs, outside any chunk, reads `world.running()`: the layout over every
  column (`layout()`, a `TerrainSampler`), the settings at a column and what a biome declares (`settings()`), the
  highest block now, the cave floors the mantle marked, the time and weather (`sky()`, empty until the platform has
  the world), the platform's loot tables and whether the world is a debug (studio) world. It is empty for a tool's
  world, which nothing runs.
- Each service says how several providers combine, with `@Service(Providers.X)`: `ALL` (the host asks every provider
  and combines the answers), `FIRST` (the host asks in order until one answers) or `ONE` (at most one provider per
  pack; a second fails the pack load, naming both modules). Providers are in module id order, each module's in
  registration order.

| Service | Providers | What the host asks |
| --- | --- | --- |
| `ObjectsAt` | `ALL` | The pack objects a module would place in a chunk, predicted without generating it, and the biomes and regions of the layout that place a given object (`PlacedBy`), so the host can narrow an object search to the areas it can appear in. |
| `StructureLocator` | `FIRST` | Every structure the module knows (the structure index), the ones a world places and can locate (commands complete them), every id under which it finds what a world places (`placed`: its own structures and the platform structures it starts or stands in for), and the nearest start of one, as a `LocateResult`: `Found(position)`, `NotFound` or `SearchLimitReached`. Ids are lower case, so a host matching typed keys matches without regard to case. |
| `SaplingGrowth` | `ALL` | The tree a growing sapling becomes, as a `Growth` of placed blocks (the first module with one wins; none leaves the growth to the platform), and the objects the module places as trees (`TreeObjects`). The host passes `Surroundings` to read the world around the sapling and places the blocks itself. |
| `Loot` | `ALL` | The loot tables a container draws from at a `Site` (`Table` of the pack, `PlatformTable` with the seed to draw it with), in the order their items are added, the `ItemDrop`s one table gives there, and the seed to shuffle the container's slots with (`shuffleSeed`, the first one given wins). The host puts them together, tells listeners, creates the stacks and shuffles the container. |
| `BlockDrops` | `ALL` | What a module drops for a broken block: `ItemDrop`s, and whether the platform's own drops of the block are replaced. |
| `EntitySpawns` | `ALL` | A `Plan` of entities to spawn in a chunk or at a marker, each `Attempt` an `EntitySpec` with the block, light range and surface it needs; how many appeared (`spawned`); what a marker means (`MarkerRule`); one entity of the pack on request. The host checks light, surface and free space, spawns, and applies the spec. |
| `CaveBiomes` | `ONE` | The pack biome a module puts at a position whatever the regions say (a band of the deep dark), and how far below the surface a biome's caves begin. The host places the cave biome of a position itself and asks this where a module overrides it. |
| `ProtectedFloor` | `ONE` | Whether the lowest layer of the world (height 0) belongs to the module that shapes the terrain, such as a bedrock floor it writes itself. The host then discards the blocks other placements and stages write there. Asked once per world; no provider protects nothing. |
| `AmbientEffects` | `ALL` | The effects due for a player standing at a position: a `Sound`, `Particles`, `Command`s and a `Potion` per `Effect`, every random choice already made (offsets, scatter, duration). The host asks now and then, carries out the parts the platform has, and keeps a stronger potion effect the player already has. |
| `VanillaStructures` | `ALL` | Rules, starts, shifts and reaches for the platform's own structures, fitting the terrain to the starts in one chunk through the `BlockAccess` the host hands over, and finding the starts a module makes of them (see Vanilla structures). |
| `VanillaFeatures` | `ALL` | Whether the platform runs its own placed features (ores, trees, plants, springs) over a world's terrain, and which decoration steps (`DecorationStep`) and features it skips there. Off unless a module turns it on; any module may refuse a step or a feature. Asked once per world, step and feature. |

```
Service example.Clock is provided by module example:alpha from modules/alpha.jar already, and module example:beta from modules/beta.jar provides it too; a pack has one provider of it (Providers.ONE), so remove one of them
```

## Studio tools

A module shows what it builds in the studio through views in `com.volmit.tooling`, registered with
`setup.tooling()` while `setup` runs. The studio draws them; the module only answers what they show.

```java
public void setup(ModuleSetup setup) {
    setup.tooling().registerView(new RuinScenes());
}

final class RuinScenes implements SceneView {
    public ResourceId id() { return ResourceId.parse("example:ruins"); }
    public String label() { return "Ruins"; }
    public String description() { return "Each ruin the pack writes, as it is placed"; }

    public List<SceneEntry> entries(PackWorld world) {
        return List.of(new SceneEntry("arch", "Arch", Optional.empty(), 1));
    }

    public Optional<GeneratedObject> scene(PackWorld world, String key, int variant) {
        return world.resources().objects().object(world.resources().idOf("ruins/" + key)).map(PlaceableObject::geometry);
    }
}
```

- A `SceneView` lists entries (`SceneEntry`) and gives each variant's blocks as a `GeneratedObject`, drawn in 3D. A
  `PatternView` lists entries (`PatternEntry`) and gives the blocks one lays over a `PatternArea` at a depth as a
  `BlockPattern`, drawn from above. `StudioView` is sealed to these two kinds: the studio draws nothing else.
- A view's id is the module's id or starts with it and `/`. A second view with an id already registered fails the
  pack load, naming both modules.
- The studio asks from its own threads, several at once, for a world of the pack it shows. A view keeps what it
  needs between calls in the world's state, like a service.
- An `EditableSceneView` also names the file an entry is written in (`SceneFile`) and reads that file's text
  (`JsonText`, which keeps where each value is written) as a `NodeGraph`: a `GraphNode` per JSON object, with its
  settings (`NodeSettings`, shown as the pack's schema describes their type), the nodes it holds and the `Edits` the
  studio may make (a child list to move nodes into, whether it moves, the `Addition`s it offers). Places in the file
  are `JsonPath`s of `Key` and `Index` steps. The studio writes the file itself, rewriting only what changes; the
  view never writes.
- Absent values are empty, never blank or `-1`: an entry without a group, a node that draws no part of the scene
  (`GraphNode.part()`), a column a pattern places nothing at (`BlockPattern.pickAt`).

## Commands

A module adds commands to the host's command root, such as `/iris structure list`, with `registerCommand`. The same
`Command` runs on every platform; the host parses it, completes it and runs it.

```java
private static final Argument<ResourceId> STRUCTURE = Argument.resource("structure", RuinResources.STRUCTURES);
private static final Argument<Long> RADIUS = Argument.integer("radius", 1, 1000).orElse(48L);

public void setup(ModuleSetup setup) {
    setup.tooling().registerCommand(Command.named("ruins").alias("rn")
            .describe("Ruin tools")
            .child(Command.named("info").argument(STRUCTURE).runs(this::info))
            .child(Command.named("scan").argument(RADIUS).runs(this::scan)));
}

private void info(CommandContext context) {
    context.sender().info("Ruin " + context.get(STRUCTURE));
}
```

- `Command` is immutable: `named`, `alias`, `describe`, `child`, `argument`, `runs` and `openToEveryone` each return a
  changed copy. A word that names a sub-command always selects it. A command with no action only lists its children.
- Arguments are typed: `word`, `text` (the rest of the line, last only), `integer`, `decimal`, `flag`, `choice` (an enum
  constant written in lower case with `-`), `resource` (an id of a `ResourceKind` in the pack) and `registered` (an id in
  one of the platform's registries, by `Reference`). `orElse` makes an argument optional; an optional argument may not
  come before a required one. `suggesting` adds completions. Read a value with `context.get(argument)`.
- The host resolves the pack before it parses the line: `pack=<name>` taken out of the arguments, else the pack of the
  sender's world, else the pack of the sender's open studio, else it fails with `Name a pack: pack=<name>`. A command
  exists only for packs whose jars contain its module. `context.pack()` is that pack, `context.world()` the sender's
  world when it generates with it, and `context.platform()` the platform's registries.
- An action runs on a worker thread, never the server's main thread or a region thread, so it may block. What it throws
  reaches the sender as an error and the module's log. `Sender` messages are plain text; the host styles them as
  information, success or error, and delivers them on the thread the platform needs. There is no colour and no
  translation.
- By default only operators run a command (Bukkit `iris.all`, game master on modded); `openToEveryone()` lifts that.
- `ToolPack` is the pack as a tool sees it. `edit` is the only way to change its files: one edit runs at a time per pack,
  the host does not hotload during it, and it reloads the resources once after the edit returns normally. Atomicity across
  files is the edit's own job. `outputFolder()` is a folder outside the pack for what tools produce, and `objects()` reads
  and writes pack objects in the host's object format (`ObjectCodec`).
- A root command's name and each alias are root names. One that another module of the pack took fails the pack load,
  naming both modules. A host without commands, such as a build check, accepts the registration and ignores it.

## Module jars

A pack loads its modules from `<pack>/modules/*.jar`. A jar holds one or more modules. Each module's
entry point is its `IrisModule` class, one per line in the jar's `META-INF/services/com.volmit.IrisModule`:

```
modules/moss.jar
  META-INF/services/com.volmit.IrisModule    contains: com.example.moss.MossModule
  com/example/moss/MossModule.class           public, public no-argument constructor
  com/example/moss/MossSettings.class
```

`info()` gives the module id, display name and version. The host reads it once, when it finds the module.
Every stage id of the module starts with the module id and `/`; the name is what logs show. Names
and versions must fit on one line: line breaks and other control characters are rejected.

Every entry class must be defined in the jar that lists it. No namespace is reserved: a pack module
may use any namespace, `iris` included. A module id is loaded once, though. Two modules with the same
id, in one jar, in two jars or one of them compiled into the host, fail the load and the message names
both sources, jar and class:

```
Duplicate module example:moss from modules/moss.jar (com.example.moss.MossModule); already loaded from host classpath (com.volmit.example.placement.MossModule)
```

`ModuleJarCheck.check(jar, exposedHostPackages)` (also its `main`) runs the load's class checks on one
jar, without loading it: for a build that writes a module jar and wants to fail on a jar a pack would
refuse. A build that checks against a full copy of an exposed library, while the host's jar strips some
of its classes, passes the stripped ones too (`check(jar, exposedHostPackages, leftOutClasses)`, or
`--left-out=<file>` on the command line; one Ant pattern or class per line): a jar that references one
fails, naming the class. `--platform=<name>:<path to the platform api jar>`, such as `--platform=bukkit:libs/paper-api.jar`,
also checks the jar's tooling parts of that platform (see below); without it parts are skipped and the output lists them.

A module compiles against the Iris API (`com.volmit:iris-api`) as `compileOnly` (Maven: `provided`) and never ships it;
Iris provides the API at runtime. At runtime a module can use the Iris API (`com.volmit.*` except the host side,
`com.volmit.host`), the
packages the host exposes besides it (the built-in modules' stages and types, so packs can require
their stages, and the libraries the host shares: Iris exposes VolmLib, `art.arcane.volmlib`, and Gson,
`com.google.gson`), the JDK and the classes in its pack's module jars. Iris internals and the other
libraries Iris bundles are hidden. Iris's own built-in modules (the IrisModule build) compile against exactly that:
`iris-api`, VolmLib and Gson, all `compileOnly`, and run `ModuleJarCheck` on their jar.

A module may use any library. One the host does not expose it ships inside its own jar or as another
jar of the pack, and never borrows Iris's copy. A class may appear in only one jar of a pack, so two jars that
shade the same library must relocate it. The API's package and the other exposed packages belong
to Iris: module classes, and the libraries they shade, must not use them.

Libraries often reference optional integrations they only use when present. A class that neither
the pack, Iris nor the JDK has therefore does not fail the load: the pack loads, logs one warning
listing such classes per jar, and code that needs one throws `NoClassDefFoundError` when it runs.
### Optional packages

A jar that expects such classes to be absent declares their packages, with their subpackages, in its manifest, and
the warning leaves them out. Which classes a jar may miss is the jar's business, not a module's identity:

```kotlin
tasks.jar {
    manifest { attributes("Iris-Optional-Packages" to "org.slf4j org.apache.logging.log4j") }   // spaces or commas
}
```

A declaration covers only that jar, all of its modules. It never excuses a load error: a class Iris hides,
a JDK class modules cannot see or use, or a class missing from an exposed package fails even inside
a declared package. Package names follow the rules of exposed packages (whole packages, trailing dot
accepted, wildcards rejected).

Modules see the JDK through the platform class loader. JDK modules outside it, such as
`jdk.compiler` and `jdk.jshell`, are not available to modules. Of the available modules, modules
can use only the packages exported to them: `jdk.internal.misc.Unsafe` fails the load, while
`sun.misc.Unsafe` from `jdk.unsupported` is the supported way to use Unsafe. A server started with
`--add-exports <module>/<package>=ALL-UNNAMED` makes that package usable.

Iris never downloads modules and does not check which API version a jar was built for. A pack
ships its module jars, and keeping them compatible with the Iris version it targets is the pack
publisher's job.

### Tooling parts

A module's commands, editors and importers that need a platform (Bukkit, say) ship in the same jar as a tooling part.
The manifest names each part's entry class per platform, several entries separated by spaces:

```kotlin
tasks.jar {
    manifest { attributes("Iris-Tooling-Parts" to "bukkit=art.arcane.iris.tools.bukkit.StudioPart") }
}
```

A part is the entry class's package and its subpackages, and a class belongs to one part at most: entries of different
platforms, or of nested packages, may not overlap. A malformed entry, or an entry class the jar does not hold, fails the
pack load naming the jar and the entry.

The jar's classes split in two. Generation code, everything outside the parts, is checked as above, and it may not use a
part class: the load fails with "Generation code cannot use the bukkit part; move the call into the part", naming both
classes. Part classes are not generation code. A host that runs on a platform (`ModuleHost.runningOn`) checks that
platform's parts when the pack loads, and skips the parts of other platforms unread. A part can use its own packages,
the platform's api, and what generation code can use, including the pack's own generation classes. It cannot use Iris
internals (`art.arcane.iris.*`, except what the host exposes, such as `art.arcane.iris.builtin`), `net.minecraft`,
`org.bukkit.craftbukkit`, `com.mojang`, `com.volmit.host`, or another part.

The host loads a part through its own class loader, `ToolingPartLoader`, a child of the pack's. It defines the part's
classes from the same jar copy, hands the platform's api packages to the platform's loader and everything else to the
pack's loader, so a part gets the same `Class` objects the modules registered. The adapter opens it
(`LoadedPack.openToolingPart`) and closes it before the pack; closing it leaves the pack's loader open. The generation
loader never sees `com.volmit.tooling.<platform>`, the platform parts of the tooling api; `com.volmit.tooling` itself stays
visible.

## Settings for studio

Modules describe their settings in the settings classes themselves. Module jars ship no JSON Schema
or workspace files: the pack load collects the description, and Iris writes the studio schema and
completion from it.

```java
@Desc("Plants flowers and bushes")
@Feature(id = "example:flora", name = "Flora", key = "flora", targets = Level.BIOME)   // packs write "flora": {...}
public final class FloraSettings implements IrisFeature {
    @Desc("Plants per chunk") @Required @MinNumber(0) @MaxNumber(64)
    private int density = 4;                                         // default 4

    @ResourceRef(PackBiome.class)
    private List<String> biomes = List.of();                         // completes the pack's biomes

    private BlockState flower = new BlockState("minecraft:poppy");   // completes blocks without @Ref
    private Season season = Season.SPRING;                           // completes the constant names
    private Patch patch = new Patch();                               // a class of the module, described once
}
```

- `@Desc` on a class, field or enum constant is its description. `@Required`, `@MinNumber`, `@MaxNumber` and
  `@MinItems` (the least length of a list, set or array) are checks for the host to render.
- Every settings class, nested ones included, needs a no-argument constructor, which may be private.
  The host creates settings with it, and the field values of that instance are the defaults studio
  shows.
- Field types: `boolean`, primitive or boxed numbers, `String`, `ResourceId`, `BlockState`, enums,
  `List`, `Set` and arrays of these, `Map<String, V>`, `Object` for any JSON value, and concrete classes,
  which are described the same way, IrisApi's value types among them (see Values in settings). `@Snippet("example/flora/patch")` on a class lets packs write it as a file under
  `snippet/example/flora/patch/`. A pack module's snippet names start with its module id as a folder
  path (`example/flora` for `example:flora`).
  Anything else fails the pack load: `char`, other JDK classes such as `UUID` or `Optional`,
  interfaces and abstract classes, inner (non-static) classes, collections without type arguments,
  concrete collections such as `ArrayList`, and maps with other keys.
- `@Ref(Reference.X)` says what a `String` or `ResourceId` names, also on lists, sets and arrays of
  them, so studio offers the right list: `BLOCK`, `ITEM`, `ENTITY`, `STRUCTURE`, `STRUCTURE_PREFIX`
  (a structure or a family prefix such as `minecraft:village`), `STRUCTURE_SET`, `TEMPLATE_POOL` (a jigsaw template pool),
  `POTION_EFFECT`, `ENCHANTMENT`, `LOOT_TABLE` or `SPECIAL_ENTITY` (a mob of another plugin) from the server's registries, or `NOISE_STYLE` from the host's noise
  styles. `BlockState` fields name blocks without it. `@Ref` on any other field fails the load.
- `@ResourceRef(Keep.class)` says that a `String` or `ResourceId`, or a list, set or array of them, names a pack
  resource of the kind read into that class, by its key: a host kind of `PackResources` (`PackBiome`, `PackRegion`,
  `GeneratedObject`, `PackExpression`, `PackImageMap`) or a module resource kind (see Pack resources of a module).
  Packs write the key in the form they always have (`desert/dunes`); on a `ResourceId` field the host reads that as
  the pack's id (`resources.idOf(...)`), so the value goes straight to `PackResources.find`. A class no kind of the
  host or the pack reads into fails the load, and so does an option with both `@Ref` and `@ResourceRef`.
- Packs write settings under the short `key` the feature declares, which is required: a plain JSON field name
  (not blank, no `:`), the same at every target: `"moss": {"amount": 0.5}`. The id (`example:moss`) stays the
  feature's identity in the stage graph, logs and `settings.declared(MossSettings.class)`; packs never write it.
  A key is unique per target across all loaded modules and the host's own JSON fields of that target
  (`ModuleHost.reservingFields`), so a module cannot take `name` or `generators` for a biome. A clash refuses the pack
  load with one message naming the key, the target and both claimants (module id and jar, or `Iris` for a host field):
  `biome key "deposits" is claimed by iris:deposit (host classpath) and acme:ores (modules/acme-ores.jar); give one of the settings another key in its @Feature`.
- One shape per key. The JSON under a key is the object of the class's options, `"moss": {"amount": 0.5}`, unless the
  class declares `root`, the name of its only option: then the key holds exactly that option's value and an object is
  not accepted. With `@Feature(id = "example:flora", key = "flowers", root = "flowers", ...)` over
  `private List<String> flowers`, packs write `"flowers": ["poppy", "daisy"]`. `root` must name the class's only option
  (nothing could set another), or the load fails. There is no second form: the studio schema renders one shape and a
  wrong shape fails with a message naming the key and the file.
- Options keep the order the class declares them in, a superclass's first.
- Settings that bring other pack biomes into the pack's layout implement `world.LinkedBiomes`: `treeBiomes()` are biomes that join the
  biome tree of every region the owner is in (the host registers their terrain generators with the region, as it does an
  owner's children), and `usedBiomes(dimension)` are biomes the settings use besides, which the dimension reaches so their
  objects, decorators and settings take part in generation. `dimension` is a `PackSettings` of what the owner's dimension
  declares itself (`dimension.declared(CarvingSettings.class).dimension()`), for settings that name something the
  dimension declares, such as a band of its caves. The host asks every dimension and biome
  it reaches and ignores blank keys and keys naming no biome.
- Built-in settings may hold classes the host describes itself, such as Iris's palettes, which the host
  names with `ModuleHost.describingHostTypesIn(...)`. They appear as `HostKind` and pack modules cannot
  use them. Both are deprecated for removal: transitional, until the cut-over.
- Settings named in `when(...)` and object generator settings are described automatically. Settings
  that no stage names, such as ones read only through `FeatureSettings.at(...)`, are registered with
  `setup.registry().registerSettings(WindSettings.class)` in `setup`, one call per type. Their feature id follows the module
  id like any other, and once registered, other modules may name them in `when(...)`.

A load error names the field, the settings and the module:

```
Field com.example.flora.FloraSettings.id of settings example:flora (module example:flora from modules/flora.jar) uses java.util.UUID, a JDK class settings cannot use; settings fields can be booleans, numbers, String, ResourceId, BlockState, enums, lists, sets and arrays of these, Map with String keys and classes with a no-argument constructor
Field com.example.flora.FloraSettings.count of settings example:flora (module example:flora from modules/flora.jar) has @Ref(BLOCK) on int, but @Ref fits only String and ResourceId and lists, sets and arrays of them
Class com.example.flora.Shape of settings example:flora (module example:flora from modules/flora.jar) has no no-argument constructor; the host creates settings with it and reads their defaults from it, so declare one, private if nothing else should use it
Module old:flora from modules/old.jar: The @Feature of settings class com.example.flora.Patch has no key, the JSON field packs write the settings under; add key = "floraPatch" (or another plain field name) to it and rebuild the jar
Snippet name 'patch' of com.example.flora.Patch of settings example:flora (module example:flora from modules/flora.jar) is not under a module of the pack; snippets of pack modules start with their module's id as a folder path, such as 'example/flora/patch' (folder snippet/example/flora/patch/)
```

## Items and entities

A service describes an item or an entity as a value after making every random choice, and the host creates it on the
platform. Everything the platform registers is named by a `ResourceId` (`minecraft:sharpness`); a module turns a key a
pack wrote without a namespace or in upper case into one with `ResourceId.ofPlatformKey("SMOKE")` (`minecraft:smoke`).
An item or entity type is a `ContentKey`: `Registered(id)` for one of a registry, or `Provided(provider, name)` for one
another plugin provides by a name an id cannot hold (`mythicmobs:SkeletonKing`); the host routes a registered id of
another namespace to the plugin that owns it.

`ItemDrop` is one stack: the item, the amount, its `Look` (name, lore, custom model, leather colour as RGB, `DyeColor`),
enchantments (`ItemEnchantment`), attribute modifiers (`ItemAttribute` with an `Operation`), its `Wear` (unbreakable,
damage as a fraction of the item's durability), `PlatformExtras` (item flags and custom NBT, which only some platforms
apply) and, for debugging, its `Source`. `EntitySpec` is one entity: its type, `Appearance` (name, glow, baby and a
panda's genes), `Behaviour`, equipment (`ItemDrop`s), attribute modifiers, `Company` (passengers, leash holder), `Drops`
(loot table ids and their shuffle seed), `Arrival` (spawn effect, console commands, the platform's name for the spawn
reason and whether the entity climbs out of the ground), whether the spec also applies to a mob of a content provider
(`adjustsProvidedMob`) and host extensions, which `extension(type)` finds: what only one host knows how to apply travels
as a value of that host's type and is ignored by the others. Modules ask the platform about
items through `resources.items()` (`ItemRegistry.maxDurability(item)`, empty for an item the platform lacks): whether
an item can be damaged decides whether a loot entry draws a damage roll, so a module needs it to keep its random stream.

## Values in settings

Settings describe blocks, noise and ranges with IrisApi's value types, which read the JSON packs already write:

| Type | Packs write | Use it through |
| --- | --- | --- |
| `BlockSpec` | `{"block": "iron_ore", "data": {"waterlogged": true}, "backup": {...}, "tileData": {...}}` | `resources.blocks().resolve(spec)` gives the `BlockState`, `blockEntity(spec)` its block entity |
| `PaletteSpec` | `{"style": "SIMPLEX", "zoom": 8, "palette": [{"block": "stone"}, {"block": "andesite", "weight": 2}]}` or `"snippet/palette/<name>"` | `resources.blocks().palette(spec, seed)` gives a `PaletteSampler`: `at(x, y, z)` is the block there |
| `NoiseStyle` | `{"style": "SIMPLEX", "zoom": 4, "fracture": {"style": "PERLIN", "multiplier": 2}}` or `"snippet/style/<name>"` | `resources.noise().sampler(style, seed)` gives a `NoiseSampler` |
| `StyledRange` | `{"min": 4, "max": 12, "style": {...}}` | `range.at(resources.noise(), seed, x, z)` |
| `Range` | `{"min": 0, "max": 15}` | `contains(value)`, `pick(random)` |
| `BlockPosition` | `{"x": 1, "y": 2, "z": 3}` | its components |
| `Object` | any JSON value | as decoded: objects as maps, arrays as lists, numbers as `Double` |

```java
@Feature(id = "example:veins", name = "Veins", key = "veins", targets = Level.BIOME)
public final class VeinSettings implements IrisFeature {
    private List<BlockSpec> palette = List.of(BlockSpec.of("iron_ore"));
    private NoiseStyle density = NoiseStyle.of("SIMPLEX").withZoom(16);
}

// in a stage
var resources = ctx.world().resources();
NoiseSampler density = resources.noise().sampler(veins.density(), ctx.world().seed(VEIN_DENSITY));   // ResourceId example:veins/density
BlockState ore = resources.blocks().resolve(veins.palette().getFirst());
```

Noise belongs to the host: a style names one of its noise generators (`@Ref(Reference.NOISE_STYLE)`), or an
expression or image map of the pack, and the host builds the noise. Seed it from a world seed (`world().seed(purpose)`), so the noise
lines up across chunks; the host keeps what it builds for the pack, so asking again is cheap. A `BlockSpec` may
name a block without a namespace, a custom block of the pack's `blocks` folder, or a block only some platforms
have; resolving it never fails: the backup, then air, stands in. A `PaletteSpec` picks one of its blocks per position
by noise (each block counts as often as its weight, the style's noise at x, y, z over `zoom` chooses); a palette of
one block is that block everywhere, and one without a block to pick (an empty list, or only weights of zero) is an
`IllegalArgumentException` when the sampler is asked for. Seed it like noise.

Every value type and settings class is decoded the same way (DESIGN.md, "Decoding contract"): an option left out or
written as `null` keeps its field's initialiser, and an enum name the enum does not know is an error. A getter
therefore never needs a `null` check; only an option whose initialiser is `null` is optional.

`NoiseSampler.signed(...)` is `sample * 2 - 1`, which a host may answer from a per-thread memo of the coordinates it was just asked (read it
where one field is sampled at the same points again and again, as a carver does). `scaled(factor)` is the same noise with the coordinates
multiplied by `factor`, folded into the host's generator, not a multiplication in front of it.

The types are values the host creates with their no-argument constructors and reads the JSON into, like settings,
so an omitted option keeps the default the type declares (a `Range` is 16 to 32 unless the pack or the field's
own default says otherwise). They keep the snippet folders packs already use (`style`, `style-range`, `range`, `palette`).
Studio describes them like settings; Iris may render its own description of the same JSON.

Directions, object rotation and scale and placement modes are not API types: a module that reads them declares its
own enum or settings class with the same JSON, and turns its blocks itself (`BlockRegistry.rotate` turns a state).

## Vanilla structures

A module decides over the platform's own structures, the ones Minecraft, data packs and mods register, with a
`VanillaStructures` service provider: the platform keeps generating one (`GENERATE`), the module places it as well
(`ALSO_PLACE`), the module places it instead and the platform's own starts are blocked (`REPLACE`), or nobody
generates it (`BLOCK`).

```java
public void setup(ModuleSetup setup) {
    setup.registry().registerService(VanillaStructures.class, new VanillaStructures() {
        @Override
        public VanillaStructureRule rule(ResourceId structure, VanillaStructureContext world) {
            return world.settings().declared(CastleSettings.class).values().stream()
                    .anyMatch(castle -> castle.replaces().equals(structure.toString()))
                    ? VanillaStructureRule.REPLACE : VanillaStructureRule.GENERATE;
        }
    });
}
```

The host asks every provider of the pack, in module id order. It asks `rule` once per world, structure and pack
snapshot, before the platform starts the structure and when it locates it, so a provider decides from the pack, never
from a position. Rules combine: a structure is blocked when any module blocks it, and placed by a module when any
module places it. The host applies the result on every platform it runs on.

Every method has a default that changes nothing, so a provider implements only what it takes part in:

- `starts(chunkX, chunkZ, context)`: the platform structures the module starts in that chunk itself. A
  `VanillaStructureStart` names the structure, its height above the bottom (`underground()` puts the structure's
  lowest block there; otherwise it sits on the surface), whether it takes the chunk from a platform start of the same
  structure, `JigsawOverrides` for jigsaw structures and a `StartMemo`.
  The context gives the world (`PackWorld`) and a `TerrainSampler` over the chunk. Answer from the world and chunk
  only: the host asks again when it locates structures and when it checks starts it finds in the world.
- `shift(start, context)`: how many blocks one of the platform's own starts moves up or down before the host
  publishes it. A `PlatformStart` gives its bounds, whether the platform generates it underground, whether the host
  aligns it itself (ocean monuments to the sea, desert and jungle temples to the terrain; a shift then applies on top),
  the dimension's bottom and the terrain's surface per column. Shifts of several modules add up; the host keeps the
  start inside the world.
- `reach(structure, underground, memo, context)`: how far around its pieces the module changes the terrain for a
  start (`StartReach.blocks(n)`, or `PLATFORM` for no further than the platform's own terrain adaptation). The host
  hands the start to every chunk any module's reach covers.
- `beforePieces(fit)` and `afterPieces(fit, placed)`: around the platform placing the pieces of the starts in one
  chunk. `StructureFit` is that chunk: its area, the world's blocks (`BlockAccess`, written in batches), the terrain's
  surface and natural block per column, and every start with its structure, step, the platform's terrain adaptation,
  memo and pieces (box, kind, ground level, jigsaw junctions, the processed template cells, template columns and
  legacy air). `state(type, factory)` keeps one object per chunk for the module, from `beforePieces` to the last
  `afterPieces`.
- `setup(context)`: before any world of the dimension exists, how often each structure set starts (`withFrequency`,
  multipliers of several modules multiply) and whether the data packs the host installs must leave the platform's own
  structure definitions alone (`protectingVanillaDefinitions`, for every pack once any dimension asks for it).

Finding the starts a module makes is `StructureLocator`'s, the one locating service: `startedStructures(world)`,
`locatableStarts(world)` and `locateStart(world, structure, fromX, fromZ, maxRings, accepts)` give the platform
structures the module starts itself, the ones of them it can find, and the nearest start of one, searched ring by ring
through the module's own placement grids. The host sends the platform's `/locate` and its own structure commands for
those structures there, then checks the start it got in the world; `accepts` lets it skip chunks it already refused.

```java
@Override
public List<VanillaStructureStart> starts(int chunkX, int chunkZ, VanillaStructureStartContext context) {
    if (!castleChunk(context.world().seed(), chunkX, chunkZ)) return List.of();
    int surface = context.terrain().surfaceHeight((chunkX << 4) + 8, (chunkZ << 4) + 8);
    return List.of(VanillaStructureStart.at(ResourceId.parse("minecraft:village_plains"), chunkX, chunkZ, surface)
            .withMemo(new StartMemo("{\"terrain\":\"VACUUM\"}")));
}
```

How a start meets the terrain is the module's: the API has no fit values. A `StartMemo` is one opaque, non-empty
text the module gives a start it places; the host persists it with the start and hands it back in `reach` and in every chunk's
`StructureFit`, so every chunk the start reaches fits it the same way, also after a restart. `JigsawOverrides` is a
value type like `PaletteSpec`: settings may hold it, and packs write it as Iris always read it. An option a pack leaves
out is empty in its getter (`Optional`, `OptionalInt`); no constant stands for "the definition's own".

## Host side

Loading a pack's modules and running their stages is the host's job, described in [HOST.md](HOST.md). A module
author does not need it.

## Values that stay

- `ResourceId` requires a namespace. Services and values name pack resources by it; a host converts to its own keys
  at its edge.
- `PackResources` serves one immutable pack snapshot; caches must not survive a reload.
  It serves pack objects (`OBJECTS`, `GeneratedObject`), biomes (`BIOMES`, `PackBiome`), regions (`REGIONS`,
  `PackRegion`), expressions (`EXPRESSIONS`, `PackExpression`) and image maps (`IMAGE_MAPS`, `PackImageMap`), the last
  two only as their ids, which noise styles name, and every module resource kind: `find(kind, id)` (empty when absent), `require(kind, id)` and
  `ids(kind)`, so `BiomeSample` ids look up directly.
- Value records say "absent" with `Optional`, `OptionalInt` or `OptionalDouble` components, never with `null` or an
  empty string (`ItemDrop`, `EntitySpec`, `AmbientEffects.Effect`). `StructureFit.Start.key()` is a `StartKey`, an
  identity the host makes once per platform start.
- A host reaches its own built-ins' facades through `extension(type)` on `PackWorld`, `InvocationContext` and
  `PackSettings`; see DESIGN, "How a module reaches the host".
- `BlockState` has value equality and sorted properties; `BlockRegistry` checks it against the
  platform and resolves `BlockSpec`s. It answers a state's traits (`AIR`, `SOLID`, `FLUID`, `FOLIAGE`, `DECORATION`,
  `VINE`, ...), tags, whether it attaches to a face (`canAttach`) and whether it can stand on a block (`canPlaceOn`, the
  rule decorations need), as the platform does. `NoiseSampler` is a typed resource and `PaletteSampler` the noise-picked palette of settings;
  `PackResources.noise()` builds noise samplers from `NoiseStyle`s.
- `GeneratedObject` is sparse, immutable local geometry. `PlacementRequest` aligns its anchor
  with a world position using checked arithmetic.
- `Hunk<T>` is a dense local volume (`width()`, `height()`, `depth()`); `ArrayHunk<T>` is the bounds-checked reference.
  A chunk's blocks are `GenerationContext.blocks()`, a `Hunk<BlockState>`.
- A placement stage writes a plain block with `access.set(x, y, z, state)`, without a position, record or `Optional`;
  a block with a block entity goes through `access.set(PlacedBlock)`. `BlockState.withProperty` copies the properties once.
- Invocation views (`ctx.mantle()`, its layers and blocks, a placement's `access().mantle()`) are made once per
  invocation, so asking again returns the same view.
- The `mantle` package keeps namespaced typed layers, lifetimes and buffered edits; `com.volmit.data.StorageCodec` stores
  their values, and the values of `RunningWorld.saved` (`com.volmit.data.SavedKey`).
- `IrisFeature`, `@Feature`, the config annotations (`@Desc`, `@Required`, `@MinNumber`,
  `@MaxNumber`, `@MinItems`, `@Snippet`, `@Ref`) describe settings; the host reads them with `OptionsMetadata`
  (`com.volmit.host`).

Invocation views and random streams expire when the invocation ends. Stage bodies are shared by
concurrent invocations and must not keep views, streams or mutable state; what a module keeps per world lives in the
world's state store (`PackWorld.state`).

## Validation

```sh
./gradlew check
```

The tests cover graph resolution and every error path, fusion, activation cascade, outputs,
rejection and rollback, setup-order determinism, `configured` reads, stage timings of all three
runners, id ownership of stages, settings, objects and
mantle keys, reach per stage, pipeline and mantle layer, reach worked out when the pack loads (mantle
stages only, never negative), one mantle stage per runner call with activation that follows its
requirements, chunk stages that declare reach or mantle writes (`StageGraphTest`, `ModuleRegistryTest`), settings entries per type in level order
(`ConfiguredSettingsTest`), and per-stage random streams: inserting a module
that draws leaves the other stages' draws unchanged, block stages draw one stream across their pass,
and the derivation is pinned to values computed from its specification (`StageStreamsTest`).
`WorldRandomTest` pins every `WorldRandom` method, bounds and edge cases to values computed by an
independent Python reference (`python3 -B api/src/test/reference/world_random.py`; the build does not
need Python) and fails if the type gains an unpinned method or implements `RandomGenerator`.
`PackSchemaTest` loads module jars and checks their description: features under their targets by key, a root option,
options in declaration order,
descriptions, required, bounds, defaults (floats, nulls, sets, maps, objects, a field holding `this`),
enums, collections, references, nested classes with a cycle, snippets, object generator settings,
explicit registration, built-in modules, the same result whatever the jar order, and the load error
for each unsupported field type, misplaced `@Ref` (pack kinds on `ResourceId` included), unusable
class, bad or repeated snippet name and pack snippet outside its module's folder, host types in built-in
settings (and the load error when a pack module uses one).
`PackSchemaTest` also loads a module jar with a resource kind: the kind's files described from its class,
`@ResourceRef` options as references to the kind (host kinds included), built-in kinds of host types, the load error for a pack kind
of a host type, for a folder two kinds claim or the host reserves, for an option with both `@Ref` and `@ResourceRef`,
and for a misplaced `@ResourceRef`.
`ModuleRegistryTest` checks kind ids, types and folders, the validator and lifecycle order, service providers in module
order, the load error for a second provider of a `ONE` service and for providers of unmarked interfaces or of the
wrong type. `WorldStatesTest` checks that each state is created once (also when threads race), nested creation, the
error for a type asked for while it is created, factories that fail or return null, and closing newest first past
failures. `LifecycleRunnerTest` checks hook order, warm-ups after the starts (each running even when one fails), stops in reverse, the stop of already started modules when a start
fails, and that every stop runs. `WorldStateExampleTest` (`example/world`) has a chunk stage, a service and a hook share
one world's state, and a new world start empty. `PackSchemaTest` also describes settings holding IrisApi's value types (`BlockSpec`, `NoiseStyle`,
`StyledRange`, `Range`, `BlockPosition`) and `Object`, and `SettingsValuesTest` checks the value types themselves
and how vanilla structure rules combine. `ModuleRegistryTest` also checks the order of service providers. `DiscoveredModuleTest`
checks that `info()` is read once. `LoadedPackTest` loads a pack with
built-ins and checks that a failed load and `close()` leave nothing open. `PackJarSnapshotTest` checks
that classes come from the copies, that the original can be replaced while loaded, that copies go
away on close and on failure, that errors name the original jar, and which leftover directories a
load removes. `PackModulesTest` compiles real module
jars into a temporary pack and loads them, including every jar error, duplicate classes in plain
and multi-release jars, API and other exposed classes in a jar, hidden host classes, JDK classes
outside the platform loader, non-exported JDK packages (`sun.misc.Unsafe` and `java.lang` still
load), several kinds of unusable class in one error, classes missing from an
exposed package, optional dependencies that only warn, declared optional packages (fully and partly
excused, per jar, never excusing an error, invalid names), references that must still load
(arrays, the JDK, other pack jars, missing annotation types), linkage errors during setup, and a
pack jar that requires a stage of a built-in module in `example.builtin`, outside `com.volmit`,
which loads only when the host exposes that package. `ExposedHostClassLoaderTest` checks what the
pack loader's parent shows, `ExposedPackagesTest` which package names it accepts and how they
match, `UnavailableJdkModulesTest` which JDK modules packs cannot see, and `NonExportedJdkPackagesTest`
which JDK packages they may not use. `PackModulesAddExportsTest` runs in its own JVM, the
`addExportsTest` task that `check` runs, started with
`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`; it checks that the exported package then
loads and that other internal packages are still reported. `PackModulesSummaryTest` and `MissingClassesWarningTest` check the log texts. The example
ports in `host/src/test/java/com/volmit/example` rebuild preparation, anchoring, block writes, stilt and
snow as separate modules, add a third-party moss module, and add a two-module chunk pipeline whose
`ChunkPipelineTest` checks any-column activation, per-column reads, stacked levels, a mantle stage
whose reach follows the pack's longest vein writing into the next chunk that a chunk stage then reads, that a chunk stage writes the mantle of its own chunk only, and that
one invocation makes its mantle views once (`StageGraphTest` checks the same for a placement). `ExposedPackagesTest`
also checks that the host side is never exposed. There is no Minecraft integration or production storage in these tests.
