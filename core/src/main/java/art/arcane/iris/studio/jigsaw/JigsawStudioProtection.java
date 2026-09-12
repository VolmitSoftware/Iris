package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static art.arcane.iris.studio.jigsaw.JigsawStudioService.message;

final class JigsawStudioProtection {
    private static final Set<String> MUTATING_COMMANDS = Set.of(
            "biome", "brush", "chunk", "clone", "copy", "cut", "data", "deform", "drain",
            "execute", "faces", "fill", "fillbiome", "fixlava", "fixwater", "flip", "flora",
            "forest", "function", "gmask", "green", "hollow", "item", "load", "mask", "move",
            "naturalize", "overlay", "paste", "place", "redo", "regen", "replace", "replacenear",
            "restore", "rotate", "schedule", "schem", "schematic", "set", "setblock", "smooth",
            "snow", "sphere", "stack", "thaw", "undo", "walls");

    private static final Set<String> SAFE_NON_OWNER_COMMANDS = Set.of(
            "help", "list", "me", "msg", "ping", "pl", "plugins", "r", "reply", "rules",
            "say", "tell", "ver", "version", "w", "whisper");

    private final JigsawStudioService service;

    JigsawStudioProtection(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean blocksInventoryMutation(Inventory inventory, UUID actorId) {
        for (Block block : inventoryBlocks(inventory)) {
            StudioBlockMutationContext context = studioBlockMutationContext(block);
            if (context != null && blocksStudioInventoryMutation(
                    true,
                    context.immutable(),
                    materializationInProgress(context.studio()),
                    service.saveLifecycle.reopenRequiredRequests.contains(context.requestId()),
                    context.ownerId(),
                    actorId)) {
                return true;
            }
        }
        return false;
    }

    boolean blocksMachineMutation(Block block) {
        StudioBlockMutationContext context = studioBlockMutationContext(block);
        return context != null && blocksStudioInventoryMutation(
                true,
                context.immutable(),
                materializationInProgress(context.studio()),
                service.saveLifecycle.reopenRequiredRequests.contains(context.requestId()),
                context.ownerId(),
                null);
    }

    private StudioBlockMutationContext studioBlockMutationContext(Block block) {
        if (block == null) {
            return null;
        }
        ActiveStudio studio = service.studios.get(block.getWorld().getUID());
        if (studio == null) {
            return null;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        boolean preview = service.previewRenderer.contains(
                request.requestId(), block.getX(), block.getY(), block.getZ());
        JigsawStudioControlPosition control = studio.generator().getLayout().controlPosition();
        boolean controlChest = block.getType() == Material.CHEST
                && block.getX() == control.worldX()
                && block.getY() == control.worldY()
                && block.getZ() == control.worldZ();
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(
                block.getX(), block.getY(), block.getZ());
        boolean workcell = bay != null;
        if (!preview && !controlChest && !workcell) {
            return null;
        }
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        boolean nonEditableWorkcell = bay != null && (variant == null || !variant.owned());
        return new StudioBlockMutationContext(
                studio,
                request.requestId(),
                request.ownerId(),
                preview || controlChest || nonEditableWorkcell);
    }

    static List<Block> inventoryBlocks(Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>(2);
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockInventoryHolder blockInventoryHolder) {
            blocks.add(blockInventoryHolder.getBlock());
        } else if (holder instanceof DoubleChest doubleChest) {
            addInventoryBlock(blocks, doubleChest.getLeftSide());
            addInventoryBlock(blocks, doubleChest.getRightSide());
        }
        return blocks;
    }

    private static void addInventoryBlock(List<Block> blocks, InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder blockInventoryHolder
                && !blocks.contains(blockInventoryHolder.getBlock())) {
            blocks.add(blockInventoryHolder.getBlock());
        }
    }

    static boolean isMutatingCommand(String commandLine) {
        return MUTATING_COMMANDS.contains(commandLabel(commandLine));
    }

