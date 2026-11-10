package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import art.arcane.iris.core.pack.ContentKeyValidator;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.spi.IrisServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Blocks of plugins like CraftEngine are real block keys to a pack, so validation must not call them unknown. */
public class BukkitRegistriesExternalBlocksTest {
    private static final String PLUGIN_BLOCK = "etopia:pine_log";
    private static final String VANILLA_BLOCK = "minecraft:stone";

    @Before
    public void registerAPluginBlock() {
        ExternalDataSVC external = mock(ExternalDataSVC.class);
        when(external.getAllIdentifiers(DataType.BLOCK)).thenReturn(List.of(Identifier.fromString(PLUGIN_BLOCK)));
        when(external.getAllIdentifiers(DataType.ITEM)).thenReturn(List.of());
        IrisServices.register(ExternalDataSVC.class, external);
    }

    @After
    public void forgetThePluginBlock() {
        IrisServices.remove(ExternalDataSVC.class);
    }

    @Test
    public void blockKeysHoldVanillaAndPluginBlocks() {
        List<String> keys = registriesWithoutAServer().blockKeys();

        assertTrue(keys.contains(VANILLA_BLOCK));
        assertTrue(keys.contains(PLUGIN_BLOCK));
    }

    @Test
    public void validationKnowsAPluginBlock() {
        assertEquals(List.of(), ContentKeyValidator.validate(registriesWithoutAServer(),
                List.of(VANILLA_BLOCK, PLUGIN_BLOCK), List.of(), List.of()));
    }

    /** Bukkit's material registry needs a running server; only the vanilla half of the keys comes from it. */
    private static BukkitRegistries registriesWithoutAServer() {
        return new BukkitRegistries() {
            @Override
            List<String> vanillaBlockKeys() {
                return List.of(VANILLA_BLOCK);
            }

            @Override
            public List<String> itemKeys() {
                return List.of();
            }

            @Override
            public List<String> entityKeys() {
                return List.of();
            }
        };
    }
}
