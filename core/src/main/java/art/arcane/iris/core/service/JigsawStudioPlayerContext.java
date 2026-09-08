package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioSession;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioSaveLifecycle.BayReadiness;
import art.arcane.iris.core.service.JigsawStudioService.ActiveStudio;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.iris.core.service.JigsawStudioProtection.ownerMatches;

final class JigsawStudioPlayerContext {
    private final JigsawStudioService service;
    final Map<UUID, PlayerWorkcellContext> playerWorkcells = new ConcurrentHashMap<>();

    JigsawStudioPlayerContext(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    void refreshAllWorkcellContexts(ActiveStudio studio) {
        scheduleOnlinePlayers(studio.worldId());
    }

    void scheduleOnlinePlayers(UUID worldId) {
        J.runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                J.runEntity(player, () -> {
                    if (player.getWorld().getUID().equals(worldId)) {
                        service.visualization.ensureVisualizationLoop(player);
                        reconcilePlayerContext(player, player.getLocation());
                    }
                });
            }
        });
    }

    void reconcilePlayerContext(Player player, Location location) {
        if (player == null) {
            return;
        }
        Location target = location == null ? player.getLocation() : location;
        if (!J.isOwnedByCurrentRegion(player)
                || target.getWorld() == null
                || !target.getWorld().getUID().equals(player.getWorld().getUID())) {
            J.runEntity(player, () -> reconcilePlayerContext(player, player.getLocation()), 1);
            return;
        }
        ActiveStudio studio = service.studios.get(target.getWorld().getUID());
        if (studio == null) {
            clearPlayerContext(player);
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay workcell = session.layout().findAt(
                target.getBlockX(), target.getBlockY(), target.getBlockZ());
        boolean owner = ownerMatches(player, studio);
        selectEnteredWorkcell(session, workcell, owner);
        String workcellId = workcell == null ? "" : workcell.stableId();
        playerWorkcells.put(player.getUniqueId(), new PlayerWorkcellContext(
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                workcellId));

        JigsawStudioVariant variant = workcell == null
                ? null
                : session.activeVariant(workcell.stableId()).orElse(null);
        JigsawStudioBoardState state = boardState(studio, session, workcell, variant);
        String workcellRole = workcell == null ? "" : workcell.canonicalDisplayName();
        String workcellName = workcell == null ? "" : workcell.displayName();
        String variantName = variant == null ? "" : variant.resolvedDisplayName();
        String hint = "Triple-sneak for controls";
        boardService().applyJigsawContext(player, new JigsawStudioBoardContext(
                studio.worldId(),
                studio.generator().getRequest().requestId(),
                studio.generator().getRequest().structureKey(),
                session.layout().mode(),
                workcellRole,
                workcellName,
                variantName,
                state,
                hint));
    }

    static boolean selectEnteredWorkcell(
            JigsawStudioSession session,
            JigsawStudioBay workcell,
            boolean owner
    ) {
        return owner && workcell != null && session.selectBay(workcell.stableId());
    }

    void refreshWorkcellContext(UUID worldId, String workcellId) {
        if (worldId == null || workcellId == null) {
            return;
        }
        J.runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                J.runEntity(player, () -> {
                    if (!player.getWorld().getUID().equals(worldId)) {
                        return;
                    }
                    PlayerWorkcellContext context = playerWorkcells.get(player.getUniqueId());
                    if (context != null && workcellId.equals(context.workcellId())) {
                        reconcilePlayerContext(player, player.getLocation());
                    }
                });
            }
        });
    }

    void clearWorldPlayerContexts(UUID worldId) {
        J.runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                J.runEntity(player, () -> {
                    PlayerWorkcellContext context = playerWorkcells.get(player.getUniqueId());
                    if (context != null && context.worldId().equals(worldId)) {
                        service.toolbelt.closeMenu(player);
                        clearPlayerContext(player);
                    }
                });
            }
        });
    }

    private void clearPlayerContext(Player player) {
        if (player == null) {
            return;
        }
        playerWorkcells.remove(player.getUniqueId());
        boardService().clearJigsawContext(player);
    }

    static BoardSVC boardService() {
        return IrisServices.get(BoardSVC.class);
    }

    private static JigsawStudioBoardState boardState(
            ActiveStudio studio,
            JigsawStudioSession session,
            JigsawStudioBay workcell,
            JigsawStudioVariant variant
    ) {
        if (workcell == null) {
            return JigsawStudioBoardState.SAVED;
        }
        JigsawStudioSession.WorkcellSnapshot snapshot = session.workcellSnapshot(workcell.stableId());
        if (snapshot.switchInProgress()) {
            return JigsawStudioBoardState.LOADING;
        }
        if (snapshot.saveInProgress()) {
            return JigsawStudioBoardState.SAVING;
        }
        if (!workcell.enabled()) {
            return JigsawStudioBoardState.DISABLED;
        }
        if (variant != null && !variant.owned()) {
            return JigsawStudioBoardState.READ_ONLY;
        }
        BayReadiness readiness = studio.population(workcell).readiness();
        if (!readiness.failure().isEmpty()) {
            return JigsawStudioBoardState.INVALID;
        }
        return snapshot.dirty() ? JigsawStudioBoardState.UNSAVED : JigsawStudioBoardState.SAVED;
    }

    private static String displayKey(String resourceKey) {
        int separator = resourceKey.lastIndexOf('/');
        return separator < 0 ? resourceKey : resourceKey.substring(separator + 1);
    }

    private record PlayerWorkcellContext(
            UUID worldId,
            UUID requestId,
            String workcellId
    ) {
        PlayerWorkcellContext {
            Objects.requireNonNull(worldId, "Jigsaw Studio player-context world ID");
            Objects.requireNonNull(requestId, "Jigsaw Studio player-context request ID");
            workcellId = workcellId == null ? "" : workcellId;
        }
    }
}