    static boolean blocksMutatingCommand(UUID ownerId, UUID actorId, String commandLine) {
        return !ownerMatches(ownerId, actorId)
                && !isSafeNonOwnerCommand(commandLine);
    }

    static boolean blocksNonEditableWorkcellMutation(
            boolean hasNonEditableWorkcell,
            String commandLine
    ) {
        return hasNonEditableWorkcell && isMutatingCommand(commandLine);
    }

    static boolean canToggleVariantRotation(
            JigsawStudioCompatibilityTarget compatibilityTarget,
            JigsawStudioVariant variant
    ) {
        JigsawStudioCompatibilityTarget target = Objects.requireNonNull(
                compatibilityTarget,
                "Jigsaw Studio compatibility target");
        JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio variant");
        return activeVariant.owned()
                && (target != JigsawStudioCompatibilityTarget.VANILLA_PORTABLE
                || !activeVariant.rotatable());
    }

    static boolean isSafeNonOwnerCommand(String commandLine) {
        String normalized = normalizeCommandLine(commandLine);
        String label = commandLabel(normalized);
        if (SAFE_NON_OWNER_COMMANDS.contains(label)) {
            return true;
        }
        int separator = normalized.indexOf(' ');
        if (!label.equals("iris") || separator < 0) {
            return false;
        }
        String arguments = normalized.substring(separator + 1).trim();
        return arguments.equals("jigsaw status") || arguments.startsWith("jigsaw status ");
    }

    private static String commandLabel(String commandLine) {
        String normalized = normalizeCommandLine(commandLine);
        int separator = normalized.indexOf(' ');
        String label = separator < 0 ? normalized : normalized.substring(0, separator);
        int namespace = label.lastIndexOf(':');
        return namespace < 0 ? label : label.substring(namespace + 1);
    }

