package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;
import net.minecraft.world.level.levelgen.structure.structures.JungleTempleStructure;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NativeStructurePostProcessorScatteredFeatureTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void desertPyramidUsesTheLowestFootprintSurfaceAndLocksVanillaReanchor() {
        TestDesertPyramidPiece piece = new TestDesertPyramidPiece(RandomSource.create(11L), 0, 0);
        StructureStart start = desertStart(piece);
        BoundingBox cachedBounds = start.getBoundingBox();
        BoundingBox footprint = piece.getBoundingBox();

        int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                start, "minecraft:desert_pyramid", 0, 63, -64, 320, false, false, null,
                (x, z) -> x == footprint.maxX() && z == footprint.maxZ() ? 64 : 92);

        assertEquals(1, offset);
        assertSame(cachedBounds, start.getBoundingBox());
        assertEquals(65, piece.getBoundingBox().minY());
        assertEquals(65, start.getBoundingBox().minY());
        assertTrue(piece.attemptVanillaAlignment(-2));
        assertEquals(65, piece.getBoundingBox().minY());
    }

    @Test
    public void jungleTempleUsesAverageFootprintSurfaceAndHonorsConfiguredShift() {
        TestJungleTemplePiece piece = new TestJungleTemplePiece(RandomSource.create(19L), 32, -16);
        StructureStart start = jungleStart(piece);
        BoundingBox footprint = piece.getBoundingBox();

        int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                start, "minecraft:jungle_pyramid", 4, 63, -64, 320, false, false, null,
                (x, z) -> x == footprint.minX() && z == footprint.minZ() ? 88 : 70);

        assertEquals(11, offset);
        assertEquals(75, piece.getBoundingBox().minY());
        assertTrue(piece.attemptVanillaAlignment());
        assertEquals(75, piece.getBoundingBox().minY());
    }

    @Test
    public void repeatedAlignmentIsIdempotent() {
        TestDesertPyramidPiece piece = new TestDesertPyramidPiece(RandomSource.create(23L), 0, 0);
        StructureStart start = desertStart(piece);

        int initialOffset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                start, "minecraft:desert_pyramid", 0, 63, -64, 320, false, false, null, (x, z) -> 80);
        int repeatedOffset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                start, "minecraft:desert_pyramid", 0, 63, -64, 320, false, false, null, (x, z) -> 80);

        assertEquals(17, initialOffset);
        assertEquals(0, repeatedOffset);
        assertEquals(81, piece.getBoundingBox().minY());
    }

    @Test
    public void scatteredAlignmentStaysInsideWorldBounds() {
        TestDesertPyramidPiece piece = new TestDesertPyramidPiece(RandomSource.create(29L), 0, 0);
        StructureStart start = desertStart(piece);

        int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                start, "minecraft:desert_pyramid", 0, 63, -64, 320, false, false, null, (x, z) -> 318);

        assertEquals(241, offset);
        assertEquals(305, piece.getBoundingBox().minY());
        assertEquals(319, start.getBoundingBox().maxY());
    }

    @Test
    public void scatteredStructuresDoNotOptIntoFullFootprintSurfaceFitting() {
        TestDesertPyramidPiece desert = new TestDesertPyramidPiece(RandomSource.create(31L), 0, 0);
        TestJungleTemplePiece jungle = new TestJungleTemplePiece(RandomSource.create(37L), 0, 0);
        SwampHutPiece swamp = new SwampHutPiece(RandomSource.create(41L), 0, 0);
        StructureStart desertStart = desertStart(desert);
        StructureStart jungleStart = jungleStart(jungle);
        StructureStart swampStart = swampStart(swamp);

        assertFalse(NativeStructureSurfaceFitter.requiresSurfaceTerrain(desertStart));
        assertFalse(NativeStructureSurfaceFitter.requiresSurfaceTerrain(jungleStart));
        assertFalse(NativeStructureSurfaceFitter.requiresSurfaceTerrain(swampStart));

        AtomicInteger terrainQueries = new AtomicInteger();
        int offset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                swampStart, "minecraft:swamp_hut", 0, 63, -64, 320, false, false, null,
                (x, z) -> terrainQueries.incrementAndGet());
        assertEquals(0, offset);
        assertEquals(0, terrainQueries.get());
        assertEquals(64, swamp.getBoundingBox().minY());
    }

    @Test
    public void scatteredHeightFieldMatchesTheRuntimeContract() {
        Field field = NativeStructureReflection.resolveScatteredHeightPositionField();

        assertEquals(ScatteredFeaturePiece.class, field.getDeclaringClass());
        assertEquals(int.class, field.getType());
        assertTrue(Modifier.isProtected(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(field.trySetAccessible());
    }

    @Test
    public void datapackOverridesOfSpecialVanillaIdsUseGenericPlacement() {
        StructureStart pyramidOverride = swampStart(
                new SwampHutPiece(RandomSource.create(43L), 0, 0));
        int pyramidMinY = pyramidOverride.getBoundingBox().minY();
        int pyramidOffset = NativeStructureVerticalPlacer.applyVerticalPlacement(
                pyramidOverride, "minecraft:desert_pyramid", 5,
                63, -64, 320, false, false, null, (x, z) -> 200);

        assertEquals(5, pyramidOffset);
        assertEquals(pyramidMinY + 5, pyramidOverride.getBoundingBox().minY());

        StructureStart monumentOverride = swampStart(
                new SwampHutPiece(RandomSource.create(47L), 0, 0));
        int monumentMinY = monumentOverride.getBoundingBox().minY();
        NativeStructureVerticalPlacer.ensureMonumentSeaLevelAlignment(
                monumentOverride, "minecraft:monument", 0, 40, -64, 320);

        assertEquals(monumentMinY, monumentOverride.getBoundingBox().minY());
    }

    private static StructureStart desertStart(StructurePiece piece) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(HolderSet.empty()));
        return start(structure, piece);
    }

    private static StructureStart jungleStart(StructurePiece piece) {
        Structure structure = new JungleTempleStructure(new Structure.StructureSettings(HolderSet.empty()));
        return start(structure, piece);
    }

    private static StructureStart swampStart(StructurePiece piece) {
        Structure structure = new SwampHutStructure(new Structure.StructureSettings(HolderSet.empty()));
        return start(structure, piece);
    }

    private static StructureStart start(Structure structure, StructurePiece piece) {
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static final class TestDesertPyramidPiece extends DesertPyramidPiece {
        private TestDesertPyramidPiece(RandomSource random, int west, int north) {
            super(random, west, north);
        }

        private boolean attemptVanillaAlignment(int offset) {
            return updateHeightPositionToLowestGroundHeight((LevelAccessor) null, offset);
        }
    }

    private static final class TestJungleTemplePiece extends JungleTemplePiece {
        private TestJungleTemplePiece(RandomSource random, int west, int north) {
            super(random, west, north);
        }

        private boolean attemptVanillaAlignment() {
            return updateAverageGroundHeight((LevelAccessor) null, null, 0);
        }
    }
}
