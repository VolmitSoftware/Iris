package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeStructureBootstrapPolicy;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public record BukkitStructureBootstrapPolicy(Engine engine, String worldName) implements NativeStructureBootstrapPolicy {
    @Override
    public CompletableFuture<Void> start(Runnable claim, Supplier<CompletableFuture<Void>> preparation, Runnable activation) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            throw new IllegalStateException("Native structure bootstrap requires an IrisEngine runtime.");
        }
        return irisEngine.startNativeStructureBootstrap(claim, preparation, activation);
    }

    @Override
    public void failed(Throwable failure) {
        IrisLogging.reportError("Native structure ring bootstrap failed for world '" + worldName + "'.", failure);
    }
}
