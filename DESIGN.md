# Iris API design

Status: pack module loading (`LoadedPack`), the stage graph, per-stage random streams, both
pipelines' reference executors, settings catalog and its description for studio, geometry,
resources, module resource kinds, validators, per-world state, lifecycle hooks, services and typed storage contracts
exist and are tested without the Iris
engine. Provider and runtime families at the end are design notes, not Java interfaces.

## Model

An Iris module owns its settings types and the stages that act on them. The host owns resource
loading, platform adapters, scheduling, storage and publication. The core engine is meant to be
a set of ordinary modules (`iris:prepare`, `iris:anchor`, `iris:blocks` and so on). No stage,
phase or hook has special status in the API.

```mermaid
flowchart TD
    Modules[Built-in and addon modules] --> Install[install: define stages]
    Modules --> Register[register: geometry, mantle layers, resource kinds, validators, hooks, services]
    Install --> Resolve[Resolve per pipeline at pack load]
    Install --> Settings[when settings types]
    Settings --> Catalog[Frozen feature catalog]
    Register --> Catalog
    Resolve --> Placement[Placement graph]
    Resolve --> MantleGraph[Mantle graph]
    Resolve --> Chunk[Chunk graph]
    Catalog --> Schema[PackSchema for studio]
    Schema --> Render[Host JSON Schema and workspace]
    Catalog --> Decode[Host JSON decoding]
    Decode --> Configured[ConfiguredSettings per invocation]
    Configured --> Placement
    Configured --> MantleGraph
    Configured --> Chunk
    Placement --> Edit[Buffered placement edit]
    MantleGraph --> MantleData[Mantle around the chunk]
    MantleData --> Chunk
    Chunk --> Hunk[Local hunks]
    Edit --> Publish[Host publication]
    Hunk --> Publish
```

## Module loading

The `IrisModule` implementation is the module's entry point. `info()` returns its id, display
name, version and optional packages; there is no separate descriptor. A pack keeps its modules in
`<pack>/modules/*.jar`, one or more modules per jar.

The host loads a pack with `LoadedPack.load(packRoot, host)`. `ModuleHost` is the host's fixed
configuration, built once and the same for every pack: built-in modules, exposed host packages,
the JSON fields the host uses itself, host type packages and reserved folders. Each of its methods adds to what earlier calls
configured, and all names are checked when it is built. `LoadedPack` runs `PackModules.load` (below), then
`LoadedModules.load` on the built-ins followed by the pack's modules. If the second step fails it
closes the first before rethrowing, so a failed load leaves no class loader or jar copy behind.
`close()` closes the pack's class loader, then deletes its jar copies.

A jar names its entry classes, one per line, in `META-INF/services/com.volmit.IrisModule`, the
provider format of `ServiceLoader`. The host reads that file from the jar itself rather than calling
`ServiceLoader`, which would also return providers of parent class loaders and could not say
which jar a broken provider came from. The rejected alternatives:

- A manifest attribute such as `Iris-Module: com.example.MossModule`. It needs the same reading
  code but is not a Java convention, IDEs do not check it, and fat-jar tools tend to rewrite
  manifests.
- A descriptor file with id and version. It duplicates `info()`, so the two can disagree, and
  parsing it needs a format the JDK does not ship.

`PackModules.load(packRoot, exposedHostPackages)` (or `load(packRoot)`, which exposes only the
API):

1. Validates the exposed packages. The API's package is always one of them.
2. Lists regular files ending in `.jar` in `<pack>/modules`, sorted by file name. A missing
   directory means no modules.
3. Copies them into a fresh temporary directory (see "Jar copies"). Every later step reads the
   copies; messages name the originals.
4. Reads each jar's entry point file and its class files. Comments (`#`) and blank lines are
   ignored; zero entry class names fail, several load in the order listed.
5. Fails if a jar holds a class of an exposed package, naming the jar and the first such class.
6. Fails if a class is in two jars, naming the class and both jars.
7. Resolves every class each class uses (see below). A class that Iris has but hides, a class of a
   JDK module packs cannot see or of a JDK package packs may not use, or a class missing from an
   exposed package fails the load, listing
   each referencing and referenced class per jar and kind. Classes that nobody has are collected
   per jar. Nothing has been defined up to here.
8. Creates one `URLClassLoader` for all copies, parent = `ExposedHostClassLoader`.
9. Loads each entry class, checks that it implements `IrisModule` and comes from its own jar,
   instantiates it with the public no-argument constructor and calls `info()`, once.
10. Drops each jar's missing classes that lie in the optional packages its module declares in
   `info()`.
11. Logs one INFO summary of the pack's modules, and one WARNING if any missing classes remain.

Any failure after step 3 closes the loader, if it exists, and deletes the copies.

One loader per pack, not per jar, because `requires(...)` takes another module's stage constant.
A pack jar that requires a stage of another pack jar must see its classes. Packs stay isolated
from each other. The cost is a shared class namespace: with two copies of a class, the loader
would silently use the one first in file name order. Step 6 turns that into a load error, so two
jars that shade the same library must relocate it, and shared code belongs in one module that the
others require.

### Jar copies

A `URLClassLoader` keeps every jar it reads open until it closes. On Windows an open file cannot be
replaced, so a loaded pack would stop its module author from rebuilding a jar while studio runs.
The load therefore copies the jars first (`JarSnapshot`) and gives the class check and the loader
only the copies. The originals are closed again as soon as they are copied. This also means a load
works on one consistent set of jars: a jar rebuilt during the load affects the next load, not half
of this one.

Copies live in a new directory `iris-pack-modules-*` under `java.io.tmpdir`, or under the directory
the host passes to `ModuleHost.copyingJarsTo(...)`, which is created when missing, named by their
original file name, next to an owner file holding a fixed header and the id of the process that made
it. Origins and messages always use the original path relative to the pack: the copy's path would
mean nothing to the author. When an entry class turns out to come from another jar of the pack, the
message names that jar's origin too.

A process that crashes never closes its packs, so its copies stay. A later load removes such a
directory only when it can show that this code made it and that nobody uses it: the name has the
prefix, the owner file has the header, its process no longer runs
(`ProcessHandle.of(pid)` finds no live process), and the directory holds nothing but the owner file
and `.jar` files. Directories of a process that still runs, including another server sharing the
temporary directory, are left alone; a reused process id can only keep a stale directory, never
remove a live one. A crash between creating the directory and writing the owner file leaves an
empty directory that is never removed, because nothing proves who made it.

The class check reads jars the way the loader does. `module-info.class` is ignored. A
multi-release jar (`Multi-Release: true`) contributes each class once, under its base name, from
the highest `META-INF/versions/N` that the running JDK selects; a versioned copy of a class in one
jar therefore clashes with the plain class in another. In a jar without that manifest attribute the
loader never reads `META-INF/versions`, so those entries are skipped.

### What a module can see

Modules depend on the Iris API. They compile against it `compileOnly` and never ship it, because
Iris provides it at runtime; in production it is shaded into the Iris plugin jar. That loader also
holds Iris internals and the libraries Iris bundles, so it is not the pack loader's parent.
`ExposedHostClassLoader` is: it delegates every class in an exposed package, and its subpackages,
to the host's loader (the loader that defines the API) and everything else to the platform class
loader, which holds the JDK. The api/host split is deferred, so `com.volmit.host` is visible too.

The platform loader provides the JDK modules defined to it and to the boot loader: `java.base`,
`java.sql`, `java.net.http`, `java.compiler` and the other standard modules. JDK modules defined to
the application loader, such as `jdk.compiler`, `jdk.jshell` and `jdk.attach`, are not available
to modules, and neither are JDK modules the runtime did not resolve.

Seeing a class is not enough to use it. Pack classes live in their loader's unnamed module, and a
JDK module lets an unnamed module use only the packages it exports to all unnamed modules. Internal
packages such as `jdk.internal.misc` (`jdk.internal.misc.Unsafe`) are not, so code using them would
fail with `IllegalAccessError` when it runs. Packages exported only to named JDK modules (qualified
exports) count as not exported. The supported way to use Unsafe is `sun.misc.Unsafe`, which
`jdk.unsupported` exports. A server started with `--add-exports <module>/<package>=ALL-UNNAMED`
exports that package to every unnamed module, and then modules may use it.

The exposed packages (`ExposedPackages`) are the API's package, `com.volmit` (the package of
`IrisModule`), plus whatever the host passes to `ModuleHost.exposing` (and so to `PackModules.load`). The host decides those; the
intended use is the packages that hold built-in modules' stage constants, so that a pack jar can
`requires(...)` a stage of the core engine or a built-in feature. A pack jar then links against
the host's class and the same stage constant, which resolution requires: a copy would be a second
definition of the id. Names match whole packages: `com.volmit` does not expose `com.volmitx`. A
trailing dot is accepted; anything that is not a package name, such as a blank string or a
wildcard, is rejected. `PackageName` holds these rules; module optional packages use them too.

