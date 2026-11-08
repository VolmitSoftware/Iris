package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioToolCodecTest {
    private static final UUID REQUEST = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final ResourceId STICK = ResourceId.parse("minecraft:stick");

    @Test
    public void createsACompleteNamedStickWithLore() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        JigsawStudioToolPayload payload = JigsawStudioToolPayload.membership(
                JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT,
                REQUEST,
                "workcell/corner",
                "village/corner_mossy",
                "village/start",
                3,
                -1);

        ToolItem tool = codec.create(payload);

        assertEquals(STICK, tool.item());
        assertEquals("Jigsaw Studio: Adjust Variant Weight", tool.name());
        assertEquals(List.of(
                "Workcell: workcell/corner",
                "Variant: village/corner_mossy",
                "Pool: village/start [3]",
                "Amount: -1",
                "Right-click to use"), tool.lore());
        assertFalse(tool.glowing());
    }

    @Test
    public void everyPayloadRoundTripsThroughTheItem() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        List<JigsawStudioToolPayload> payloads = List.of(
                JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, REQUEST),
                JigsawStudioToolPayload.workcell(JigsawStudioToolAction.SELECT_WORKCELL, REQUEST, "workcell/end"),
                JigsawStudioToolPayload.membership(
                        JigsawStudioToolAction.ADJUST_VARIANT_WEIGHT,
                        REQUEST,
                        "workcell/corner",
                        "village/corner_mossy",
                        "village/start",
                        3,
                        -1));

        for (JigsawStudioToolPayload payload : payloads) {
            assertEquals(payload, codec.decode(codec.create(payload)).orElseThrow());
        }
    }

    @Test
    public void decodesCurrentSchemaAndDefaultsOptionalFields() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        String payload = "{\"schema\":" + JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION
                + ",\"action\":\"PREVIEW_GRAPH\",\"request\":\"" + REQUEST + "\"}";

        Optional<JigsawStudioToolPayload> decoded = codec.decode(payload);

        assertTrue(decoded.isPresent());
        assertEquals(
                JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, REQUEST),
                decoded.orElseThrow());
    }

    @Test
    public void schemaTwoInvalidatesLegacyBoundTools() {
        assertEquals(2, JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION);
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        String legacy = "{\"schema\":1,\"action\":\"DUPLICATE_VARIANT\",\"request\":\"" + REQUEST + "\"}";

        assertTrue(codec.decode(legacy).isEmpty());
    }

    @Test
    public void rejectsMalformedOrUnsupportedPayloads() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        String unsupported = "{\"schema\":99,\"action\":\"SELECT_WORKCELL\",\"request\":\"" + REQUEST + "\"}";
        String malformed = "{\"schema\":" + JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION
                + ",\"action\":\"NOT_AN_ACTION\",\"request\":\"not-a-uuid\"}";

        assertTrue(codec.decode(unsupported).isEmpty());
        assertTrue(codec.decode(malformed).isEmpty());
        assertTrue(codec.decode("not json").isEmpty());
        assertTrue(codec.decode("").isEmpty());
    }

    @Test
    public void recognizesOnlyValidStickBindings() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        JigsawStudioToolPayload payload = JigsawStudioToolPayload.request(JigsawStudioToolAction.PREVIEW_GRAPH, REQUEST);
        ToolItem tool = codec.create(payload);
        ToolItem stone = new ToolItem(ResourceId.parse("minecraft:stone"), tool.name(), tool.lore(), false, tool.payload());
        ToolItem plainStick = new ToolItem(STICK, "Stick", List.of(), false, "");

        assertTrue(codec.isTool(tool));
        assertFalse(codec.isTool(stone));
        assertFalse(codec.isTool(plainStick));
        assertFalse(codec.isTool(null));
    }

    @Test
    public void refusesToCreateAToolOfAnOldSchema() {
        JigsawStudioToolCodec codec = new JigsawStudioToolCodec();
        JigsawStudioToolPayload old = new JigsawStudioToolPayload(
                1,
                JigsawStudioToolAction.PREVIEW_GRAPH,
                REQUEST,
                "",
                "",
                "",
                JigsawStudioToolPayload.NO_ENTRY_INDEX,
                0);

        assertThrows(IllegalArgumentException.class, () -> codec.create(old));
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
