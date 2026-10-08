# Iris API design

Status: pack module loading (`LoadedPack`), the stage graph, per-stage random streams, both
pipelines' reference executors, settings catalog and its description for studio, geometry,
resources and typed storage contracts
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
    Modules --> Register[register: geometry, mantle layers]
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
`<pack>/modules/*.jar`, one module per jar.

The host loads a pack with `LoadedPack.load(packRoot, host)`. `ModuleHost` is the host's fixed
configuration, built once and the same for every pack: built-in modules, exposed host packages,
reserved JSON fields and reserved namespaces. Each of its methods adds to what earlier calls
configured, and all names are checked when it is built. `LoadedPack` runs `PackModules.load` (below), then
`LoadedModules.load` on the built-ins followed by the pack's modules. If the second step fails it
closes the first before rethrowing, so a failed load leaves no class loader or jar copy behind.
`close()` closes the pack's class loader, then deletes its jar copies.

A jar names its entry class in `META-INF/services/com.volmit.IrisModule`, the provider format of
`ServiceLoader`. The host reads that file from the jar itself rather than calling
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
   ignored; zero or more than one entry class name fails.
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

A module may use any library, but it ships that library itself, inside its own jar or as another
jar of the pack. It never borrows Iris's copy: Iris's libraries can change with any Iris update,
and two modules would otherwise depend on whatever version Iris happens to bundle.

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
affected. `ModuleInfo.of(id, name, version)` declares none. Invalid names fail in `info()`, which the
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

The host reserves namespaces for its own modules (`ModuleHost.reservingNamespaces("iris")`). A
module from a pack jar whose id uses a reserved namespace fails the load, naming the jar and the
namespace. Its stage ids, settings ids and mantle keys start with its id, so this one check keeps
pack modules out of reserved namespaces everywhere. Built-in modules, discovered with
`DiscoveredModule.builtIn`, may use them.

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

## Chunk invocations

`ChunkRunner.generate(inputs, invocationSeed)` runs active chunk stages once each in resolved order.
`GenerationContext.hunk(type)` returns the host-owned local volume of that value type, shared by
all stages of the invocation. Hunk coordinates are local; samples and mantle coordinates are
world blocks. A chunk stage writes only its own chunk and reads the mantle: the runner hands it a view
whose writes throw. The runner does no neighbor scheduling or rollback. A failed invocation must stop
the host from publishing the chunk.

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

Settings types implement `IrisFeature` and carry `@Feature` (namespaced id, display name, optional
alias, attachment targets). `when(type)` validates the annotation while the module installs. The id
must be owned by a module naming the type (see Namespaces), or the owner registers the type itself
with `FeatureRegistry.registerSettings`. That is for settings no stage names, such as settings read
only through `FeatureSettings.at(...)`; registering a type a stage also names changes nothing, and a
type its owner registered may be named in `when(...)` by any module.
`LoadedModules.load` catalogs those types with geometry generator settings and rejects duplicate
ids and alias collisions per attachment target, including reserved host fields.
`OptionsMetadata` reads field metadata, in declaration order; it does not decode JSON.

### JSON keys and aliases

A feature whose settings class has exactly one option may be written as that option's value instead of
an object holding it: `"snow": 0.5` for `"snow": {"amount": 0.5}`, or a bare JSON list for a class holding
one list. Existing pack JSON writes several settings that way, and keeping the format meant either this
rule or a compatibility layer per key. `FeatureSchema.shorthand()` returns the option when there is
exactly one, the host decodes a value that is not a JSON object into it, and renders the schema to accept
both forms. A `@Snippet` class written as a `"snippet/..."` string stays a snippet reference.

Packs write a feature's settings under its feature id at each of its targets:
`"example:moss": {...}`. The id is unique, namespaced and owned by one module, so two modules can never
claim the same key, and a key can never be one of the host's own fields: those have no `:`.

