package art.arcane.iris.core.link.data;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import io.th0rgal.oraxen.api.OraxenBlocks;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.MissingResourceException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class OraxenDataProviderTest {
    @Test
    public void blockResolutionRetainsProviderIdentityForDeferredPlacement() {
        Identifier identifier = new Identifier("oraxen", "ruby_ore");
        BlockData carrier = mock(BlockData.class);
        OraxenDataProvider provider = new OraxenDataProvider();
        try (MockedStatic<OraxenBlocks> blocks = mockStatic(OraxenBlocks.class)) {
            blocks.when(() -> OraxenBlocks.getOraxenBlockData("ruby_ore")).thenReturn(carrier);

            BlockData result = provider.getBlockData(identifier, new KMap<>());

            assertTrue(result instanceof IrisCustomData);
            IrisCustomData custom = (IrisCustomData) result;
            assertSame(carrier, custom.getBase());
            assertEquals(identifier, custom.getCustom());
        }
    }

    @Test
    public void missingBlockDoesNotProduceCarrierData() {
        OraxenDataProvider provider = new OraxenDataProvider();
        try (MockedStatic<OraxenBlocks> blocks = mockStatic(OraxenBlocks.class)) {
            assertThrows(MissingResourceException.class,
                    () -> provider.getBlockData(new Identifier("oraxen", "unknown"), new KMap<>()));
        }
    }

    @Test
    public void unsupportedStateDoesNotResolveDefaultBlockSilently() {
        OraxenDataProvider provider = new OraxenDataProvider();
        KMap<String, String> state = new KMap<>();
        state.put("facing", "north");
        try (MockedStatic<OraxenBlocks> blocks = mockStatic(OraxenBlocks.class)) {
            assertThrows(MissingResourceException.class,
                    () -> provider.getBlockData(new Identifier("oraxen", "ruby_ore"), state));

            blocks.verifyNoInteractions();
        }
    }

    @Test
    public void placementUsesProviderApiAtTargetLocation() {
        OraxenDataProvider provider = new OraxenDataProvider();
        Block block = mock(Block.class);
        Location location = new Location(null, 31, 80, -18);
        when(block.getLocation()).thenReturn(location);
        try (MockedStatic<OraxenBlocks> blocks = mockStatic(OraxenBlocks.class)) {
            provider.processUpdate(mock(Engine.class), block, new Identifier("oraxen", "ruby_ore"));

            blocks.verify(() -> OraxenBlocks.place("ruby_ore", location));
        }
    }

    @Test
    public void blockClaimsRequireExactRegisteredMechanic() {
        OraxenDataProvider provider = new OraxenDataProvider();
        try (MockedStatic<OraxenBlocks> blocks = mockStatic(OraxenBlocks.class)) {
            blocks.when(() -> OraxenBlocks.isOraxenBlock("ruby_ore")).thenReturn(true);

            assertTrue(provider.isValidProvider(new Identifier("oraxen", "ruby_ore"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("oraxen", "ruby_sword"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("nexo", "ruby_ore"), DataType.BLOCK));
            assertFalse(provider.isValidProvider(new Identifier("oraxen", "ruby_ore"), DataType.ENTITY));
            assertTrue(provider.getTypes(DataType.ENTITY).isEmpty());
        }
    }
}
