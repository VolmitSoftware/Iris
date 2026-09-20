package art.arcane.iris.modded;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.modded.api.ModdedBlockData;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockResolver;
import art.arcane.volmlib.util.data.UnresolvedKeyLog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedBlockResolution implements NativeBlockResolver.Policy {
    private static final UnresolvedKeyLog UNRESOLVED = new UnresolvedKeyLog("Iris modded block resolution", 30_000L);
    private static final int REPORTED_FAILURE_KEYS_MAX = 256;
    private static final Set<String> REPORTED_FAILURE_KEYS = ConcurrentHashMap.newKeySet();
    public static final NativeBlockResolver BLOCKS = new NativeBlockResolver(new ModdedBlockResolution());

    private ModdedBlockResolution() {
    }

    @Override
    public ModdedBlockState resolveCustomBlock(String key) {
        ModdedBlockData provided = ModdedCustomContentRegistry.resolveBlock(key);
        if (provided == null) {
            return null;
        }
        return provided.deferredPlacement()
                ? provided.state().withDeferredPlacement(key)
                : provided.state();
    }

    @Override
    public boolean preventLeafDecay() {
        return IrisSettings.get().getGenerator().isPreventLeafDecay();
    }

    @Override
    public void debug(String message) {
        IrisLogging.debug(message);
    }

    public void reportError(String key, Throwable error) {
        String failureKey = key == null ? "<null>" : key;
        if (!REPORTED_FAILURE_KEYS.add(failureKey)) {
            return;
        }
        if (REPORTED_FAILURE_KEYS.size() > REPORTED_FAILURE_KEYS_MAX) {
            REPORTED_FAILURE_KEYS.clear();
        }
        IrisLogging.reportError("Iris block data '" + failureKey + "' failed to resolve", error);
    }

    public void warnUnresolved(String key, String message) {
        if (UNRESOLVED.firstOccurrence(key)) {
            IrisLogging.warn(message);
        }
        String summary = UNRESOLVED.pollSummary();
        if (summary != null) {
            IrisLogging.warn(summary);
        }
    }

}
