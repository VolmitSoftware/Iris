package art.arcane.iris.studio.jigsaw;


import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioToolCodecTest {
    private static final UUID REQUEST = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Test
    public void bindsACompleteNamedStickPayload() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        ItemStack tool = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        JigsawStudioToolPayload payload = JigsawStudioToolPayload.membership(
                JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT,
                REQUEST,
                "workcell/corner",
                "village/corner_mossy",
                "village/start",
                3,
                -1);
        when(tool.getType()).thenReturn(Material.STICK);
        when(tool.getItemMeta()).thenReturn(meta);
        when(tool.setItemMeta(meta)).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(data);

        codec.bind(tool, payload);

        verify(data).set(
                JigsawStudioToolCodec.SCHEMA_KEY,
                PersistentDataType.INTEGER,
                JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION);
        verify(data).set(
                JigsawStudioToolCodec.ACTION_KEY,
                PersistentDataType.STRING,
                JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT.name());
        verify(data).set(
                JigsawStudioToolCodec.REQUEST_KEY,
                PersistentDataType.STRING,
                REQUEST.toString());
        verify(data).set(
                JigsawStudioToolCodec.WORKCELL_KEY,
                PersistentDataType.STRING,
                "workcell/corner");
        verify(data).set(
                JigsawStudioToolCodec.PIECE_KEY,
                PersistentDataType.STRING,
                "village/corner_mossy");
        verify(data).set(
                JigsawStudioToolCodec.POOL_KEY,
                PersistentDataType.STRING,
                "village/start");
        verify(data).set(JigsawStudioToolCodec.ENTRY_INDEX_KEY, PersistentDataType.INTEGER, 3);
        verify(data).set(JigsawStudioToolCodec.AMOUNT_KEY, PersistentDataType.INTEGER, -1);
        verify(meta).setDisplayName(ChatColor.AQUA + "Jigsaw Studio: Adjust Variant Weight");
        verify(meta).setLore(argThat(lore -> lore.contains(ChatColor.GRAY + "Amount: -1")
                && lore.contains(ChatColor.YELLOW + "Right-click to use")));
        verify(tool).setItemMeta(meta);
    }

    @Test
    public void decodesCurrentSchemaAndDefaultsOptionalFields() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(JigsawStudioToolCodec.SCHEMA_KEY, PersistentDataType.INTEGER))
                .thenReturn(JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION);
        when(data.get(JigsawStudioToolCodec.ACTION_KEY, PersistentDataType.STRING))
                .thenReturn(JigsawStudioToolAction.PREVIEW_GRAPH.name());
        when(data.get(JigsawStudioToolCodec.REQUEST_KEY, PersistentDataType.STRING))
                .thenReturn(REQUEST.toString());

        Optional<JigsawStudioToolPayload> decoded = codec.decode(data);

        assertTrue(decoded.isPresent());
        assertEquals(
                JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, REQUEST),
                decoded.orElseThrow());
    }

    @Test
    public void schemaTwoInvalidatesLegacyBoundTools() {
        assertEquals(2, JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION);
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        PersistentDataContainer legacy = mock(PersistentDataContainer.class);
        when(legacy.get(JigsawStudioToolCodec.SCHEMA_KEY, PersistentDataType.INTEGER)).thenReturn(1);
        when(legacy.get(JigsawStudioToolCodec.ACTION_KEY, PersistentDataType.STRING))
                .thenReturn(JigsawStudioToolAction.DUPLICATE_VARIANT.name());
        when(legacy.get(JigsawStudioToolCodec.REQUEST_KEY, PersistentDataType.STRING))
                .thenReturn(REQUEST.toString());

        assertTrue(codec.decode(legacy).isEmpty());
    }

    @Test
    public void rejectsMalformedOrUnsupportedPersistentBindings() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        PersistentDataContainer unsupported = mock(PersistentDataContainer.class);
        when(unsupported.get(JigsawStudioToolCodec.SCHEMA_KEY, PersistentDataType.INTEGER)).thenReturn(99);
        when(unsupported.get(JigsawStudioToolCodec.ACTION_KEY, PersistentDataType.STRING))
                .thenReturn(JigsawStudioToolAction.SELECT_WORKCELL.name());
        when(unsupported.get(JigsawStudioToolCodec.REQUEST_KEY, PersistentDataType.STRING))
                .thenReturn(REQUEST.toString());
        assertTrue(codec.decode(unsupported).isEmpty());

        PersistentDataContainer malformed = mock(PersistentDataContainer.class);
        when(malformed.get(JigsawStudioToolCodec.SCHEMA_KEY, PersistentDataType.INTEGER))
                .thenReturn(JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION);
        when(malformed.get(JigsawStudioToolCodec.ACTION_KEY, PersistentDataType.STRING))
                .thenReturn("NOT_AN_ACTION");
        when(malformed.get(JigsawStudioToolCodec.REQUEST_KEY, PersistentDataType.STRING))
                .thenReturn("not-a-uuid");
        assertTrue(codec.decode(malformed).isEmpty());
    }

    @Test
    public void recognizesOnlyValidStickBindings() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);

        assertFalse(codec.isTool(stone));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.bind(
                        stone,
                        JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, REQUEST)));
    }

    @Test
    public void payloadValidatesSchemaIndexesAndFieldBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JigsawStudioToolPayload(
                        0,
                        JigsawStudioToolAction.SELECT_WORKCELL,
                        REQUEST,
                        "",
                        "",
                        "",
                        JigsawStudioToolPayload.NO_ENTRY_INDEX,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JigsawStudioToolPayload(
                        JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION,
                        JigsawStudioToolAction.SELECT_WORKCELL,
                        REQUEST,
                        "",
                        "",
                        "",
                        -2,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> JigsawStudioToolPayload.workcell(
                        JigsawStudioToolAction.SELECT_WORKCELL,
                        REQUEST,
                        "x".repeat(513)));
    }

    @Test
    public void marksOnlyDestructiveActionsForConfirmation() {
        assertFalse(JigsawStudioToolAction.OPEN_MENU.destructive());
        assertFalse(JigsawStudioToolAction.TOGGLE_WORKCELL.destructive());
        assertFalse(JigsawStudioToolAction.RESIZE_WORKCELL.destructive());
        assertFalse(JigsawStudioToolAction.ADJUST_VARIANT_CHANCE.destructive());
        assertFalse(JigsawStudioToolAction.SET_THEME.destructive());
        assertFalse(JigsawStudioToolAction.SET_PIECE_RULES.destructive());
        assertFalse(JigsawStudioToolAction.TOGGLE_REQUIRE_CAPS.destructive());
        assertFalse(JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT.destructive());
        assertTrue(JigsawStudioToolAction.UNLINK_MEMBERSHIP.destructive());
        assertTrue(JigsawStudioToolAction.DELETE_VARIANT.destructive());
        assertTrue(JigsawStudioToolAction.DELETE_PROJECT.destructive());
    }
}
