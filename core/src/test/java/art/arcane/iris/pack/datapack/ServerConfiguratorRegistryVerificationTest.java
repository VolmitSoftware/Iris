package art.arcane.iris.pack.datapack;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Bukkit;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ServerConfiguratorRegistryVerificationTest {
    @Test
    public void freshlyCompiledRegistryMissReportsOneRestartWarning() {
        VerificationFixture fixture = missingRegistryFixture();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(fixture.binding());
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            logging.clearInvocations();

            assertTrue(ServerConfigurator.verifyDataPacksPost(Stream.of(fixture.data())));

            logging.verify(() -> IrisLogging.debug("Checking Pack: packs/overworld"));
            logging.verify(() -> IrisLogging.warn(ServerConfigurator.POST_COMPILE_RESTART_WARNING));
            logging.verifyNoMoreInteractions();
        }
    }

    @Test
    public void worldCreationRegistryMissRetainsFullOperationalError() {
        VerificationFixture fixture = missingRegistryFixture();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
             MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(fixture.binding());
            logging.clearInvocations();

            assertFalse(ServerConfigurator.verifyDataPackInstalled(fixture.dimension()));

            logging.verify(() -> IrisLogging.warn(
                    "The Biome overworld:missing is not registered on the server."));
            logging.verify(() -> IrisLogging.warn(
                    "The Dimension Type for packs/overworld/dimensions/overworld.json is not registered on the server."));
            logging.verify(() -> IrisLogging.error(
                    "The Pack overworld is INCAPABLE of generating custom biomes"));
            logging.verify(() -> IrisLogging.error(
                    "If not done automatically, restart your server before generating with this pack!"));
            logging.verifyNoMoreInteractions();
        }
    }

    @SuppressWarnings("unchecked")
    private static VerificationFixture missingRegistryFixture() {
        IrisBiomeCustom customBiome = mock(IrisBiomeCustom.class);
        when(customBiome.getId()).thenReturn("missing");

        IrisBiome biome = mock(IrisBiome.class);
        when(biome.isCustom()).thenReturn(true);
        when(biome.getCustomDerivitives()).thenReturn(new KList<>(customBiome));

        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("packs/overworld"));

        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getAllBiomes(any())).thenReturn(new KList<>(biome));
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(dimension.getCustomBiomeKey("missing")).thenReturn("overworld:missing");
        when(dimension.getLoadFile()).thenReturn(
                new File("packs/overworld/dimensions/overworld.json"));
        when(dimension.getDimensionTypeKey()).thenReturn("iris:overworld");
        when(dimension.getLoader()).thenReturn(data);
        when(data.customBiomeResourceKey(dimension, customBiome)).thenReturn("overworld:missing");

        ResourceLoader<IrisDimension> loader = mock(ResourceLoader.class);
        when(loader.getPossibleKeys()).thenReturn(new String[]{"overworld"});
        when(loader.loadAll(any(String[].class))).thenReturn(new KList<>(dimension));
        when(data.getDimensionLoader()).thenReturn(loader);

        INMSBinding binding = mock(INMSBinding.class);
        when(binding.supportsDataPacks()).thenReturn(true);
        when(binding.getCustomBiomeBaseFor("overworld:missing")).thenReturn(null);
        when(binding.missingDimensionTypes("iris:overworld")).thenReturn(true);
        return new VerificationFixture(data, dimension, binding);
    }

    private record VerificationFixture(
            IrisData data,
            IrisDimension dimension,
            INMSBinding binding
    ) {
    }
}
