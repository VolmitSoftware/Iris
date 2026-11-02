package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BukkitStructureStagePolicySeaSurfaceTest {
    private static final int MIN_HEIGHT = -256;
    private static final int SEA_LEVEL = 50;
    private static final IrisData DATA = mock(IrisData.class);

    @Test
    public void topYIsMinHeightPlusFluidHeight() {
        NativeBlockState water = fluid();
        Engine engine = engine(dimension(SEA_LEVEL, water));

        StructureSeaSurface sea = new BukkitStructureStagePolicy(engine).seaSurface(engine);

        assertEquals(engine.getMinHeight() + engine.getDimension().getFluidHeight(), sea.topY());
        assertEquals(SEA_LEVEL, sea.topY());
        assertSame(water, sea.fluid());
    }

    @Test
    public void lavaSeaDimensionSuppliesLava() {
        NativeBlockState lava = fluid();
        Engine engine = engine(dimension(SEA_LEVEL, lava));

        StructureSeaSurface sea = new BukkitStructureStagePolicy(engine).seaSurface(engine);

        assertEquals(SEA_LEVEL, sea.topY());
        assertSame(lava, sea.fluid());
    }

    @Test
    public void seaFluidIsTheFirstFluidOfThePalette() {
        NativeBlockState lava = fluid();
        Engine engine = engine(dimension(SEA_LEVEL, nonFluid(), lava, fluid()));

        assertSame(lava, new BukkitStructureStagePolicy(engine).seaSurface(engine).fluid());
    }

    @Test
    public void dimensionWithoutASeaSuppliesNone() {
        Engine dryPalette = engine(dimension(SEA_LEVEL, nonFluid()));
        Engine seaAtTheFloor = engine(dimension(MIN_HEIGHT, fluid()));

        assertSame(StructureSeaSurface.NONE, new BukkitStructureStagePolicy(dryPalette).seaSurface(dryPalette));
        assertSame(StructureSeaSurface.NONE, new BukkitStructureStagePolicy(seaAtTheFloor).seaSurface(seaAtTheFloor));
    }

    private static IrisDimension dimension(int fluidHeight, NativeBlockState... fluidPalette) {
        IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
        when(palette.getBlockData(DATA)).thenReturn(new KList<>(fluidPalette));
        return new IrisDimension()
                .setDimensionHeight(new IrisRange(MIN_HEIGHT, 512))
                .setFluidHeight(fluidHeight)
                .setFluidPalette(palette);
    }

    private static Engine engine(IrisDimension dimension) {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(MIN_HEIGHT);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(DATA);
        return engine;
    }

    private static NativeBlockState fluid() {
        NativeBlockState state = nonFluid();
        when(state.isFluid()).thenReturn(true);
        return state;
    }

    private static NativeBlockState nonFluid() {
        return mock(NativeBlockState.class);
    }
}
