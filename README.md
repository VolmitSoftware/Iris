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
`iris:objects` also provides `SaplingGrowth` (`TreeGrowing`: the tree a sapling grows into, as placed blocks, and which
objects are trees) and says which biomes and regions place an object (`ObjectLocating`, for the object search).
`iris:loot` owns the loot table resource kind (`LootResources`, folder `loot`), checks the tables and the references to
them (`LootValidator`), and provides `Loot` (`LootGiving`: which tables a container draws from and the `ItemDrop`s a
table gives) and `BlockDrops` (`BlockDropping`). `iris:spawn` owns spawners, entities and markers (`SpawnResources`,
folders `spawners`, `entities`, `markers`) and provides `EntitySpawns` (`EntitySpawning`: what to spawn in a chunk or at
a marker, as `EntitySpec`s; `EntityResolution` makes the entity's random choices). Iris's Bukkit code creates the items
and entities from those values (`BukkitItemStacks`, `BukkitEntitySpawner`).
`iris:decorant` keeps its per-world `Decorating` in the world's state store (`context.world().state(...)`); the other
built-ins still reach the same store through `IrisChunk.state(...)` / `engine.getGeneration().state(...)`.

Settings move off Iris's classes one feature at a time. `iris:deposit` is the first whose settings are IrisApi types
only: `DepositSettings` holds `DepositGenerator`s (blocks as `BlockSpec`, its enums beside it in
`art.arcane.iris.builtin.deposit`), and packs write `"iris:deposit": {"generators": [...]}` (the old `"deposits": [...]` is converted by the pack migration script). What needs Iris (resolving the
palette, ore checks, clump shapes per world seed) lives in the module's own code, `ResolvedDeposit`.

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
with `art.arcane.iris.builtin` as the only exposed Iris package; a listed class that uses Iris internals fails
the task naming the class and what it uses. `check` runs the task. Nothing is ported yet, so the jar is empty
and not loadable as a pack module (a pack module jar must name at least one module).