A module may use any library. A library the host does not expose it ships itself, inside its own jar or as another
jar of the pack, and never borrows the host's hidden copy: those can change with any host update, and two modules would
otherwise depend on whatever version the host happens to bundle. A host may instead expose a library it promises to
keep, by passing its packages to `ModuleHost.exposing`; the pack loader then gives modules the host's classes of it,
like the API's. Iris exposes VolmLib (`art.arcane.volmlib`, its utility library, shaded unrelocated) and Gson
(`com.google.gson`, which every platform it runs on provides), so Iris's built-ins and pack modules share one copy of
each. A module jar may not contain classes of an exposed library, as it may not contain API classes.

Resources follow the same rule by path: `com/volmit/...` and the paths of other exposed packages
from the host's loader, everything else from the platform loader, then the pack's jars. A class
and its class file come from the same place, and a pack never sees Iris's own resources such as
`plugin.yml` or its service files.

The pack loader asks its parent first, so a packaged copy of a host class would be silently
replaced by the host's, and a module class in an exposed package could be replaced by a later host
class of the same name. Step 5 rejects both; exposed packages belong to the host. For the API the
message points at `compileOnly`, the usual cause. A module class that shares a name with a hidden
Iris internal is harmless, because the pack loader cannot see it. A module class that shares a name
with a JDK class is replaced by the JDK's; that is not checked.

Step 7 reads every class file with the JDK's `java.lang.classfile` API. A class uses every class
in its constant pool (supertypes, and instantiated, cast, caught, called-on and class-literal
types) and every type in the descriptors of its own fields and methods and of the members and
call sites it uses. Arrays count as their element type; primitives do not count. Annotations and
generic signatures are left out: the JVM never resolves them while linking, and reflection skips an
annotation whose type is missing, so a `compileOnly` annotation library such as JetBrains
annotations needs nothing at runtime. Each used class outside the pack is judged by its class
file, so nothing is loaded or initialized:

| The class is | Result |
| --- | --- |
| in a package of a JDK module the platform loader provides, not exported to unnamed modules, such as `jdk.internal.misc` | load error: names the package and module; points to `sun.misc.Unsafe` and `--add-exports` |
| in a jar of the pack, an exposed package of the host, or the JDK | resolves |
| in an exposed package, but the host does not have it | load error: the jar was built against a different Iris API or Iris version |
| in a JDK module the platform loader does not provide, such as `jdk.compiler` | load error: names the JDK module, which is not available to modules |
| not exposed, but the host's loader has it (Iris internals, Iris's copy of a library) | load error: modules cannot use Iris's classes; ship the library in the pack or use the API |
| nowhere | the pack loads, with a warning unless the module declares the package optional |

The rows are checked in that order. The export row comes first because access does not depend on
which loader finds the class: whoever loads `jdk.internal.misc.Unsafe`, `java.base` decides who may
use it. It asks the boot layer's `Module` objects whether they export the package to an unnamed
module that no export names (`Module.isExported(package, unnamedModule)`), so exports added with
`--add-exports ...=ALL-UNNAMED` count, exactly as they do at run time, and qualified exports do not.
Modules the platform loader does not provide are left to the unavailable-module row, so the two
never overlap. The unavailable-module row comes before the host row because the host's loader
can usually load application-loader JDK classes, which would otherwise be reported as hidden Iris
classes. Whether a class belongs to such a module is decided by its package, from the JDK's module
descriptors (`ModuleFinder.ofSystem()`) and the loader the boot layer gives each module, so nothing
is loaded. When a jar uses several kinds of unusable class, the error has one group per kind and
jar, each with its own advice.

The JVM resolves a class only when code first needs it, so a class that nobody has is usually an
optional dependency of a bundled library, used only when present. Rejecting those would be stricter
than the JVM and would make many ordinary libraries unusable. Instead the pack loads,
`PackModules.missingClasses()` lists the missing classes per jar (`MissingClasses`), and the load
logs one WARNING for the pack, one line per jar with at most five class names
(`MissingClassesWarning`), so a large shaded library cannot flood the log. Code that needs a missing
class throws `NoClassDefFoundError` when it runs; the verifier may also need it while linking a
class that uses it, which then fails when that class is first used.

A module that knows its jar uses such classes declares their packages as optional in `info()`:

```java
public ModuleInfo info() {
    return ModuleInfo.of("example:moss", "Moss", "1.0.0")
            .withOptionalPackages("org.slf4j", "org.apache.logging.log4j");
}
```

Each package covers its subpackages. Missing classes of that jar inside them are dropped from the
warning and from `missingClasses()`; the rest still warn, and other jars of the pack are not
affected. A jar with several modules is excused by the optional packages of all of them. `ModuleInfo.of(id, name, version)` declares none. Invalid names fail in `info()`, which the
load reports as a broken entry point of the jar.

The declaration lives in the entry point because all module metadata does. That fixes its timing:
`info()` exists only once the entry class is defined, after the reference check. This is safe
because the declaration only removes warnings. Every load error is decided in step 7, without it,
so a declared package can never turn a hidden Iris class, an unavailable or non-exported JDK class
or a class missing from an exposed package into a pass. `missingClasses()` drops the excused classes instead
of flagging them: it then says exactly what the warning says, and a class the author declared
expected is not a problem to show in studio.

A class the host hides fails, even though the pack might never run that code, because it exists:
the module was written against Iris's copy, and the error says to ship the library or use the API
instead of leaving the author with a `NoClassDefFoundError` during install or generation.

There is no version gating. Packs ship their module jars and Iris never downloads any, so whether
a jar works with the running Iris is the pack publisher's responsibility. The class check catches
API and exposed classes that no longer exist. It does not check methods and fields: a jar built
against an API whose methods have changed fails with a linkage error, which `LoadedModules.load`
reports with the module's jar when it happens during install, and which surfaces at generation
time when it happens only in a stage body.

Closing `PackModules` closes the loader and then deletes the copies. Classes loaded before that
keep working, so the host must drop everything built from the pack first.

Each module reaches `LoadedModules.load` as a `DiscoveredModule`: the module, its `ModuleInfo` and
its origin, the jar path relative to the pack or "host classpath" for built-ins. `info()` is called
exactly once, when the module is discovered (`DiscoveredModule.builtIn`, or step 9 for a jar), and
everything afterwards uses the stored value, so a module whose `info()` changes between calls cannot
give the host two answers. Errors about a module name its origin. Every jar error names the jar.

### Namespaces

A module owns its id: `ns:name`, with no `/` in the name. It may define only stages whose ids start
with its id and `/` (`iris:stilt/fill`), so no module can define stages under another module's
path, and a stage id names exactly one possible owner. A `/` in a module id would let `iris:stilt/x`
define stages under `iris:stilt`, so `ModuleInfo` rejects it.

Feature ids of geometry generators and mantle keys a module registers follow the same rule
(`ModuleInfo.ownsId`): they are the module id or start with it and `/`, such as `iris:snow` or
`iris:snow/depth`. Sharing the namespace is not enough, or `iris:snow` could claim `iris:stilt`'s
ids. A settings type named in `when(...)` must have a feature id that at least one module naming it
owns in this sense: its owner. Other modules may name it too, to run only where that setting is
configured. Checking against the module that names it alone would forbid that; checking nothing
would let any module claim another module's settings id.

No namespace is reserved: `iris` is as free to a pack module as any other. A host that ships
modules of its own protects nothing by a reservation, so a module id is only ever loaded once: a
second module with the same id fails the load wherever it comes from (one jar, two jars, the host's
built-ins), naming both the origin and the class of each. That one check is what keeps a built-in
module that moves into a pack jar from being loaded twice.

The load summary and the missing-classes warning go to the `System.Logger` the host passes to
`ModuleHost.loggingTo(...)`, by default `System.getLogger(PackModules.class.getName())`. A host
argument rather than a `System.LoggerFinder`: the JDK finds a finder through the system class loader,
which a plugin's classes are not on. The summary lists display name, id, version and jar for each module in load
order, or says the pack has no module jars. `PackModulesSummary` and `MissingClassesWarning` build
the texts and are tested on their own.

## Stages

A stage is a constant created by `Stage.placement`, `Stage.placementBlock`, `Stage.chunk` or `Stage.mantle`. Its
identity is that object. Another module that wants to depend on it references the owner's
constant, which makes the dependency a compile-time one. A copy with the same id is rejected at
resolve time.

| Stage type | Pipeline | Runs | Context |
| --- | --- | --- | --- |
| `PlacementStage<O>` | PLACEMENT | once per placement attempt | `PlacementStageContext` |
| `BlockStage<O>` | PLACEMENT | once per object block, in a block pass | `BlockStageContext` |
| `ChunkStage<O>` | CHUNK | once per chunk invocation | `ChunkStageContext` |
| `MantleStage<Void>` | MANTLE | once per chunk, before the chunks it reaches generate, each stage its own pass | `MantleStageContext` |

Rules enforced by `StageGraphBuilder` and the resolver:

1. The stage id must start with the defining module's id and `/`. A second definition of an id
   fails. Two modules with the same id fail.
2. `requires(stage...)` adds a REQUIRED edge. If the stage is not installed, loading fails with
   both stage ids, the requiring module and the namespace the missing module should have.
