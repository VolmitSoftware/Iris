package art.arcane.iris.integration.data;

import art.arcane.iris.integration.Identifier;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProviderNamespaceClaimTest {
    @Test
    public void ecoItemsClaimsOnlyItemsInItsOwnNamespace() {
        EcoItemsDataProvider provider = new EcoItemsDataProvider();

        assertTrue(provider.isValidProvider(new Identifier("ecoitems", "ruby"), DataType.ITEM));
        assertTrue(provider.isValidProvider(new Identifier("EcoItems", "ruby"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("ecoitems", "ruby"), DataType.BLOCK));
        assertFalse(provider.isValidProvider(new Identifier("ecoitems", "ruby"), DataType.ENTITY));
        assertFalse(provider.isValidProvider(new Identifier("oraxen", "ruby"), DataType.ITEM));
    }

    @Test
    public void executableItemsClaimsOnlyItemsInItsOwnNamespace() {
        ExecutableItemsDataProvider provider = new ExecutableItemsDataProvider();

        assertTrue(provider.isValidProvider(new Identifier("executable_items", "wand"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("executable_items", "wand"), DataType.BLOCK));
        assertFalse(provider.isValidProvider(new Identifier("executableitems", "wand"), DataType.ITEM));
    }

    @Test
    public void kgeneratorsClaimsItemsWithoutConsultingTheGeneratorRegistry() {
        KGeneratorsDataProvider provider = new KGeneratorsDataProvider();

        assertTrue(provider.isValidProvider(new Identifier("kgenerators", "cobble"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("kgenerators", "cobble"), DataType.ENTITY));
        assertFalse(provider.isValidProvider(new Identifier("oraxen", "cobble"), DataType.ITEM));
    }

    @Test
    public void mmoItemsEncodesItsItemTypeInTheNamespace() {
        MMOItemsDataProvider provider = new MMOItemsDataProvider();

        assertTrue(provider.isValidProvider(new Identifier("mmoitems_sword", "excalibur"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("mmoitems", "excalibur"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("mmoitems_sword", "excalibur"), DataType.ENTITY));
        assertFalse(provider.isValidProvider(new Identifier("MMOITEMS", "1"), DataType.BLOCK));
    }

    @Test
    public void mythicCrucibleClaimsItemsWithoutConsultingItsItemManager() {
        MythicCrucibleDataProvider provider = new MythicCrucibleDataProvider();

        assertTrue(provider.isValidProvider(new Identifier("crucible", "ruby"), DataType.ITEM));
        assertTrue(provider.isValidProvider(new Identifier("Crucible", "ruby"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("crucible", "ruby"), DataType.ENTITY));
        assertFalse(provider.isValidProvider(new Identifier("oraxen", "ruby"), DataType.ITEM));
    }

    @Test
    public void oraxenRefusesForeignNamespacesBeforeTouchingItsApi() {
        OraxenDataProvider provider = new OraxenDataProvider();

        assertFalse(provider.isValidProvider(new Identifier("nexo", "ruby_ore"), DataType.BLOCK));
        assertFalse(provider.isValidProvider(new Identifier("nexo", "ruby_ore"), DataType.ITEM));
        assertEquals(List.of(), provider.getTypes(DataType.ENTITY));
    }

    @Test
    public void nexoRefusesForeignNamespacesBeforeTouchingItsApi() {
        NexoDataProvider provider = new NexoDataProvider();

        assertFalse(provider.isValidProvider(new Identifier("oraxen", "lamp"), DataType.BLOCK));
        assertFalse(provider.isValidProvider(new Identifier("oraxen", "lamp"), DataType.ITEM));
        assertEquals(List.of(), provider.getTypes(DataType.ENTITY));
    }

    @Test
    public void itemsAdderClaimsNothingBeforeItsRegistryHasLoaded() {
        ItemAdderDataProvider provider = new ItemAdderDataProvider();

        assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.ITEM));
        assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.BLOCK));
        assertFalse(provider.isValidProvider(new Identifier("rocks", "ruby_ore"), DataType.ENTITY));
        assertTrue(provider.getTypes(DataType.ITEM).isEmpty());
        assertTrue(provider.getTypes(DataType.BLOCK).isEmpty());
    }
}
