# IrisApi

Java 25 contracts for Iris modules, with a small host-independent reference implementation.
Main code depends only on the JDK. No Bukkit, Iris implementation, Gson or Lombok types appear
in the public contracts. Nothing here is stable yet.

[DESIGN.md](DESIGN.md) explains the model and its rules. [CONTRACTS.md](CONTRACTS.md) lists
what the Iris host adapter still has to provide. Existing Iris features are not ported or wired
into this project; the test sources contain example ports only.

## Modules own their stages

There are no host-defined hooks or phases. A module declares stages as constants and defines
them in `install`. Other modules order themselves against those constants.

```java
public final class MossModule implements IrisModule {
    public static final PlacementStage<Void> SPREAD = Stage.placement("example:moss/spread");

    public ModuleInfo info() { return ModuleInfo.of("example:moss", "Moss", "1.0.0"); }

    public void install(StageGraph graph) {
        graph.define(SPREAD)
             .when(MossSettings.class)                      // only where a pack configures moss
             .requires(AnchorModule.HEIGHT, BlocksModule.COMPLETE)
             .softAfter(StageRef.of("iris:snow/finish"))    // ordering only, if snow is installed
             .run((ctx, moss) -> spread(ctx.output(AnchorModule.HEIGHT), moss.getFirst().value()));
    }
}
```

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
- `softAfter` and `softBefore` take ids and only order. There is no hard "before".
- `run(...)` ends a stage without output, `produce(...)` a stage with output and
  `accumulate(initial, collector)` a block stage whose accumulator becomes its output.
- `when(settingsType)` runs the stage only where those settings are configured, and the body gets every
  configured entry (see Settings levels below).
- `ctx.output(stage)` works only for stages the current stage transitively requires.
- `ctx.random()` is the stage's own random stream, derived from the invocation seed and the stage
  id. Installing or removing other modules, or how much they draw, never changes what a stage
  draws. A block stage has one stream per placement, drawn across the blocks of its pass.
  The stream is a `WorldRandom` (see Random streams below).

`IrisModule.register(FeatureRegistry)` stays for non-stage contributions: geometry generators,
mantle layers, settings no stage names (see Settings for studio), pack resource kinds and validators
(see below). Their feature ids, mantle keys and kind ids are the module id or start with it and `/`,
like stage ids: `iris:snow` or `iris:snow/depth` for module `iris:snow`. A settings type named in `when(...)` needs a feature id that at least one
module naming it owns in that sense, or its owner must have registered it, so another module may
still condition its stages on it, as moss does on `iris:snow`.

## Settings levels

Settings can be declared at several levels: the dimension, a region, a biome and, in a placement, the
placement itself. Each level's declaration is its own entry, and levels stack: nothing is merged and no
level overrides another. A module decides what several entries mean, such as running a deposit once per
entry, or using the most specific one.

```java
for (var entry : ctx.settings().at(DepositSettings.class, worldX, worldZ)) {   // every level at this column
    entry.level();   // DIMENSION, REGION, BIOME or OBJECT_PLACEMENT, in that order
    entry.source();  // the declaring resource, such as overworld:biome/desert/dunes
    entry.value();   // the decoded settings
}
```

Settings are read per column, at the column's surface biome. A chunk or mantle stage conditioned with
`when(...)` runs when any column of the chunk has an entry, so a biome border inside a chunk never
hides a feature, and its body receives every distinct entry of the chunk. It then reads
`ctx.settings().at(...)` per column, or at the chunk's centre column (local 7, 7) for something that
happens once per chunk. Something that spans several columns, such as a placed object, uses the column
it treats as its origin. In a placement the body receives the placement's own entry; a placement
declares each settings type once.

A feature whose settings class has exactly one option may be written as that option's value, such as
`"snow": 0.5` for `"snow": {"amount": 0.5}` or a bare list for a class holding one list.

## Coordinates

