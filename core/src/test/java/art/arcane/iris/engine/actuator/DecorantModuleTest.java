package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorateSettings;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.iris.engine.object.TestSettings;
import com.google.gson.Gson;
import com.volmit.block.BlockState;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Decorating puts a biome's decorators on its surface, unless the dimension says not to or the biome has none. */
public class DecorantModuleTest {
    private static final int GROUND = 8;
    private static final BlockState GRASS_BLOCK = new BlockState("minecraft:grass_block");
    private static final BlockState POPPY = new BlockState("minecraft:poppy");
    private static final DecorantSettings POPPIES = new DecorantSettings(List.of(new Gson().fromJson("{\"chance\": 1, \"palette\": [{\"block\": \"poppy\"}]}", Decorator.class)));

    @Test
    public void everyColumnOfABiomeWithDecoratorsGetsOne() {
        ApiChunk chunk = ground().biomeSettings(POPPIES);

        chunk.generate(new DecorantModule());

        assertEquals(256, countAt(chunk, GROUND + 1, POPPY));
        assertEquals("on top of the ground, not in it", 0, countAt(chunk, GROUND, POPPY));
    }

    @Test
    public void aDimensionThatDoesNotDecorateIsLeftAlone() {
        ApiChunk chunk = ground().biomeSettings(POPPIES).dimensionSettings(TestSettings.of(DecorateSettings.class, "{\"decorate\": false}"));

        chunk.generate(new DecorantModule());

        assertEquals(0, countAt(chunk, GROUND + 1, POPPY));
    }

    @Test
    public void aBiomeWithoutDecoratorsGetsNothingPlaced() {
        ApiChunk chunk = ground();

        chunk.generate(new DecorantModule());

        assertEquals(0, countAt(chunk, GROUND + 1, POPPY));
    }

    private static ApiChunk ground() {
        ApiChunk chunk = new ApiChunk(0, 0, 32).surface((x, z) -> GROUND);
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                for (int y = 0; y <= GROUND; y++) {
                    chunk.set(x, y, z, GRASS_BLOCK);
                }
            }
        }
        return chunk;
    }

    private static int countAt(ApiChunk chunk, int y, BlockState block) {
        int count = 0;
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                count += chunk.get(x, y, z).equals(block) ? 1 : 0;
            }
        }
        return count;
    }
}
