package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.studio.jigsaw.JigsawStudioActivation;
import art.arcane.iris.studio.jigsaw.JigsawStudioBay;
import art.arcane.iris.studio.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioSession;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletionException;

final class CommandJigsawOpen {
    private CommandJigsawOpen() {
    }

    static void openProject(
            Player targetPlayer,
            VolmitSender commandSender,
            IrisDimension dimension,
            String structure,
            long seed
    ) {
        UUID playerId = targetPlayer.getUniqueId();
        UUID ownerId = JigsawStudioActivation.activeOwnerId();
        if (ownerId != null && !ownerId.equals(playerId)) {
            CommandJigsaw.sendError(commandSender, "Jigsaw Studio is already owned by another player session.");
            return;
        }
        UUID openingOwner = JigsawStudioActivation.openingOwnerId();
        if (openingOwner != null) {
            CommandJigsaw.sendError(commandSender, openingOwner.equals(playerId)
                    ? "Your Jigsaw Studio is already opening."
                    : "Jigsaw Studio is already opening for another player.");
            return;
        }
        CommandJigsaw.ActiveContext previous = CommandJigsaw.active(targetPlayer);
        if (ownerId != null && previous == null) {
            CommandJigsaw.sendError(commandSender, "Your active Jigsaw Studio binding is unavailable. Close it before opening another project.");
            return;
        }
        CommandJigsaw.ActiveContext dirty = CommandJigsaw.findDirtyContext();
        if (dirty != null) {
            CommandJigsaw.sendError(commandSender,
                    "The current Jigsaw Studio is waiting for autosave. Wait for it to finish or close with discard=true.");
            return;
        }
        IrisData data = CommandJigsaw.requireData(dimension, commandSender);
        if (data == null) {
            return;
        }
        IrisStructure irisStructure = data.load(IrisStructure.class, structure, false);
        if (irisStructure == null) {
            CommandJigsaw.sendError(commandSender, "No Iris jigsaw structure '" + structure + "' exists in this pack.");
            return;
        }

        JigsawStudioLayout layout;
        try {
            layout = JigsawStudioGraphMapper.map(data, irisStructure);
        } catch (RuntimeException exception) {
            Iris.reportError("Failed to map Jigsaw Studio graph '" + structure + "'.", exception);
            CommandJigsaw.sendError(commandSender, "Could not map this jigsaw graph: " + exception.getMessage());
            return;
        }
        String packKey = dimension.getLoadKey();
        if (!JigsawStudioActivation.tryBeginOpen(playerId)) {
            CommandJigsaw.sendError(commandSender,
                    "Jigsaw Studio began opening for another player. Try again after it finishes.");
            return;
        }
        JigsawStudioService studioService = JigsawStudioService.get();
        UUID closingRequestId = previous == null ? null : previous.request().requestId();
        if (closingRequestId != null) {
            JigsawStudioService.CloseStart closeStart = studioService.tryBeginClose(
                    closingRequestId,
                    playerId,
                    false);
            if (closeStart != JigsawStudioService.CloseStart.STARTED) {
                JigsawStudioActivation.finishOpen(playerId);
                CommandJigsaw.sendError(commandSender, closeStart == JigsawStudioService.CloseStart.SAVE_IN_PROGRESS
                        ? "The current Jigsaw Studio is saving. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.OPERATION_IN_PROGRESS
                        ? "The current Jigsaw Studio is loading a variant. Wait for it to finish before opening another project."
                        : closeStart == JigsawStudioService.CloseStart.DIRTY
                        ? "The current Jigsaw Studio is waiting for autosave. Wait for it to finish before opening another project."
                        : "The current Jigsaw Studio could not be reserved for replacement.");
                return;
            }
        }
        JigsawStudioActivation.StagedActivation staged;
        try {
            staged = JigsawStudioActivation.stage(
                    packKey,
                    structure,
                    layout.mode(),
                    CommandJigsaw.compatibilityOf(irisStructure),
                    layout.cellDimensions(),
                    data,
                    layout,
                    playerId,
                    closingRequestId);
        } catch (RuntimeException exception) {
            JigsawStudioActivation.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            Iris.reportError("Failed to stage Jigsaw Studio for '" + structure + "'.", exception);
            CommandJigsaw.sendError(commandSender, "Jigsaw Studio staging failed: " + exception.getMessage());
            return;
        }
        commandSender.sendMessage(C.GREEN + "Opening transient Jigsaw Studio for '" + structure + "' with "
                + layout.bays().size() + " workcells.");

        try {
            Iris.service(StudioSVC.class).openTracked(
                            commandSender,
                            seed,
                            packKey,
                            CommandJigsaw.STUDIO_OPEN_KIND,
                            () -> beginStagedOpen(staged),
                            world -> finishOpen(targetPlayer, world, staged, studioService))
                    .whenComplete((result, throwable) -> J.s(() -> finishOpenAttempt(
                            commandSender,
                            playerId,
                            structure,
                            closingRequestId,
                            studioService,
                            staged,
                            result,
                            throwable)));
        } catch (Throwable exception) {
            JigsawStudioActivation.finishOpen(playerId);
            studioService.cancelClose(closingRequestId);
            JigsawStudioActivation.rollback(staged);
            Iris.reportError("Failed to open Jigsaw Studio for '" + structure + "'.", exception);
            CommandJigsaw.sendError(commandSender, "Jigsaw Studio open failed: " + exception.getMessage());
        }
    }

