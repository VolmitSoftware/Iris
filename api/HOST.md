# Host side

How a host loads a pack's modules and runs their stages. Module authors do not need it: [README.md](README.md) is for
them. [DESIGN.md](DESIGN.md) explains the model and [CONTRACTS.md](CONTRACTS.md) lists what a host has to provide. Everything
here is in the `host` subproject (`com.volmit.host`), which pack jars never see.

```java
// Once, at startup: the same for every pack.
var host = ModuleHost.of(builtInModules)
        .exposing("art.arcane.iris.builtin", "art.arcane.volmlib")   // besides the API
        .reservingFields(hostJsonFields)                  // JSON fields the host uses per target; no settings key may take one
        .reservingFolders("biomes", "regions", "objects")         // pack folders the host reads itself
        .bundlingJars(builtInModulesJar)                  // module jars the host ships with every pack
        .copyingJarsTo(cacheDirectory)                    // default: java.io.tmpdir
        .loggingTo(serverLogger);                         // a System.Logger; default: System.getLogger

// Per pack.
try (var pack = LoadedPack.load(packRoot, host)) {
    System.out.println(pack.placement().describe());

    StageTimings timings = (stage, nanos) -> metrics.time(stage, nanos);   // or StageTimings.NONE

    var placer = new PlacementRunner(pack.placement());
    PlacementOutcome outcome = placer.place(request, edit, inputs, placementSeed,
            ConfiguredSettings.placement(objectId, stilt, snow), timings);

    var mantle = new MantleRunner(pack.mantle());
    for (var stage : pack.mantle().stages()) {                                // each stage its own pass, first
        mantle.generate(stage.stage(), mantleContext, chunkSeed, timings);    // for each chunk it reaches
    }
    new ChunkRunner(pack.chunk()).generate(generationContext, chunkSeed, timings);
}   // stop using the runners before the pack closes
```

All three runners report how long each stage that ran took to their `StageTimings`. A block stage is
reported once per placement, with its time summed over the blocks of its pass. Stages that are
inactive or throw are not reported.

`ModuleHost` holds what the host passes to every pack load: built-in modules (their `info()` is read
once, here), the host packages pack modules may use, the JSON fields it uses itself per target, the module jars it
bundles and the pack folders it reads itself. Each method adds to what earlier calls configured. Package names,
reserved fields and reserved folders are checked when it is built. `LoadedPack.load`
loads the pack's jars, sets them up after the built-ins, catalogs settings and registrations,
describes them for studio and resolves the three pipelines. It returns a complete pack or throws a
`ModuleLoadException` with nothing left open. `close()`
releases the pack's class loader and its jar copies. `PackModules` and `LoadedModules`, which it
uses, stay available for tests and special cases.

A host that runs on a platform tells `ModuleHost.runningOn(new PlatformApi(ToolingPlatform.BUKKIT, loader))` which
loader holds the platform's api classes. A load then checks the pack's tooling parts of that platform;
`LoadedPack.toolingParts()` lists them, and `openToolingPart(part)` gives a `ToolingPartLoader` whose
`loadEntryClass()` is the part to instantiate. Close it before the pack.

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
constants. The load (`PackModules`, under `LoadedPack`) reads the entry points of each jar, in jar file
name order and in the order each jar lists them, and loads every jar of the pack through one class loader. A pack without a `modules`
directory has no modules. Every load error is a `ModuleLoadException` (unchecked), the one type a host catches;
`moduleId()` and `origin()` name the module and jar at fault when the error is one module's or one jar's. Errors name the
jar: no entry point, a class of an exposed package in the jar, a class that is
also in another jar of the pack (both jars are named), a class that uses a class Iris has but hides,
a class of a JDK module modules cannot see, a class of a JDK package not exported to modules, a
class missing from an exposed package, or an entry
class that cannot be loaded, is not an `IrisModule`, is not in that jar, fails to construct or whose
`info()` fails, or a manifest that declares an invalid optional package. All checks before the last run before any
class of the pack is defined. The last error below lists every kind of unusable class the jar
uses, one group per kind:

