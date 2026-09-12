package art.arcane.iris.nativegen;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipBundle;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.volmlib.util.collection.KList;
import com.github.benmanes.caffeine.cache.Cache;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NativeStructureReferenceRepairTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void persistedEnvelopeRepairsReloadedMonumentReferenceOutsideLiveBounds() {
        long seed = 119723L;
        ChunkPos origin = new ChunkPos(-3, 6);
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        StructureStart generated = monumentStart(structure, origin, seed);
        NativeStructureVerticalPlacer.alignOceanMonumentToSeaLevel(
                generated, 0, 80, -64, 320);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);
        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(
                generated,
                structure,
                0,
                terrain);
        NativeStructureOwnershipRecord ownership = NativeStructureOwnershipFingerprint.capture(
                "minecraft:monument",
                wrapped,
                plan(origin, NativeStructureReferenceEnvelope.contentBounds(wrapped).minY()),
                NativeStructureReferenceEnvelope.referenceBounds(wrapped, structure, terrain));
        PiecesContainer regeneratedPieces = OceanMonumentStructure.regeneratePiecesAfterLoad(
                origin, seed, new PiecesContainer(wrapped.getPieces()));
        StructureStart reloaded = new StructureStart(structure, origin, 0, regeneratedPieces);
        ChunkPos target = outsideLiveBounds(ownership, reloaded);

        assertNotNull(target);
        assertTrue(NativeStructureReferenceRepair.requiresReference(
                target, "minecraft:monument", reloaded, ownership));
        assertFalse(NativeStructureReferenceRepair.requiresReference(
                target, "minecraft:monument", reloaded, null));
    }

    @Test
    public void deniedNaturalStartsCannotEnterCollisionArbitration() {
        IrisNativeStructureDecision enabled = new IrisNativeStructureDecision(
                NativeStructureGenerationStatus.GENERATE_NATIVE,
                0, null, false, null, new IrisStructureTerrain());
        IrisNativeStructureDecision disabled = new IrisNativeStructureDecision(
                NativeStructureGenerationStatus.DISABLED_BY_PACK,
                0, null, false, null, new IrisStructureTerrain());
        IrisNativeStructureDecision replaced = new IrisNativeStructureDecision(
                NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                0, null, false, null, new IrisStructureTerrain());

        assertTrue(NativeStructureReferenceRepair.naturalDecisionAllows(enabled));
        assertFalse(NativeStructureReferenceRepair.naturalDecisionAllows(disabled));
        assertFalse(NativeStructureReferenceRepair.naturalDecisionAllows(replaced));
    }

    @Test
    public void persistedManualOwnershipSurvivesDisabledNaturalPolicyDuringReferenceScans()
            throws Exception {
        String structureKey = "minecraft:monument";
        long seed = 582119L;
        ChunkPos origin = new ChunkPos(2, -4);
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        StructureStart generated = monumentStart(structure, origin, seed);
        NativeStructureVerticalPlacer.alignOceanMonumentToSeaLevel(
                generated, 0, 80, -64, 320);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);
        StructureStart start = NativeStructureReferenceEnvelope.wrap(
                generated, structure, 0, terrain);
        NativeStructureOwnershipRecord ownership = NativeStructureOwnershipFingerprint.capture(
                structureKey,
                start,
                plan(origin, NativeStructureReferenceEnvelope.contentBounds(start).minY()),
                NativeStructureReferenceEnvelope.referenceBounds(start, structure, terrain));
        IrisDimension dimension = new IrisDimension();
        dimension.getImportedStructures().getDisabled().add(structureKey);
        IrisData data = allocateWithoutConstructor(IrisData.class);
        Engine engine = engine(dimension, data);
        IrisNativeStructureDecision currentDecision = NativeStructureGenerationPolicy.resolve(
                engine, structureKey, false);

        assertFalse(currentDecision.generate());
        assertFalse(NativeStructureReferenceRepair.naturalDecisionAllows(currentDecision));

        ProtoChunk originChunk = emptyChunk(origin);
        originChunk.setStartForStructure(structure, start);
        ProtoChunk emptyScannedChunk = emptyChunk(new ChunkPos(Integer.MIN_VALUE, Integer.MIN_VALUE));
        Registry<Structure> registry = structureRegistry(structure, structureKey);
        ServerLevel serverLevel = allocateWithoutConstructor(ServerLevel.class);
        StructureManager structureManager = new StructureManager(null, null, null);
        ChunkPos coveredTarget = new ChunkPos(
                ownership.referenceMinChunkX(), ownership.referenceMinChunkZ());
        ChunkPos uncoveredTarget = outsideReferenceEnvelopeWithinScan(ownership);

        assertNotNull(uncoveredTarget);

        installOwnership(engine, ownership);
        try {
            WorldGenLevel level = worldGenLevel(
                    registry, serverLevel, originChunk, emptyScannedChunk);
            ProtoChunk coveredChunk = emptyChunk(coveredTarget);
            NativeStructureReferenceRepair.createReferences(
                    engine, level, structureManager, coveredChunk);

            assertSame(start, originChunk.getStartForStructure(structure));
            assertTrue(originChunk.getStartForStructure(structure).isValid());
            assertTrue(coveredChunk.getReferencesForStructure(structure).contains(origin.pack()));

            ProtoChunk uncoveredChunk = emptyChunk(uncoveredTarget);
            NativeStructureReferenceRepair.createReferences(
                    engine, level, structureManager, uncoveredChunk);

            assertSame(start, originChunk.getStartForStructure(structure));
            assertTrue(originChunk.getStartForStructure(structure).isValid());
            assertFalse(uncoveredChunk.getReferencesForStructure(structure).contains(origin.pack()));
        } finally {
            NativeStructureOwnershipStore.close(engine);
        }
    }

    private static StructureStart monumentStart(OceanMonumentStructure structure,
                                                  ChunkPos origin, long seed) {
        WorldgenRandom random = new WorldgenRandom(
                new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setLargeFeatureSeed(seed, origin.x(), origin.z());
        Direction orientation = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        OceanMonumentPieces.MonumentBuilding building = new OceanMonumentPieces.MonumentBuilding(
                random,
                origin.getMinBlockX() - 29,
                origin.getMinBlockZ() - 29,
                orientation
        );
        return new StructureStart(
                structure,
                origin,
                0,
                new PiecesContainer(List.of(building))
        );
    }

    private static NativeStructureStartPlan plan(ChunkPos origin, int baseY) {
        IrisNativeStructure source = new IrisNativeStructure()
                .setStructure("minecraft:monument")
                .setWeight(1);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId("monument-reference-test")
                .setNativeStructures(new KList<IrisNativeStructure>().qadd(source));
        return new NativeStructureStartPlan(
                placement,
                source,
                origin.x(),
                origin.z(),
                baseY
        );
    }

    private static ChunkPos outsideLiveBounds(NativeStructureOwnershipRecord ownership,
                                              StructureStart start) {
        for (int chunkX = ownership.referenceMinChunkX();
             chunkX <= ownership.referenceMaxChunkX(); chunkX++) {
            for (int chunkZ = ownership.referenceMinChunkZ();
                 chunkZ <= ownership.referenceMaxChunkZ(); chunkZ++) {
                ChunkPos candidate = new ChunkPos(chunkX, chunkZ);
                if (!start.getBoundingBox().intersects(
                        candidate.getMinBlockX(), candidate.getMinBlockZ(),
                        candidate.getMaxBlockX(), candidate.getMaxBlockZ())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static ChunkPos outsideReferenceEnvelopeWithinScan(
            NativeStructureOwnershipRecord ownership) {
        int originX = ownership.originChunkX();
        int originZ = ownership.originChunkZ();
        List<ChunkPos> candidates = List.of(
                new ChunkPos(ownership.referenceMinChunkX() - 1, originZ),
                new ChunkPos(ownership.referenceMaxChunkX() + 1, originZ),
                new ChunkPos(originX, ownership.referenceMinChunkZ() - 1),
                new ChunkPos(originX, ownership.referenceMaxChunkZ() + 1));
        for (ChunkPos candidate : candidates) {
            if (!ownership.covers(candidate.x(), candidate.z())
                    && Math.abs(candidate.x() - originX)
                    <= NativeStructureOwnershipRecord.MAX_REFERENCE_DISTANCE_CHUNKS
                    && Math.abs(candidate.z() - originZ)
                    <= NativeStructureOwnershipRecord.MAX_REFERENCE_DISTANCE_CHUNKS) {
                return candidate;
            }
        }
        return null;
    }

    private static ProtoChunk emptyChunk(ChunkPos position) {
        return new ProtoChunk(
                position,
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(0, 0),
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    private static Registry<Structure> structureRegistry(
            Structure structure, String structureKey) {
        Identifier identifier = Identifier.parse(structureKey);
        InvocationHandler handler = (Object proxy, Method method, Object[] arguments) -> {
            if (method.getName().equals("getKey")) {
                return arguments != null && arguments.length == 1
                        && arguments[0] == structure ? identifier : null;
            }
            return defaultProxyValue(proxy, method, arguments);
        };
        return (Registry<Structure>) Proxy.newProxyInstance(
                Registry.class.getClassLoader(),
                new Class<?>[]{Registry.class},
                handler);
    }

    private static WorldGenLevel worldGenLevel(
            Registry<Structure> registry,
            ServerLevel serverLevel,
            ChunkAccess originChunk,
            ChunkAccess emptyChunk) {
        RegistryAccess registryAccess = registryAccess(registry);
        ChunkPos origin = originChunk.getPos();
        InvocationHandler handler = (Object proxy, Method method, Object[] arguments) -> {
            return switch (method.getName()) {
                case "registryAccess" -> registryAccess;
                case "getLevel" -> serverLevel;
                case "getChunk" -> arguments != null
                        && arguments.length >= 2
                        && ((Integer) arguments[0]) == origin.x()
                        && ((Integer) arguments[1]) == origin.z()
                        ? originChunk : emptyChunk;
                default -> defaultProxyValue(proxy, method, arguments);
            };
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                handler);
    }

    private static RegistryAccess registryAccess(Registry<Structure> registry) {
        InvocationHandler handler = (Object proxy, Method method, Object[] arguments) -> {
            return switch (method.getName()) {
                case "lookupOrThrow" -> registry;
                case "lookup" -> Optional.of(registry);
                case "registries" -> Stream.empty();
                default -> defaultProxyValue(proxy, method, arguments);
            };
        };
        return (RegistryAccess) Proxy.newProxyInstance(
                RegistryAccess.class.getClassLoader(),
                new Class<?>[]{RegistryAccess.class},
                handler);
    }

    private static Engine engine(IrisDimension dimension, IrisData data) throws Exception {
        TestEngine engine = allocateWithoutConstructor(TestEngine.class);
        engine.dimension = dimension;
        engine.data = data;
        return engine;
    }

    @SuppressWarnings("unchecked")
    private static void installOwnership(
            Engine engine, NativeStructureOwnershipRecord ownership) throws Exception {
        NativeStructureOwnershipBundle bundle =
                NativeStructureOwnershipBundle.empty().with(ownership);
        Class<?> storageType = Class.forName(
                "art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore$Storage");
        InvocationHandler storageHandler =
                (Object proxy, Method method, Object[] arguments) -> {
                    return switch (method.getName()) {
                        case "read" -> arguments != null
                                && arguments.length == 2
                                && ((Integer) arguments[0]) == ownership.originChunkX()
                                && ((Integer) arguments[1]) == ownership.originChunkZ()
                                ? bundle : null;
                        case "write", "remove" -> null;
                        default -> defaultProxyValue(proxy, method, arguments);
                    };
                };
        Object storage = Proxy.newProxyInstance(
                storageType.getClassLoader(),
                new Class<?>[]{storageType},
                storageHandler);
        Class<?> stateType = Class.forName(
                "art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore$State");
        Constructor<?> constructor = stateType.getDeclaredConstructor(Engine.class, storageType);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(engine, storage);
        Field statesField = NativeStructureOwnershipStore.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Cache<Engine, Object> states = (Cache<Engine, Object>) statesField.get(null);
        states.put(engine, state);
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeType.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
        return type.cast(allocateInstance.invoke(unsafe, type));
    }

    private static Object defaultProxyValue(
            Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> arguments != null && arguments.length == 1
                    && proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
            default -> primitiveDefault(method.getReturnType());
        };
    }

    private static Object primitiveDefault(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private static final class TestEngine extends IrisEngine {
        private IrisDimension dimension;
        private IrisData data;

        private TestEngine() {
            super(null, InitializationMode.RUNTIME);
        }

        @Override
        public IrisDimension getDimension() {
            return dimension;
        }

        @Override
        public IrisData getData() {
            return data;
        }

        @Override
        public GenerationHistoryRuntimeRouter.CoordinateScope openGenerationHistoryCoordinateScope(
                int blockX,
                int blockZ
        ) {
            return null;
        }

        @Override
        public boolean isClosing() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