3. `softAfter(ref...)` and `softBefore(ref...)` add SOFT edges only when the referenced id is
   installed. Absent targets are kept for diagnostics. A module cannot force itself before
   another module's stage with a hard edge.
4. A stage with output must be defined with `produce` (or `accumulate` for block stages), and a
   stage without output with `run`. Every defined stage needs exactly one body before its
   module's `install` returns.
5. A stage and everything it references must be in the same pipeline, soft references included.
6. Cycles fail and print the whole cycle: each stage with its module and the kind of each edge.

### Order

Resolution is Kahn's algorithm over the pipeline's stages. Among stages whose predecessors have
all run, the lexically smallest id goes first, with one refinement for fusion:

- while a block pass is open, a ready block stage continues it;
- otherwise a ready invocation stage runs first;
- otherwise a block stage opens a new pass.

The result depends only on ids and edges, never on install order. A tie-break on raw lexical id
alone would interleave unrelated invocation stages into block passes and split them. Note that
no topological order can put every unordered pair in lexical order once edges point against the
alphabet, so "lexical" here always means "lexical among ready stages".

### Block passes

Adjacent block stages in the resolved order form one block pass. The placement runner loops over
the request object's blocks once per pass and runs every active stage of the pass on a block
before moving to the next block. A block stage that requires another block stage runs after it
for the same block. An invocation stage between two block stages splits the pass, even when it
is inactive for a particular invocation. The split is static so that `describe()` matches what
runs.

Each pass starts from the request object's blocks. `replace` and `skip` affect only later stages
of the same pass for the same block. Work that later passes need belongs in an accumulator.

### Outputs

Outputs live in a per-invocation array indexed by resolved position. Stage definitions never hold
them. `ctx.output(stage)` scans the current stage's precomputed list of transitive requirements by
identity, which is a handful of entries, then reads the array. There is no hashing per call.
Reading anything outside that list throws `IllegalStateException` and names the fix. A block
stage's accumulator is created once per placement and becomes readable when its pass ends;
reading it from inside the same pass throws. A null output from a stage that declares one fails
the invocation.

### Activation

The graph is resolved once per pack load. Activation is decided per invocation:

1. The invocation gets `ConfiguredSettings`: for each settings type, the entries configured for it, in
   level order. For a placement the host passes the placement's own declarations. For a chunk or mantle
   invocation the runner collects them itself from `FeatureSettings`: every distinct entry, by level and
   source, of the chunk's 16 by 16 columns.
2. The invocation looks up each distinct `when` type once.
3. One pass in resolved order marks a stage active when its settings have an entry (or it has no
   condition) and every stage it requires is active.

So an inactive requirement deactivates its dependents. That is not an error. Soft edges never
affect activation. The cost per invocation is two small arrays and one pass over stages and
REQUIRED edges, plus, for chunks, one settings read per column and conditioned type. The entries are
passed to the body as its second argument, a never-empty `List<FeatureSettings.Entry<S>>`.

A chunk stage is active when any column of the chunk configures it, so a biome border inside a chunk
never hides a feature on either side. The body then reads `ctx.settings().at(type, x, z)` per column,
or at the chunk's centre column (local 7, 7) for behaviour that happens once per chunk.

### Settings levels

Settings are declared at levels: the dimension, a region, a biome (the column's surface biome) and a
placement. `FeatureSettings.at(type, x, z)` returns one entry per level that declares the type at that
column, in that order, each with its level, the declaring resource and the value. Levels stack: there is
no "most specific wins" and no field merge. A module decides what several entries mean: deposits run
once per entry, a surface block may take the most specific. The alternatives each hide a decision from
the module: overriding would make a biome's deposits silently drop the dimension's, merging would need a
rule per field. Something spanning several columns reads at one origin column: per chunk the centre
column, per placed object the column it is anchored at. Cave biomes are not a level.

### Diagnostics

`ResolvedGraph.describe()` lists every stage in order with its kind, block pass, owner module,
`when` feature, required and soft predecessors and absent soft targets. It also lists the fused
block passes and every pair of stages with no path between them in either direction. Those pairs
are ordered only by the tie-break, so they are where two modules can silently conflict.
`stages()`, `blockPasses()` and `unorderedPairs()` expose the same data as values.

## Placement attempts

`PlacementRunner.place(request, edit, inputs, invocationSeed, settings)` owns the edit. It runs active stages in
order, stops after the current stage or block once a stage calls `reject(reason)`, commits only
when nothing rejected or threw, and closes the edit in every case. Stages receive a buffered
`PlacementAccess` view of the edit without commit or close, and it expires with the attempt.

## Random streams

Every stage has its own random stream per invocation, `StageContext.random()`. There is no shared
stream: `InvocationContext` has no `random()`, and the host passes a `long` invocation seed to
`PlacementRunner.place` and `ChunkRunner.generate` instead of a generator stages could see. With one
shared stream, every draw of an earlier stage shifts what later stages get, so installing a module
would change other modules' output for the same seed.

The stream depends only on the invocation seed and the stage id (`StageStreams`):

1. `idHash` = 64-bit FNV-1a of the UTF-8 bytes of `namespace:path`, computed once per pack load;
2. `streamSeed` = `mix(mix(invocationSeed) ^ idHash)`, `mix` being the SplitMix64 finalizer
   (Stafford variant 13);
3. the stream is `new WorldRandom(streamSeed)`, created when the stage first draws.

`mix` is a bijection, so a stage gets a different stream for every invocation seed, and in one
invocation stages with different id hashes get different streams. The hash and the mixer are written
out in IrisApi, not taken from `String.hashCode` or a JDK class whose output may change.

A test pins the derivation to values computed outside Java from this description.

### WorldRandom

`com.volmit.random.WorldRandom` is the generator stages and object generators see. No JDK generator
fits: `SplittableRandom` and the `RandomGeneratorFactory` algorithms promise identical sequences only
within one program, and `java.util.Random` fixes only some methods. The ones it inherits from
`RandomGenerator`, such as `nextInt(origin, bound)`, `nextDouble(bound)` and `nextExponential()`, are
JDK defaults that a JDK update may change, and those are the ones module authors reach for.
`Random` is also a 48-bit LCG with an atomic update per draw, a cost in per-block stages.

`WorldRandom` therefore does not implement `RandomGenerator`. Overriding every default would hold
only until a JDK adds another one; a type of its own exposes nothing IrisApi does not define. The
price is that it cannot be passed to JDK methods that take a `Random` or `RandomGenerator`, such as
`Collections.shuffle`; `pick` and `shuffle` cover the list cases instead. Its methods:

| Method | Definition |
| --- | --- |
| `nextLong()` | SplitMix64 (Steele, Lea and Flood): `state += 0x9e3779b97f4a7c15`, return `mix(state)` |
| `nextInt()` | the high 32 bits of `nextLong()` |
| `nextBoolean()` | whether `nextLong()` is negative |
| `nextFloat()` | the high 24 bits of `nextLong()` times 2^-24, in `[0, 1)` |
| `nextDouble()` | the high 53 bits of `nextLong()` times 2^-53, in `[0, 1)` |
| `nextInt(bound)`, `nextInt(origin, bound)`, `nextLong(bound)`, `nextLong(origin, bound)` | Lemire's multiply-and-reject method, unbiased; ranges wider than `MAX_VALUE` work |
| `nextDouble(bound)`, `nextDouble(origin, bound)` | `origin + nextDouble() * (bound - origin)`, a result rounded up to `bound` replaced by the double below it; a width beyond `Double.MAX_VALUE` is halved first |
| `nextGaussian()` | Marsaglia's polar method on `StrictMath.log` and `StrictMath.sqrt`; the pair's second value is discarded |
| `chance(probability)` | `nextDouble() < probability` |
| `pick(items)` | `items.get(nextInt(items.size()))`; an empty list throws |
| `shuffle(items)` | Fisher-Yates in place: for `i` from `size - 1` down to `1`, swap `i` with `nextInt(i + 1)` |

The state is one `long`, initially the seed. Methods without rejection draw exactly one value; how
many values a method draws is part of the sequence. `StrictMath` is fixed bit for bit by the Java SE
specification (fdlibm), so the gaussian needs no math of its own. Invalid bounds (`bound <= 0`,
`origin >= bound`, NaN or infinite doubles, a probability outside `[0, 1]`) throw
`IllegalArgumentException`, as does `pick` on an empty list. `pick` draws what `nextInt(size)`
draws. A shuffle of `n >= 2` elements makes `n - 1` `nextInt` calls; a shorter list draws nothing
and is not touched. `RandomAccess` lists are swapped through `List.set`; other lists are copied to
an array, shuffled there and written back through their `ListIterator`, like `Collections.shuffle`,
with the same result. A list that does not support `set` throws its `UnsupportedOperationException`
before any element changes. Weighted picking is left out until a stage needs it. The generator is
not thread-safe: one stage uses its stream within one invocation.

`WorldRandomTest` pins every method to values computed by `src/test/reference/world_random.py`, an
independent implementation of this table with Python integers and IEEE doubles. Python's `math.log`
is the platform's, so the script carries a port of fdlibm's `log`. The build does not run it. The test
also fails if `WorldRandom` gains a public method without pinned values or implements
`RandomGenerator`.

A block stage gets one stream per placement and draws from it across the blocks of its pass in block
order. Fused block stages interleave per block, but each still draws only from its own stream. A
stage that never draws never creates its stream.

Object generators are not stages. `ObjectGenerationContext.random()` stays, a `WorldRandom` seeded
from the variant seed the host chooses.

## Coordinates

X and Z are world coordinates. Y is the height above the dimension's bottom: 0 is always the dimension's
bottom block, whatever its minimum height is in the platform, so Y is never negative. That applies to
every value that carries a height: `TerrainSample` heights, `TerrainSampler` and `BlockView` positions and
bounds, `BlockPalette` and `NoiseSampler` sample positions, `PlacedBlock` and `PlacedMarker` positions,
placement anchors, `Mantle` positions and bounds, and a chunk `Hunk`'s local y. `BlockBox` carries
whichever coordinates its owner defines; an object's bounds are local to the object, and a placed
footprint can extend below height 0 where the object overhangs the bottom. The host converts to and from
the platform's Y at its boundary.

## Chunk invocations

`ChunkRunner.generate(inputs, invocationSeed)` runs active chunk stages once each in resolved order.
`GenerationContext.hunk(type)` returns the host-owned local volume of that value type, shared by
all stages of the invocation. Hunk coordinates are local. Samples and mantle coordinates use
world X and Z, and Y as the height above the dimension's bottom. A chunk stage writes only its own chunk and reads the mantle: the runner hands it a view
whose writes throw. The runner does no neighbor scheduling or rollback. A failed invocation must stop
the host from publishing the chunk.

A chunk stage writes only its chunk but may look around it: its `TerrainSampler` covers the chunk and the 16 blocks
around it, so a stage that smooths a surface reads its neighbors' heights (`surfaceY`, which spares the host the slope
that `column` works out). The sampler also gives the dimension's
`fluidHeight()` and, per column, `upperDimensionY` where a dimension stacked above begins; a stage writes nothing at or
above it. The mantle's `blocks()` are the blocks placements wrote into the mantle for chunks to take over (objects,
structures): a chunk stage copies them into its chunk, as Iris's `iris:matter` does. They are the host's layer, so they
need no registration, and stages only read them; placements keep writing blocks through `PlacementAccess.set`.
`ChunkRunner.generate(inputs, seed, timings)` reports how long each stage that ran took, so the host times modules
instead of modules timing themselves.

