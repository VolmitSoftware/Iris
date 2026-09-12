package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot.PaperLikeFlavor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class CapabilitySnapshotFixtures {
    private CapabilitySnapshotFixtures() {
    }

    static CapabilitySnapshot forTesting(ServerFamily serverFamily, boolean regionizedRuntime, boolean worldsProviderHealthy, boolean paperLikeRuntimeHealthy) {
        Object minecraftServer = paperLikeRuntimeHealthy ? new TestingPaperLikeServer("datapack-registry", "server-registry") : null;
        Method createLevelMethod = null;
        Field worldLoaderContextField = null;
        Method serverRegistryAccessMethod = null;
        try {
            createLevelMethod = paperLikeRuntimeHealthy
                    ? TestingPaperLikeServer.class.getDeclaredMethod("createLevel", Object.class, Object.class, Object.class)
                    : null;
            worldLoaderContextField = paperLikeRuntimeHealthy
                    ? CapabilityResolution.resolveField(TestingPaperLikeServer.class, "worldLoaderContext")
                    : null;
            serverRegistryAccessMethod = paperLikeRuntimeHealthy
                    ? CapabilityResolution.resolveServerRegistryAccessMethod(TestingPaperLikeServer.class)
                    : null;
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
        return new CapabilitySnapshot(
                serverFamily,
                regionizedRuntime,
                worldsProviderHealthy ? new Object() : null,
                worldsProviderHealthy ? Object.class : null,
                worldsProviderHealthy ? Object.class : null,
                worldsProviderHealthy ? "test-provider" : "inactive",
                null,
                minecraftServer,
                createLevelMethod,
                paperLikeRuntimeHealthy ? PaperLikeFlavor.CURRENT_INFO_AND_DATA : PaperLikeFlavor.UNSUPPORTED,
                null,
                null,
                null,
                null,
                null,
                null,
                worldLoaderContextField,
                serverRegistryAccessMethod,
                null,
                null,
                null,
                null,
                null,
                null,
                paperLikeRuntimeHealthy ? "available(test)" : "unsupported(test)"
        );
    }

    static CapabilitySnapshot forTestingRuntimeRegistries(ServerFamily serverFamily, boolean regionizedRuntime, Object datapackDimensions, Object serverRegistryAccess) {
        TestingPaperLikeServer minecraftServer = new TestingPaperLikeServer(datapackDimensions, serverRegistryAccess);
        Method createLevelMethod;
        Field worldLoaderContextField;
        Method registryAccessMethod;
        try {
            createLevelMethod = TestingPaperLikeServer.class.getDeclaredMethod("createLevel", Object.class, Object.class, Object.class);
            worldLoaderContextField = CapabilityResolution.resolveField(TestingPaperLikeServer.class, "worldLoaderContext");
            registryAccessMethod = CapabilityResolution.resolveServerRegistryAccessMethod(TestingPaperLikeServer.class);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
        return new CapabilitySnapshot(
                serverFamily,
                regionizedRuntime,
                null,
                null,
                null,
                "inactive",
                null,
                minecraftServer,
                createLevelMethod,
                PaperLikeFlavor.CURRENT_INFO_AND_DATA,
                null,
                null,
                null,
                null,
                null,
                null,
                worldLoaderContextField,
                registryAccessMethod,
                null,
                null,
                null,
                null,
                null,
                null,
                "available(test-runtime-registries)"
        );
    }

    private static final class TestingPaperLikeServer {
        private final TestingWorldLoaderContext worldLoaderContext;
        private final Object registryAccess;

        private TestingPaperLikeServer(Object datapackDimensions, Object registryAccess) {
            this.worldLoaderContext = new TestingWorldLoaderContext(datapackDimensions);
            this.registryAccess = registryAccess;
        }

        @SuppressWarnings("unused")
        private void createLevel(Object levelStem, Object worldLoadingInfoAndData, Object worldDataAndGenSettings) {
        }

        @SuppressWarnings("unused")
        private Object registryAccess() {
            return registryAccess;
        }
    }

    private static final class TestingWorldLoaderContext {
        private final Object datapackDimensions;

        private TestingWorldLoaderContext(Object datapackDimensions) {
            this.datapackDimensions = datapackDimensions;
        }

        @SuppressWarnings("unused")
        private Object datapackDimensions() {
            return datapackDimensions;
        }
    }
}
