package art.arcane.iris.integration.data;

import art.arcane.iris.integration.ExternalDataProvider;
import art.arcane.iris.integration.Identifier;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class DataTypeProbeContractTest {
    private static final Identifier RUBY = new Identifier("testplugin", "ruby_ore");

    @Test
    public void anUnclaimedIdentifierIsRejectedWithoutProbingTheProvider() {
        RecordingProvider provider = new RecordingProvider(false, true, true);

        assertFalse(DataType.BLOCK.test(provider, RUBY));
        assertFalse(DataType.ITEM.test(provider, RUBY));
        assertFalse(DataType.ENTITY.test(provider, RUBY));
        assertEquals(List.of(), provider.probes);
    }

    @Test
    public void aClaimedBlockIsProbedThroughTheStatelessBlockLookup() {
        RecordingProvider provider = new RecordingProvider(true, true, true);

        assertTrue(DataType.BLOCK.test(provider, RUBY));
        assertEquals(List.of("block"), provider.probes);
    }

    @Test
    public void aClaimedItemIsProbedThroughTheStatelessItemLookup() {
        RecordingProvider provider = new RecordingProvider(true, true, true);

        assertTrue(DataType.ITEM.test(provider, RUBY));
        assertEquals(List.of("item"), provider.probes);
    }

    @Test
    public void aClaimedEntityIsAcceptedOnTheClaimAlone() {
        RecordingProvider provider = new RecordingProvider(true, false, false);

        assertTrue(DataType.ENTITY.test(provider, RUBY));
        assertEquals(List.of(), provider.probes);
    }

    @Test
    public void aMissingResourceDowngradesTheClaimInsteadOfFailing() {
        RecordingProvider provider = new RecordingProvider(true, false, false);

        assertFalse(DataType.BLOCK.test(provider, RUBY));
        assertFalse(DataType.ITEM.test(provider, RUBY));
        assertEquals(List.of("block", "item"), provider.probes);
    }

    @Test
    public void anUnexpectedProviderFailureIsNotSwallowed() {
        ExplodingProvider provider = new ExplodingProvider();

        assertThrows(IllegalStateException.class, () -> DataType.BLOCK.test(provider, RUBY));
    }

    @Test
    public void thePredicateFormFiltersEveryElementThroughTheSameProvider() {
        RecordingProvider provider = new RecordingProvider(true, true, true);
        List<Identifier> claimed = List.of(RUBY, new Identifier("other", "quartz"));

        List<Identifier> filtered = claimed.stream().filter(DataType.BLOCK.asPredicate(provider)).toList();

        assertEquals(claimed, filtered);
        assertEquals(List.of("block", "block"), provider.probes);
    }

    private static final class RecordingProvider extends ExternalDataProvider {
        private final List<String> probes = new ArrayList<>();
        private final boolean claims;
        private final boolean resolvesBlocks;
        private final boolean resolvesItems;

        private RecordingProvider(boolean claims, boolean resolvesBlocks, boolean resolvesItems) {
            super("TestPlugin");
            this.claims = claims;
            this.resolvesBlocks = resolvesBlocks;
            this.resolvesItems = resolvesItems;
        }

        @Override
        public void init() {
        }

        @Override
        public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) {
            probes.add("block");
            if (!resolvesBlocks) {
                throw new MissingResourceException("no block", blockId.namespace(), blockId.key());
            }
            return mock(BlockData.class);
        }

        @Override
        public @NotNull ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) {
            probes.add("item");
            if (!resolvesItems) {
                throw new MissingResourceException("no item", itemId.namespace(), itemId.key());
            }
            return mock(ItemStack.class);
        }

        @Override
        public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
            return List.of();
        }

        @Override
        public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
            return claims;
        }
    }

    private static final class ExplodingProvider extends ExternalDataProvider {
        private ExplodingProvider() {
            super("TestPlugin");
        }

        @Override
        public void init() {
        }

        @Override
        public @NotNull BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) {
            throw new IllegalStateException("the provider backend is broken");
        }

        @Override
        public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
            return List.of();
        }

        @Override
        public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
            return true;
        }
    }
}
