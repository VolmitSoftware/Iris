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

`info()` gives the module id, display name and version. The id's namespace is the namespace of
every stage the module defines; the name is what logs show. Names and versions must fit on one
line: line breaks and other control characters are rejected.

A module compiles against the Iris API as `compileOnly` (Maven: `provided`) and never ships it;
Iris provides the API at runtime. At runtime a module can use only the Iris API (`com.volmit.*`),
the JDK and the classes in its pack's module jars. Iris internals and the libraries Iris bundles
are hidden, so a library the module needs goes inside one of the pack's jars. A class may appear
in only one jar of a pack, so two jars that shade the same library must relocate it. The
`com.volmit.` package prefix belongs to the API: module classes, and the libraries they shade,
must not use it.

Iris never downloads modules and does not check which API version a jar was built for. A pack
ships its module jars, and keeping them compatible with the Iris version it targets is the pack
publisher's job.

## Host side

```java
try (var pack = PackModules.load(packRoot)) {
    var modules = new ArrayList<>(builtIns);           // DiscoveredModule.builtIn(...)
    modules.addAll(pack.modules());
    var loaded = LoadedModules.load(modules, reservedHostFields);
    System.out.println(loaded.placement().describe());

    var placer = new PlacementRunner(loaded.placement());
    PlacementOutcome outcome = placer.place(request, edit, inputs, ConfiguredSettings.of(stilt, snow));

    new ChunkRunner(loaded.chunk()).generate(generationContext, chunkSettings);
}   // drop everything built from the pack before it closes
```

`PackModules.load` reads one entry point per jar, in jar file name order, and loads every jar
of the pack through one class loader. Closing it closes that loader. A pack without a `modules`
directory has no modules. Errors name the jar: no entry point, several entry points, a
`com.volmit.` class in the jar, a class that is also in another jar of the pack (both jars are
named), a class that uses a class outside the API, the JDK and the pack's jars, or an entry class
that cannot be loaded, is not an `IrisModule`, is not in that jar, or fails to construct. All
checks before the last run before any class of the pack is defined:

```
Module jar modules/alpha.jar contains Iris API class com.volmit.FeatureAttachment and 130 more; Iris provides the API at runtime, so mark the Iris API dependency compileOnly (Maven: provided) and keep module classes out of com.volmit.*

Module jar modules/moss.jar uses classes that are not in the Iris API, the JDK or the pack's module jars:
  com.example.moss.MossModule -> com.google.gson.Gson
  com.example.moss.MossSettings -> art.arcane.iris.engine.object.IrisBiome
A module can use only the Iris API, the JDK and the classes in its pack's module jars; ship any library it needs inside a module jar of the pack.
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
and multi-release jars, API classes in a jar, references outside the pack, references that must
still load (arrays, the JDK, other pack jars, missing annotation types) and linkage errors during
install. `ApiAndJdkClassLoaderTest` checks what the pack loader's parent shows.
`PackModulesSummaryTest` checks the log text. The example ports in
`src/test/java/com/volmit/example` rebuild preparation, anchoring, block writes, stilt and snow
as separate modules, add a third-party moss module, and add a two-module chunk pipeline. There is
no Minecraft integration or production storage in these tests.
