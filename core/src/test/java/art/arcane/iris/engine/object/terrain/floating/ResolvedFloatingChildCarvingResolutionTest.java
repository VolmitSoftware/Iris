package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingEntry;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ResolvedFloatingChildCarvingResolutionTest {
    @BeforeClass
    public static void setupBukkit() {
        if (Bukkit.getServer() != null) {
            return;
        }

        Server server = mock(Server.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn("IrisTestServer").when(server).getName();
        doReturn("1.0").when(server).getVersion();
        doReturn("1.0").when(server).getBukkitVersion();
        doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, Material.class).name().toLowerCase(Locale.ROOT))).when(server).createBlockData(any(Material.class));
        doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, String.class))).when(server).createBlockData(anyString());
        try {
            Bukkit.setServer(server);
        } catch (Throwable ignored) {
        }
    }

    private static BlockData namedBlockData(String key) {
        String canonical = key.indexOf(':') >= 0 ? key : "minecraft:" + key;
        BlockData data = mock(BlockData.class);
        doReturn(canonical).when(data).getAsString();
        return data;
    }

    @Test
    public void resolveCarvingBiome_loadsBiomeKeyWhenEntryIdMissing() {
        IrisBiome biome = mock(IrisBiome.class);
        Fixture fixture = createFixture(Map.of(), Map.of("carving/mushroom", biome));

        IrisBiome resolved = ResolvedFloatingChild.resolveCarvingBiome("carving/mushroom", fixture.engine, fixture.data);

        assertSame(biome, resolved);
    }

    @Test
    public void resolveCarvingBiome_prefersDimensionCarvingEntryId() {
        IrisBiome entryBiome = mock(IrisBiome.class);
        IrisBiome sameKeyBiome = mock(IrisBiome.class);
        IrisDimensionCarvingEntry entry = new IrisDimensionCarvingEntry();
        entry.setId("global-deepdark-band");
        entry.setBiome("carving/standard-deepdark");

        Map<String, IrisDimensionCarvingEntry> entries = new HashMap<>();
        entries.put("global-deepdark-band", entry);

        Map<String, IrisBiome> biomes = new HashMap<>();
        biomes.put("carving/standard-deepdark", entryBiome);
        biomes.put("global-deepdark-band", sameKeyBiome);

        Fixture fixture = createFixture(entries, biomes);

        IrisBiome resolved = ResolvedFloatingChild.resolveCarvingBiome("global-deepdark-band", fixture.engine, fixture.data);

        assertSame(entryBiome, resolved);
    }

    private Fixture createFixture(Map<String, IrisDimensionCarvingEntry> entries, Map<String, IrisBiome> biomes) {
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        for (Map.Entry<String, IrisBiome> biome : biomes.entrySet()) {
            doReturn(biome.getValue()).when(biomeLoader).load(biome.getKey());
        }

        IrisData data = mock(IrisData.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();

        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(entries).when(dimension).getCarvingEntryIndex();
        doReturn(new KList<>(entries.values())).when(dimension).getCarving();

        Engine engine = mock(Engine.class);
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();

        return new Fixture(engine, data);
    }

    private static final class Fixture {
        private final Engine engine;
        private final IrisData data;

        private Fixture(Engine engine, IrisData data) {
            this.engine = engine;
            this.data = data;
        }
    }
}
