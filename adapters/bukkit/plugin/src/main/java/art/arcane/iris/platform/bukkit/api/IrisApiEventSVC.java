package art.arcane.iris.platform.bukkit.api;

import art.arcane.iris.Iris;
import art.arcane.iris.api.pregen.IrisPregenPhase;
import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.iris.api.pregen.IrisPregenerationEvent;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.iris.api.world.IrisWorldEngineEvent;
import art.arcane.iris.api.world.IrisWorldPhase;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.world.pregen.PregenApiPhase;
import art.arcane.iris.world.pregen.PregenApiSink;
import art.arcane.iris.platform.bukkit.terrain.IrisWorldInfoFactory;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;

public class IrisApiEventSVC implements IrisService, PregenApiSink {
    public static void fireWorldPhase(World world, IrisWorldPhase phase) {
        if (phase == null) {
            return;
        }
        if (world == null) {
            IrisLogging.debug("Iris world API skipped phase " + phase
                    + " because the engine has no bound platform world.");
            return;
        }

        try {
            deliver(new IrisWorldEngineEvent(world, phase, describe(world, phase)));
        } catch (Throwable error) {
            IrisLogging.reportError("Iris world API dispatch failed for phase " + phase
                    + " on world \"" + world.getName() + "\".", error);
        }
    }

    private static IrisWorldInfo describe(World world, IrisWorldPhase phase) {
        try {
            return IrisWorldInfoFactory.forWorld(world);
        } catch (Throwable error) {
            IrisLogging.reportError("Iris world API could not describe world \"" + world.getName()
                    + "\" for phase " + phase + "; the event is delivered without world info.", error);
            return null;
        }
    }

    private static void deliver(Event event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return;
        }

        Iris.callEvent(event);
    }

    private static IrisPregenPhase toApi(PregenApiPhase phase) {
        return switch (phase) {
            case STARTED -> IrisPregenPhase.STARTED;
            case TICK -> IrisPregenPhase.TICK;
            case PAUSED -> IrisPregenPhase.PAUSED;
            case RESUMED -> IrisPregenPhase.RESUMED;
            case SAVING -> IrisPregenPhase.SAVING;
            case COMPLETED -> IrisPregenPhase.COMPLETED;
            case CANCELLED -> IrisPregenPhase.CANCELLED;
        };
    }

    private static IrisPregenProgress toApi(PregeneratorJob.PregenProgress progress) {
        return new IrisPregenProgress(
                progress.worldName(),
                progress.worldIdentity(),
                progress.percent(),
                progress.generated(),
                progress.totalChunks(),
                progress.chunksRemaining(),
                progress.failed(),
                progress.chunksPerSecond(),
                progress.eta(),
                progress.elapsed(),
                progress.method(),
                progress.paused()
        );
    }

    @Override
    public void onEnable() {
        IrisServices.register(PregenApiSink.class, this);
    }

    @Override
    public void onDisable() {
        IrisServices.remove(PregenApiSink.class);
    }

    @Override
    public void pregen(PregenApiPhase phase, PregeneratorJob.PregenProgress progress) {
        if (phase == null || progress == null || progress.worldIdentity() == null) {
            return;
        }

        Iris.callEvent(new IrisPregenerationEvent(toApi(phase), toApi(progress)));
    }
}
