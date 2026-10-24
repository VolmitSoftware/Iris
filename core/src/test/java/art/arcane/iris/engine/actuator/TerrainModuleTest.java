package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.terrain.BedrockSettings;
import art.arcane.iris.modules.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.modules.terrain.OreSettings;
import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.block.BlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** Terrain fills a column from bedrock to its height with the biome's layers over rock, and the fluid above it up to the fluid height. */
public class TerrainModuleTest {
    private static final int HEIGHT = 20;
    private static final int FLUID_HEIGHT = 25;
    private static final int WORLD_HEIGHT = 64;
    private static final BlockState GRASS = new BlockState("minecraft:grass_block");
    private static final BlockState DIRT = new BlockState("minecraft:dirt");
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState WATER = new BlockState("minecraft:water");
    private static final BlockState BEDROCK = new BlockState("minecraft:bedrock");

    @Test
    public void aColumnIsBedrockThenRockThenLayersThenFluid() {
        ApiChunk chunk = world().biomeSettings(TestSettings.of(LayersSettings.class,
                "{\"layers\": [{\"palette\": [{\"block\": \"grass_block\"}]}, {\"palette\": [{\"block\": \"dirt\"}]}]}"));

        chunk.generate(new TerrainModule());

        assertEquals("bedrock at the bottom", BEDROCK, chunk.get(3, 0, 4));
        assertEquals("rock below the layers", STONE, chunk.get(3, 5, 4));
        assertEquals("the second layer", DIRT, chunk.get(3, HEIGHT - 1, 4));
        assertEquals("the first layer is the surface", GRASS, chunk.get(3, HEIGHT, 4));
        assertEquals("fluid above the surface", WATER, chunk.get(3, HEIGHT + 1, 4));
        assertEquals("fluid up to the fluid height", WATER, chunk.get(3, FLUID_HEIGHT, 4));
        assertEquals("nothing above the fluid", ApiChunk.AIR, chunk.get(3, FLUID_HEIGHT + 1, 4));
    }

    @Test
    public void aBiomeWithoutLayersHasOneLayerOfGrass() {
        ApiChunk chunk = world();

        chunk.generate(new TerrainModule());

        assertEquals(GRASS, chunk.get(3, HEIGHT, 4));
        assertEquals(STONE, chunk.get(3, HEIGHT - 1, 4));
    }

    @Test
    public void oresOfTheBiomeThenTheRegionThenTheDimensionReplaceTheBlocksTheirBandsReach() {
        ApiChunk chunk = world()
                .biomeSettings(ores("coal_ore", 2, 2))
                .regionSettings(ores("iron_ore", 2, 3))
                .dimensionSettings(ores("gold_ore", 2, 4));

        chunk.generate(new TerrainModule());

        assertEquals("the biome's ore comes first", new BlockState("minecraft:coal_ore"), chunk.get(3, 2, 4));
        assertEquals("then the region's", new BlockState("minecraft:iron_ore"), chunk.get(3, 3, 4));
        assertEquals("then the dimension's", new BlockState("minecraft:gold_ore"), chunk.get(3, 4, 4));
        assertEquals("rock where no ore reaches", STONE, chunk.get(3, 5, 4));
    }

    @Test
    public void aDimensionThatHidesOresPlacesNone() {
        ApiChunk chunk = world().dimensionSettings(ores("gold_ore", 2, 4), new HideOresSettings(true));

        chunk.generate(new TerrainModule());

        assertEquals(STONE, chunk.get(3, 3, 4));
    }

    @Test
    public void aDimensionWithoutBedrockHasRockAtItsBottom() {
        ApiChunk chunk = world().dimensionSettings(TestSettings.of(BedrockSettings.class, "{\"bedrock\": false}"));

        chunk.generate(new TerrainModule());

        assertEquals(STONE, chunk.get(3, 0, 4));
        assertNotEquals(BEDROCK, chunk.get(0, 0, 0));
    }

    @Test
    public void aDimensionStackedOnItselfHangsItsGroundFromTheTop() {
        ApiChunk chunk = new ApiChunk(0, 0, WORLD_HEIGHT).surface((x, z) -> HEIGHT).upperDimension((x, z) -> 50).stackedOnItself();

        chunk.generate(new TerrainModule());

        assertEquals("bedrock caps the upper ground", BEDROCK, chunk.get(3, WORLD_HEIGHT - 1, 4));
        assertEquals("rock under the upper surface", STONE, chunk.get(3, 55, 4));
        assertEquals("the upper ground's face is the biome's layer", GRASS, chunk.get(3, 50, 4));
        assertEquals("nothing between the grounds", ApiChunk.AIR, chunk.get(3, 49, 4));
    }

    private static ApiChunk world() {
        return new ApiChunk(0, 0, WORLD_HEIGHT).surface((x, z) -> HEIGHT).fluidHeight(FLUID_HEIGHT);
    }

    /** Ore generators of one block that give it at every height from {@code min} to {@code max}. */
    private static OreSettings ores(String block, int min, int max) {
        return TestSettings.of(OreSettings.class, "{\"generators\": [{\"palette\": {\"palette\": [{\"block\": \"" + block
                + "\"}]}, \"chanceStyle\": {\"style\": \"STATIC\"}, \"threshold\": 0.5, \"range\": {\"min\": " + min + ", \"max\": " + max + "}}]}");
    }
}
