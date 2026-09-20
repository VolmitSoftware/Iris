package art.arcane.iris.world.lifecycle;

import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CapabilitySnapshotFixtures {
    private CapabilitySnapshotFixtures() {
    }

    static CapabilitySnapshot forTesting(ServerFamily family, boolean regionized, boolean providerHealthy, boolean runtimeHealthy) {
        NativeWorldRuntime runtime = mock(NativeWorldRuntime.class);
        when(runtime.available()).thenReturn(runtimeHealthy);
        when(runtime.description()).thenReturn(runtimeHealthy ? "available(test)" : "unsupported(test)");
        return new CapabilitySnapshot(family, regionized,
                providerHealthy ? new Object() : null, providerHealthy ? Object.class : null,
                providerHealthy ? Object.class : null, providerHealthy ? "test-provider" : "inactive", runtime, null);
    }
}
