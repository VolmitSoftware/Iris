package art.arcane.iris.core.link.data;

import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ExternalDataProviderDefaultsTest {
    private static final Identifier RUBY = new Identifier("testplugin", "ruby_ore");

    @Test
    public void theProviderRemembersThePluginItSpeaksFor() {
        assertEquals("TestPlugin", new StatelessProvider().getPluginId());
    }

    @Test
    public void aProviderWithoutAPluginIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new StatelessProvider(null));
    }

    @Test
    public void anUnimplementedProviderRefusesEveryResourceKind() {
        StatelessProvider provider = new StatelessProvider();

        MissingResourceException blockFailure =
                assertThrows(MissingResourceException.class, () -> provider.getBlockData(RUBY));
        MissingResourceException itemFailure =
                assertThrows(MissingResourceException.class, () -> provider.getItemStack(RUBY));

        assertEquals("testplugin", blockFailure.getClassName());
        assertEquals("ruby_ore", blockFailure.getKey());
        assertEquals("testplugin", itemFailure.getClassName());
        assertEquals("ruby_ore", itemFailure.getKey());
        assertThrows(MissingResourceException.class, () -> provider.spawnMob(mock(Location.class), RUBY));
    }

    @Test
    public void anUnimplementedProviderExposesNoBlockProperties() {
        assertTrue(new StatelessProvider().getBlockProperties(RUBY).isEmpty());
    }

    @Test
    public void theStatelessLookupsDelegateWithAnEmptyStateMap() {
        DelegationProvider provider = new DelegationProvider();

        provider.getBlockData(RUBY);
        provider.getItemStack(RUBY);

        assertEquals(List.of("block:0", "item:0"), provider.calls);
    }

    @Test
    public void deferredPlacementIsANoOpUntilAProviderImplementsIt() {
        new StatelessProvider().processUpdate(null, null, RUBY);
    }

    private static class StatelessProvider extends ExternalDataProvider {
        private StatelessProvider() {
            super("TestPlugin");
        }

        private StatelessProvider(String pluginId) {
            super(pluginId);
        }

        @Override
        public void init() {
        }

        @Override
        public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
            return List.of();
        }

        @Override
        public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
            return false;
        }
    }

    private static final class DelegationProvider extends StatelessProvider {
        private final List<String> calls = new ArrayList<>();

        @Override
        public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) {
            calls.add("block:" + state.size());
            return mock(BlockData.class);
        }

        @Override
        public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) {
            calls.add("item:" + customNbt.size());
            return mock(ItemStack.class);
        }
    }
}
