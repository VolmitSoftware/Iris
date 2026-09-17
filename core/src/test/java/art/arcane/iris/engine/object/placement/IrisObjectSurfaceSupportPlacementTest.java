package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.engine.object.placement.ObjectPlaceMode;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisObjectSurfaceSupportPlacementTest {
    private static final int SURFACE_Y = 80;

    private IrisData data;
    private Engine engine;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        // IrisObject caches built-in states across test fixtures; every registry answer must be usable.
        Map<String, PlatformBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.<String>getArgument(0).toLowerCase(java.util.Locale.ROOT);
            return registryStates.computeIfAbsent(key, value -> {
                PlatformBlockState block = mock(PlatformBlockState.class);
                when(block.key()).thenReturn(value);
                when(block.materialKey()).thenReturn(value.split("\\[", 2)[0]);
                when(block.isSolid()).thenReturn(!value.contains("air"));
                when(block.isOccluding()).thenReturn(!value.contains("air"));
                return block;
            });
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        when(heightStream.get(anyDouble(), anyDouble())).thenReturn((double) SURFACE_Y);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(heightStream);
        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        when(engine.getComplex()).thenReturn(complex);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void carvedAnchorColumnSkipsEverySurfaceAnchoredMode() {
        for (ObjectPlaceMode mode : new ObjectPlaceMode[]{
                ObjectPlaceMode.CENTER_HEIGHT,
                ObjectPlaceMode.MAX_HEIGHT,
                ObjectPlaceMode.MIN_STILT,
                ObjectPlaceMode.VACUUM
        }) {
            SurfacePlacer placer = new SurfacePlacer();
            placer.carve(0, SURFACE_Y, 0);

            assertEquals("mode " + mode + " must skip over a carved column",
                    -1, place(placer, placement(mode), -1));
            assertTrue("mode " + mode + " must not write blocks", placer.written().isEmpty());
        }
    }

    @Test
    public void holeInsideBufferRingSkipsPlacement() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(2, SURFACE_Y, 0);

        assertEquals(-1, place(placer, placement(ObjectPlaceMode.CENTER_HEIGHT), -1));
    }

    @Test
    public void holeOutsideBufferRingStillPlaces() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(6, SURFACE_Y, 0);

        assertTrue(place(placer, placement(ObjectPlaceMode.CENTER_HEIGHT), -1) >= 0);
    }

    @Test
    public void solidGroundStillPlaces() {
        SurfacePlacer placer = new SurfacePlacer();

        assertTrue(place(placer, placement(ObjectPlaceMode.CENTER_HEIGHT), -1) >= 0);
    }

    @Test
    public void explicitSurfaceAnchorIsGuardedByTheBufferRing() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(2, SURFACE_Y, 0);

        assertEquals(-1, place(placer, placement(ObjectPlaceMode.CENTER_HEIGHT), SURFACE_Y));
    }

    @Test
    public void forcePlaceIgnoresSurfaceSupport() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(0, SURFACE_Y, 0);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setForcePlace(true);

        assertTrue(place(placer, placement, -1) >= 0);
    }

    @Test
    public void disablingRequireSurfaceSupportIgnoresSurfaceSupport() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(0, SURFACE_Y, 0);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setRequireSurfaceSupport(false);

        assertTrue(place(placer, placement, -1) >= 0);
    }

    @Test
    public void carvingOnlyExplicitAnchorIsNotGuarded() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(0, SURFACE_Y, 0);
        placer.carve(0, SURFACE_Y - 1, 0);
        placer.carve(0, SURFACE_Y - 2, 0);
        placer.carve(0, SURFACE_Y - 3, 0);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setCarvingSupport(CarvingMode.CARVING_ONLY);

        assertTrue(place(placer, placement, SURFACE_Y) >= 0);
    }

    @Test
    public void structurePieceIsNotGuarded() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(0, SURFACE_Y, 0);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STRUCTURE_PIECE);

        assertTrue(place(placer, placement, SURFACE_Y) >= 0);
    }

    @Test
    public void explicitStructurePiecePlacesAfterItsStudioEngineCloses() {
        when(data.getEngine()).thenReturn(null);
        SurfacePlacer placer = new SurfacePlacer();
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STRUCTURE_PIECE);

        assertTrue(place(placer, placement, SURFACE_Y) >= 0);
        assertFalse(placer.written().isEmpty());
        verify(data, never()).getEngine();
    }

    @Test
    public void targetWorldEngineSuppliesRequestedSlopeContext() {
        when(data.getEngine()).thenReturn(null);
        SurfacePlacer placer = new SurfacePlacer(engine);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STRUCTURE_PIECE);
        placement.setRotateTowardsSlope(true);

        assertTrue(place(placer, placement, SURFACE_Y) >= 0);
        assertFalse(placer.written().isEmpty());
        verify(data, never()).getEngine();
    }

    @Test
    public void forcePlaceBypassesSlopeConditionWithoutAnEngine() {
        when(data.getEngine()).thenReturn(null);
        SurfacePlacer placer = new SurfacePlacer();
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STRUCTURE_PIECE);
        placement.setSlopeCondition(new IrisSlopeClip().setMinimumSlope(100).setMaximumSlope(100));
        placement.setForcePlace(true);

        assertTrue(place(placer, placement, SURFACE_Y) >= 0);
        assertFalse(placer.written().isEmpty());
        verify(data, never()).getEngine();
    }

    @Test
    public void forcePlaceStillRequiresSlopeRotationContext() {
        when(data.getEngine()).thenReturn(null);
        SurfacePlacer placer = new SurfacePlacer();
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STRUCTURE_PIECE);
        placement.setSlopeCondition(new IrisSlopeClip().setMinimumSlope(100).setMaximumSlope(100));
        placement.setForcePlace(true);
        placement.setRotateTowardsSlope(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> place(placer, placement, SURFACE_Y));

        assertTrue(error.getMessage().contains("requires an active Iris engine"));
        assertTrue(placer.written().isEmpty());
        verify(data, never()).getEngine();
    }

    @Test
    public void floatingIsNotGuarded() {
        SurfacePlacer placer = new SurfacePlacer();
        placer.carve(0, SURFACE_Y, 0);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.FLOATING);
        placement.setTranslate(new IrisObjectTranslate().setY(SURFACE_Y + 40));

        assertTrue(place(placer, placement, -1) >= 0);
    }

    private int place(SurfacePlacer placer, IrisObjectPlacement placement, int anchorY) {
        return object().place(0, anchorY, 0, placer, placement, new RNG(1234L), data);
    }

    private IrisObjectPlacement placement(ObjectPlaceMode mode) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(mode);
        return placement;
    }

    private IrisObject object() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        when(solid.key()).thenReturn("minecraft:stone");
        when(solid.materialKey()).thenReturn("minecraft:stone");
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 0, 1, solid);
        object.setUnsigned(1, 1, 1, solid);
        return object;
    }

    private static final class SurfacePlacer implements IObjectPlacer {
        private final Set<String> carved = new HashSet<>();
        private final Map<String, PlatformBlockState> written = new HashMap<>();
        private final Engine engine;

        private SurfacePlacer() {
            this(null);
        }

        private SurfacePlacer(Engine engine) {
            this.engine = engine;
        }

        private void carve(int x, int y, int z) {
            carved.add(blockKey(x, y, z));
        }

        private Map<String, PlatformBlockState> written() {
            return written;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return SURFACE_Y;
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return SURFACE_Y;
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
            written.put(blockKey(x, y, z), state);
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
            return engine;
        }

        private static String blockKey(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }
}