## Mantle invocations

Some work writes beyond its chunk: a carver whose tunnels cross chunk borders, a vein running on into
the next chunk. A chunk stage cannot do that, because the host may already have generated the
neighbor. Such work is a mantle stage. `MantleRunner.generate(stage, inputs, invocationSeed)` runs one
mantle stage for one chunk, if it is active there, against the mantle around it
(`MantleGenerationContext`: origin, terrain, settings, mantle), without a block hunk.

Every mantle stage is its own pass. Before a chunk generates, the host runs the first stage in resolved
order for every chunk within that stage's reach plus the reach of every later stage, each chunk once,
then the next stage the same way. A stage therefore sees, in the chunks around it, everything earlier
stages wrote, and a chunk's mantle is complete when its chunk stages read it. In Iris each mantle stage
is one mantle component, a pass of its own, whose radius is the stage's reach. Because stages of one
chunk run in different invocations, mantle stages have no outputs: `Stage.mantle` declares `Void`, and
stages pass data on through mantle layers. `requires(...)` still orders mantle stages and still
deactivates a stage in a chunk where a stage it requires is inactive; the runner works that out from
the settings of the stage and everything it requires, and collects only those.

The mantle pipeline is the only place that writes the mantle during generation, and mantle and chunk
pipelines meet only through mantle layers: an edge between them is a cross-pipeline edge and fails the
load. A mantle stage may rely on what earlier stages wrote around its chunk, but not on what a stage of
its own pass wrote in other chunks: that depends on the order the host generates chunks in.

## Reach

A stage may write outside its footprint: a carver reaches into neighbor chunks, a tree's leaves spread
past the object's trunk. The host has to prepare that area, and the mantle in it, before the stage
runs. Stages declare it in their definition:

```java
graph.define(CARVE).reach(32).writesMantle(CaveModule.CAVES).run(CaveModule::carve);   // CARVE is a mantle stage
```

- `reach(blocks)`: how far, in blocks along X and Z, the stage writes blocks or mantle values beyond
  its footprint. The footprint is the chunk's 16 by 16 columns for a mantle stage and the X/Z bounds of
  the placed object for a placement or block stage. A chunk stage writes only its chunk; declaring a
  reach above 0 on one fails the install, naming mantle stages as the place for the work. Whole
  columns: height is not limited, because Iris prepares chunks and mantle as full columns. Leaving it out means 0. Declaring it twice or
  negative fails while the module installs.
- `reach(PackReach)`: for a mantle stage whose reach depends on the pack, such as the largest object
  or the longest vein any level configures. The host works it out once per pack load from
  `PackSettings`, every entry of a settings type the dimension and its reachable regions and biomes
  declare, and asks each stage with `ResolvedStage.reachBlocks(settings)`; a negative result fails
  there, naming the stage. Placement and chunk stages may not declare one: placement areas and chunks
  are sized without the pack's settings. It counts as declaring the reach, so it cannot be combined
  with `reach(blocks)`.
- `writesMantle(layers...)`: the mantle layers the stage writes. Every layer must be registered by
  some module with the same value type, or the pack load fails naming stage and layer. Chunk stages
  cannot declare any.

Each resolved stage carries its `StageReach`. `ResolvedGraph.reach()` collects a `PipelineReach`:
the largest reach of the pipeline's stages, and per mantle layer the largest reach of the stages
that write it. Inactive stages count, because activation is per invocation and the prepared area is
decided once per pack load. A mantle pipeline with a reach worked out per pack has no such number, so
`reach()` throws there; the host sizes each mantle pass from its own stage instead. `describe()` lists
each stage's reach.

One number per stage, not per layer: a stage that writes two layers at different distances declares
the larger. The host can still size each layer's mantle from the per-layer maximum. Reads are not
declared separately: the host prepares the pipeline's reach around every placement, and each mantle
stage's own reach around its chunk, so a stage may read what others wrote that far out (for mantle
stages, see the ordering caveat under Mantle invocations). A stage that must read farther declares that distance
as its reach. Terrain samples are computed, not prepared, and need nothing.

## Settings, geometry and storage

Settings types implement `IrisFeature` and carry `@Feature` (namespaced id, display name, attachment targets). `when(type)` validates the annotation while the module installs. The id
must be owned by a module naming the type (see Namespaces), or the owner registers the type itself
with `FeatureRegistry.registerSettings`. That is for settings no stage names, such as settings read
only through `FeatureSettings.at(...)`; registering a type a stage also names changes nothing, and a
type its owner registered may be named in `when(...)` by any module.
`LoadedModules.load` catalogs those types with geometry generator settings and rejects duplicate
ids. Two modules with one id never get that far: the load fails naming both ("Duplicate module").
`OptionsMetadata` reads field metadata, in declaration order; it does not decode JSON.

### JSON keys

Packs write a feature's settings under the short key its `@Feature` declares (`key`, required), at each of
its targets: `"moss": {...}`. The feature id (`example:moss`) is the identity everywhere else (stage graph, logs,
lookups by class) and is never a JSON key. A key is a plain field name, not blank and without `:`.

*Why a declared key and not the id.* Pack authors write these keys by hand, and a biome already says `deposits`,
`decorators`; `"iris:deposit": {"generators": [...]}` would make every existing pack unreadable and every file longer.
The id carried an implicit uniqueness guarantee (module ids are unique); a declared key has to be checked instead.

