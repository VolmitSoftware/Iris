package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCarveModifierBoundarySupportTest {
    @Test
    @SuppressWarnings("unchecked")
    public void boundaryBiomeUsesCustomMatterAtItsOwnY() throws ReflectiveOperationException {
        IrisBiome customFloor = mock(IrisBiome.class);
        IrisBiome resolvedCeiling = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();
        doReturn(customFloor).when(biomeLoader).load("custom/floor");

        Engine engine = mock(Engine.class);
        doReturn(data).when(engine).getData();
        doReturn(resolvedCeiling).when(engine).getCaveBiome(anyInt(), eq(42), anyInt(), any(IrisDimensionCarvingResolver.State.class));

        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        Field rng = IrisCarveModifier.class.getDeclaredField("rng");
        rng.setAccessible(true);
        rng.set(modifier, new RNG(71L));

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
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();

        IrisBiome floor = modifier.resolveCaveBoundaryBiome(
                mantleChunk, 1, 6, 2, 40, 44, resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceiling = modifier.resolveCaveBoundaryBiome(
                mantleChunk, 1, 42, 2, 40, 44, resolverState, caveBiomeCache, customBiomeCache);

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
        assertFalse(IrisCarveModifier.canReplaceCaveFloorLayer(output, 0, 5, 0, sand));
        assertTrue(IrisCarveModifier.canReplaceCaveFloorLayer(output, 0, 5, 0, stone));

        doReturn(solid).when(output).getRaw(0, 4, 0);
        assertTrue(IrisCarveModifier.canReplaceCaveFloorLayer(output, 0, 5, 0, sand));
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
        assertFalse(IrisCarveModifier.hasStableCaveFloorSupport(output, 0, 6, 0));

        doReturn(stone).when(output).getRaw(0, 4, 0);
        assertTrue(IrisCarveModifier.hasStableCaveFloorSupport(output, 0, 6, 0));
    }

    @Test
    public void hydrologyGuardOnlyAcceptsStableSolidBoundaryLayers() {
        HydrologyCaveCell guard = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        PlatformBlockState stone = state("minecraft:stone", true);
        PlatformBlockState sand = state("minecraft:sand", true);
        PlatformBlockState water = state("minecraft:water", false, true);

        assertTrue(IrisCarveModifier.canReplaceHydrologyGuard(guard, stone, false));
        assertTrue(IrisCarveModifier.canReplaceHydrologyGuard(guard, stone, true));
        assertTrue(IrisCarveModifier.canReplaceHydrologyGuard(guard, sand, false));
        assertFalse(IrisCarveModifier.canReplaceHydrologyGuard(guard, sand, true));
        assertFalse(IrisCarveModifier.canReplaceHydrologyGuard(guard, water, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void submergedCaveFloorUsesBuriedSubstrateInsteadOfVegetatedSurface() {
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        PlatformBlockState grass = state("minecraft:grass_block", true);
        PlatformBlockState dirt = state("minecraft:dirt", true);
        PlatformBlockState moss = state("minecraft:moss_block", true);
        HydrologyCaveCell wet = HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE);

        doReturn(dirt).when(output).getRaw(0, 4, 0);

        assertSame(dirt, IrisCarveModifier.resolveSubmergedCaveFloorLayer(
                output, 0, 5, 0, grass, wet));
        assertSame(dirt, IrisCarveModifier.resolveSubmergedCaveFloorLayer(
                output, 0, 5, 0, moss, wet));
        assertSame(grass, IrisCarveModifier.resolveSubmergedCaveFloorLayer(
                output, 0, 5, 0, grass, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR)));
    }

    private PlatformBlockState state(String key, boolean solid) {
        return state(key, solid, false);
    }

    private PlatformBlockState state(String key, boolean solid, boolean fluid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        doReturn(key).when(state).key();
        doReturn(solid).when(state).isSolid();
        doReturn(fluid).when(state).isFluid();
        return state;
    }
}
