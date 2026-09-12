package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.formation.IrisFormation;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSurfaceSupportTest {
    private static final List<IrisBlockVector> SINGLE_COLUMN = List.of(new IrisBlockVector(0, 0, 0));

    @Test
    public void carvedSurfaceColumnRejectsPlacement() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.carve(0, 80, 0);

        assertTrue(isUnsupported(placer, SINGLE_COLUMN, 0, 1));
    }

    @Test
    public void solidGroundStillPlaces() {
        SurfacePlacer placer = new SurfacePlacer(80);

        assertFalse(isUnsupported(placer, SINGLE_COLUMN, 2, 2));
    }

    @Test
    public void openingInsideBufferRingRejectsPlacement() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.carve(2, 80, 0);

        assertTrue(isUnsupported(placer, SINGLE_COLUMN, 2, 1));
    }

    @Test
    public void openingJustOutsideBufferRingStillPlaces() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.carve(3, 80, 0);

        assertFalse(isUnsupported(placer, SINGLE_COLUMN, 2, 1));
    }

    @Test
    public void crustThinnerThanRequiredDepthRejectsPlacement() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.carve(0, 79, 0);

        assertTrue(isUnsupported(placer, SINGLE_COLUMN, 0, 2));
        assertFalse(isUnsupported(placer, SINGLE_COLUMN, 0, 1));
    }

    @Test
    public void nonSolidSurfaceBlockRejectsPlacement() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.hollow(0, 80, 0);

        assertTrue(isUnsupported(placer, SINGLE_COLUMN, 0, 1));
    }

    @Test
    public void everyUniqueStencilColumnIsQueriedExactlyOnce() {
        SurfacePlacer placer = new SurfacePlacer(80);
        List<IrisBlockVector> footprint = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                footprint.add(new IrisBlockVector(dx, 0, dz));
            }
        }

        assertFalse(isUnsupported(placer, footprint, 1, 1));
        assertEquals(25, placer.heightQueries());
        assertEquals(25, placer.uniqueColumnsQueried());
    }

    @Test
    public void everySupportColumnUsesItsOwnSurfaceHeight() {
        SurfacePlacer placer = new SurfacePlacer(80);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placer.height(dx, dz, 80 + dx + dz);
            }
        }
        placer.carve(1, 82, 1);

        assertTrue(isUnsupported(placer, SINGLE_COLUMN, 1, 1));
    }

    @Test
    public void offsetSupportFootprintMovesSupportStencil() {
        SurfacePlacer placer = new SurfacePlacer(80);
        placer.carve(5, 80, -3);

        assertTrue(IrisSurfaceSupport.isUnsupported(placer, null, 0, 0, null, null, 0, 0, 0,
                List.of(new IrisBlockVector(5, 0, -3)), 0, 1));
    }

    @Test
    public void placementTranslationAndRotationMatchObjectBlocks() {
        SurfacePlacer placer = new SurfacePlacer(80);
        IrisObjectTranslate translate = new IrisObjectTranslate().setX(5).setZ(-3);
        IrisObjectRotation rotation = IrisObjectRotation.of(0, 90, 0);
        placer.carve(-3, 80, -7);

        assertTrue(IrisSurfaceSupport.isUnsupported(placer, null, 0, 0, translate, rotation, 0, 0, 0,
                List.of(new IrisBlockVector(2, 0, 0)), 0, 1));
    }

    @Test
    public void emptyFootprintFallsBackToPlacementOrigin() {
        SurfacePlacer placer = new SurfacePlacer(80);
        IrisObjectTranslate translate = new IrisObjectTranslate().setX(4).setZ(-2);
        placer.carve(4, 80, -2);

        assertTrue(IrisSurfaceSupport.isUnsupported(placer, null, 0, 0, translate, null, 0, 0, 0,
                List.of(), 0, 1));
    }

    @Test
    public void objectCachesItsLowestNonFoliageSupportBlocks() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState foliage = mock(PlatformBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        when(foliage.isSolid()).thenReturn(true);
        when(foliage.isFoliage()).thenReturn(true);

        IrisObject object = new IrisObject(9, 9, 9);
        object.setUnsigned(7, 1, 4, solid);
        object.setUnsigned(4, 0, 4, foliage);
        object.setUnsigned(4, 5, 4, solid);

        List<IrisBlockVector> initial = object.getSurfaceSupportOffsets();
        assertEquals(1, initial.size());
        assertEquals(3, initial.getFirst().getBlockX());
        assertEquals(-3, initial.getFirst().getBlockY());

        object.setUnsigned(2, 0, 4, solid);

        List<IrisBlockVector> updated = object.getSurfaceSupportOffsets();
        assertEquals(1, updated.size());
        assertEquals(-2, updated.getFirst().getBlockX());
        assertEquals(-4, updated.getFirst().getBlockY());
    }

    @Test
    public void formationDefaultsToWiderBufferThanObjectDefault() {
        IrisFormation formation = new IrisFormation();

        assertEquals(2, new IrisObjectPlacement().getSurfaceSupportBuffer());
        assertEquals(3, formation.asPlacement().getSurfaceSupportBuffer());

        formation.setSurfaceSupportBuffer(0);
        assertEquals(0, formation.asPlacement().getSurfaceSupportBuffer());
    }

    private static boolean isUnsupported(IObjectPlacer placer, List<IrisBlockVector> footprint,
                                         int buffer, int minSolidDepth) {
        return IrisSurfaceSupport.isUnsupported(placer, null, 0, 0, null, null, 0, 0, 0,
                footprint, buffer, minSolidDepth);
    }

    private static final class SurfacePlacer implements IObjectPlacer {
        private final int defaultHeight;
        private final Map<String, Integer> heights = new HashMap<>();
        private final Set<String> carved = new HashSet<>();
        private final Set<String> hollow = new HashSet<>();
        private final Set<String> queriedColumns = new HashSet<>();
        private int heightQueries;

        private SurfacePlacer(int defaultHeight) {
            this.defaultHeight = defaultHeight;
        }

        private void height(int x, int z, int y) {
            heights.put(columnKey(x, z), y);
        }

        private void carve(int x, int y, int z) {
            carved.add(blockKey(x, y, z));
        }

        private void hollow(int x, int y, int z) {
            hollow.add(blockKey(x, y, z));
        }

        private int heightQueries() {
            return heightQueries;
        }

        private int uniqueColumnsQueried() {
            return queriedColumns.size();
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return getHighest(x, z, data, true);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            heightQueries++;
            queriedColumns.add(columnKey(x, z));
            return heights.getOrDefault(columnKey(x, z), defaultHeight);
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            return null;
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return carved.contains(blockKey(x, y, z));
        }

        @Override
        public boolean isSurfaceSolid(int x, int y, int z) {
            return !hollow.contains(blockKey(x, y, z));
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return null;
        }

        private static String columnKey(int x, int z) {
            return x + ":" + z;
        }

        private static String blockKey(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }
}