X and Z are world coordinates. Y is always the height above the dimension's bottom: 0 is the bottom block
of the dimension, whatever its minimum height is in the platform, so Y is never negative. This holds for
every API value that carries a height: terrain and biome samples, `BlockBox` bounds of the terrain
sampler, block views, placement edits and the mantle, hunks, block palettes and noise samplers. The host
converts to and from the platform's own Y at its boundary; modules never see it.

## Random streams

Stages and object generators draw from `com.volmit.random.WorldRandom`, an IrisApi type whose every
method is defined and pinned by tests, so the same seed gives the same world on every JDK. It is
SplitMix64 with `nextInt`, `nextLong` and `nextDouble` in plain, bounded and `origin, bound` forms,
plus `nextBoolean`, `nextFloat`, `nextGaussian`, `chance(probability)`, `pick(list)` and
`shuffle(list)`. It does not implement `java.util.random.RandomGenerator`, whose default methods the
JDK may change, so use `pick` and `shuffle` instead of `Collections.shuffle`. Bounded integers are
unbiased, `nextDouble` never returns its bound, and invalid bounds throw. It is not thread-safe: a
stage uses its own stream within one invocation and must not share or keep it.

## The mantle pipeline and reach

A chunk stage writes only its own chunk: the host may already have generated its neighbors. Work that
writes the mantle or reaches beyond its chunk, such as a carver or a vein that runs on into the next
chunk, is a mantle stage. Every mantle stage is its own pass: before a chunk generates, the host runs
the first mantle stage for every chunk within that stage's reach plus the reach of every later stage,
then the next stage the same way, in resolved order. So a stage sees what earlier stages wrote into the
chunks around it, and the chunk's mantle is complete when its chunk stages read it. Mantle stages have
no outputs: they pass data on through mantle layers. The two pipelines meet only in the mantle.

```java
public static final MantleStage<Void> CARVE = Stage.mantle("example:caves/carve");
public static final ChunkStage<Void> APPLY = Stage.chunk("example:caves/apply");

graph.define(CARVE).when(CaveSettings.class).reach(32).writesMantle(CAVES).run(CaveModule::carve);
graph.define(APPLY).run(CaveModule::apply);   // reads its own chunk's CAVES and writes blocks
```

`reach(blocks)` is the distance in blocks along X and Z beyond the stage's footprint: the chunk for a
mantle stage, the X/Z bounds of the placed object for a placement stage, whole columns in both cases.
It defaults to 0. `writesMantle(...)` names the mantle layers the stage writes; each must be
registered by some module. A chunk stage that declares either fails the install, and its mantle is
read-only. `pack.placement().reach()` gives the host the largest reach of a pipeline and the largest
per mantle layer.

When how far a mantle stage writes depends on the pack, such as the longest vein or the largest object
any level configures, it declares a `PackReach` instead of a number. The host works it out from the
pack's settings (`PackSettings.declared(type)`: every entry of the dimension and of the regions and
biomes it can reach) when the pack loads, and asks each mantle stage with
`ResolvedStage.reachBlocks(settings)`. Only mantle stages may declare one.

```java
graph.define(PLAN).when(VeinSettings.class).reach(VeinModule::reach).writesMantle(ORE).run(VeinModule::plan);

static int reach(PackSettings settings) {
    int longest = settings.declared(VeinSettings.class).stream().mapToInt(vein -> vein.value().length()).max().orElse(0);
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

public void register(FeatureRegistry registry) {
    registry.registerResourceKind(KEEPS);           // reads <pack>/keeps/**/*.json
}

// anywhere the pack's resources are at hand, such as a stage or a validator
Keep keep = resources.require(KEEPS.key(new ResourceId(resources.packId().namespace(), "towers/north")));
List<ResourceId> all = resources.ids(Keep.class);  // every keep of the pack, in id order
```

- A file's key is its path below the folder without `.json` (`towers/north`), and its id is
  `<pack namespace>:<key>`, like every pack resource. JSON keys inside the file are the class's field names.
