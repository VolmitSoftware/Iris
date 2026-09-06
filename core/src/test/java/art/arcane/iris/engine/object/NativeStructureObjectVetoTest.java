package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureObjectVetoTest {
    private static final int SURFACE_Y = 80;

    private IrisData data;
    private Engine engine;
    private PlatformBlockState log;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(block);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        log = state("minecraft:oak_log", true);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(heightStream);
        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        volumes();
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void objectPlacesWhenNoNativeStructureIsNear() {
        RecordingPlacer placer = new RecordingPlacer(engine);

        assertTrue(place(placer) >= 0);
        assertFalse(placer.written().isEmpty());
    }

    @Test
    public void canopyBlockInsideAPieceRejectsTheWholeObject() {
        int canopyY = plantedTopY();
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(-1, canopyY, -1, 1, canopyY + 4, 1));

        assertEquals(-1, place(placer));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void trunkBlockInsideAPieceRejectsTheWholeObject() {
        int baseY = plantedBottomY();
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(0, baseY, 0, 0, baseY, 0));

        assertEquals(-1, place(placer));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void negativeWorldMinimumConvertsMantleCoordinatesBeforeVeto() {
        when(engine.getMinHeight()).thenReturn(-256);
        int worldBaseY = plantedBottomY() - 256;
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(0, worldBaseY, 0, 0, worldBaseY, 0));

        assertEquals(-1, place(placer));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void obliqueRotationRejectsNativePiecesBeyondTheUnrotatedRadius() {
        IrisObject object = new IrisObject(21, 1, 21);
        object.setUnsigned(20, 0, 20, log);
        IrisObjectPlacement placement = placement();
        placement.setBottom(false);
        placement.setRotation(IrisObjectRotation.of(0, 45, 0));
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(14, SURFACE_Y, 0, 14, SURFACE_Y, 0));

        assertEquals(-1, object.place(0, SURFACE_Y, 0, placer, placement, new RNG(1234L), data));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void offCenterOriginRejectsNativePiecesAtTheFarEdge() {
        IrisObject object = new IrisObject(9, 1, 1);
        object.setCenter(new Vector3i(0, 0, 0));
        object.setUnsigned(8, 0, 0, log);
        IrisObjectPlacement placement = placement();
        placement.setBottom(false);
        placement.setRotation(IrisObjectRotation.of(0, 0, 0));
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(8, SURFACE_Y, 0, 8, SURFACE_Y, 0));

        assertEquals(-1, object.place(0, SURFACE_Y, 0, placer, placement, new RNG(1234L), data));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void pieceOverlappingOnlyTheEnvelopeStillPlaces() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(2, plantedBottomY(), 2, 4, plantedTopY(), 4));

        assertTrue(place(placer) >= 0);
        assertFalse(placer.written().isEmpty());
    }

    @Test
    public void pieceBelowTheObjectStillPlaces() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(-32, -60, -32, 32, plantedBottomY() - 1, 32));

        assertTrue(place(placer) >= 0);
        assertFalse(placer.written().isEmpty());
    }

    @Test
    public void rejectionWritesNoBlocksTilesOrMarkers() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(-64, -64, -64, 64, 320, 64));

        assertEquals(-1, place(placer));
        assertTrue(placer.written().isEmpty());
        assertEquals(0, placer.tiles());
        assertEquals(0, placer.markers());
    }

    /**
     * Iris authored structure pieces are arbitrated by the jigsaw placement scope, not by this veto: rejecting them
     * one piece at a time would publish a partial structure. The sibling placement pins the test to the exemption
     * rather than to geometry that simply misses every volume.
     */
    @Test
    public void irisStructurePiecesBypassTheVetoUnlikeTheirSiblings() {
        volumes(volume(-64, -64, -64, 64, 320, 64));

        RecordingPlacer vetoed = new RecordingPlacer(engine);
        assertEquals(-1, tree().place(0, SURFACE_Y, 0, vetoed, placement(), new RNG(1234L), data));
        assertTrue(vetoed.written().isEmpty());

        RecordingPlacer exempt = new RecordingPlacer(engine);
        IrisObjectPlacement structurePiece = placement();
        structurePiece.setMode(ObjectPlaceMode.STRUCTURE_PIECE);

        assertTrue(tree().place(0, SURFACE_Y, 0, exempt, structurePiece, new RNG(1234L), data) >= 0);
        assertFalse(exempt.written().isEmpty());
    }

    @Test
    public void forcePlaceStillObeysTheVeto() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(-64, -64, -64, 64, 320, 64));
        IrisObjectPlacement placement = placement();
        placement.setForcePlace(true);

        assertEquals(-1, tree().place(0, -1, 0, placer, placement, new RNG(1234L), data));
        assertTrue(placer.written().isEmpty());
    }

    @Test
    public void structurePiecePlacementsBypassTheVeto() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes(volume(-64, -64, -64, 64, 320, 64));
        IrisObjectPlacement placement = placement();
        placement.setMode(ObjectPlaceMode.STRUCTURE_PIECE);

        assertTrue(tree().place(0, 100, 0, placer, placement, new RNG(1234L), data) >= 0);
        assertFalse(placer.written().isEmpty());
    }

    private int plantedTopY() {
        int top = Integer.MIN_VALUE;
        for (int[] position : plantedPositions()) {
            top = Math.max(top, position[1]);
        }
        return top;
    }

    private int plantedBottomY() {
        int bottom = Integer.MAX_VALUE;
        for (int[] position : plantedPositions()) {
            bottom = Math.min(bottom, position[1]);
        }
        return bottom;
    }

    private List<int[]> plantedPositions() {
        RecordingPlacer placer = new RecordingPlacer(engine);
        volumes();
        assertTrue(place(placer) >= 0);
        assertFalse(placer.written().isEmpty());
        return placer.written();
    }

    private int place(RecordingPlacer placer) {
        return tree().place(0, -1, 0, placer, placement(), new RNG(1234L), data);
    }

    private IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        return placement;
    }

    private IrisObject tree() {
        IrisObject object = new IrisObject(3, 7, 3);
        for (int y = 0; y < 7; y++) {
            object.setUnsigned(1, y, 1, log);
        }
        return object;
    }

    private void volumes(NativeStructureVolume... volumes) {
        KList<NativeStructureVolume> list = new KList<>();
        for (NativeStructureVolume volume : volumes) {
            list.add(volume);
        }
        when(engine.getNativeStructureVolumes(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(list);
    }

    private NativeStructureVolume volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new NativeStructureVolume("minecraft:village_plains", minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static PlatformBlockState state(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.isSolid()).thenReturn(solid);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        return state;
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final List<int[]> written = new ArrayList<>();
        private final PlatformBlockState air = state("minecraft:air", false);
        private final Engine engine;
        private int tiles;
        private int markers;

        private RecordingPlacer(Engine engine) {
            this.engine = engine;
        }

        private List<int[]> written() {
            return written;
        }

        private int tiles() {
            return tiles;
        }

        private int markers() {
            return markers;
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
            written.add(new int[]{x, y, z});
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            return air;
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
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
            tiles++;
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
            markers++;
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }
}
