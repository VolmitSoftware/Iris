package art.arcane.iris.studio.jigsaw;


import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JigsawStudioToolCodec {
    static final NamespacedKey SCHEMA_KEY = new NamespacedKey("iris", "jigsaw_tool_schema");
    static final NamespacedKey ACTION_KEY = new NamespacedKey("iris", "jigsaw_tool_action");
    static final NamespacedKey REQUEST_KEY = new NamespacedKey("iris", "jigsaw_tool_request");
    static final NamespacedKey WORKCELL_KEY = new NamespacedKey("iris", "jigsaw_tool_workcell");
    static final NamespacedKey PIECE_KEY = new NamespacedKey("iris", "jigsaw_tool_piece");
    static final NamespacedKey POOL_KEY = new NamespacedKey("iris", "jigsaw_tool_pool");
    static final NamespacedKey ENTRY_INDEX_KEY = new NamespacedKey("iris", "jigsaw_tool_entry_index");
    static final NamespacedKey AMOUNT_KEY = new NamespacedKey("iris", "jigsaw_tool_amount");

    public ItemStack create(JigsawStudioToolPayload payload) {
        ItemStack tool = new ItemStack(Material.STICK);
        bind(tool, payload);
        return tool;
    }

    public void bind(ItemStack tool, JigsawStudioToolPayload payload) {
        ItemStack item = Objects.requireNonNull(tool, "Jigsaw Studio tool item");
        JigsawStudioToolPayload binding = requireCurrentSchema(payload);
        if (item.getType() != Material.STICK) {
            throw new IllegalArgumentException("Jigsaw Studio tools must use a stick");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Jigsaw Studio tool stick has no item metadata");
        }
        write(meta.getPersistentDataContainer(), binding);
        meta.setDisplayName(ChatColor.AQUA + "Jigsaw Studio: " + binding.action().displayName());
        meta.setLore(lore(binding));
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Jigsaw Studio tool metadata was rejected");
        }
    }

    public Optional<JigsawStudioToolPayload> decode(ItemStack tool) {
        if (tool == null || tool.getType() != Material.STICK || !tool.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = tool.getItemMeta();
        return meta == null ? Optional.empty() : decode(meta.getPersistentDataContainer());
    }

    public boolean isTool(ItemStack tool) {
        return decode(tool).isPresent();
    }

    void write(PersistentDataContainer container, JigsawStudioToolPayload payload) {
        PersistentDataContainer data = Objects.requireNonNull(
                container,
                "Jigsaw Studio tool persistent data");
        JigsawStudioToolPayload binding = requireCurrentSchema(payload);
        data.set(SCHEMA_KEY, PersistentDataType.INTEGER, binding.schemaVersion());
        data.set(ACTION_KEY, PersistentDataType.STRING, binding.action().name());
        data.set(REQUEST_KEY, PersistentDataType.STRING, binding.requestId().toString());
        data.set(WORKCELL_KEY, PersistentDataType.STRING, binding.workcellId());
        data.set(PIECE_KEY, PersistentDataType.STRING, binding.pieceKey());
        data.set(POOL_KEY, PersistentDataType.STRING, binding.poolKey());
        data.set(ENTRY_INDEX_KEY, PersistentDataType.INTEGER, binding.entryIndex());
        data.set(AMOUNT_KEY, PersistentDataType.INTEGER, binding.amount());
    }

    Optional<JigsawStudioToolPayload> decode(PersistentDataContainer container) {
        if (container == null) {
            return Optional.empty();
        }
        Integer schemaVersion = container.get(SCHEMA_KEY, PersistentDataType.INTEGER);
        String actionName = container.get(ACTION_KEY, PersistentDataType.STRING);
        String requestValue = container.get(REQUEST_KEY, PersistentDataType.STRING);
        if (schemaVersion == null
                || schemaVersion != JigsawStudioToolPayload.CURRENT_SCHEMA_VERSION
                || actionName == null
                || requestValue == null) {
            return Optional.empty();
        }
        try {
            JigsawStudioToolAction action = JigsawStudioToolAction.valueOf(actionName);
            UUID requestId = UUID.fromString(requestValue);
            String workcellId = optionalString(container, WORKCELL_KEY);
            String pieceKey = optionalString(container, PIECE_KEY);
            String poolKey = optionalString(container, POOL_KEY);
            Integer entryIndex = container.get(ENTRY_INDEX_KEY, PersistentDataType.INTEGER);
            Integer amount = container.get(AMOUNT_KEY, PersistentDataType.INTEGER);
            return Optional.of(new JigsawStudioToolPayload(
                    schemaVersion,
                    action,
                    requestId,
                    workcellId,
                    pieceKey,
                    poolKey,
                    entryIndex == null ? JigsawStudioToolPayload.NO_ENTRY_INDEX : entryIndex,
                    amount == null ? 0 : amount));
        } catch (IllegalArgumentException exception) {
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

    private static String optionalString(PersistentDataContainer container, NamespacedKey key) {
        String value = container.get(key, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private static List<String> lore(JigsawStudioToolPayload payload) {
        List<String> lore = new ArrayList<>();
        if (!payload.workcellId().isEmpty()) {
            lore.add(ChatColor.GRAY + "Workcell: " + payload.workcellId());
        }
        if (!payload.pieceKey().isEmpty()) {
            lore.add(ChatColor.GRAY + "Variant: " + payload.pieceKey());
        }
        if (!payload.poolKey().isEmpty()) {
            lore.add(ChatColor.GRAY + "Pool: " + payload.poolKey()
                    + (payload.entryIndex() < 0 ? "" : " [" + payload.entryIndex() + "]"));
        }
        if (payload.amount() != 0) {
            lore.add(ChatColor.GRAY + "Amount: " + payload.amount());
        }
        lore.add(payload.action().destructive()
                ? ChatColor.RED + "Right-click twice to confirm"
                : ChatColor.YELLOW + "Right-click to use");
        return List.copyOf(lore);
    }
}
