# Iris default module

This project owns the built-in Iris feature and setting sources under
`src/main/java/art/arcane/iris/engine`. The files retain their existing packages, pack fields and
direct dependencies on Iris internals.

Ported features are IrisApi modules that every pack loads before its own: object placement
(`iris:prepare`, `iris:anchor`, `iris:validate`, `iris:bore`, `iris:blocks`, `iris:stilt`,
`iris:vacuum`, `iris:snow`) and `iris:perfection`. Their stage constants and the output types packs
may read live in `src/main/java/art/arcane/iris/builtin`, the package Iris exposes to pack modules;
their stage bodies live beside the features they replace.

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
