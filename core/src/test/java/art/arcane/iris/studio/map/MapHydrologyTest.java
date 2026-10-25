package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapStyle;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.PickDisplay;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapHydrologyTest {
    @Test
    public void biomeViewIncludesCurrentWaterOverlayByDefault() throws Exception {
        try (FakeHydrology water = new FakeHydrology(() -> false, (x, z) -> 60, 40, 63)) {
            Engine engine = engine(water.runtime, biome());
            MapTileRenderer renderer = new MapTileRenderer(engine);
            MapTile tile = settled(renderer, MapLayer.of(TileType.BIOME));
            BufferedImage expected = new IrisRenderer(engine).renderStudio(0, 0, 64, 64, RenderType.BIOME, () -> false);
            assertNotEquals(new Color(0x738249).getRGB(), expected.getRGB(16, 16));
            assertEquals(expected.getRGB(16, 16), tile.image(MapStyle.PLAIN).getRGB(16, 16));
            MapTile dry = settled(renderer, MapLayer.biomes(PickDisplay.PICKS, false));
            assertEquals(new Color(0x738249).getRGB(), dry.image(MapStyle.PLAIN).getRGB(16, 16));
            assertNotEquals(MapLayer.biomes(PickDisplay.PICKS, true), MapLayer.biomes(PickDisplay.PICKS, false));
        }
    }

    @Test
    public void pickingNoiseNeverReceivesTheWaterOverlay() {
        for (PickDisplay display : new PickDisplay[]{PickDisplay.NOISE, PickDisplay.BOTH}) {
            assertEquals(MapLayer.biomes(display, false), MapLayer.biomes(display, true));
        }
    }

    @Test
    public void diagnosticsUseExistingConfigurationCalculations() {
        IrisBiome biome = biome();
        biome.setObjects(new KList<>(new IrisObjectPlacement().setDensity(4).setChance(0.5)));
        biome.setDecorators(new KList<>(new IrisDecorator().setChance(0.25).setStackMax(2)));
        Engine engine = engine(FakeHydrology.disabled(), biome);
        when(engine.getMaxBiomeObjectDensity()).thenReturn(8D);
        when(engine.getMaxBiomeDecoratorDensity()).thenReturn(256D);
        when(engine.getMaxBiomeLayerDensity()).thenReturn(4D);
        MapTileRenderer renderer = new MapTileRenderer(engine);
        for (String name : new String[]{"OBJECT_LOAD", "DECORATOR_LOAD", "LAYER_LOAD"}) {
            TileType type = TileType.valueOf(name);
            MapView view = MapView.valueOf(name);
            assertEquals("Diagnostics", view.group().label());
            assertTrue(view.description().contains("Configuration estimate"));
            MapTile tile = renderer.renderTile(0, 0, 1, 8, MapLayer.of(type), false, () -> false);
            assertEquals(biome.getColor(engine, RenderType.valueOf(name)).getRGB(), tile.image(MapStyle.PLAIN).getRGB(0, 0));
        }
    }

    @Test
    public void disabledWaterPreservesBiomeColors() {
        Engine engine = engine(FakeHydrology.disabled(), biome());
        MapTile tile = new MapTileRenderer(engine).renderTile(0, 0, 1, 8, MapLayer.of(TileType.BIOME), false, () -> false);
        assertEquals(new Color(0x738249).getRGB(), tile.image(MapStyle.PLAIN).getRGB(0, 0));
    }

    @Test
    public void riverLegendMatchesCurrentRendererIncludingRecordedCavePools() {
        assertEquals(IrisRenderer.mapWaterTypes().size(), RiverKey.rows().size());
        for (int index = 0; index < RiverKey.rows().size(); index++) {
            assertEquals(IrisRenderer.hydrologyFeatureColor(IrisRenderer.mapWaterTypes().get(index)) & 0xFFFFFF,
                    RiverKey.rows().get(index).color());
        }
        assertEquals("cave pool", RiverKey.rows().getLast().label());
    }

    private static MapTile settled(MapTileRenderer renderer, MapLayer layer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
        while (true) {
            long revision = ProvisionalSampling.revision();
            MapTile tile = renderer.renderTile(0, 0, 1, 64, layer, false, () -> false);
            if (revision == ProvisionalSampling.revision()) {
                return tile;
            }
            assertTrue("water never settled", System.nanoTime() < deadline);
            Thread.sleep(10L);
        }
    }

    private static IrisBiome biome() {
        IrisBiome biome = new IrisBiome();
        biome.setColor("#738249");
        return biome;
    }

    private static Engine engine(HydrologyRuntime water, IrisBiome biome) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(256);
        when(complex.getHydrologyRuntime()).thenReturn(water);
        IrisRegion region = new IrisRegion();
        when(complex.getBaseBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> biome, Interpolated.of(b -> 0D, d -> biome)));
        when(complex.getRegionStream()).thenReturn(ProceduralStream.of((x, z) -> region, Interpolated.of(r -> 0D, d -> region)));
        return engine;
    }
}
