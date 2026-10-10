package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.host.ModuleLogs;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CaveApplyingBoundarySupportTest {
    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void boundaryBiomeUsesCustomMatterAtItsOwnY() {
        IrisBiome customFloor = mock(IrisBiome.class);
        IrisBiome resolvedCeiling = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();
        doReturn(customFloor).when(biomeLoader).load("custom/floor");

        Engine engine = mock(Engine.class);
        doReturn(data).when(engine).getData();
        doReturn(resolvedCeiling).when(engine).getCaveBiome(anyInt(), eq(42), anyInt());

        doReturn(mock(SeedManager.class)).when(engine).getSeedManager();
        DecoratorTestEngines.withGeneration(engine);
        CaveApplying modifier = new CaveApplying(engine, ModuleLogs.discarding());

        MantleChunk<Matter> mantleChunk = mock(MantleChunk.class);
        Matter floorMatter = mock(Matter.class);
        Matter ceilingMatter = mock(Matter.class);
        MatterSlice<MatterCavern> floorSlice = mock(MatterSlice.class);
        MatterSlice<MatterCavern> ceilingSlice = mock(MatterSlice.class);
        doReturn(true).when(mantleChunk).exists(0);
        doReturn(true).when(mantleChunk).exists(2);
        doReturn(floorMatter).when(mantleChunk).get(0);
        doReturn(ceilingMatter).when(mantleChunk).get(2);
        doReturn(true).when(floorMatter).hasSlice(MatterCavern.class);
        doReturn(true).when(ceilingMatter).hasSlice(MatterCavern.class);
        doReturn(floorSlice).when(floorMatter).getSlice(MatterCavern.class);
        doReturn(ceilingSlice).when(ceilingMatter).getSlice(MatterCavern.class);
        doReturn(new MatterCavern(true, "custom/floor", (byte) 0)).when(floorSlice).get(1, 6, 2);
        doReturn(null).when(ceilingSlice).get(1, 10, 2);

        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = new Long2ObjectOpenHashMap<>();
        Map<String, IrisBiome> customBiomeCache = new HashMap<>();

        IrisBiome floor = modifier.resolveCaveBoundaryBiome(
                mantleChunk, 1, 6, 2, 40, 44, caveBiomeCache, customBiomeCache);
        IrisBiome ceiling = modifier.resolveCaveBoundaryBiome(
                mantleChunk, 1, 42, 2, 40, 44, caveBiomeCache, customBiomeCache);

        assertSame(customFloor, floor);
        assertSame(resolvedCeiling, ceiling);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void gravityFloorLayerRequiresSolidSupportBelowItsTarget() {
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        PlatformBlockState air = state("minecraft:cave_air", false);
        PlatformBlockState solid = state("minecraft:stone", true);
        PlatformBlockState sand = state("minecraft:sand", true);
        PlatformBlockState stone = state("minecraft:stone", true);

        doReturn(air).when(output).getRaw(0, 4, 0);
        assertFalse(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, sand));
        assertTrue(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, stone));

        doReturn(solid).when(output).getRaw(0, 4, 0);
        assertTrue(CaveApplying.canReplaceCaveFloorLayer(output, 0, 5, 0, sand));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void gravityFloorDoesNotReceiveDecoratorsOverLowerCaveAir() {
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        PlatformBlockState air = state("minecraft:cave_air", false);
        PlatformBlockState sand = state("minecraft:sand", true);
        PlatformBlockState stone = state("minecraft:stone", true);

        doReturn(sand).when(output).getRaw(0, 5, 0);
        doReturn(air).when(output).getRaw(0, 4, 0);
        assertFalse(CaveApplying.hasStableCaveFloorSupport(output, 0, 6, 0));

        doReturn(stone).when(output).getRaw(0, 4, 0);
        assertTrue(CaveApplying.hasStableCaveFloorSupport(output, 0, 6, 0));
    }

    private PlatformBlockState state(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        doReturn(key).when(state).key();
        doReturn(solid).when(state).isSolid();
        doReturn(key.equals("minecraft:sand")).when(state).hasGravity();
        return state;
    }
}
