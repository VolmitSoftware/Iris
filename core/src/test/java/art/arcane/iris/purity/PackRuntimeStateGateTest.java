package art.arcane.iris.purity;

import art.arcane.iris.pack.schema.annotation.Snippet;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pack objects are read-only configuration once loaded. Everything derived for an engine (seeded noise, samplers,
 * per-chunk memos) lives in runtime objects the engine owns, so a closed engine can never be pinned by a pack object
 * that outlives it. This gate is the ledger of every non-serialized field on a pack type: each one must be listed as
 * {@link Kind#CONFIG} (derived from the pack's own content only) or still be pending migration.
 */
public class PackRuntimeStateGateTest {
    private static final String IRIS_PACKAGE = "art/arcane/iris";
    private static final String REGISTRANT = "art/arcane/iris/pack/loading/IrisRegistrant";
    private static final String SNIPPET_DESCRIPTOR = "Lart/arcane/iris/pack/schema/annotation/Snippet;";
    private static final List<String> GSON_SKIPPED_TYPES = List.of(
            "Lart/arcane/volmlib/util/cache/AtomicCache;",
            "Lart/arcane/volmlib/util/scheduling/ChronoLatch;");
    private static final List<String> ADDITIONAL_PACK_TYPES = List.of(
            "art.arcane.iris.generation.block.TileData",
            "art.arcane.iris.generation.block.LegacyTileData",
            "art.arcane.iris.world.loot.IrisVanillaLootTable");
    private static final List<String> ENGINE_TYPES = List.of(
            "Lart/arcane/iris/generation/runtime/Engine;",
            "Lart/arcane/iris/generation/runtime/IrisEngine;",
            "Lart/arcane/iris/generation/runtime/IrisComplex;");
    private static final String THREAD_LOCAL = "Ljava/lang/ThreadLocal;";
    private static final List<String> ENGINE_LIFETIME_TYPES = List.of(
            "Lart/arcane/iris/generation/runtime/Engine;",
            "Lart/arcane/iris/generation/runtime/IrisEngine;",
            "Lart/arcane/iris/generation/runtime/IrisComplex;",
            "Lart/arcane/iris/pack/loading/IrisData;",
            "Lart/arcane/iris/generation/runtime/ThreadSlots;",
            "Lart/arcane/iris/world/IrisWorld;",
            "Lart/arcane/volmlib/nativelib/terrain/NativeWorld;",
            "Lorg/bukkit/World;");
    private static final Pattern REFERENCED_TYPE = Pattern.compile("L(art/arcane/iris/[A-Za-z0-9_/$]+)[;<]");

    enum Kind {
        CONFIG,
        RUNTIME,
        THREAD,
        SCRATCH,
        STATIC,
        UNRELATED
    }

    private record Entry(String owner, String field, Kind kind, String reason) {
        private String key() {
            return owner + "#" + field;
        }
    }

    private static Entry config(String owner, String field, String reason) {
        return new Entry(owner, field, Kind.CONFIG, reason);
    }

    private static final List<Entry> CONFIG_FIELDS = List.of(
            config("art.arcane.iris.generation.biome.IrisBiome", "biomeScatterResolved", "platform biome resolved from the biome's own key"),
            config("art.arcane.iris.generation.biome.IrisBiome", "biomeSkyScatterResolved", "platform biome resolved from the biome's own key"),
            config("art.arcane.iris.generation.biome.IrisBiome", "cacheColor", "preview color computed from the biome's own content"),
            config("art.arcane.iris.generation.biome.IrisBiome", "cacheColorDecoratorLoad", "preview color computed from the biome's own content"),
            config("art.arcane.iris.generation.biome.IrisBiome", "cacheColorDepositLoad", "preview color computed from the biome's own content"),
            config("art.arcane.iris.generation.biome.IrisBiome", "cacheColorLayerLoad", "preview color computed from the biome's own content"),
            config("art.arcane.iris.generation.biome.IrisBiome", "cacheColorObjectDensity", "preview color computed from the biome's own content"),
            config("art.arcane.iris.generation.biome.IrisBiome", "carveObjectsCache", "filters the biome's own object placements"),
            config("art.arcane.iris.generation.biome.IrisBiome", "decoratorBuckets", "groups the biome's own decorators by part"),
            config("art.arcane.iris.generation.biome.IrisBiome", "derivativeResolved", "platform biome resolved from the biome's own key"),
            config("art.arcane.iris.generation.biome.IrisBiome", "genCache", "generator links indexed by key from the biome's own list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "inferredType", "biome context inferred while loading the pack's regions"),
            config("art.arcane.iris.generation.biome.IrisBiome", "inferredVariants", "biome context inferred while loading the pack's regions"),
            config("art.arcane.iris.generation.biome.IrisBiome", "maxHeight", "sums the biome's own generator link and object heights"),
            config("art.arcane.iris.generation.biome.IrisBiome", "maxWithObjectHeight", "sums the biome's own generator link and object heights"),
            config("art.arcane.iris.generation.biome.IrisBiome", "realCarveBiome", "biome references resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.biome.IrisBiome", "realChildren", "biome references resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.biome.IrisBiome", "surfaceObjectsCache", "filters the biome's own object placements"),
            config("art.arcane.iris.generation.biome.IrisBiome", "surfaceOreBandsCache", "filters and bands the biome's own ore list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "surfaceOreCache", "filters and bands the biome's own ore list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "surfaceOreReplaceableBlockData", "parsed block keys from the biome's own list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "undergroundOreBandsCache", "filters and bands the biome's own ore list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "undergroundOreCache", "filters and bands the biome's own ore list"),
            config("art.arcane.iris.generation.biome.IrisBiome", "vanillaDerivativeResolved", "platform biome resolved from the biome's own key"),
            config("art.arcane.iris.generation.biome.IrisBiomeCustomParticle", "particleResolved", "platform particle resolved from its own key"),
            config("art.arcane.iris.generation.biome.IrisBiomeCustomSpawn", "typeResolved", "platform entity type resolved from its own key"),
            config("art.arcane.iris.generation.biome.IrisBiomeGeneratorLink", "gen", "generator resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.biome.IrisBiomePaletteLayer", "blockData", "block states parsed from the layer's own palette"),
            config("art.arcane.iris.generation.biome.IrisFloatingChildBiomes", "resolvedBiome", "child biome resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.biome.IrisFloatingChildBiomes", "shrinkScaleCache", "object scale built from the child's own shrink factor"),
            config("art.arcane.iris.generation.block.IrisBlockData", "blockdata", "block state parsed from its own key through the pack's IrisData"),
            config("art.arcane.iris.generation.block.IrisBlockData", "realProperties", "block state parsed from its own key through the pack's IrisData"),
            config("art.arcane.iris.generation.block.VectorMap", "orderedSnapshot", "ordered view of the map's own entries"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "blacklistArray", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "blockData", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "blockDataArray", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "blockDataTops", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "blockDataTopsArray", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDecorator", "whitelistArray", "block states parsed from the decorator's own palettes"),
            config("art.arcane.iris.generation.decoration.IrisDepositGenerator", "blockData", "block states parsed from the deposit's own palette"),
            config("art.arcane.iris.generation.decoration.IrisDepositGenerator", "ore", "block states parsed from the deposit's own palette"),
            config("art.arcane.iris.generation.decoration.IrisDepositGenerator", "replaceableBlockData", "block states parsed from the deposit's own palette"),
            config("art.arcane.iris.generation.decoration.IrisDepositGenerator", "surfaceReplaceableBlockData", "block states parsed from the deposit's own palette"),
            config("art.arcane.iris.generation.decoration.IrisDepositVariant", "resolved", "block states parsed from the variant's own palette"),
            config("art.arcane.iris.generation.decoration.coral.IrisCoral", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.decoration.crystal.IrisCrystal", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.decoration.formation.IrisFormation", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.decoration.fungus.IrisFungus", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.decoration.ruin.IrisRuin", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.decoration.tree.IrisProceduralTree", "variantCache", "variants baked from the decorator's own seed and shape settings"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "carvingEntryIndex", "carving entries indexed from the dimension's own list"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "cosr", "trigonometry of the dimension's own angle"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "rad", "trigonometry of the dimension's own angle"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "sinr", "trigonometry of the dimension's own angle"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "staticObjectLayer", "static object layer compiled from the dimension's own placements"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "surfaceOreBandsCache", "filters and bands the dimension's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "surfaceOreCache", "filters and bands the dimension's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "undergroundOreBandsCache", "filters and bands the dimension's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "undergroundOreCache", "filters and bands the dimension's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisDimension", "undergroundSpawnTables", "spawner references resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry", "realBiome", "carving biome resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisMaterialPalette", "blockData", "block states parsed from the palette's own entries"),
            config("art.arcane.iris.generation.terrain.IrisMaterialPalette", "fallback", "constant default stone entry of the palette"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "cacheColor", "preview color computed from the region's own content"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "cacheRidge", "biome keys collected from the region's own lists"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "cacheSpot", "biome keys collected from the region's own lists"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "carveObjectsCache", "filters the region's own object placements"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "realCaveBiomes", "biome pools resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "realLandBiomes", "biome pools resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "realSeaBiomes", "biome pools resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "realShoreBiomes", "biome pools resolved through the pack's IrisData"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "shoreHeightGenerator", "noise seeded only from the region's own name and biome settings"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "surfaceObjectsCache", "filters the region's own object placements"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "surfaceOreBandsCache", "filters and bands the region's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "surfaceOreCache", "filters and bands the region's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "undergroundOreBandsCache", "filters and bands the region's own ore list"),
            config("art.arcane.iris.generation.terrain.IrisRegion", "undergroundOreCache", "filters and bands the region's own ore list"),
            config("art.arcane.iris.pack.loading.IrisRegistrant", "compat", "compatibility status of the object's own content"),
            config("art.arcane.iris.pack.loading.IrisRegistrant", "loadFile", "the key and file this object was loaded from"),
            config("art.arcane.iris.pack.loading.IrisRegistrant", "loadKey", "the key and file this object was loaded from"),
            config("art.arcane.iris.pack.loading.IrisRegistrant", "loader", "the IrisData that loaded this object"),
            config("art.arcane.iris.pack.value.IrisColor", "color", "color parsed from its own hex or channels"),
            config("art.arcane.iris.structure.object.IrisAxisRotationClamp", "forceLock", "lock flag derived from the clamp's own settings"),
            config("art.arcane.iris.structure.object.IrisObject", "aabb", "geometry derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "center", "geometry derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "floatingFootprint", "geometry derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "placementBakes", "rotated block layouts keyed only by rotation and translation"),
            config("art.arcane.iris.structure.object.IrisObject", "placementLayout", "rotated block layouts keyed only by rotation and translation"),
            config("art.arcane.iris.structure.object.IrisObject", "readLock", "guards the object's own block data"),
            config("art.arcane.iris.structure.object.IrisObject", "shrinkOffset", "geometry derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "smartBoreVariant", "shape variant derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "smartBored", "shape variant derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "surfaceSupportOffsets", "geometry derived from the object's own blocks"),
            config("art.arcane.iris.structure.object.IrisObject", "treeGrounding", "grounding offsets of a tree object's own trunk"),
            config("art.arcane.iris.structure.object.IrisObject", "writeLock", "guards the object's own block data"),
            config("art.arcane.iris.structure.object.IrisObjectLoot", "filterCache", "block states parsed from its own filter list"),
            config("art.arcane.iris.structure.object.IrisObjectMarker", "findData", "block states parsed from its own marker list"),
            config("art.arcane.iris.structure.object.IrisObjectPlacement", "cache", "loot tables resolved through the pack's IrisData"),
            config("art.arcane.iris.structure.object.IrisObjectPlacement", "compatCache", "compatibility of the placement's own object keys"),
            config("art.arcane.iris.structure.object.IrisObjectPlacement", "compatPlaceCache", "compatibility of the placement's own object keys"),
            config("art.arcane.iris.structure.object.IrisObjectReplace", "findData", "block states parsed from its own lists"),
            config("art.arcane.iris.structure.object.IrisObjectReplace", "replaceData", "block states parsed from its own lists"),
            config("art.arcane.iris.structure.object.IrisObjectRotation", "stateRotations", "rotated block states keyed by state and rotation step"),
            config("art.arcane.iris.structure.object.IrisObjectVanillaLoot", "filterCache", "block states parsed from its own filter list"),
            config("art.arcane.iris.world.entity.IrisEffect", "particleEffectResolved", "platform types resolved from the effect's own keys"),
            config("art.arcane.iris.world.entity.IrisEffect", "pt", "platform types resolved from the effect's own keys"),
            config("art.arcane.iris.world.entity.IrisEffect", "soundResolved", "platform types resolved from the effect's own keys"),
            config("art.arcane.iris.world.entity.IrisEntitySpawn", "ent", "entity resolved through the pack's IrisData"),
            config("art.arcane.iris.world.entity.IrisPotionEffect", "pt", "potion type resolved from its own key"),
            config("art.arcane.iris.world.loot.IrisBlockDrops", "data", "block data parsed from its own list"),
            config("art.arcane.iris.world.loot.IrisLoot", "dyeColorResolved", "dye color parsed from its own key"),
            config("art.arcane.iris.world.loot.IrisLootReference", "tt", "loot tables resolved through the pack's IrisData"),
            config("art.arcane.iris.world.loot.IrisVanillaLootTable", "resolvedKey", "namespaced key parsed from its own string")
    );

    @Test
    public void everyNonSerializedPackFieldIsInTheLedger() throws Exception {
        Map<String, Entry> ledger = ledger();
        PackTypes types = PackTypes.scan();
        List<String> unlisted = new ArrayList<>();
        for (String type : types.packTypes()) {
            for (ClassFileFacts.DeclaredField field : types.facts(type).fields()) {
                if (!isNonSerializedState(field)) {
                    continue;
                }
                String key = type + "#" + field.name();
                if (!ledger.containsKey(key)) {
                    unlisted.add(key + " : " + (field.signature() == null ? field.descriptor() : field.signature()));
                }
            }
        }
        assertTrue("Non-serialized pack fields missing from the ledger. Derived engine state belongs in a runtime "
                + "object the engine owns; list only content-derived values as CONFIG:" + join(unlisted), unlisted.isEmpty());
    }

    @Test
    public void everyThreadLocalAndEngineHoldingStaticIsInTheLedger() throws Exception {
        PackTypes types = PackTypes.scan();
        Map<String, Entry> ledger = holderLedger();
        List<String> unlisted = new ArrayList<>();
        for (Map.Entry<String, ClassFileFacts> type : types.all().entrySet()) {
            for (ClassFileFacts.DeclaredField field : type.getValue().fields()) {
                if (!isThreadLocal(field) && !(field.isStatic() && holdsEngineLifetimeState(field))) {
                    continue;
                }
                String key = type.getKey() + "#" + field.name();
                if (!ledger.containsKey(key)) {
                    unlisted.add(key + " : " + (field.signature() == null ? field.descriptor() : field.signature()));
                }
            }
        }
        assertTrue("Thread-locals and static holders missing from the ledger:" + join(unlisted), unlisted.isEmpty());
    }

    @Test
    public void everyLedgerEntryStillExists() throws Exception {
        PackTypes types = PackTypes.scan();
        List<String> stale = new ArrayList<>();
        for (List<Entry> list : List.of(CONFIG_FIELDS, SETTLED_HOLDERS, ENGINE_PARAMETERS)) {
            for (Entry entry : list) {
                if (!types.declares(entry.owner(), entry.field())) {
                    stale.add(entry.key());
                }
            }
        }
        assertTrue("Ledger entries for fields that no longer exist; remove them:" + join(stale), stale.isEmpty());
    }

    @Test
    public void configAllowlistHoldsOnlyConfigEntries() {
        for (Entry entry : CONFIG_FIELDS) {
            assertTrue(entry.key() + " is not CONFIG", entry.kind() == Kind.CONFIG);
            assertTrue(entry.key() + " needs a reason", !entry.reason().isBlank());
        }
    }

    @Test
    public void packTypesHoldNoEngineReference() throws Exception {
        PackTypes types = PackTypes.scan();
        Set<String> exempt = keys(ENGINE_PARAMETERS);
        List<String> failures = new ArrayList<>();
        for (String type : types.packTypesWithNested()) {
            for (ClassFileFacts.DeclaredField field : types.facts(type).fields()) {
                if (exempt.contains(type + "#" + field.name())) {
                    continue;
                }
                for (String engineType : ENGINE_TYPES) {
                    if (mentions(field, engineType)) {
                        failures.add(type + "#" + field.name() + " : " + engineType);
                    }
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("Pack types must not hold an engine; move the derived value into an engine-owned runtime object:"
                    + join(failures));
        }
    }

    @Test
    public void scanFindsThePackTypes() throws Exception {
        PackTypes types = PackTypes.scan();
        assertTrue("expected the scan to find the biome type", types.packTypes().contains("art.arcane.iris.generation.biome.IrisBiome"));
        assertTrue("expected the scan to follow field types to nested config", types.packTypes().contains("art.arcane.iris.generation.noise.IrisGeneratorStyle"));
        assertTrue("expected more than 100 pack types, found " + types.packTypes().size(), types.packTypes().size() > 100);
    }

    private static boolean isThreadLocal(ClassFileFacts.DeclaredField field) {
        return field.descriptor().equals(THREAD_LOCAL);
    }

    private static boolean holdsEngineLifetimeState(ClassFileFacts.DeclaredField field) {
        for (String type : ENGINE_LIFETIME_TYPES) {
            if (mentions(field, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNonSerializedState(ClassFileFacts.DeclaredField field) {
        if (field.isStatic()) {
            return false;
        }
        return field.isTransient() || GSON_SKIPPED_TYPES.contains(field.descriptor());
    }

    private static boolean mentions(ClassFileFacts.DeclaredField field, String descriptor) {
        String bare = descriptor.substring(0, descriptor.length() - 1);
        return field.descriptor().contains(descriptor)
                || (field.signature() != null && (field.signature().contains(descriptor) || field.signature().contains(bare + "<")));
    }

    private static final List<Entry> ENGINE_PARAMETERS = List.of(
            holder("art.arcane.iris.generation.runtime.IrisEngineStreamType", "getter", Kind.CONFIG,
                    "a function that takes the engine as its argument, stores none"),
            holder("art.arcane.iris.generation.runtime.IrisEngineValueType", "getter", Kind.CONFIG,
                    "a function that takes the engine as its argument, stores none"),
            holder("art.arcane.iris.generation.terrain.IrisDimensionModeType", "factory", Kind.CONFIG,
                    "a function that takes the engine as its argument, stores none"),
            holder("art.arcane.iris.world.entity.IrisEntity$BukkitOps$1", "val$gen", Kind.UNRELATED,
                    "loot table adapter attached to a spawned entity, never stored on the pack object")
    );

    private static final List<Entry> SETTLED_HOLDERS = List.of(
            holder("art.arcane.iris.generation.biome.FloatingIslandSample", "LAST_DENSITY", Kind.SCRATCH, "the last cluster diagnostic doubles, no engine or pack reference"),
            holder("art.arcane.iris.generation.biome.FloatingIslandSample", "LAST_REJECT", Kind.SCRATCH, "the last reject code, no engine or pack reference"),
            holder("art.arcane.iris.generation.noise.ExpressionRuntime", "EVALUATION_SCRATCH", Kind.SCRATCH, "per-depth double argument arrays, no engine or pack reference"),
            holder("art.arcane.iris.generation.bake.ChunkBakeCache", "SPARSE", Kind.SCRATCH, "a per-thread sparse-bake depth counter, no engine or pack reference"),
            holder("art.arcane.iris.generation.context.IrisContext", "OVERFLOW", Kind.SCRATCH, "a scoped context binding removed when the thread's outermost scope closes"),
            holder("art.arcane.iris.generation.decoration.DecoratorCore", "SCRATCH_OPTS", Kind.SCRATCH, "placement flags; the mantle reference is cleared when each placement returns"),
            holder("art.arcane.iris.generation.decoration.IrisDepositGenerator", "FOOTPRINT_CELLS", Kind.SCRATCH, "primitive footprint cell buffers, no engine or pack reference"),
            holder("art.arcane.iris.generation.mantle.CarveOrphanSweep", "SCRATCH", Kind.SCRATCH, "primitive sweep buffers, no engine or pack reference"),
            holder("art.arcane.iris.generation.mantle.CaveLatticeBatch", "CURRENT", Kind.SCRATCH, "primitive noise and lattice buffers, no engine or pack reference"),
            holder("art.arcane.iris.generation.mantle.MantleCarvingComponent", "BLEND_SCRATCH", Kind.SCRATCH, "profile, biome and region slots cleared by release when each chunk returns"),
            holder("art.arcane.iris.generation.noise.IrisNoiseGenerator", "FRACTURE_SAMPLES", Kind.SCRATCH, "fracture node and generator slots cleared when each sample returns"),
            holder("art.arcane.iris.generation.runtime.IrisComplex", "BATCH_SCRATCH", Kind.SCRATCH, "primitive noise batch buffers, no engine or pack reference"),
            holder("art.arcane.iris.generation.runtime.PaintedColumnRuns", "SCRATCH", Kind.SCRATCH, "primitive run buffers, no engine or pack reference"),
            holder("art.arcane.iris.generation.stage.IrisCarveModifier", "SCRATCH", Kind.SCRATCH, "carve masks, cavern and biome memos released when each chunk is carved"),
            holder("art.arcane.iris.generation.stream.ProvisionalSampling", "TRACKERS", Kind.SCRATCH, "a per-thread provisional revision counter, no engine or pack reference"),
            holder("art.arcane.iris.generation.terrain.Terrain3DFragmentFilter", "TRAVERSALS", Kind.SCRATCH, "traversal buffers whose column slots are cleared after each classification"),
            holder("art.arcane.iris.structure.object.IrisSurfaceSupport", "STENCIL", Kind.SCRATCH, "primitive stencil buffers, no engine or pack reference"),
            holder("art.arcane.iris.world.history.NativeTerrainReceipt", "DEFLATERS", Kind.SCRATCH, "a reusable zlib deflater, no engine or pack reference"),
            holder("art.arcane.iris.world.history.SavedBiomeStore", "DEFLATERS", Kind.SCRATCH, "a reusable zlib deflater, no engine or pack reference"),
            holder("art.arcane.iris.world.storage.matter.IrisMatterContext", "DATA", Kind.SCRATCH, "a scoped pack data binding removed when the thread's outermost scope closes"),
            holder("art.arcane.iris.pack.PackValidationIo", "ACTIVE_RUN", Kind.SCRATCH, "parsed pack files of one validation run, removed when the outermost run closes"),
            holder("art.arcane.iris.world.tree.TreeSVC", "REENTRANT", Kind.SCRATCH, "a reentrancy flag, no engine or pack reference"),
            holder("art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore", "STATES", Kind.STATIC, "live-engine registry; a closed engine releases its store in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex", "INDEXES", Kind.STATIC, "live-engine registry; a closed engine uninstalls its index in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.structure.placement.IrisStructureLocator", "INDEX_CACHE", Kind.STATIC, "live-engine registry; a closed engine invalidates its index in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.structure.placement.IrisStructureLocator", "LOCATABLE_INDEX_CACHE", Kind.STATIC, "live-engine registry; a closed engine invalidates its index in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.structure.placement.StructurePlacementScope", "SCOPE_INDEXES", Kind.STATIC, "live-engine registry; a closed engine invalidates its index in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.structure.placement.StructureReachability", "REACHABLE_CACHE", Kind.STATIC, "live-engine registry; a closed engine invalidates its keys in the shutdown sequence (EngineShutdownDrainTest)"),
            holder("art.arcane.iris.generation.runtime.EngineTickRegistry", "TICK_ENGINES", Kind.STATIC, "live-engine registry; every engine unregisters itself when it closes (EngineReleaseProbeTest)"),
            holder("art.arcane.iris.pack.loading.IrisData", "ENGINE_HOLDERS", Kind.STATIC, "pack data with live engines; an engine unregisters from its pack data when it closes (EngineReleaseProbeTest)"),
            holder("art.arcane.iris.pack.loading.IrisData", "dataLoaders", Kind.STATIC, "pack data by folder; pack data drops each engine that closes (EngineReleaseProbeTest)"),
            holder("art.arcane.iris.studio.object.ObjectStudioActivation", "SOURCES", Kind.STATIC, "studio object source packs, removed when the studio pack deactivates; pack data holds no closed engine"),
            holder("art.arcane.iris.generation.runtime.EnginePanic$Diagnostics", "current", Kind.UNRELATED, "panic diagnostics strings for crash reports"),
            holder("art.arcane.iris.pack.IrisPackBenchmarking", "instance", Kind.UNRELATED, "benchmark command session")
    );

    private static Entry holder(String owner, String field, Kind kind, String reason) {
        return new Entry(owner, field, kind, reason);
    }

    private static Set<String> keys(List<Entry> entries) {
        Set<String> keys = new TreeSet<>();
        for (Entry entry : entries) {
            keys.add(entry.key());
        }
        return keys;
    }

    private static Map<String, Entry> holderLedger() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : SETTLED_HOLDERS) {
            if (entries.put(entry.key(), entry) != null) {
                fail("Duplicate holder ledger entry " + entry.key());
            }
        }
        return entries;
    }

    private static Map<String, Entry> ledger() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : CONFIG_FIELDS) {
            if (entries.put(entry.key(), entry) != null) {
                fail("Duplicate ledger entry " + entry.key());
            }
        }
        return entries;
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append("\n  ").append(line);
        }
        return out.toString();
    }

    private record PackTypes(Map<String, ClassFileFacts> all, Set<String> packTypes) {
        private static PackTypes scan() throws Exception {
            Map<String, ClassFileFacts> all = new TreeMap<>();
            ClassLoader loader = PackRuntimeStateGateTest.class.getClassLoader();
            for (String name : classNamesUnder(IRIS_PACKAGE)) {
                byte[] bytes = BukkitHidingClassLoader.readClassBytes(loader, name);
                if (bytes != null) {
                    all.put(name, ClassFileFacts.read(bytes));
                }
            }
            Set<String> roots = new TreeSet<>(ADDITIONAL_PACK_TYPES);
            for (Map.Entry<String, ClassFileFacts> entry : all.entrySet()) {
                if (entry.getValue().hasClassAnnotation(SNIPPET_DESCRIPTOR) || extendsRegistrant(entry.getValue(), all)) {
                    roots.add(entry.getKey());
                }
            }
            Set<String> packTypes = new TreeSet<>();
            List<String> queue = new ArrayList<>(roots);
            while (!queue.isEmpty()) {
                String type = queue.removeLast();
                ClassFileFacts facts = all.get(type);
                if (facts == null || !packTypes.add(type)) {
                    continue;
                }
                if (facts.superName() != null) {
                    queue.add(facts.superName().replace('/', '.'));
                }
                for (ClassFileFacts.DeclaredField field : facts.fields()) {
                    if (field.isStatic() || field.isTransient() || GSON_SKIPPED_TYPES.contains(field.descriptor())) {
                        continue;
                    }
                    queue.addAll(referencedTypes(field));
                }
            }
            return new PackTypes(all, packTypes);
        }

        private ClassFileFacts facts(String type) {
            return all.get(type);
        }

        private boolean declares(String type, String fieldName) {
            ClassFileFacts facts = all.get(type);
            if (facts == null) {
                return false;
            }
            for (ClassFileFacts.DeclaredField field : facts.fields()) {
                if (field.name().equals(fieldName)) {
                    return true;
                }
            }
            return false;
        }

        private Set<String> packTypesWithNested() {
            Set<String> types = new TreeSet<>(packTypes);
            for (String candidate : all.keySet()) {
                int nested = candidate.indexOf('$');
                if (nested > 0 && packTypes.contains(candidate.substring(0, nested))) {
                    types.add(candidate);
                }
            }
            return types;
        }

        private static boolean extendsRegistrant(ClassFileFacts facts, Map<String, ClassFileFacts> all) {
            ClassFileFacts current = facts;
            while (current != null && current.superName() != null) {
                if (current.superName().equals(REGISTRANT)) {
                    return true;
                }
                current = all.get(current.superName().replace('/', '.'));
            }
            return false;
        }

        private static List<String> referencedTypes(ClassFileFacts.DeclaredField field) {
            List<String> types = new ArrayList<>();
            collect(field.descriptor(), types);
            if (field.signature() != null) {
                collect(field.signature(), types);
            }
            return types;
        }

        private static void collect(String text, List<String> sink) {
            Matcher matcher = REFERENCED_TYPE.matcher(text);
            while (matcher.find()) {
                sink.add(matcher.group(1).replace('/', '.'));
            }
        }

        private static List<String> classNamesUnder(String packageInternalName) throws Exception {
            URL location = Snippet.class.getProtectionDomain().getCodeSource().getLocation();
            Path root = Paths.get(location.toURI());
            List<String> names = new ArrayList<>();
            if (!Files.isDirectory(root)) {
                throw new IOException("Pack type scan expects a class directory, found " + root);
            }
            try (Stream<Path> walk = Files.walk(root.resolve(packageInternalName))) {
                walk.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                    String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                    names.add(relative.substring(0, relative.length() - ".class".length()).replace('/', '.'));
                });
            }
            return names;
        }
    }
}
