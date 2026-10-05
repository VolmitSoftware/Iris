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
- A stage id uses the namespace of the module that defines it. Defining an id twice fails.
- `requires(...)` takes another module's stage constant. It orders, makes the output readable
  and deactivates this stage wherever the requirement is inactive.
- `softAfter` and `softBefore` take ids and only order. There is no hard "before".
- `run(...)` ends a stage without output, `produce(...)` a stage with output and
  `accumulate(initial, collector)` a block stage whose accumulator becomes its output.
- `ctx.output(stage)` works only for stages the current stage transitively requires.

`IrisModule.register(FeatureRegistry)` stays for non-stage contributions: geometry generators
and mantle layers.

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
the module expects may be missing (see below). The id's namespace is the namespace of every stage
the module defines; the name is what logs show. Names and versions must fit on one line: line
breaks and other control characters are rejected.

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
try (var pack = PackModules.load(packRoot, builtInPackages)) {   // exposed besides the API
    var modules = new ArrayList<>(builtIns);           // DiscoveredModule.builtIn(...)
    modules.addAll(pack.modules());
    var loaded = LoadedModules.load(modules, reservedHostFields);
    System.out.println(loaded.placement().describe());

    var placer = new PlacementRunner(loaded.placement());
    PlacementOutcome outcome = placer.place(request, edit, inputs, ConfiguredSettings.of(stilt, snow));

    new ChunkRunner(loaded.chunk()).generate(generationContext, chunkSettings);
}   // drop everything built from the pack before it closes
```

`PackModules.load(packRoot, exposedHostPackages)` exposes the API plus the given host packages,
each with its subpackages; `PackModules.load(packRoot)` exposes only the API. The host chooses
those packages, such as the ones that hold built-in modules' stage constants. It reads one entry
point per jar, in jar file name order, and loads every jar of the pack through one class loader.
Closing it closes that loader. A pack without a `modules` directory has no modules. Errors name the
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
(missing requirement, cycle, foreign namespace, duplicate, cross-pipeline edge) fail here, at
pack load. Duplicate module ids and foreign namespaces also name the jar or "host classpath", and
so does a module that fails to link a class while installing, such as a jar built against an API
whose methods have since changed.
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

Invocation views expire when the invocation ends. Stage bodies are shared by concurrent
invocations and must not keep views or mutable state.

## Validation

```sh
./gradlew check
```

The tests cover graph resolution and every error path, fusion, activation cascade, outputs,
rejection and rollback, and install-order determinism. `PackModulesTest` compiles real module
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