- The kind's id follows the module id. The folder is one folder name directly below the pack root; the module
  chooses it, so a kind that replaces one of Iris's own keeps the folder and file format packs already have.
- One folder holds one kind and one class backs one kind: `PackResources` finds resources by type. Two kinds
  claiming one folder, or a folder the host reads itself (`ModuleHost.reservingFolders`), fail the pack load,
  naming both owners.
- The host creates each resource with the class's no-argument constructor and reads the JSON into it, as it does
  settings, so an omitted field keeps its default. It reads a resource once per pack load and hands every caller
  the same instance, so resources are values nobody may change. A reload (studio hotload) reads them again.
- Studio describes the files from the class like settings: `@Desc`, `@MinNumber`, `@Required` and the rest. An
  option that names a resource of a kind carries `@ResourceRef(Keep.class)` on a `String`, or a list, set or
  array of them, and studio completes the kind's keys. Built-in kinds may read into a class the host describes
  itself, as built-in settings may hold one.

```
Resource kind example:castle/keep of module example:castle from modules/castle.jar reads folder 'biomes', which the host reads itself; give the kind a folder of its own
```

## Validators

A module checks the pack for problems it cares about with a `Validator`. The host runs every validator of the
pack wherever it validates packs (when a pack loads, when studio opens it, on request) and reports their
problems with its own.

