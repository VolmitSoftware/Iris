package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvedDecoratorTest {
    private static final long SEED = 1337L;
    /** Taken from {@code IrisDecorator} (now deleted) picking the same blocks with the same seed, before decorators moved into the module. */
    private static final int PICKS_LENGTH = 246533;
    private static final int PICKS_HASH = 1275397037;
    private static final String[] DECORATORS = {
            "{\"chance\":0.5,\"palette\":[{\"block\":\"grass\"},{\"block\":\"fern\",\"weight\":2}],\"style\":{\"style\":\"SIMPLEX\",\"zoom\":2},"
                    + "\"variance\":{\"style\":\"PERLIN\",\"zoom\":3},\"stackMin\":1,\"stackMax\":4,\"heightVariance\":{\"style\":\"SIMPLEX\",\"zoom\":5},"
                    + "\"topPalette\":[{\"block\":\"poppy\"},{\"block\":\"dandelion\"}],\"topThreshold\":0.6}",
            "{}",
            "{\"chance\":1,\"palette\":[{\"block\":\"cactus\"}],\"topPalette\":[{\"block\":\"cactus_flower\"},{\"block\":\"stone\"}],\"stackMin\":2,\"stackMax\":2}",
            "{\"chance\":0.8,\"palette\":[{\"block\":\"kelp\"},{\"block\":\"seagrass\"},{\"block\":\"tall_seagrass\"}],\"stackMin\":2,\"stackMax\":9}",
    };

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyDecoratorPicksAsIrisDecoratorDid() {
        UsableBlockPlatform.bind();
        IrisData data = data();
        Engine engine = engine(data);
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getName()).thenReturn("test");
        Gson gson = new Gson();
        StringBuilder transcript = new StringBuilder();
        for (String json : DECORATORS) {
            ResolvedDecorator resolved = ResolvedDecorator.of(gson.fromJson(json, Decorator.class), engine, SEED);
            for (int x = -40; x < 40; x += 3) {
                for (int z = -40; z < 40; z += 5) {
                    transcript.append(resolved.passesChanceGate(x, z)).append(resolved.stackHeight(x, z)).append(key(resolved.pickBlock(x, z)))
                            .append(key(resolved.pickTopBlock(x, z)));
                    for (int y = 60; y < 66; y += 2) {
                        transcript.append(key(resolved.blockAt(biome, x, y, z))).append(key(resolved.topBlockAt(biome, x, y, z)));
                    }
                }
            }
        }
        assertTrue("both outcomes of the chance gate occur", transcript.indexOf("true") >= 0 && transcript.indexOf("false") >= 0);
        assertTrue("the palettes' blocks are picked", transcript.indexOf("minecraft:fern") >= 0 && transcript.indexOf("minecraft:tall_seagrass") >= 0
                && transcript.indexOf("minecraft:cactus_flower") >= 0 && transcript.indexOf("minecraft:dandelion") >= 0);
        assertEquals(PICKS_LENGTH, transcript.length());
        assertEquals(PICKS_HASH, transcript.toString().hashCode());
    }

    private static String key(PlatformBlockState state) {
        return state == null ? null : state.key();
    }

    private static IrisData data() {
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        return data;
    }

    private static Engine engine(IrisData data) {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        return engine;
    }
}