Built-in modules may also set `@Feature(alias = "stilt")`, a short key that existing pack JSON
already uses, such as `"stilt": {...}`. An alias has no `:`, so it never equals an id. Aliases are
unique per target and may not equal a reserved host field; reserved host fields therefore constrain
aliases only. A pack module that sets an alias fails the load and is told to use its id: aliases are
for keeping existing pack files working, and a pack module has no existing files to keep.

A JSON object that holds both a feature's alias and its id is ambiguous. That is a host decoding
rule, since IrisApi does not decode JSON: the host fails such an object, naming both keys.

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
core engine is modules as well. The host's own JSON fields are not in it; reserved host fields
already keep module fields from colliding with them.

### Shape

- A `FeatureSchema` per catalogued type: feature id and name, role (`SETTINGS` for types named in
  `when(...)` or registered with `registerSettings`, `OBJECT_GENERATOR` for `registerObject`), the
  owning module's `ModuleInfo`, the JSON key (the feature id), the alias if any, its targets and the
  settings `ObjectType`. `fields(target)` lists a target's features; a feature attached to several targets appears under
  each. The owner is the one module whose id owns the feature id; module ids are unique and contain
  no `/`, so there is never more than one.
- An `ObjectType` per class reachable from module settings, keyed by binary class name: description,
  `@Snippet` name and options. A field of a class type is an `ObjectKind` naming the type, never an
  inlined copy, so a class used in many places is described once, and a class that contains itself,
  directly or through others, ends the walk at its own name. Two different classes with one name fail
  the load; only a built-in class and a pack class can collide that way.
- A `SchemaOption` per field that `OptionsMetadata` selects, which means instance fields that are
  not transient, inherited ones included, in declaration order. Each has its name, `ValueKind`,
  `@Desc`, `@Required`, `@MinNumber`/`@MaxNumber` and the default value.

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
| a class in a host type package, in built-in settings | `HostKind(class name)` |
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
- JDK classes such as `UUID`, `Optional`, `BigDecimal` or `Object`, meaning classes of named modules
  in the boot layer: the host would need a format for each;
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
structures, potion effects and enchantments from the server registries, and biomes, regions and
objects of the pack, the resource folders Iris's own settings refer to most. More kinds come when a
module needs one and the host can serve it. `@Ref` fits `String` and `ResourceId` and lists, sets and
arrays of them, where it applies to the elements. On any other field, a map included, it fails the
load. The pack kinds (`PACK_BIOME`, `PACK_REGION`, `PACK_OBJECT`) fit `String` only: packs name their
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

## Not implemented

- Iris host wiring: JSON decoding and validation, rendering `PackSchema` into Iris's schema and
  workspace files, resource loaders, platform block adapters, storage and codecs.
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
| Ores and deposits | Chunk stages + noise/palette resources + configured settings |
| Object selection, transforms, height and placement modes | Geometry + placement stages per mode module |
| Stilt, erosion, ceiling hang, vacuum, paint, floating | Placement modules with block and invocation stages |
| Slope, support, limits, collision, protection, bore, meld, snow | Placement stages that reject or write |
| Markers, tile data, tree and loot metadata | Object metadata + placement edit + typed storage |
| Procedural trees, crystals, fungi, coral, ruins | Object generators returning sparse geometry |
| Cave profiles, carving, cave fluids and decoration | Chunk stages + typed carve layers |
| Floating islands and child biomes | Terrain/biome sampling + chunk and placement stages |
| Structures, jigsaw pools and foundations | Structure assembly producing placement requests |
| Imported/native structures and ownership | Host native-generation policy adapter |
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
  runtime work.

Exact signatures for these families should follow their first real port.

## Readiness gate

A feature replaces its legacy implementation only when existing pack JSON works, representative
seeds match promised output, negative heights and chunk boundaries pass, concurrent execution is
deterministic, reload caches expire and failures cannot publish partial chunks. Until then the
legacy path stays available.
