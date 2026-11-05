package art.arcane.iris.engine.terrain;

import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.modules.terrain.LayerBlocks;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LayerBlocksSeededStyleTest {
    private static final String ZOOMED = "{\"zoom\": 4, \"style\": {\"style\": \"SIMPLEX\"}, \"palette\": [{\"block\": \"stone\"}, {\"block\": \"dirt\"}]}";
    private static final String SEEDED = "{\"zoom\": 4, \"style\": {\"style\": \"SIMPLEX\", \"seed\": 9}, \"palette\": [{\"block\": \"stone\"}, {\"block\": \"dirt\"}]}";

    @Test
    public void aSeededLayerStyleIsReadFlatInPlainBlocks() {
        List<String> reads = new ArrayList<>();
        LayerBlocks blocks = TestLayerBlocks.of(resourcesRecording(reads)).get();
        Gson gson = new Gson();

        blocks.pick(gson.fromJson(ZOOMED, PaletteLayer.class), new RNG(1), 0, 40, 3, -8);
        blocks.pick(gson.fromJson(SEEDED, PaletteLayer.class), new RNG(1), 0, 40, 3, -8);

        assertEquals(List.of("10.0,0.75,-2.0", "40.0,-8.0"), reads);
    }

    private static PackResources resourcesRecording(List<String> reads) {
        BlockRegistry registry = mock(BlockRegistry.class);
        when(registry.resolve(any(BlockSpec.class))).thenReturn(BlockState.parse("minecraft:stone"));
        NoiseFactory recording = (style, seed) -> new NoiseSampler() {
            @Override
            public double sample(double worldX, double worldZ) {
                reads.add(worldX + "," + worldZ);
                return 0;
            }

            @Override
            public double sample(double worldX, double y, double worldZ) {
                reads.add(worldX + "," + y + "," + worldZ);
                return 0;
            }
        };
        PackResources resources = mock(PackResources.class);
        when(resources.blocks()).thenReturn(registry);
        when(resources.noise()).thenReturn(recording);
        return resources;
    }
}
