package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisModuleHost;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.service.JigsawStudioGraphEvaluation;
import art.arcane.iris.core.service.JigsawStudioRegistry;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.toolworld.BukkitToolWorldService;
import art.arcane.iris.core.tooling.IrisToolPack;
import art.arcane.iris.spi.IrisServices;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the Director commands of the Jigsaw Studio need to reach the studio, which speaks the tool world api: the studio,
 * the pack as a {@link ToolPack}, and a Bukkit player as a {@link ToolPlayer}. It goes when the commands are rewritten
 * as api commands, which hand the studio their own sender's player and pack.
 */
final class JigsawStudioBridge {
    private final JigsawStudioService service;

    private JigsawStudioBridge(JigsawStudioService service) {
        this.service = service;
    }

    static JigsawStudioBridge get() {
        return new JigsawStudioBridge(Iris.jigsawStudio());
    }

    static ToolPlayer toolPlayer(Player player) {
        return IrisServices.get(BukkitToolWorldService.class).playerOf(player);
    }

    static ToolPack toolPack(IrisData data) {
        return new IrisToolPack(data, IrisModuleHost.hostLog());
    }

    JigsawStudioRegistry registry() {
        return service.registry();
    }

    CompletableFuture<ToolWorld> open(IrisData data, JigsawStudioRegistry.StagedActivation staged) {
        return service.open(toolPack(data), staged);
    }

    void activationCommitted(UUID requestId) {
        service.activationCommitted(requestId);
    }

    JigsawStudioService.CloseStart tryBeginClose(UUID requestId, UUID ownerId, boolean discard) {
        return service.tryBeginClose(requestId, ownerId, discard);
    }

    void cancelClose(UUID requestId) {
        service.cancelClose(requestId);
    }

    JigsawStudioService.ExportStart tryBeginExport(UUID requestId, UUID ownerId) {
        return service.tryBeginExport(requestId, ownerId);
    }

    void finishExport(UUID requestId) {
        service.finishExport(requestId);
    }

    boolean runCommandGraphMutation(
            Player player,
            UUID expectedRequestId,
            JigsawStudioService.CommandGraphMutation mutation
    ) {
        return service.runCommandGraphMutation(toolPlayer(player), expectedRequestId, mutation);
    }

    boolean deleteProject(Player player) {
        return service.deleteProject(toolPlayer(player));
    }

    Optional<JigsawStudioGraphEvaluation> evaluation(Player player) {
        return service.evaluation(toolPlayer(player));
    }

    boolean openControlMenu(Player player) {
        return service.openControlMenu(toolPlayer(player));
    }

    boolean updateWorkcellDimensions(Player player, String workcellId, JigsawStudioCellDimensions dimensions) {
        return service.updateWorkcellDimensions(toolPlayer(player), workcellId, dimensions);
    }

    boolean flushAutosave(Player player, String workcellId) {
        return service.flushAutosave(toolPlayer(player), workcellId);
    }

    boolean teleportTo(Player player, String bayId) {
        return service.teleportTo(toolPlayer(player), bayId);
    }

    boolean setParticles(Player player, boolean visible) {
        return service.setParticles(toolPlayer(player), visible);
    }

    boolean expandVariantToCell(Player player, String workcellId) {
        return service.expandVariantToCell(toolPlayer(player), workcellId);
    }

    boolean updateWorkcellDisplayName(Player player, String workcellId, String displayName) {
        return service.updateWorkcellDisplayName(toolPlayer(player), workcellId, displayName);
    }

    boolean resizeVariant(
            Player player,
            String workcellId,
            String pieceKey,
            JigsawStudioCellDimensions dimensions
    ) {
        return service.resizeVariant(toolPlayer(player), workcellId, pieceKey, dimensions);
    }

    boolean updateVariantDisplayName(Player player, String workcellId, String pieceKey, String displayName) {
        return service.updateVariantDisplayName(toolPlayer(player), workcellId, pieceKey, displayName);
    }

    boolean createVariant(Player player, String workcellId, boolean duplicateActive) {
        return service.createVariant(toolPlayer(player), workcellId, duplicateActive);
    }

    boolean duplicateActiveFamily(Player player, String themeKey) {
        return service.duplicateActiveFamily(toolPlayer(player), themeKey);
    }

    boolean goToPreview(Player player) {
        return service.goToPreview(toolPlayer(player));
    }

    boolean previewStudio(Player player, long seed) {
        return service.previewStudio(toolPlayer(player), seed);
    }
}