    private static String normalizeCommandLine(String commandLine) {
        if (commandLine == null) {
            return "";
        }
        String normalized = commandLine.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    static boolean ownerMatches(Player player, ActiveStudio studio) {
        if (player == null || studio == null) {
            return false;
        }
        UUID ownerId = studio.generator().getRequest().ownerId();
        return ownerMatches(ownerId, player.getUniqueId());
    }

    static boolean canCreateVariants(JigsawStudioLayout layout) {
        return Objects.requireNonNull(layout, "Jigsaw Studio layout")
                .variantCatalog()
                .editableGraph();
    }

    static String variantCreationSourceFailure(
            JigsawStudioVariant activeVariant,
            boolean duplicateActive
    ) {
        if (activeVariant == null) {
            return duplicateActive
                    ? "This workcell has no active variant to duplicate."
                    : "This workcell has no active variant whose pool role can be copied. Use "
                    + "/iris jigsaw piece create <poolKey> <pieceKey> to choose an owned pool explicitly.";
        }
        if (!activeVariant.owned()) {
            return "Adopt or clone this graph before creating from its read-only variant.";
        }
        if (activeVariant.memberships().isEmpty()) {
            return "The active variant has no owned pool membership to copy. Use "
                    + "/iris jigsaw piece create <poolKey> <pieceKey> to choose an owned pool explicitly.";
        }
        return "";
    }

    static boolean ownerMatches(UUID ownerId, UUID actorId) {
        return ownerId == null || ownerId.equals(actorId);
    }

    static boolean authorizeOwner(Player player, ActiveStudio studio) {
        if (ownerMatches(player, studio)) {
            return true;
        }
        message(player, "This Jigsaw Studio is owned by another player session.");
        return false;
    }

    boolean isUnauthorizedStudioEdit(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        ActiveStudio studio = service.studios.get(block.getWorld().getUID());
        if (studio == null) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        if (service.previewRenderer.contains(requestId, block.getX(), block.getY(), block.getZ())) {
            message(player, "The seed-1337 Jigsaw preview is read-only and refreshes automatically.");
            return true;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(block.getX(), block.getY(), block.getZ());
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        if (bay != null && (variant == null || !variant.owned())) {
            message(player, variant == null
                    ? "This empty Jigsaw Studio workcell has no loaded editable variant. Load one before editing it."
                    : "This Jigsaw Studio variant is read-only. Adopt or clone its graph before editing it.");
            return true;
        }
        if (service.saveLifecycle.reopenRequiredRequests.contains(requestId)) {
            message(player, "Close and reopen Jigsaw Studio before editing the resized layout.");
            return true;
        }
        if (materializationInProgress(studio)) {
            message(player, "Wait for the current Jigsaw Studio variant load or rollback to finish.");
            return true;
        }
        if (!blocksStudioEdit(studio.generator().getRequest().ownerId(), player.getUniqueId())) {
            return false;
        }
        message(player, "This Jigsaw Studio is owned by another player session.");
        return true;
    }

    boolean materializationInProgress(ActiveStudio studio) {
        if (studio == null) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        synchronized (service.saveLifecycleLock) {
            return service.materializer.materializationsInProgress.contains(requestId);
        }
    }

    boolean anyMaterializationInProgress() {
        synchronized (service.saveLifecycleLock) {
            return !service.materializer.materializationsInProgress.isEmpty();
        }
    }

    boolean anyNonEditableWorkcell() {
        for (ActiveStudio studio : service.studios.values()) {
            if (hasNonEditableWorkcell(studio)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasNonEditableWorkcell(ActiveStudio studio) {
        JigsawStudioSession session = studio.generator().getSession();
        for (JigsawStudioBay bay : session.layout().bays()) {
            JigsawStudioVariant variant = session.activeVariant(bay.stableId()).orElse(null);
            if (variant == null || !variant.owned()) {
                return true;
            }
        }
        return false;
    }

    static boolean blocksStudioEdit(UUID ownerId, UUID actorId) {
        return !ownerMatches(ownerId, actorId);
    }

    static boolean blocksStudioInventoryMutation(
            boolean studioBlockInventory,
            boolean immutable,
            boolean materializing,
            boolean reopenRequired,
            UUID ownerId,
            UUID actorId
    ) {
        return studioBlockInventory
                && (immutable
                || materializing
                || reopenRequired
                || actorId != null && blocksStudioEdit(ownerId, actorId));
    }

    boolean movesProtectedBlocks(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (isImmutableStudioBlock(block)
                    || isImmutableStudioBlock(block.getRelative(direction))
                    || isImmutableStudioBlock(block.getRelative(direction.getOppositeFace()))) {
                return true;
            }
        }
        return false;
    }

    boolean containsImmutableStudioBlock(List<BlockState> states) {
        for (BlockState state : states) {
            if (isImmutableStudioBlock(state.getBlock())) {
                return true;
            }
        }
        return false;
    }

    boolean isImmutableStudioBlock(Block block) {
        StudioBlockMutationContext context = studioBlockMutationContext(block);
        return context != null && context.immutable();
    }

    private boolean isPreviewBlock(Block block) {
        if (block == null) {
            return false;
        }
        ActiveStudio studio = service.studios.get(block.getWorld().getUID());
        if (studio == null) {
            return false;
        }
        UUID requestId = studio.generator().getRequest().requestId();
        return service.previewRenderer.contains(requestId, block.getX(), block.getY(), block.getZ());
    }

    boolean isControlChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) {
            return false;
        }
        ActiveStudio studio = service.studios.get(block.getWorld().getUID());
        if (studio == null) {
            return false;
        }
        JigsawStudioControlPosition control = studio.generator().getLayout().controlPosition();
        return block.getX() == control.worldX()
                && block.getY() == control.worldY()
                && block.getZ() == control.worldZ();
    }

    private record StudioBlockMutationContext(
            ActiveStudio studio,
            UUID requestId,
            UUID ownerId,
            boolean immutable
    ) {
        private StudioBlockMutationContext {
            Objects.requireNonNull(studio, "Jigsaw Studio block mutation studio");
            Objects.requireNonNull(requestId, "Jigsaw Studio block mutation request ID");
        }
    }
}
