package art.arcane.iris.nativegen;

import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NativeStructureOwnershipFingerprintTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void monumentFingerprintSurvivesSourceYRegenerationWithoutSyntheticPieces() {
        long seed = 8372619L;
        ChunkPos origin = new ChunkPos(9, -4);
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        StructureStart generated = monumentStart(structure, origin, seed);
        NativeStructureVerticalPlacer.alignOceanMonumentToSeaLevel(
                generated, 0, 80, -64, 320);
        int alignedLocatorY = NativeStructureOwnershipFingerprint.locatorY(generated);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);
        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(
                generated,
                structure,
                0,
                terrain);
        String beforeReload = NativeStructureOwnershipFingerprint.fingerprint(
                "minecraft:monument", wrapped);

        ListTag serializedPieces = (ListTag) new PiecesContainer(wrapped.getPieces()).save(null);
        PiecesContainer loadedPieces = PiecesContainer.load(serializedPieces, null);
        assertEquals(1, loadedPieces.pieces().size());
        assertTrue(loadedPieces.pieces().getFirst()
                instanceof OceanMonumentPieces.MonumentBuilding);
        PiecesContainer regeneratedPieces = OceanMonumentStructure.regeneratePiecesAfterLoad(
                origin, seed, loadedPieces);
        StructureStart reloaded = new StructureStart(structure, origin, 0, regeneratedPieces);
        String afterReload = NativeStructureOwnershipFingerprint.fingerprint(
                "minecraft:monument", reloaded);
        NativeStructureOwnershipRecord ownership = NativeStructureOwnershipFingerprint.capture(
                "minecraft:monument",
                wrapped,
                plan(origin, NativeStructureReferenceEnvelope.contentBounds(wrapped).minY()),
                NativeStructureReferenceEnvelope.referenceBounds(wrapped, structure, terrain));

        assertEquals(beforeReload, afterReload);
        assertTrue(NativeStructureOwnershipFingerprint.matches(ownership, reloaded));
        assertNotEquals(alignedLocatorY, NativeStructureOwnershipFingerprint.locatorY(reloaded));
        assertEquals(alignedLocatorY, ownership.locatorY());
        assertEquals(1, reloaded.getPieces().size());
        assertEquals(1, wrapped.getPieces().size());
    }

    @Test
    public void sameKeyAndOriginStillRequiresMatchingContentIdentity() {
        long seed = 18273L;
        ChunkPos origin = new ChunkPos(2, 3);
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        StructureStart expected = monumentStart(structure, origin, seed);
        StructureStart moved = monumentStart(structure, origin, seed);
        for (StructurePiece piece : moved.getPieces()) {
            piece.move(1, 0, 0);
        }
        NativeStructureOwnershipRecord ownership = NativeStructureOwnershipFingerprint.capture(
                "minecraft:monument",
                expected,
                plan(origin, NativeStructureReferenceEnvelope.contentBounds(expected).minY()),
                expected.getBoundingBox());

        assertFalse(NativeStructureOwnershipFingerprint.matches(ownership, moved));
    }

    @Test
    public void persistedEnvelopeRepairsReloadedMonumentReferenceOutsideLiveBounds() {
        long seed = 772931L;
        ChunkPos origin = new ChunkPos(4, -7);
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
    public void overlappingManualStartsSixteenChunksApartRemainIndependentlyReferenced() {
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        ChunkPos westOrigin = new ChunkPos(0, 0);
        ChunkPos eastOrigin = new ChunkPos(16, 0);
        StructureStart west = monumentStart(structure, westOrigin, 317L);
        StructureStart east = monumentStart(structure, eastOrigin, 941L);
        west.getPieces().getFirst().move(112, 0, 0);
        east.getPieces().getFirst().move(-96, 0, 0);
        NativeStructureOwnershipRecord westOwnership = ownership(westOrigin, west, structure);
        NativeStructureOwnershipRecord eastOwnership = ownership(eastOrigin, east, structure);
        ChunkPos shared = new ChunkPos(8, 0);

        assertTrue(NativeStructureReferenceEnvelope.contentBounds(west).intersects(
                NativeStructureReferenceEnvelope.contentBounds(east)));
        assertTrue(NativeStructureReferenceRepair.requiresReference(
                shared, "minecraft:monument", west, westOwnership));
        assertTrue(NativeStructureReferenceRepair.requiresReference(
                shared, "minecraft:monument", east, eastOwnership));
    }

    @Test
    public void denseAdjacentManualStartsDoNotSuppressEachOther() {
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        ChunkPos firstOrigin = new ChunkPos(0, 0);
        ChunkPos secondOrigin = new ChunkPos(1, 0);
        StructureStart first = monumentStart(structure, firstOrigin, 17L);
        StructureStart second = monumentStart(structure, secondOrigin, 23L);
        NativeStructureOwnershipRecord firstOwnership = ownership(firstOrigin, first, structure);
        NativeStructureOwnershipRecord secondOwnership = ownership(secondOrigin, second, structure);
        ChunkPos shared = new ChunkPos(0, 0);

        assertTrue(NativeStructureReferenceEnvelope.contentBounds(first).intersects(
                NativeStructureReferenceEnvelope.contentBounds(second)));
        assertTrue(NativeStructureReferenceRepair.requiresReference(
                shared, "minecraft:monument", first, firstOwnership));
        assertTrue(NativeStructureReferenceRepair.requiresReference(
                shared, "minecraft:monument", second, secondOwnership));
    }

    @Test
    public void customPoolElementIdentityDoesNotDependOnProcessLocalToString() {
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        ChunkPos origin = new ChunkPos(3, -2);
        StructureStart first = poolStart(structure, origin, new IdentityStringPoolElement());
        StructureStart reloaded = poolStart(structure, origin, new IdentityStringPoolElement());

        assertEquals(
                NativeStructureOwnershipFingerprint.fingerprint("test:custom_pool", first),
                NativeStructureOwnershipFingerprint.fingerprint("test:custom_pool", reloaded));
    }

    @Test
    public void namedPoolTemplateRemainsPartOfTheStableFingerprint() {
        OceanMonumentStructure structure = new OceanMonumentStructure(
                new OceanMonumentStructure.StructureSettings(HolderSet.empty()));
        ChunkPos origin = new ChunkPos(3, -2);
        StructurePoolElement firstElement = StructurePoolElement.single("test:first")
                .apply(StructureTemplatePool.Projection.RIGID);
        StructurePoolElement secondElement = StructurePoolElement.single("test:second")
                .apply(StructureTemplatePool.Projection.RIGID);

        assertNotEquals(
                NativeStructureOwnershipFingerprint.fingerprint(
                        "test:named_pool", poolStart(structure, origin, firstElement)),
                NativeStructureOwnershipFingerprint.fingerprint(
                        "test:named_pool", poolStart(structure, origin, secondElement)));
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
                .setPlacementId("monument-test")
                .setNativeStructures(new KList<IrisNativeStructure>().qadd(source));
        return new NativeStructureStartPlan(
                placement,
                source,
                origin.x(),
                origin.z(),
                baseY
        );
    }

    private static NativeStructureOwnershipRecord ownership(
            ChunkPos origin, StructureStart start, OceanMonumentStructure structure) {
        return NativeStructureOwnershipFingerprint.capture(
                "minecraft:monument",
                start,
                plan(origin, NativeStructureReferenceEnvelope.contentBounds(start).minY()),
                NativeStructureReferenceEnvelope.referenceBounds(
                        start, structure, new IrisStructureTerrain())
        );
    }

    private static StructureStart poolStart(
            OceanMonumentStructure structure, ChunkPos origin, StructurePoolElement element) {
        BlockPos position = new BlockPos(
                origin.getMiddleBlockX(), 64, origin.getMiddleBlockZ());
        PoolElementStructurePiece piece = new PoolElementStructurePiece(
                null, element, position, 0, Rotation.NONE,
                new BoundingBox(position), LiquidSettings.APPLY_WATERLOGGING);
        return new StructureStart(
                structure, origin, 0, new PiecesContainer(List.of(piece)));
    }

    private static final class IdentityStringPoolElement extends StructurePoolElement {
        private IdentityStringPoolElement() {
            super(StructureTemplatePool.Projection.RIGID);
        }

        @Override
        public Vec3i getSize(StructureTemplateManager templateManager, Rotation rotation) {
            return new Vec3i(1, 1, 1);
        }

        @Override
        public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
                StructureTemplateManager templateManager, BlockPos position,
                Rotation rotation, RandomSource random) {
            return List.of();
        }

        @Override
        public BoundingBox getBoundingBox(StructureTemplateManager templateManager,
                                          BlockPos position, Rotation rotation) {
            return new BoundingBox(position);
        }

        @Override
        public boolean place(StructureTemplateManager templateManager,
                             WorldGenLevel world, StructureManager structureManager,
                             ChunkGenerator chunkGenerator, BlockPos position, BlockPos pivot,
                             Rotation rotation, BoundingBox area, RandomSource random,
                             LiquidSettings liquidSettings, boolean keepJigsaws) {
            return true;
        }

        @Override
        public StructurePoolElementType<?> getType() {
            return StructurePoolElementType.EMPTY;
        }

        @Override
        public String toString() {
            return "IdentityStringPoolElement@"
                    + Integer.toHexString(System.identityHashCode(this));
        }
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
}
