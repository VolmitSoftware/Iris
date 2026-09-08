package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioPieceRules;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioToolPayload;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public interface JigsawStudioMenuActions {
    Optional<JigsawStudioMenuState> menuState(Player player);

    boolean selectWorkcell(Player player, String workcellId);

    boolean teleportToWorkcell(Player player, String workcellId);

    boolean setConnectorBlocksVisible(Player player, String workcellId, boolean visible);

    boolean resetConnectorBlocks(Player player, String workcellId);

    boolean undoAutosave(Player player);

    boolean switchVariant(Player player, String workcellId, String pieceKey, boolean discardDirty);

    boolean createVariant(Player player, String workcellId, boolean duplicateActive);

    boolean setWorkcellEnabled(Player player, String workcellId, boolean enabled);

    boolean updateWorkcellDimensions(
            Player player,
            String workcellId,
            JigsawStudioCellDimensions dimensions
    );

    boolean updateWorkcellDisplayName(Player player, String workcellId, String displayName);

    boolean setRequireCaps(Player player, boolean requireCaps);

    boolean duplicateActiveFamily(Player player, String themeKey);

    boolean updateThemeSetWeight(Player player, String themeKey, int weight);

    boolean flushAutosave(Player player, String workcellId);

    boolean goToPreview(Player player);

    boolean toggleVariantRotatable(Player player, String workcellId);

    boolean expandVariantToCell(Player player, String workcellId);

    boolean resizeVariant(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    );

    boolean updateVariantDisplayName(
            Player player,
            String workcellId,
            String pieceKey,
            String displayName
    );

    boolean updateVariantThemes(
            Player player,
            String workcellId,
            String pieceKey,
            List<String> themes
    );

    boolean updateVariantRules(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioPieceRules rules
    );

    boolean adjustVariantWeight(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int delta
    );

    boolean adjustVariantChance(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int deltaPercentagePoints
    );

    boolean unlinkVariantMembership(
            Player player,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex
    );

    boolean deleteVariant(Player player, String workcellId, String pieceKey);

    boolean deleteProject(Player player);

    boolean giveTool(Player player, JigsawStudioToolPayload payload);
}