```
Module jar modules/alpha.jar contains Iris API class com.volmit.FeatureRegistry and 130 more; Iris provides the API at runtime, so mark the Iris API dependency compileOnly (Maven: provided) and keep module classes out of com.volmit.*

Module jar modules/caves.jar contains class art.arcane.iris.builtin.objects.ObjectRun of package art.arcane.iris.builtin, which Iris provides to modules at runtime; compile against it compileOnly (Maven: provided) instead of shipping it, and keep module classes out of art.arcane.iris.builtin.*

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
in the optional packages a jar's manifest declares appear in neither:

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

`LoadedModules.load` sets up every module, catalogs the settings types named in `when(...)`
together with explicitly registered settings, geometry and mantle registrations, resource kinds and
validators and service providers (`catalog().resourceKinds()`, `catalog().validators()`,
`catalog().registrations(service)`), describes them and resolves the pipelines. Graph errors
(missing requirement, cycle, a stage outside its module's id, duplicate, cross-pipeline edge) fail
here, at pack load, and so do duplicate module ids and feature ids, mantle keys or saved keys outside the
module's id, and a settings key two features take at one target or that is a reserved host field. Errors about one module name its jar or "host classpath", and so does a module
that fails to link a class during setup, such as a jar built against an API whose methods have
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
  `ModuleInfo`, the JSON key it declares, its `root` option name when the key holds one option's value
  (`rootOption()` gives that `SchemaOption`; the host renders that option's schema as the key's value and no
  object form), its targets and the settings `ObjectType`.
- `types()`: every class reachable from module settings, once each, by name. An `ObjectType` has the
  class name, description, snippet name and options. A `SchemaOption` has name, `ValueKind`,
  description, required, minimum, maximum, minimum item count and default value.
- `ValueKind` is sealed: `BooleanKind`, `IntegerKind`, `DecimalKind`, `TextKind(format)`,
  `ReferenceKind(reference, format)`, `EnumKind(type, constants)`, `ListKind(element, unique)`,
  `MapKind(value)`, `ObjectKind(type)`, which names an entry of `types()`, `HostKind(type)`,
  `ResourceReferenceKind(kind)` and `AnyKind` for any JSON value. `TextFormat` is `PLAIN`,
  `RESOURCE_ID` or `BLOCK_STATE`.
- `snippets()` picks the types with a snippet name.
- `resources()`: every resource kind, by id. A `ResourceKindSchema` has the kind's id, module, folder and the
  files' type: an `ObjectKind` naming an entry of `types()`, or a `HostKind` for a built-in kind whose class the
  host describes itself. `ResourceReferenceKind(kind)` is an option naming a resource of a kind by its key.

For the stilt example the host sees one `PLACEMENT` feature. The records are abridged:

```
FeatureSchema[id=iris:stilt, name=Stilt, role=SETTINGS, module=Stilts (iris:stilt), key=stilt, root=Optional.empty, targets=[PLACEMENT],
  settings=ObjectType[name=com.volmit.example.placement.StiltSettings, description=Fills the space below an object down to the ground,
    snippet=Optional.empty, options=[SchemaOption[name=maxDepth, kind=IntegerKind[], description=Maximum blocks filled below each
    lowest object block, required=false, minimum=OptionalDouble[1.0], maximum=OptionalDouble.empty, defaultValue=Optional[16]]]]]
```

Everything is in a fixed order, so the same modules write the same schema files on every run. The
host refuses to open a pack in studio when its load fails, and loads the pack again, rebuilding the
schema, after a module jar changes.


`PlacementRunner` owns the edit for one attempt. It commits once if no stage rejects or throws
and closes the edit in every case. Stages get buffered `PlacementAccess` without commit rights.

The host keeps one `PackWorld` per world and pack snapshot, and builds one for each placement a tool makes outside
those worlds (a studio paste), with its own `WorldStates` that it closes when the placement ends; no hook runs for it.
`WorldStates` is the reference store behind
`PackWorld.state`, and `LifecycleRunner` calls a pack's hooks the way `ModuleLifecycle` promises:

```java
var lifecycles = new LifecycleRunner(pack.catalog());
lifecycles.packLoaded(resources, packSettings);     // once per snapshot, before its first world starts
lifecycles.worldStarted(world);                     // then hand world to every invocation of the world
lifecycles.warmUp(world);                           // slow cache building, on any thread, before the first generation
...
lifecycles.worldStopped(world);                     // then close the world's WorldStates

List<ObjectsAt> providers = pack.catalog().services(ObjectsAt.class);   // in module id order
Optional<Clock> clock = pack.catalog().service(Clock.class);            // a Providers.ONE service only
```
