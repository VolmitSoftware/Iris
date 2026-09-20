package art.arcane.iris.modded.api;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedCustomContentRegistryTest {
    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void entityProviderReceivesCanonicalKeyAndOriginalWorldAndPosition() {
        ModdedDataProvider provider = mock(ModdedDataProvider.class);
        NativeEntityRuntime runtime = mock(NativeEntityRuntime.class);
        NativeSpawnedEntity entity = mock(NativeSpawnedEntity.class);
        NativeEntityRuntime.Position position = new NativeEntityRuntime.Position(2.5, 63.5, -4.5);
        NativeEntityRuntime.CustomSpawn canonical = new NativeEntityRuntime.CustomSpawn(runtime, position, "provider_test:mob");
        when(provider.modId()).thenReturn("provider_entity_test");
        when(provider.isReady()).thenReturn(true);
        when(provider.isValidProvider("provider_test:mob", ModdedDataType.ENTITY)).thenReturn(true);
        when(provider.spawnMob(canonical)).thenReturn(entity);
        ModdedCustomContentRegistry.Discovery discovery = ModdedCustomContentRegistry.discover(List.of(provider));
        try {
            assertSame(entity, ModdedCustomContentRegistry.spawnMob(
                    new NativeEntityRuntime.CustomSpawn(runtime, position, " provider_test:mob[variant=a] ")));
            verify(provider).spawnMob(canonical);
        } finally {
            discovery.rollback();
        }
    }

    @Test
    public void unclaimedDeferredBlockDoesNotReadTheWorld() {
        ModdedCustomContentRegistry.processBlockPlacement(null, "provider_test:unclaimed", () -> {
            throw new AssertionError("unclaimed placement read the world");
        });
    }

    @Test
    public void publishesProvidersOnlyAfterCompleteDiscoveryAndCanRollBack() {
        String modId = "iris_discovery_success";
        TestProvider provider = new TestProvider(modId, null);
        boolean previousDiscoveryComplete = ModdedCustomContentRegistry.discoveryComplete();

        ModdedCustomContentRegistry.Discovery discovery =
                ModdedCustomContentRegistry.discover(List.of(provider));
        try {
            assertTrue(ModdedCustomContentRegistry.discoveryComplete());
            assertTrue(ModdedCustomContentRegistry.hasProvider(modId));
        } finally {
            discovery.rollback();
        }

        assertEquals(previousDiscoveryComplete, ModdedCustomContentRegistry.discoveryComplete());
        assertFalse(ModdedCustomContentRegistry.hasProvider(modId));
    }

    @Test
    public void failedDiscoveryPublishesNothingAndPreservesTheCause() {
        String firstModId = "iris_discovery_staged";
        String failingModId = "iris_discovery_failure";
        RuntimeException original = new RuntimeException("provider init failed");
        boolean previousDiscoveryComplete = ModdedCustomContentRegistry.discoveryComplete();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ModdedCustomContentRegistry.discover(List.of(
                        new TestProvider(firstModId, null),
                        new TestProvider(failingModId, original))));

        assertSame(original, thrown);
        assertEquals(previousDiscoveryComplete, ModdedCustomContentRegistry.discoveryComplete());
        assertFalse(ModdedCustomContentRegistry.hasProvider(firstModId));
        assertFalse(ModdedCustomContentRegistry.hasProvider(failingModId));
    }

    private static final class TestProvider implements ModdedDataProvider {
        private final String modId;
        private final RuntimeException failure;

        private TestProvider(String modId, RuntimeException failure) {
            this.modId = modId;
            this.failure = failure;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public Collection<String> getTypes(ModdedDataType type) {
            return List.of();
        }

        @Override
        public boolean isValidProvider(String id, ModdedDataType type) {
            return false;
        }

        @Override
        public void init() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
