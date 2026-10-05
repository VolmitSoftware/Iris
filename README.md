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
             .run((ctx, moss) -> spread(ctx.output(AnchorModule.HEIGHT), moss));
    }
}
```

- `Stage.placement`, `Stage.placementBlock` and `Stage.chunk` create stages for the placement
  pipeline (once per attempt), the placement pipeline (once per object block) and the chunk
  pipeline. Pass an output class to give a stage a typed output.
- A stage id starts with the id of the module that defines it and `/`: module `example:moss`
  defines `example:moss/spread`, never `example:other/...`. Module ids therefore contain no `/`.
  Defining an id twice fails.
- `requires(...)` takes another module's stage constant. It orders, makes the output readable
  and deactivates this stage wherever the requirement is inactive.
- `softAfter` and `softBefore` take ids and only order. There is no hard "before".
- `run(...)` ends a stage without output, `produce(...)` a stage with output and
  `accumulate(initial, collector)` a block stage whose accumulator becomes its output.
- `ctx.output(stage)` works only for stages the current stage transitively requires.
- `ctx.random()` is the stage's own random stream, derived from the invocation seed and the stage
  id. Installing or removing other modules, or how much they draw, never changes what a stage
  draws. A block stage has one stream per placement, drawn across the blocks of its pass.

`IrisModule.register(FeatureRegistry)` stays for non-stage contributions: geometry generators
and mantle layers. Their feature ids and mantle keys use the module's namespace. A settings type
named in `when(...)` needs a feature id in the namespace of a module that names it, so another
module may still condition its stages on it.

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
reserved namespace fails the load, and because stages, settings ids and mantle keys follow the
module's namespace, none of them can use it either:

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

## Host side

```java
// Once, at startup: the same for every pack.
var host = ModuleHost.of(builtInModules)
        .exposing("art.arcane.iris.engine.modules")     // besides the API
        .reservingNamespaces("iris")
        .reservingFields(reservedHostFields);

// Per pack.
try (var pack = LoadedPack.load(packRoot, host)) {
    System.out.println(pack.placement().describe());

    var placer = new PlacementRunner(pack.placement());
    PlacementOutcome outcome = placer.place(request, edit, inputs, placementSeed, ConfiguredSettings.of(stilt, snow));

    new ChunkRunner(pack.chunk()).generate(generationContext, chunkSeed, chunkSettings);
}   // stop using the runners before the pack closes
```

`ModuleHost` holds what the host passes to every pack load: built-in modules (their `info()` is read
once, here), the host packages pack modules may use, the JSON fields the host itself uses and the
reserved namespaces. Package names and namespaces are checked when it is built. `LoadedPack.load`
loads the pack's jars, installs them after the built-ins, catalogs settings and registrations and
resolves both pipelines. It returns a complete pack or throws with nothing left open. `close()`
releases the pack's class loader and its jar copies. `PackModules` and `LoadedModules`, which it
uses, stay available for tests and special cases.

The host derives one seed per placement or chunk, from the world seed and the placement's stable
identity or the chunk position, and passes it to the runner. Each stage's stream comes from that
seed and the stage id (`StageRandom` documents the derivation). Stages cannot reach a shared stream.

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

A successful load logs one INFO summary through `System.Logger`:

```
Pack overworld uses 2 modules:
  Moss (example:moss 1.0.0) from modules/moss.jar
  Stilts (iris:stilt 2.1.0) from modules/stilt.jar
```

A pack without jars logs `Pack overworld uses no modules: no jars in its modules directory`.

`LoadedModules.load` installs every module, catalogs the settings types named in `when(...)`
together with geometry and mantle registrations, and resolves both pipelines. Graph errors
(missing requirement, cycle, a stage outside its module's id, duplicate, cross-pipeline edge) fail
here, at pack load, and so do reserved namespaces and feature ids or mantle keys outside the
module's namespace. Errors about one module name its jar or "host classpath", and so does a module
that fails to link a class while installing, such as a jar built against an API whose methods have
since changed:

```
Stage iris:anchor/height must start with example:moss/, the id of its defining module example:moss from modules/moss.jar
Mantle layer iris:depth must use namespace 'example' of its registering module example:moss from modules/moss.jar
```
`describe()` prints the order, owners, edge kinds, fused block passes and the stage pairs that
have no path between them.

`PlacementRunner` owns the edit for one attempt. It commits once if no stage rejects or throws
and closes the edit in every case. Stages get buffered `PlacementAccess` without commit rights.

## Values that stay

- `ResourceId` requires a namespace. `ResourceKey<T>` pairs it with a public type.
- `PackResources` serves one immutable pack snapshot; caches must not survive a reload.
- `BlockState` has value equality and sorted properties; `BlockRegistry` checks it against the
  platform. `BlockPalette` and `NoiseSampler` are typed resources.
- `GeneratedObject` is sparse, immutable local geometry. `PlacementRequest` aligns its anchor
  with a world position using checked arithmetic.
- `Hunk<T>` is a dense local volume; `ArrayHunk<T>` is the bounds-checked reference.
- The `mantle` package keeps namespaced typed layers, codecs, lifetimes and buffered edits.
- `IrisFeature`, `@Feature`, the config annotations and `OptionsMetadata` describe settings.

Invocation views and random streams expire when the invocation ends. Stage bodies are shared by
concurrent invocations and must not keep views, streams or mutable state.

## Validation

```sh
./gradlew check
```

The tests cover graph resolution and every error path, fusion, activation cascade, outputs,
rejection and rollback, install-order determinism, stage-id prefixes and settings, object and mantle
namespaces (`StageGraphTest`, `ModuleRegistryTest`), and per-stage random streams: inserting a module
that draws leaves the other stages' draws unchanged, block stages draw one stream across their pass,
and the derivation is pinned to values computed from its specification (`StageRandomTest`).
`DiscoveredModuleTest` checks that `info()` is read once. `LoadedPackTest` loads a pack with
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
snow as separate modules, add a third-party moss module, and add a two-module chunk pipeline. There
is no Minecraft integration or production storage in these tests.