    private static void beginStagedOpen(JigsawStudioActivation.StagedActivation staged) {
        if (!JigsawStudioActivation.beginStagedGeneration(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation is no longer current");
        }
    }

    private static void finishOpenAttempt(
            VolmitSender commandSender,
            UUID playerId,
            String structureKey,
            UUID closingRequestId,
            JigsawStudioService studioService,
            JigsawStudioActivation.StagedActivation staged,
            StudioOpenCoordinator.StudioOpenResult result,
            Throwable throwable
    ) {
        JigsawStudioActivation.finishOpen(playerId);
        if (throwable == null && result != null) {
            studioService.cancelClose(closingRequestId);
            return;
        }
        studioService.cancelClose(closingRequestId);
        JigsawStudioActivation.rollback(staged);
        Throwable failure = unwrapCompletionFailure(throwable == null
                ? new IllegalStateException("Studio open completed without a result")
                : throwable);
        if (isExpectedOpenDenial(failure)) {
            Iris.warn("Jigsaw Studio open for '%s' was denied: %s",
                    structureKey, failure.getMessage());
            return;
        }
        Iris.reportError("Failed to open Jigsaw Studio for '" + structureKey + "'.", failure);
        CommandJigsaw.sendError(commandSender, "Jigsaw Studio open failed: " + failure.getMessage());
    }

    private static void finishOpen(
            Player targetPlayer,
            World world,
            JigsawStudioActivation.StagedActivation staged,
            JigsawStudioService studioService
    ) {
        if (world == null) {
            throw new IllegalStateException("Jigsaw Studio opened without a world");
        }
        JigsawStudioActivation.Request request = staged.request();
        JigsawStudioSession session = staged.session();
        try {
            WorldRuntimeControlService.get().applyObjectStudioWorldRules(world);
        } catch (Throwable exception) {
            Iris.reportError("Failed to apply Jigsaw Studio world rules for '" + world.getName() + "'.", exception);
        }
        JigsawStudioBay destination = initialBay(session.layout());
        Location target = destination == null
                ? new Location(world, 0.5D, 66D, 0.5D)
                : new Location(
                        world,
                        destination.bounds().originX() + destination.bounds().dimensions().width() / 2.0D,
                        destination.bounds().maxY() + 2.0D,
                        destination.bounds().originZ() + destination.bounds().dimensions().depth() / 2.0D);
        if (destination != null) {
            session.selectBay(destination.stableId());
        }
        if (!JigsawStudioActivation.commit(staged)) {
            throw new IllegalStateException("The staged Jigsaw Studio activation could not be committed");
        }
        studioService.activationCommitted(world, request.requestId());
        CommandJigsaw.PLAYER_PACKS.put(targetPlayer.getUniqueId(), new CommandJigsaw.SessionBinding(
                request.packKey(), request.requestId()));
        try {
            J.runEntity(targetPlayer, () -> BukkitPlatform.teleportAsync(targetPlayer, target)
                    .thenRun(() -> J.runEntity(targetPlayer, () -> targetPlayer.setGameMode(GameMode.CREATIVE))));
        } catch (RuntimeException exception) {
            Iris.reportError("Failed to move the player to the committed Jigsaw Studio workcell.", exception);
        }
    }

    private static JigsawStudioBay initialBay(JigsawStudioLayout layout) {
        for (JigsawStudioBay bay : layout.bays()) {
            if (layout.defaultVariant(bay).isPresent()) {
                return bay;
            }
        }
        return layout.bays().isEmpty() ? null : layout.bays().getFirst();
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static boolean isExpectedOpenDenial(Throwable failure) {
        return unwrapCompletionFailure(failure) instanceof BrokenPackException;
    }
}