*Uniqueness.* A key is unique per target (dimension, region, biome, object placement), across all loaded modules and
the host's own JSON fields of that target. The host passes its fields with `ModuleHost.reservingFields` (the same
fields it reads itself: a module may not take `name` or `generators` for a biome). `ModuleRegistry` claims `(target,
key)` for every feature it catalogs, settings and object generators alike, including types only a stage names, and a
clash fails the registration, so the pack load, with one message naming the key, the target and both claimants (feature
id and the module's jar, or `Iris` for a host field): `biome key "deposits" is claimed by iris:deposit (host classpath)
and acme:ores (modules/acme-ores.jar)`. Claimants are listed host field first, then built-in features, then pack
features, then by feature id, so the message does not depend on which module registered first.

*One shape.* The JSON under a key is the object of the class's options, unless `@Feature(root = "<option>")` declares
that the JSON is that option's value, as `deposits` is a list (`root = "generators"`). Then the key holds exactly that
value and the object form is not accepted. `root` must name the class's only option. This replaces the one-option
shorthand wave 2 removed (a bare value accepted as an alternative to the object, so every renderer needed an `anyOf`):
the class chooses its one shape once, and the decoder, the studio schema and the pack author see only that shape.
A `@Snippet` class may still be written as a `"snippet/..."` string instead of its object.

*A class built before keys existed.* A settings class compiled against an API whose `@Feature` had no `key` has an
incomplete annotation; reading it would throw `IncompleteAnnotationException`, which names neither module nor class.
`FeatureDescriptor.of` turns it into a load error that names the module and its jar, the class, and the key to add,
suggested from the id: `Module old:flora from modules/old.jar: The @Feature of settings class pack.old.Old$Patch has no
key, the JSON field packs write the settings under; add key = "floraWildPatch" (or another plain field name) to it and
rebuild the jar`.

`GeneratedObject` is sparse, immutable local geometry with bounds, an anchor, sorted blocks,
optional block entity payloads and markers. `ObjectGenerator` produces it from settings and a
`WorldRandom`. `ObjectTransformer` is host-owned.

The mantle contracts are unchanged: namespaced typed keys, versioned codecs, generation and
persistent lifetimes, ordered sparse visits and buffered edits. Only owners register a layer.
Unknown persisted layers must be preserved or fail explicitly.

## Settings description for studio

Studio needs a JSON Schema per pack file type and completion lists, and Iris writes both
(`.iris/schema/*.json`, the `.code-workspace` and IntelliJ's `jsonSchemas.xml`). Module settings
must appear there too. Module jars ship no schema: a schema written by the module's author would
describe the jar's build, not the pack that loads it, and would have to be kept in sync by hand.
IrisApi also writes no JSON Schema: it has no JSON library, and the host already renders its own
classes. The pack load instead collects what modules declare in their settings classes into a
`PackSchema` (`com.volmit.host.schema`), plain immutable Java values, and the host renders it.

`LoadedModules.load` builds it right after freezing the catalog (`PackSchemaBuilder`), so a pack
that loads has a complete description and a settings class the host could not render fails the
load. It contains every catalogued settings type of every module, built-in ones too, because the
core engine is modules as well. The host's own JSON fields are not in it; no key may equal one
(see JSON keys).

### Shape

- A `FeatureSchema` per catalogued type: feature id and name, role (`SETTINGS` for types named in
  `when(...)` or registered with `registerSettings`, `OBJECT_GENERATOR` for `registerObject`), the
  owning module's `ModuleInfo`, the JSON key the feature declares, its `root` option when the key holds one
  option's value (`rootOption()`), its targets and the settings `ObjectType`. `fields(target)` lists a target's features; a feature attached to several targets appears under
  each. The owner is the one module whose id owns the feature id; module ids are unique and contain
  no `/`, so there is never more than one.
- An `ObjectType` per class reachable from module settings, keyed by binary class name: description,
  `@Snippet` name and options. A field of a class type is an `ObjectKind` naming the type, never an
  inlined copy, so a class used in many places is described once, and a class that contains itself,
  directly or through others, ends the walk at its own name. Two different classes with one name fail
  the load; only a built-in class and a pack class can collide that way.
- Built-in modules may also register a class that Iris's own classes hold (`registerHeldType`, `FeatureCatalog.heldTypes()`):
  it is described like any settings class reachable from a feature, but no `FeatureSchema` names it. This is for
  settings that live in a host class until their feature owns them, such as the clamp of an object placement.
- A `SchemaOption` per field that `OptionsMetadata` selects, which means instance fields that are
  not transient, inherited ones included, in declaration order. Each has its name, `ValueKind`,
  `@Desc`, `@Required`, `@MinNumber`/`@MaxNumber`, `@MinItems` and the default value.

### Value kinds

`ValueKind` is sealed, so a host renderer that switches over it covers every case:

| Field type | Kind |
| --- | --- |
| `boolean`, `Boolean` | `BooleanKind` |
| `byte`, `short`, `int`, `long` and boxes | `IntegerKind` |
| `float`, `double` and boxes | `DecimalKind` |
| `String` | `TextKind(PLAIN)`, or `ReferenceKind(reference, PLAIN)` with `@Ref` |
| `ResourceId` | `TextKind(RESOURCE_ID)`, or `ReferenceKind(reference, RESOURCE_ID)` with `@Ref` |
| `BlockState` | `ReferenceKind(BLOCK, BLOCK_STATE)` |
| an enum | `EnumKind`: class name and constants in declaration order, each with its `@Desc` |
| `List<E>`, `E[]` | `ListKind(E, false)` |
| `Set<E>` | `ListKind(E, true)` |
| `Map<String, V>` | `MapKind(V)` |
| `String` with `@ResourceRef(Type.class)` | `ResourceReferenceKind(id of the kind read into Type)` |
| a class in a host type package, in built-in settings | `HostKind(class name)` |
| `Object` | `AnyKind`: any JSON value |
| any other concrete class | `ObjectKind(class name)` |

Host types are classes the host describes in studio itself, such as Iris's palettes and noise styles,
which built-in settings keep while their features move into modules. The host names their packages with
`ModuleHost.describingHostTypesIn(...)`; the builder neither reads their fields nor describes them, and
the host renders its own description where a `HostKind` appears. Pack modules' settings cannot use them:
pack modules never see host classes anyway, and a host type in their settings fails the load. Without
this, built-in settings would have to replace every Iris type with a describable copy before any feature
could move.

Everything else fails the load with the field, its settings and its module named, and what fits
instead:

- `char` and `Character`: no settings use them, and JSON has no character type;
- JDK classes such as `UUID`, `Optional` or `BigDecimal`, meaning classes of named modules in the boot
  layer: the host would need a format for each. `Object` is the exception: it means any JSON value
  (`AnyKind`), decoded as maps, lists, `Double`s, strings and booleans, for data whose shape belongs to
  someone else, such as a block entity's data in a `BlockSpec`;
- `ResourceKey<T>`: its type argument is the Java class a resource loads as, which tells studio
  nothing it could list; `ResourceId` with `@Ref` says what a value names;
- interfaces and abstract classes: their fields depend on the subclass a pack would pick;
- inner (non-static), local and anonymous classes, which cannot be created on their own;
- raw `List`, `Set` and `Map`, concrete collections such as `ArrayList`, wildcards and type
  variables, other generic classes, and maps whose keys are not `String`.

Only `List`, `Set` and `Map` are accepted as collections. They are what the host can decode without
choosing an implementation class, and accepting `ArrayList` or `Collection` would add cases without
adding anything a pack can write.

### References

`@Ref(Reference.X)` says what a text value names. The kinds are a closed enum of what the Iris host
can list today through its `RegistryList*` annotations: blocks, items, entities, platform biomes,
structures, potion effects and enchantments from the server registries, the host's noise styles, and
biomes, regions, objects, expressions and image maps of the pack, the resource folders Iris's own settings
refer to most. More kinds come when a
module needs one and the host can serve it. `@Ref` fits `String` and `ResourceId` and lists, sets and
arrays of them, where it applies to the elements. On any other field, a map included, it fails the
load. The pack kinds (`PACK_BIOME`, `PACK_REGION`, `PACK_OBJECT`, `PACK_EXPRESSION`, `PACK_IMAGE_MAP`) fit `String` only: packs name their
biomes, regions and objects by path without a namespace, such as `desert/dunes`, which a `ResourceId`
cannot hold. `BlockState` always names a block, so its fields are block references without `@Ref`;
`@Ref(BLOCK)` there is accepted, any other kind fails.

### Defaults

Every described class needs a no-argument constructor, of any visibility. The builder creates one
instance of each class with it and reads the fields of that instance as the defaults. The
alternatives were worse: reading no defaults leaves studio without the values a pack gets when it
leaves a field out, and parsing initializers from class files cannot see values a constructor sets.
Running module code is not new at this point: the load has already run the module's constructor,
`info()`, `install` and `register`. The host creates settings this way anyway before decoding JSON
into them, so a constructor that would fail here would fail on every decode: the load fails with the
constructor's exception, and a class without the constructor fails too, rather than describing
settings the host cannot create.

Defaults are JSON-shaped: `Boolean`, `Long`, `Double`, `String` (text, references as written,
`BlockState.key()`, enum constant names), `List<Object>` and `Map<String, Object>` with sorted keys.
An object default is the map of its options that have defaults, read through the declared class's
options. A float keeps its own shortest decimal form (`0.1f` is `0.1`). There is no default for a
null field, a NaN or infinite number, a list or map with an element without one, or a set of
objects, lists or maps, which has no order to write; sets of scalars are sorted. An object inside
itself, such as a field holding `this`, is left out of the outer object.

### Snippets

`@Snippet("name")` on a class lets a pack write that object in its own file under `snippet/name/`.
`snippets()` lists those types. A snippet name is a folder path: segments of lowercase letters,
digits, `-`, `_` and `.`, separated by `/`, never `.` or `..`. It must belong to one class; otherwise
the load fails naming both classes.

A class of a pack module keeps its snippets under the folder of a module of the pack: the name is the
module's id as a path, `example/moss`, or starts with it and `/`, such as `example/moss/patch`
(folder `snippet/example/moss/patch/`). Built-in classes keep the short names Iris's snippet folders
already have. Since built-in modules ship with Iris and pack names always start with a pack module's
namespace, which no built-in uses, a pack snippet can never collide with Iris's own or another
module's. A class belongs to the pack when the pack's class loader defined it.

### Stable order

The host writes schema files from this, so the order is fixed: features by id, a target's features
by JSON key, types by class name, snippets by snippet name, and options and enum constants in
declaration order. Options of a superclass come before the subclass's own.
`Class.getDeclaredFields()` promises no order, but the class file lists fields as the source declares
them, so `OptionsMetadata` reads it with `java.lang.classfile`. A settings class therefore has to come
from a jar or a class directory, which every module class does. Which settings an error names first follows feature id order too, so the same
pack always fails with the same message.

## Value types

Iris's features read blocks, noise, ranges and positions through Iris classes (`IrisBlockData`,
`IrisGeneratorStyle`, `IrisStyledRange`, `IrisRange`, `IrisPosition`) whose methods reach into Iris's loaders,
platform and noise. A module that may only see IrisApi needs the same JSON as API values, and the behaviour
behind them as host services. Each value type is a settings-shaped class: a final class with private fields
and defaults, a private no-argument constructor, `@Desc` and, where packs already write snippets of it, the
same `@Snippet` folder. So the schema builder describes it with no special case, the host decodes it like
settings, and pack JSON keeps its shape: the field names and the no-argument defaults are Iris's, odd ones
included (an omitted `Range` bound is 16 or 32). They are values: accessors return unmodifiable views,
withers return copies, and equality is by value, so the host can cache by them.

| Iris class | API | Behaviour behind it |
| --- | --- | --- |
| `IrisBlockData` | `com.volmit.block.BlockSpec` | `BlockRegistry.resolve(BlockSpec)`, `blockEntity(BlockSpec)` |
| `TileData` | `BlockEntityData` (existing) for placed data; a `BlockSpec`'s `tileData` (`Map<String, Object>`) in settings | `BlockRegistry.blockEntity(BlockSpec)` |
| `IrisMaterialPalette` | `com.volmit.block.PaletteSpec` | `BlockRegistry.palette(PaletteSpec, seed)`, a `PaletteSampler` |
| `IrisGeneratorStyle` | `com.volmit.noise.NoiseStyle` | `PackResources.noise()`, a `NoiseFactory` building `NoiseSampler`s |
| `IrisStyledRange` | `com.volmit.noise.StyledRange` | `at(noise, seed, x, z)` |
| `IrisRange` | `com.volmit.config.Range` | none: `contains`, `pick(WorldRandom)` |
| `IrisPosition` | `com.volmit.world.BlockPosition` (existing record, now with a private no-argument constructor) | none |
| `IrisDirection` | none: a module declares its own enum with the same constant names, and maps to `BlockFace` | |
| `IrisObjectRotation`, `IrisObjectScale` | none: settings of the module that picks a rotation or scale | `ObjectTransform`, `ObjectTransformer` (existing) |
| `ObjectPlaceMode` | none: an enum of the placement modules | |

Why these lines:

- **Noise is the host's.** Styles name the host's generators, and expressions and image maps are pack
  resources the host compiles. A `NoiseStyle` is only the description; `NoiseFactory.sampler(style, seed)`
  builds the noise. It hangs off `PackResources`, like `blocks()`, because noise depends on the pack snapshot
  (its expressions and image maps), and every context that has resources (stages, validators, hooks) can
  build noise. The host caches per snapshot, so stage bodies may ask for a sampler per invocation. The seed is
  the module's: noise lines up across chunks only when it comes from `worldSeed()`, not a stage's stream.
- **`NoiseSampler` moved to `com.volmit.noise`** and gained a two-dimensional `sample(x, z)`: Iris's noise
  gives different fields in two and three dimensions, and most features sample columns.
- **`NoiseSampler.signed(...)`** is the sample mapped to -1..1 that a host may answer from a per-thread memo of the
  coordinates it was just asked: a carver adds several fields and tests the sum around zero, and reads the same points
  of one field again and again, so the memo is the difference between caves that cost what they always did and caves that
  do not. A default method (`sample * 2 - 1`) keeps every other sampler valid.
- **Style names are text with `@Ref(NOISE_STYLE)`**, not an enum: the host owns the list, and an API enum would
  pin it. An unknown name samples flat, as Iris has always read one.
- **`isFlat()` is stricter than Iris's**: a flat base style with an expression or image map is not flat.
- **Block properties are text.** `{"waterlogged": true}` decodes to `"true"`, which is what the platform's state
  key holds; the host's studio description may accept the JSON types it accepted before.
- **A palette is a value plus a picker, not a new `BlockPalette`.** `BlockPalette.select` takes a random stream
  and a block per call, which is how a module picks by chance; `IrisMaterialPalette` picks by noise at the
  position, so `PaletteSampler.at(x, y, z)` has no stream. The picker takes doubles because Iris's does (zoom
  divides them), and the seed is the one the old code gave its `RNG`, so a module can keep the layout of old worlds.
  A palette without a pickable block throws when the sampler is asked for, where Iris returned `null`: the API has
  no absent block, and `@MinItems(1)` already says a palette needs one.
- **Resolving a `BlockSpec` never throws.** It follows what packs rely on: a custom block of the pack's
  `blocks` folder, then the platform's block, then the backup, then air. `resolve(String)` keeps throwing for
  an unknown key.
- **Directions, rotations, scales and place modes stay module types.** They are small enums or settings whose
  meaning is a feature's (which axes may turn, by which steps; which anchor sampling a mode uses), and applying
  a rotation is already the host's `ObjectTransformer`. An API type would freeze a feature's choices.

## Vanilla structures

Minecraft, data packs and mods register structures the platform starts on its own. Iris blocks some (a pack
replaces them with its own structures), lets others generate, places some itself, and fits all of them to its
terrain. The decisions and the fit belonged to core and the platform adapters, reading feature classes. A module now
states them through the `VanillaStructures` service; the host applies them on every platform.

- **A service, every provider asked.** `@Service(Providers.ALL)`: each module that takes part in the platform's
  structures answers for its part, in module id order. It was a dedicated hook registry; a service is the one way
  modules answer host questions, so the catalog, ordering and failure naming are the services' own.
- **A rule per structure, not per start.** `rule(structure, context)` answers `GENERATE`, `ALSO_PLACE`,
  `REPLACE` or `BLOCK` for one structure id in one world. The platform asks per start, but also when it locates
  a structure, which has no position; and an answer that depended on the position could block a start
  `/locate` had promised. The host caches the rule per world, structure and pack snapshot.
- **Two facts, one enum.** Whether the platform's own starts are blocked, and whether a module places the
  structure itself. The second matters even where the first does not: a structure a pack's dimension disables
  but a module places is reported, and located, as the module's. `and` combines two modules' rules, so the
  result never depends on module order.
- **The context is the world's pack.** `resources()` and `settings()` (`PackSettings`: the dimension and every
  region and biome it can reach), the same view reaches are worked out from.
- **Placing starts: `starts(chunkX, chunkZ, context)`.** Asked per chunk because the platform generates per chunk. A
  start carries everything the platform needs and nothing it can work out: structure, chunk, height above the
  bottom, surface or underground, whether it takes the chunk from a platform start, jigsaw overrides, a memo, and an
  identity the host persists with the start so a start found in a saved world can be traced to its placement. Across
  modules the host keeps the first start per structure and chunk in module id order. The context is the `PackWorld`
  plus a `TerrainSampler`.
- **No fit values in the API.** How a start meets the terrain (Iris's SOURCE, PRESERVE, VACUUM, ENCASE, BORE,
  FORCE_CARVE, stilts, bands) is a feature's vocabulary; an API type would freeze one feature's choices. The API gives
  a module what it needs to fit: the platform start's place (`shift`), how far a start changes terrain (`reach`) and
  the chunk around the pieces (`beforePieces`, `afterPieces`). The host keeps only what every platform needs: move and
  clamp a start, wrap its references, place its pieces.
- **Shift and reach, not an adjustment value.** A start the platform makes is moved by the sum of every module's
  `shift(PlatformStart, context)`. `PlatformStart` gives what a module needs to decide (bounds, underground, aligned
  by the host, the dimension's bottom, the surface per column) without platform types. `reach` is asked for every
  start before it is published, so the platform hands it to every chunk whose terrain a module changes; `PLATFORM`
  means the platform's own adaptation envelope, so a module that changes nothing more need not know it.
- **The fit sees one chunk at a time.** `StructureFit` is a chunk's area and its starts in placing order, each with
  its pieces: box, kind (`FIXED` built in code, `RIGID` and `TERRAIN_MATCHING` jigsaw pieces), ground level, jigsaw
  junctions, the template's cells after the platform's processors (`TemplateCells`, with removed blocks and the
  number of template blocks read, so a module can bound its work), template columns and legacy template air. Blocks
  go through `BlockAccess`: `get`, `column` and `set` work on plain states, traits and tags answer the platform's own
  questions, so a fit scans millions of cells without a value per block. `state(type, factory)` holds a module's
  object for the chunk from `beforePieces` to the last `afterPieces`, the only place a fit keeps state.
- **A memo instead of a persisted fit.** A start a module places carries a `StartMemo` (two texts, the module's to
  fill). The host stores it with the start's ownership record and hands it back in `reach` and in every chunk's
  `StructureFit`, so a start fits the same in every chunk it reaches, also after a restart or a pack change. Iris's
  structure module writes its terrain and stilts JSON there, in the two places records always held them, so records
  written before read the same.
- **Setup before a world exists: `setup(context)`.** Structure set frequencies and protecting the platform's
  definitions from installed data packs are decided when the platform builds its structure state or the host
  installs data packs, before any world of the dimension runs. Frequencies multiply across modules; protection holds
  when any module asks for it.

## Pack resource kinds

Iris loads its pack resources (biomes, regions, objects, structures...) with one hard-coded loader per type,
so core knew every feature class that had a folder. A module now declares a kind of pack resource itself:
`FeatureRegistry.registerResourceKind(ResourceKind.of(id, folder, type))`. The host reads every `.json` file below
`<pack>/<folder>/` into a new `type` and serves it through `PackResources.find` and `PackResources.ids`, like
the resources it serves itself.

- **Identity.** A file's key is its path below the folder without `.json`; its id is `<pack namespace>:<key>`,
  the form every pack resource has. The JSON inside keeps unprefixed keys, the class's field names; namespacing
  pack JSON is a later decision. The kind's own id follows the module id like stage ids, feature ids and mantle
  keys (`ModuleInfo.ownsId`), and names the kind in errors and studio.
- **Folders.** The module chooses the folder, one folder name directly below the pack root, so a kind that takes
  over one of Iris's own keeps the folder and format packs already use. A nested path would let one kind's folder
  sit inside another's, whose files the host would then read twice. One folder holds one kind. The host's own
  folders are reserved with `ModuleHost.reservingFolders`. Either conflict fails the pack load in `ModuleRegistry`,
  naming both owners; the host is named as "the host".
- **Type.** `PackResources` selects a kind by the requested type, so one class backs at most one kind and
  `find(new ResourceKey<>(id, Keep.class))` needs no kind id. The class must be concrete; like settings it needs a
  no-argument constructor, checked when the pack's description is built.
- **Reading.** The host creates the instance with that constructor and reads the JSON into it, exactly as it
  decodes settings, so a missing field keeps its default and studio's defaults are what packs get.
- **Lifetime.** A resource is read once per pack snapshot and every caller gets the same instance, so resources
  are values: nobody may change one. A reload is a new snapshot (`snapshotId()` changes) that reads the files
  again. A file that fails to read is an error from `find`, not an absent resource.
- **Host types.** A built-in module may read a kind into a class the host describes itself (`describingHostTypesIn`),
  so a built-in feature can become a module before its classes are rewritten against IrisApi's annotations; studio
  then renders the host's own description of the class. A pack module's kind of a host type fails the load.
- **Studio.** `PackSchema.resources()` lists each kind with its folder and the files' type, an `ObjectKind`
  described in `types()` or a `HostKind`. The host writes a schema per kind for the files below its folder. An
  option that names a resource of a kind carries `@ResourceRef(Keep.class)`: a class, not a kind id, so the
  reference is checked by the compiler and matches how `PackResources` selects kinds. It becomes
  `ResourceReferenceKind(kindId)`, and studio completes the kind's keys. Keys are pack paths without a
  namespace, so only `String` fits, like the pack references of `@Ref`. A class that backs no kind of the pack
  fails the load.

## Validators

A module registers `Validator`s with `FeatureRegistry.registerValidator`. The host runs them wherever it validates
a pack, alongside its own checks, with a `ValidationContext`: the pack's `PackResources` (module kinds included),
its `PackSettings` (every entry of every dimension, region and biome of the pack, reachable or not, since a
validator checks files rather than one world) and `report(Problem)`.

A `Problem` has a `Severity`, a `Subject` and a message. `ERROR` stops the pack from loading, `WARNING` is only
shown: the two outcomes Iris's own validators have. The subject is a resource id or a file path below the pack
root; one id can name resources of several kinds, so a file is the clearer subject where the kind matters. The
host shows the subject with the message. A validator that throws fails the pack's validation with an error
naming its module; the other validators still run. `FeatureCatalog.validators()` lists them by module id, each
module's in registration order, so the order never depends on discovery.

## World state and lifecycle

Iris kept the caches of its features itself: decoration streams in the biome complex, a warm-up pass over feature
data when an engine starts, per-runtime workers in a map only built-in stages reached. A module could not do the
same, because stage bodies are shared by every world and every invocation and must keep no state. The API now gives
each world a state store and tells modules when worlds start and stop.

- **The world.** `PackWorld` is one world generating with one pack snapshot: seed, resources, the settings of its
  dimension and of the regions and biomes it can reach (what mantle reaches see), and `state(type, create)`.
  `InvocationContext.world()` hands it to every stage; services take it as their first argument; hooks get it. A
  placement outside any world, such as a studio paste, has none and throws.
- **The store.** One instance per type, keyed by class: a class is compile-checked and module-owned, so two modules
  never share an entry by accident, and it matches the internal `state(Class, factory)` Iris already used. A factory
  runs once per type even when threads race (the others wait and get the same instance); it may ask for other state
  on its own thread, since creation holds one re-entrant lock, and asking for the type being created fails naming the
  chain instead of recursing or deadlocking. A factory that throws keeps nothing. Reads of existing state take no lock.
  `WorldStates` is the reference store hosts use.
- **Hooks.** `ModuleLifecycle` has `packLoaded(resources, settings)`, `worldStarted(world)`, `warmUp(world)` and
  `worldStopped(world)`,
  each a no-op by default, registered with `FeatureRegistry.registerLifecycle` like validators, so the host knows the
  owning module for errors and orders hooks by module id. A module class may implement it and register itself.
  `packLoaded` runs once per snapshot before its first world starts; a pack that is only validated or edited in studio
  is not loaded in this sense. The settings it gets are the whole pack's, as validators see them.
- **Hotload semantics.** A world's state lives as long as the world runs with one pack snapshot. A hotload stops the
  world and starts a new one with the reloaded pack; so does a reload of the pack's resources while the world runs,
  such as a tool rewriting structure files. State therefore never outlives the resources it was built from, and
  modules need no invalidation calls of their own. An invocation keeps the world it began with; the host closes the
  old world's `AutoCloseable` states once nothing generates with it.
- **Failures.** A failing `packLoaded` or `worldStarted` keeps the world from starting, naming the module, and the
  modules that had started get `worldStopped`. Every `worldStopped` runs even when one fails; the failures are reported
  together. `LifecycleRunner` implements these rules for hosts.
- **Warm-up.** `warmUp` is a second start hook for caches that take long to build. `worldStarted` runs on the thread
  that starts the world and delays it, so a studio host would stall its start; `warmUp` lets the host pick: wait for it
  on the starting thread (server worlds, which must not generate before the caches exist) or run it in the background
  and make the first generation wait (studio, where the world is usable at once). A world therefore never generates
  with half-built caches, and a module need not guard its own cache against a still-running warm-up beyond the
  state store's rules. It only builds caches: a failure is reported per module and does not stop the world, since the
  cache is built on first use anyway. `LifecycleRunner.warmUp` runs every module's hook even when one fails.
- **Threading.** `packLoaded`, `worldStarted` and `worldStopped` of one world run one at a time on the thread that
  starts or stops it, in module id order and stops in reverse; `warmUp` runs where the host chose, after
  `worldStarted`. State is safe from any thread.
- **Not added (yet):** a pack-level store shared by the worlds of one snapshot. Every known consumer (decoration
  streams, warm-up, placement workers, structure indexes) is per world; per-world state built from pack data costs
  a little duplication when several worlds share a pack.

## Module logging

Iris's features logged through its static `IrisLogging`, which a module jar cannot see and a host cannot label. The API
gives each module a `ModuleLog` instead.

- **Shape.** `debug`, `info`, `warn`, `warnOnce(key, message)` and `error(message, cause)`: the levels the features used,
  the "state it once" helper they all grew (a condition met per block must not flood the log), and error reporting with the
  failure attached. Messages are plain text, so a `%` or `{` in a pack's names never breaks a line.
- **One log per module, labelled by the host.** `DiscoveredModule.log()` is created when the host discovers the module
  (built-in modules once, pack modules per pack load) from the `System.Logger` the host passed to `ModuleHost.loggingTo`,
  the same one the load summary uses, and puts `[module id] ` in front of every message. The host's logger decides what
  `DEBUG` means (Iris follows its debug setting) and what an `ERROR` with a cause does (Iris reports it like its own
  failures). The reference `ModuleLogs.labelled` implements the labelling, the once-keys and the debug check; a host only
  supplies the `System.Logger`. `warnOnce` keys are kept per log, so they belong to the module, and past a few hundred new
  keys it only debugs, which keeps a key space nobody bounded from growing.
- **Where a module reaches it.** `InvocationContext.log()` for stage bodies and generators, `PackWorld.log()` for services
  and lifecycle hooks, `FeatureRegistry.log()` during `register`. The world and the context agree: the runners hand a stage a
  view of the world whose log is its module's (`PackWorld.withLog`, a host method; the view shares the state store), and
  `LifecycleRunner` and `ServiceRegistration.scope` do the same for hooks and providers. A host that calls a provider itself
  must scope the world, or the provider's lines carry the host's label.
- **Why not `System.Logger` for modules.** A module that logs through the JDK works where the host installed a logger
  finder and silently goes elsewhere where it did not (Iris's plugin class loader is not the system loader), and nothing
  tells the host which module spoke.
- **Not added:** format arguments (callers build the message; a string concatenation is cheap beside what a debug line
  costs when it is on), and a `ValidationContext` log (validators report problems as values, `Problem`).

## Services

Iris core still asks feature classes directly: which objects a chunk gets, where a structure is, which tree grows
from a sapling, which drops a block has. A module answers such questions through a service instead.

- **Shape.** A service is an interface marked `@Service`. Its methods take the `PackWorld` they concern (or the
  `PackResources` for a question about the pack alone) and return values. A provider is registered once per pack with
  `FeatureRegistry.registerService(type, provider)` and holds no state: it reaches the world's store. Services compute;
  what should happen in the world (an entity spawned, items dropped) comes back as a value the host carries out on the
  thread the platform needs, so no platform type ever appears in a service.
- **Several providers.** Each service chooses with `@Service(Providers.X)`: `ALL`, the host asks every provider and
  combines the answers (objects in a chunk); `FIRST`, the host asks in order until one answers (the module that knows a
  structure key); `ONE`, at most one provider per pack, and a second module providing it fails the pack load naming
  both. Order is module id order and, within a module, registration order, so it never depends on discovery.
- **Lookup.** The host asks the pack's catalog: `services(type)` for every provider, `service(type)` for the one
  provider of a `ONE` service. Looking up a type that is not marked fails, and so does `service` for a service that may
  have several providers.
- **Threading.** The host may call a provider from any thread, several at once. A provider that searches long
  (`StructureLocator.locate`) is called off the server's main thread.
- **Loot, drops, entities, trees.** `Loot` (`ALL`) asks in two steps, which tables a container draws from and what a
  table gives, so the host and plugins can change the tables in between. `BlockDrops`, `EntitySpawns` and `SaplingGrowth`
  return values the host carries out: `ItemDrop` and `EntitySpec` carry every decision the module made (the module draws
  from its streams in the order the feature always did, which is why `ItemRegistry` answers whether an item can be
  damaged), and the host reads what only the platform knows (light, the surface below, free space, the blocks around a
  sapling) through the attempt's conditions or a `Surroundings` view. A service that must hear how its plan went has a
  second method for it (`EntitySpawns.spawned`, which keeps a spawner's rate limits).
  `AmbientEffects` follows the same rule: the module decides which effects are due (interval and chance, kept in the world's
  state) and every random value they carry, the host only plays them.
- **Services now.** `ObjectsAt` (`ALL`): pack object keys a module would place in a chunk, predicted without
  generating it, and the biomes and regions whose settings place a given object (`PlacedBy`, named by pack keys; the
  host builds its search area from them through the biome tree it owns). `StructureLocator` (`FIRST`): every structure key a module knows, the ones a world places, and the
  nearest start of one as a `LocateResult` (`Found`, `NotFound`, `SearchLimitReached`; a sealed interface, so callers
  switch over every outcome). Keys are pack keys as packs write them and match without regard to case.
  `CaveBiomes` (`ONE`): the pack biome a module puts at a position whatever the regions say (a band of the deep dark,
  `overriddenBiome`, the height counted above the dimension's bottom) and how deep below the surface a biome's caves begin
  (`minDepthBelowSurface`, asked once per biome and kept while the world runs). The host owns the 3D biome of a position (its regions, terrain height and the cave biome stream)
  and asks this only where a module decides; one provider, since two modules could not both decide the biome of one cave.
- **Settings that name biomes.** The host owns the biome tree (a region's biomes and their children) and the set of
  biomes a dimension reaches, and both must include biomes that module settings name: a cave band's biome, the target of a
  floating island. Settings say so by implementing `LinkedBiomes` (`treeBiomes`, which join a region's tree and so its terrain
  generators, and `usedBiomes`, which the dimension reaches without joining), instead of the host reading any module's
  settings class. `usedBiomes` receives the dimension's own settings, since a name may stand for something the dimension
  declares (a floating island's `carving` is a band id or a biome key).
- **Why not per-world provider instances.** A factory per world (`world -> provider`) would let a provider hold its
  world, but every service method would then hide which world it answers for, and a host could not ask a pack-level
  question without a world. Passing the world keeps providers stateless like stage bodies and puts all per-world data
  in one place.

## How a module reaches the host

A module jar sees three things besides the JDK and its own pack's jars, and nothing else of the host:

1. **The API** for everything a worldgen module needs: blocks (`BlockState`, `BlockRegistry` traits, tags, support and
   specs), noise (`NoiseFactory`), randomness (`WorldRandom`, stage streams), the layout as values (`TerrainSampler`,
   `BiomeSample`, `PackBiome`, `PackRegion`, settings per column), the chunk's hunk and mantle (layers and planned
   blocks), pack resources and module resource kinds, per-world state, lifecycle hooks, services and validation.
   Whatever a second module could want goes here, host-neutral and tested without Iris.
2. **Packages the host exposes**: libraries it shares (VolmLib, Gson) and the public face of its built-in modules
   (`art.arcane.iris.builtin`: stage constants, settings and output types packs may read).
3. **Narrow facades for the host's own built-ins**, in that same exposed package: interfaces the host's worlds
   implement, for what only the host's own features need from its internals, such as Iris's per-feature world seeds
   (`IrisSeed`), which existing worlds depend on. A pack module never needs one. Every such type is a promise the host
   keeps, so there are few, each answers one question, and none hands out an engine.

A module never reaches the host's engine, its data loader or its layout classes; it reads the layout through the API's
values and its settings through `FeatureSettings`. Iris's mapping from its internals to this surface, with the
utilities that move into VolmLib, is in its notes (context.md, "How a module reaches the host").

## Not implemented

- Host wiring, which lives in Iris: JSON decoding and validation, rendering `PackSchema` into Iris's schema and
  workspace files, reading module resource kinds, running validators, platform block adapters, storage and codecs.
- Legacy random compatibility. Existing Iris features draw from shared, positional generators in a
  fixed order. Where existing worlds are promised to stay the same, the adapter has to reproduce
  those draws inside the ported stages; per-stage streams will not match them on their own.
- Real feature ports. The test examples are simplified slices, not parity ports.

## Feature-family migration map

This is an inventory of where each existing family should land, not a claim that it is ported.

| Existing family | Target |
| --- | --- |
| Surface, ceiling, shore, sea and floating decoration | Chunk stages + palette/support/sampling resources |
| Slab/wall/snow post-processing, foliage cleanup, ore hiding | Chunk stages + block traits and neighbor views |
| Custom block recording and deferred application | Chunk stage + persistent typed layers + host content adapter |
| Ores and deposits | Chunk stages + `BlockSpec` palettes + configured settings |
| Object selection, transforms, height and placement modes | Geometry + placement stages per mode module |
| Stilt, erosion, ceiling hang, vacuum, paint, floating | Placement modules with block and invocation stages |
| Slope, support, limits, collision, protection, bore, meld, snow | Placement stages that reject or write |
| Markers, tile data, tree and loot metadata | Object metadata + placement edit + typed storage |
| Procedural trees, crystals, fungi, coral, ruins | Object generators returning sparse geometry |
| Cave profiles, carving, cave fluids and decoration | Chunk stages + typed carve layers |
| Floating islands and child biomes | Terrain/biome sampling + chunk and placement stages |
| Structures, jigsaw pools and foundations | Structure assembly producing placement requests |
| Imported/native structures and ownership | `VanillaStructures` service (rule, starts, shift, reach, beforePieces, afterPieces, setup); host native-generation bridge per platform |
| Terrain layers, fluids, bedrock, interpolation | Terrain providers + chunk stages |
| Region/biome selection | Biome providers + platform biome writer |
| Image maps, noise expressions, pack injection | Compiled immutable pack resources |
| Spawning, effects, drops, loot, tree growth, commands | Separate runtime family with host actions |
| World lifecycle, pregen, locate, studio tooling | Bridge existing integrations |

## Provider and runtime direction

- Terrain and biome providers should return sampled fields and identities. The host adapts them
  to native chunk and biome formats. They must not own worlds or ticks.
- Structure assembly should consume immutable resources and return placement requests. The host
  owns reach, scheduling and native-generation gates.
- Image maps and expressions should compile into pack resources before a snapshot is published.
- Runtime features need their own family with read-only event inputs and requested actions that
  host adapters perform on the right scheduler. Generation hunks, RNGs and views never leak into
  runtime work. Services are that family's seam: a service takes the world and an event value and returns the
  actions (see Services).

Exact signatures for these families should follow their first real port.

## Readiness gate

A feature replaces its legacy implementation only when existing pack JSON works, representative
seeds match promised output, dimensions whose bottom is below world Y 0 and chunk boundaries pass, concurrent execution is
deterministic, reload caches expire and failures cannot publish partial chunks. Until then the
legacy path stays available.
