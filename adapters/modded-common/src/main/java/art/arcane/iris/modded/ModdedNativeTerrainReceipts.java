package art.arcane.iris.modded;

import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeTerrainReceipt;

import java.io.IOException;

public final class ModdedNativeTerrainReceipts {
    private ModdedNativeTerrainReceipts() {
    }

    public static byte[] encode(GenerationHistoryRuntimeRouter.RuntimeRoute route) throws IOException {
        return route == null || route.naturalTerrain().isEmpty() ? null
                : NativeTerrainReceipt.encode(route.naturalTerrain().orElseThrow(),
                        route.activation().activationId(), route.epoch().epochId());
    }
}
