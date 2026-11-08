package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolAction;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Turns a studio tool payload into the tool item a player holds, and the item back into the payload. */
public final class JigsawStudioToolCodec {
    static final ResourceId TOOL_ITEM = ResourceId.parse("minecraft:stick");
    static final String NAME_PREFIX = "Jigsaw Studio: ";
    static final String SCHEMA_FIELD = "schema";
    static final String ACTION_FIELD = "action";
    static final String REQUEST_FIELD = "request";
    static final String WORKCELL_FIELD = "workcell";
    static final String PIECE_FIELD = "piece";
    static final String POOL_FIELD = "pool";
    static final String ENTRY_INDEX_FIELD = "entryIndex";
    static final String AMOUNT_FIELD = "amount";

    private static final Gson GSON = new Gson();

    public ToolItem create(JigsawStudioToolPayload payload) {
        JigsawStudioToolPayload binding = requireCurrentSchema(payload);
        return new ToolItem(
                TOOL_ITEM,
                NAME_PREFIX + binding.action().displayName(),
                lore(binding),
                false,
                encode(binding));
    }

    public Optional<JigsawStudioToolPayload> decode(ToolItem tool) {
        if (tool == null || !TOOL_ITEM.equals(tool.item())) {
            return Optional.empty();
        }
        return decode(tool.payload());
    }

    public boolean isTool(ToolItem tool) {
        return decode(tool).isPresent();
    }

    String encode(JigsawStudioToolPayload payload) {
        JigsawStudioToolPayload binding = requireCurrentSchema(payload);
        JsonObject json = new JsonObject();
        json.addProperty(SCHEMA_FIELD, binding.schemaVersion());
        json.addProperty(ACTION_FIELD, binding.action().name());
        json.addProperty(REQUEST_FIELD, binding.requestId().toString());
        json.addProperty(WORKCELL_FIELD, binding.workcellId());
        json.addProperty(PIECE_FIELD, binding.pieceKey());
        json.addProperty(POOL_FIELD, binding.poolKey());
        json.addProperty(ENTRY_INDEX_FIELD, binding.entryIndex());
        json.addProperty(AMOUNT_FIELD, binding.amount());
        return GSON.toJson(json);
    }

    Optional<JigsawStudioToolPayload> decode(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonObject json = GSON.fromJson(payload, JsonObject.class);
            if (json == null
                    || !json.has(SCHEMA_FIELD)
                    || !json.has(ACTION_FIELD)
                    || !json.has(REQUEST_FIELD)
                    || json.get(SCHEMA_FIELD).getAsInt() != JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION) {
                return Optional.empty();
            }
            return Optional.of(new JigsawStudioToolPayload(
                    json.get(SCHEMA_FIELD).getAsInt(),
                    JigsawStudioToolAction.valueOf(json.get(ACTION_FIELD).getAsString()),
                    UUID.fromString(json.get(REQUEST_FIELD).getAsString()),
                    optionalString(json, WORKCELL_FIELD),
                    optionalString(json, PIECE_FIELD),
                    optionalString(json, POOL_FIELD),
                    json.has(ENTRY_INDEX_FIELD)
                            ? json.get(ENTRY_INDEX_FIELD).getAsInt()
                            : JigsawStudioToolPayload.NO_ENTRY_INDEX,
                    json.has(AMOUNT_FIELD) ? json.get(AMOUNT_FIELD).getAsInt() : 0));
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                 | JsonParseException exception) {
            return Optional.empty();
        }
    }

    private static JigsawStudioToolPayload requireCurrentSchema(JigsawStudioToolPayload payload) {
        JigsawStudioToolPayload binding = Objects.requireNonNull(payload, "Jigsaw Studio tool payload");
        if (binding.schemaVersion() != JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Jigsaw Studio tool schema "
                    + binding.schemaVersion());
        }
        return binding;
    }

    private static String optionalString(JsonObject json, String field) {
        return json.has(field) ? json.get(field).getAsString() : "";
    }

    private static List<String> lore(JigsawStudioToolPayload payload) {
        List<String> lore = new ArrayList<>();
        if (!payload.workcellId().isEmpty()) {
            lore.add("Workcell: " + payload.workcellId());
        }
        if (!payload.pieceKey().isEmpty()) {
            lore.add("Variant: " + payload.pieceKey());
        }
        if (!payload.poolKey().isEmpty()) {
            lore.add("Pool: " + payload.poolKey()
                    + (payload.entryIndex() < 0 ? "" : " [" + payload.entryIndex() + "]"));
        }
        if (payload.amount() != 0) {
            lore.add("Amount: " + payload.amount());
        }
        lore.add(payload.action().destructive()
                ? "Right-click twice to confirm"
                : "Right-click to use");
        return List.copyOf(lore);
    }
}