```java
registry.registerValidator(pack -> {
    for (ResourceId id : pack.resources().ids(Keep.class)) {
        Keep keep = pack.resources().require(KEEPS.key(id));
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

## Module jars

A pack loads its modules from `<pack>/modules/*.jar`. One jar holds one module. Its entry point
is the `IrisModule` class, named in the jar's `META-INF/services/com.volmit.IrisModule`:

```
modules/moss.jar
  META-INF/services/com.volmit.IrisModule    contains: com.example.moss.MossModule
  com/example/moss/MossModule.class           public, public no-argument constructor
  com/example/moss/MossSettings.class
```

`info()` gives the module id, display name and version, and optionally the packages whose classes
the module expects may be missing (see below). The host reads it once, when it finds the module.
Every stage id of the module starts with the module id and `/`; the name is what logs show. Names
and versions must fit on one line: line breaks and other control characters are rejected.

The host reserves namespaces for its own modules, such as `iris`. A pack module whose id uses a
reserved namespace fails the load, and because stage ids, settings ids and mantle keys start with the
module id, none of them can use it either:

```
Module iris:stilt from modules/stilt.jar uses reserved namespace 'iris', which only modules that ship with the host may use; give the module a namespace of its own, which its stages, settings ids and mantle keys then use too
```

A module compiles against the Iris API as `compileOnly` (Maven: `provided`) and never ships it;
Iris provides the API at runtime. At runtime a module can use the Iris API (`com.volmit.*`), the
packages the host exposes besides it (meant for built-in modules, so packs can require their
stages), the JDK and the classes in its pack's module jars. Iris internals and the libraries Iris
bundles are hidden.

A module may use any library. It ships the library inside its own jar or as another jar of the
pack, and never borrows Iris's copy. A class may appear in only one jar of a pack, so two jars that
shade the same library must relocate it. The API's package and the other exposed packages belong
to Iris: module classes, and the libraries they shade, must not use them.

Libraries often reference optional integrations they only use when present. A class that neither
the pack, Iris nor the JDK has therefore does not fail the load: the pack loads, logs one warning
listing such classes per jar, and code that needs one throws `NoClassDefFoundError` when it runs.
A module that expects such classes to be absent declares their packages in `info()`, and the
warning leaves them out:

```java
public ModuleInfo info() {
    return ModuleInfo.of("example:moss", "Moss", "1.0.0")
            .withOptionalPackages("org.slf4j", "org.apache.logging.log4j");   // with subpackages
}
```

A declaration covers only that module's own jar. It never excuses a load error: a class Iris hides,
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

## Settings for studio

Modules describe their settings in the settings classes themselves. Module jars ship no JSON Schema
or workspace files: the pack load collects the description, and Iris writes the studio schema and
completion from it.

```java
@Desc("Plants flowers and bushes")
@Feature(id = "example:flora", name = "Flora", targets = Target.BIOME)   // packs write "example:flora": {...}
public final class FloraSettings implements IrisFeature {
    @Desc("Plants per chunk") @Required @MinNumber(0) @MaxNumber(64)
    private int density = 4;                                         // default 4

    @Ref(Reference.PACK_BIOME)
    private List<String> biomes = List.of();                         // completes the pack's biomes

    private BlockState flower = new BlockState("minecraft:poppy");   // completes blocks without @Ref
    private Season season = Season.SPRING;                           // completes the constant names
    private Patch patch = new Patch();                               // a class of the module, described once
}
```

- `@Desc` on a class, field or enum constant is its description. `@Required`, `@MinNumber` and
  `@MaxNumber` are checks for the host to render.
- Every settings class, nested ones included, needs a no-argument constructor, which may be private.
  The host creates settings with it, and the field values of that instance are the defaults studio
  shows.
- Field types: `boolean`, primitive or boxed numbers, `String`, `ResourceId`, `BlockState`, enums,
  `List`, `Set` and arrays of these, `Map<String, V>`, and concrete classes, which are described the
  same way. `@Snippet("example/flora/patch")` on a class lets packs write it as a file under
  `snippet/example/flora/patch/`. A pack module's snippet names start with its module id as a folder
  path (`example/flora` for `example:flora`).
  Anything else fails the pack load: `char`, JDK classes such as `UUID` or `Optional`, `ResourceKey`,
  interfaces and abstract classes, inner (non-static) classes, collections without type arguments,
  concrete collections such as `ArrayList`, and maps with other keys.
- `@Ref(Reference.X)` says what a `String` or `ResourceId` names, also on lists, sets and arrays of
  them, so studio offers the right list: `BLOCK`, `ITEM`, `ENTITY`, `BIOME`, `STRUCTURE`,
  `POTION_EFFECT` or `ENCHANTMENT` from the server's registries, or `PACK_BIOME`, `PACK_REGION` or
  `PACK_OBJECT` of the pack. The pack kinds fit `String` only, in the form packs use (`desert/dunes`),
  never `ResourceId`. `BlockState` fields name blocks without it. `@Ref` on any other field fails
  the load.
- `@ResourceRef(Keep.class)` says that a `String`, or a list, set or array of them, names a resource of the
  module resource kind read into that class, by its key (see Pack resources of a module). The class must back a
  kind of the pack; anywhere else, or together with `@Ref`, it fails the load.
- Packs write settings under the feature id. Built-in modules may also set `@Feature(alias = "stilt")`,
  a short key existing pack JSON uses; a pack module that sets one fails the load. One JSON object may
  not hold both.
- Options keep the order the class declares them in, a superclass's first.
- Settings with exactly one option have a shorthand: packs may write the option's value instead of an
  object, and `FeatureSchema.shorthand()` says so.
- Built-in settings may hold classes the host describes itself, such as Iris's palettes, which the host
  names with `ModuleHost.describingHostTypesIn(...)`. They appear as `HostKind` and pack modules cannot
  use them.
- Settings named in `when(...)` and object generator settings are described automatically. Settings
  that no stage names, such as ones read only through `FeatureSettings.at(...)`, are registered with
  `registry.registerSettings(WindSettings.class)` in `register`. Their feature id follows the module
  id like any other, and once registered, other modules may name them in `when(...)`.

A load error names the field, the settings and the module:

```
Field com.example.flora.FloraSettings.id of settings example:flora (module example:flora from modules/flora.jar) uses java.util.UUID, a JDK class settings cannot use; settings fields can be booleans, numbers, String, ResourceId, BlockState, enums, lists, sets and arrays of these, Map with String keys and classes with a no-argument constructor
Field com.example.flora.FloraSettings.count of settings example:flora (module example:flora from modules/flora.jar) has @Ref(BLOCK) on int, but @Ref fits only String and ResourceId and lists, sets and arrays of them
Class com.example.flora.Shape of settings example:flora (module example:flora from modules/flora.jar) has no no-argument constructor; the host creates settings with it and reads their defaults from it, so declare one, private if nothing else should use it
Snippet name 'patch' of com.example.flora.Patch of settings example:flora (module example:flora from modules/flora.jar) is not under a module of the pack; snippets of pack modules start with their module's id as a folder path, such as 'example/flora/patch' (folder snippet/example/flora/patch/)
Settings com.example.flora.FloraSettings of module example:flora from modules/flora.jar set alias 'flora'; only modules that ship with the host may set one, so remove it: packs write these settings under their id "example:flora"
```

## Host side

```java
// Once, at startup: the same for every pack.
var host = ModuleHost.of(builtInModules)
        .exposing("art.arcane.iris.engine.modules")     // besides the API
        .reservingNamespaces("iris")
        .reservingFields(reservedHostFields)
        .describingHostTypesIn("art.arcane.iris.engine.object")   // classes the host describes itself
        .reservingFolders("biomes", "regions", "objects")         // pack folders the host reads itself
        .copyingJarsTo(cacheDirectory)                    // default: java.io.tmpdir
        .loggingTo(serverLogger);                         // a System.Logger; default: System.getLogger

// Per pack.
try (var pack = LoadedPack.load(packRoot, host)) {
    System.out.println(pack.placement().describe());

    var placer = new PlacementRunner(pack.placement());
    PlacementOutcome outcome = placer.place(request, edit, inputs, placementSeed,
            ConfiguredSettings.placement(objectId, stilt, snow));

    var mantle = new MantleRunner(pack.mantle());
    for (var stage : pack.mantle().stages()) {                            // each stage its own pass, first
        mantle.generate(stage.stage(), mantleContext, chunkSeed);         // for each chunk it reaches
    }
    new ChunkRunner(pack.chunk()).generate(generationContext, chunkSeed);
}   // stop using the runners before the pack closes
```

`ModuleHost` holds what the host passes to every pack load: built-in modules (their `info()` is read
once, here), the host packages pack modules may use, the JSON fields the host itself uses, the
reserved namespaces, the packages of the classes it describes itself and the pack folders it reads itself. Each method adds to what earlier calls configured. Package names, namespaces and
reserved field names are checked when it is built. `LoadedPack.load`
loads the pack's jars, installs them after the built-ins, catalogs settings and registrations,
describes them for studio and resolves the three pipelines. It returns a complete pack or throws with nothing left open. `close()`
releases the pack's class loader and its jar copies. `PackModules` and `LoadedModules`, which it
uses, stay available for tests and special cases.

The host derives one seed per placement or chunk, from the world seed and the placement's stable
identity or the chunk position, and passes it to the runner. Each stage's stream comes from that
seed and the stage id (`StageStreams` documents the derivation). Stages cannot reach a shared stream.

The load copies the pack's jars into a fresh temporary directory (`iris-pack-modules-*`) and reads,
checks and loads classes only from the copies. The originals are never held open, so a module author
can rebuild a jar while studio has the pack loaded, also on Windows, and a load sees one consistent
set of jars even if one is rebuilt meanwhile. Messages still name `modules/moss.jar`. The copies are
deleted when the pack closes or its load fails. A later load removes copies a crashed process left
behind, but only directories with this prefix and an owner file naming a process that has exited,
holding nothing but that file and jars.

Pack modules see the API plus the packages given to `ModuleHost.exposing(...)`, each with its
subpackages. The host chooses those packages, such as the ones that hold built-in modules' stage
constants. The load (`PackModules`, under `LoadedPack`) reads one entry point per jar, in jar file
name order, and loads every jar of the pack through one class loader. A pack without a `modules`
directory has no modules. Errors name the
jar: no entry point, several entry points, a class of an exposed package in the jar, a class that is
also in another jar of the pack (both jars are named), a class that uses a class Iris has but hides,
a class of a JDK module modules cannot see, a class of a JDK package not exported to modules, a
class missing from an exposed package, or an entry
class that cannot be loaded, is not an `IrisModule`, is not in that jar, fails to construct or whose
`info()` fails (an invalid optional package, for example). All checks before the last run before any
class of the pack is defined. The last error below lists every kind of unusable class the jar
uses, one group per kind:

```
Module jar modules/alpha.jar contains Iris API class com.volmit.FeatureAttachment and 130 more; Iris provides the API at runtime, so mark the Iris API dependency compileOnly (Maven: provided) and keep module classes out of com.volmit.*

Module jar modules/caves.jar contains class art.arcane.iris.engine.modules.CarveModule of package art.arcane.iris.engine.modules, which Iris provides to modules at runtime; compile against it compileOnly (Maven: provided) instead of shipping it, and keep module classes out of art.arcane.iris.engine.modules.*

Module jar modules/moss.jar uses classes that exist in Iris but are hidden from modules:
  com.example.moss.MossModule -> com.google.gson.Gson
  com.example.moss.MossSettings -> art.arcane.iris.engine.object.IrisBiome
Modules cannot use Iris's own classes or its copies of libraries; ship the library in a module jar of the pack, or use the Iris API.
Module jar modules/moss.jar uses classes of JDK modules that are not available to modules:
  com.example.moss.Scripts -> com.sun.source.tree.Tree (JDK module jdk.compiler)
Modules can use only the JDK modules of the platform class loader, such as java.base, java.sql and java.net.http.
Module jar modules/moss.jar uses classes of JDK packages that are not exported to modules:
  com.example.moss.FastBuffer -> jdk.internal.misc.Unsafe (package jdk.internal.misc of java.base is not exported)
Use the supported alternative where there is one, such as sun.misc.Unsafe from jdk.unsupported instead of jdk.internal.misc.Unsafe. Otherwise the server must be started with --add-exports <module>/<package>=ALL-UNNAMED.
Module jar modules/moss.jar uses classes that this Iris does not have, in packages Iris provides to modules:
  com.example.moss.MossModule -> com.volmit.stage.NewerStage
The jar was built against a different Iris API or Iris version.
```

Classes that nobody has only produce a warning, also available as `pack.missingClasses()`. Classes
in the optional packages a jar's module declares appear in neither:

```
Pack overworld: module jars use classes that neither the pack, Iris nor the JDK has, usually optional dependencies of a bundled library. The jars load, but code that needs these classes throws NoClassDefFoundError when it runs:
  modules/moss.jar: org.slf4j.Logger, org.slf4j.LoggerFactory
  modules/stilt.jar: org.apache.logging.log4j.LogManager, org.apache.logging.log4j.Logger, org.slf4j.Logger, org.slf4j.LoggerFactory, org.slf4j.spi.LocationAwareLogger and 3 more
```

A successful load logs one INFO summary to the host's `System.Logger`:

```
Pack overworld uses 2 modules:
  Moss (example:moss 1.0.0) from modules/moss.jar
  Stilts (iris:stilt 2.1.0) from modules/stilt.jar
```

A pack without jars logs `Pack overworld uses no modules: no jars in its modules directory`.

`LoadedModules.load` installs every module, catalogs the settings types named in `when(...)`
together with explicitly registered settings, geometry and mantle registrations, resource kinds and
validators (`catalog().resourceKinds()`, `catalog().validators()`), describes them and resolves the pipelines. Graph errors
(missing requirement, cycle, a stage outside its module's id, duplicate, cross-pipeline edge) fail
here, at pack load, and so do reserved namespaces and feature ids or mantle keys outside the
module's id. Errors about one module name its jar or "host classpath", and so does a module
that fails to link a class while installing, such as a jar built against an API whose methods have
since changed:

```
Stage iris:anchor/height must start with example:moss/, the id of its defining module example:moss from modules/moss.jar
Mantle layer iris:depth must be example:moss or start with example:moss/, the id of its registering module example:moss from modules/moss.jar
```
`describe()` prints the order, owners, edge kinds, fused block passes and the stage pairs that
have no path between them.

`pack.schema()` is the `PackSchema` of the pack's modules, built-in ones included: plain Java values,
no JSON. The host renders it and merges it with its own fields:

- `fields(target)`: the module features attached to a target, by JSON key. Each `FeatureSchema` has
  the feature id and name, its role (`SETTINGS` or `OBJECT_GENERATOR`), the owning module's
  `ModuleInfo`, the JSON key (the id), the alias of a built-in if it has one, its targets and the
  settings `ObjectType`.
- `types()`: every class reachable from module settings, once each, by name. An `ObjectType` has the
  class name, description, snippet name and options. A `SchemaOption` has name, `ValueKind`,
  description, required, minimum, maximum and default value.
- `ValueKind` is sealed: `BooleanKind`, `IntegerKind`, `DecimalKind`, `TextKind(format)`,
  `ReferenceKind(reference, format)`, `EnumKind(type, constants)`, `ListKind(element, unique)`,
  `MapKind(value)`, `ObjectKind(type)`, which names an entry of `types()`, `HostKind(type)` and
  `ResourceReferenceKind(kind)`. `TextFormat` is `PLAIN`,
  `RESOURCE_ID` or `BLOCK_STATE`.
- `snippets()` and `objectGenerators()` pick the types with a snippet name and the object generator
  features.
- `resources()`: every resource kind, by id. A `ResourceKindSchema` has the kind's id, module, folder and the
  files' type: an `ObjectKind` naming an entry of `types()`, or a `HostKind` for a built-in kind whose class the
  host describes itself. `ResourceReferenceKind(kind)` is an option naming a resource of a kind by its key.

For the stilt example the host sees one `OBJECT_PLACEMENT` feature. The records are abridged:

```
FeatureSchema[id=iris:stilt, name=Stilt, role=SETTINGS, module=Stilts (iris:stilt), key=iris:stilt, alias=Optional[stilt], targets=[OBJECT_PLACEMENT],
  settings=ObjectType[name=com.volmit.example.placement.StiltSettings, description=Fills the space below an object down to the ground,
    snippet=Optional.empty, options=[SchemaOption[name=maxDepth, kind=IntegerKind[], description=Maximum blocks filled below each
    lowest object block, required=false, minimum=OptionalDouble[1.0], maximum=OptionalDouble.empty, defaultValue=Optional[16]]]]]
```

Everything is in a fixed order, so the same modules write the same schema files on every run. The
host refuses to open a pack in studio when its load fails, and loads the pack again, rebuilding the
schema, after a module jar changes.


`PlacementRunner` owns the edit for one attempt. It commits once if no stage rejects or throws
and closes the edit in every case. Stages get buffered `PlacementAccess` without commit rights.

## Values that stay

- `ResourceId` requires a namespace. `ResourceKey<T>` pairs it with a public type.
- `PackResources` serves one immutable pack snapshot; caches must not survive a reload.
  It serves pack objects (`GeneratedObject`), biomes (`PackBiome`), regions (`PackRegion`) and every module
  resource kind; the requested type selects the kind, so `BiomeSample` ids look up directly. `ids(type)` lists
  a kind.
- `BlockState` has value equality and sorted properties; `BlockRegistry` checks it against the
  platform. `BlockPalette` and `NoiseSampler` are typed resources.
- `GeneratedObject` is sparse, immutable local geometry. `PlacementRequest` aligns its anchor
  with a world position using checked arithmetic.
- `Hunk<T>` is a dense local volume; `ArrayHunk<T>` is the bounds-checked reference.
- The `mantle` package keeps namespaced typed layers, codecs, lifetimes and buffered edits.
- `IrisFeature`, `@Feature`, the config annotations (`@Desc`, `@Required`, `@MinNumber`,
  `@MaxNumber`, `@Snippet`, `@Ref`) and `OptionsMetadata` describe settings.

Invocation views and random streams expire when the invocation ends. Stage bodies are shared by
concurrent invocations and must not keep views, streams or mutable state.

## Validation

```sh
./gradlew check
```

The tests cover graph resolution and every error path, fusion, activation cascade, outputs,
rejection and rollback, install-order determinism, id ownership of stages, settings, objects and
mantle keys, reach per stage, pipeline and mantle layer, reach worked out when the pack loads (mantle
stages only, never negative), one mantle stage per runner call with activation that follows its
requirements, chunk stages that declare reach or mantle writes (`StageGraphTest`, `ModuleRegistryTest`), settings entries per type in level order
(`ConfiguredSettingsTest`), and per-stage random streams: inserting a module
that draws leaves the other stages' draws unchanged, block stages draw one stream across their pass,
and the derivation is pinned to values computed from its specification (`StageStreamsTest`).
`WorldRandomTest` pins every `WorldRandom` method, bounds and edge cases to values computed by an
independent Python reference (`python3 -B src/test/reference/world_random.py`; the build does not
need Python) and fails if the type gains an unpinned method or implements `RandomGenerator`.
`PackSchemaTest` loads module jars and checks their description: features under their targets by id,
aliases of built-ins and the load error when a pack module sets one, options in declaration order,
descriptions, required, bounds, defaults (floats, nulls, sets, maps, objects, a field holding `this`),
enums, collections, references, nested classes with a cycle, snippets, object generator settings,
explicit registration, built-in modules, the same result whatever the jar order, and the load error
for each unsupported field type, misplaced `@Ref` (pack kinds on `ResourceId` included), unusable
class, bad or repeated snippet name and pack snippet outside its module's folder, host types in built-in
settings (and the load error when a pack module uses one), and the one-option shorthand.
`PackSchemaTest` also loads a module jar with a resource kind: the kind's files described from its class,
`@ResourceRef` options as references to the kind, built-in kinds of host types, the load error for a pack kind
of a host type, for a folder two kinds claim or the host reserves, and for a misplaced `@ResourceRef`.
`ModuleRegistryTest` checks kind ids, types and folders, and the validator order. `DiscoveredModuleTest`
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
(arrays, the JDK, other pack jars, missing annotation types), linkage errors during install, and a
pack jar that requires a stage of a built-in module in `example.builtin`, outside `com.volmit`,
which loads only when the host exposes that package. `ExposedHostClassLoaderTest` checks what the
pack loader's parent shows, `ExposedPackagesTest` which package names it accepts and how they
match, `UnavailableJdkModulesTest` which JDK modules packs cannot see, and `NonExportedJdkPackagesTest`
which JDK packages they may not use. `PackModulesAddExportsTest` runs in its own JVM, the
`addExportsTest` task that `check` runs, started with
`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`; it checks that the exported package then
loads and that other internal packages are still reported. `PackModulesSummaryTest` and `MissingClassesWarningTest` check the log texts. The example
ports in `src/test/java/com/volmit/example` rebuild preparation, anchoring, block writes, stilt and
snow as separate modules, add a third-party moss module, and add a two-module chunk pipeline whose
`ChunkPipelineTest` checks any-column activation, per-column reads, stacked levels, a mantle stage
whose reach follows the pack's longest vein writing into the next chunk that a chunk stage then reads, and the read-only mantle of chunk stages. There
is no Minecraft integration or production storage in these tests.
