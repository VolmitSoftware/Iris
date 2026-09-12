package art.arcane.iris.world.lifecycle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class WorldLifecycleRuntimeLevelStemTest {
    @Test
    public void runtimeStemUsesFullServerRegistryAccessForPlatformGenerators() throws Exception {
        Object datapackDimensions = new MissingDimensionTypeRegistry();
        Object serverRegistryAccess = new Object();
        CapabilitySnapshot capabilities = CapabilitySnapshotFixtures.forTestingRuntimeRegistries(ServerFamily.PURPUR, false, datapackDimensions, serverRegistryAccess);
        WorldLifecycleRequest request = new WorldLifecycleRequest(
                "studio",
                NamespacedKey.minecraft("studio"),
                World.Environment.NORMAL,
                new TestingPlatformChunkGenerator(),
                null,
                null,
                true,
                false,
                1337L,
                true,
                false,
                WorldLifecycleCaller.STUDIO
        );
        AtomicReference<Object> seenRegistryAccess = new AtomicReference<>();
        INMSBinding binding = createBinding((registryAccess, generator) -> {
            seenRegistryAccess.set(registryAccess);
            return "runtime-stem";
        });

        Object levelStem = WorldLifecycleSupport.resolveRuntimeLevelStem(capabilities, request, binding);

        assertEquals("runtime-stem", levelStem);
        assertSame(serverRegistryAccess, seenRegistryAccess.get());
        assertNotSame(datapackDimensions, seenRegistryAccess.get());
    }

    private static INMSBinding createBinding(RuntimeStemFactory factory) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("createRuntimeLevelStem".equals(method.getName())) {
                return factory.create(args[0], (ChunkGenerator) args[1]);
            }
            Class<?> returnType = method.getReturnType();
            if (boolean.class.equals(returnType)) {
                return false;
            }
            if (int.class.equals(returnType)) {
                return 0;
            }
            if (long.class.equals(returnType)) {
                return 0L;
            }
            if (float.class.equals(returnType)) {
                return 0F;
            }
            if (double.class.equals(returnType)) {
                return 0D;
            }
            return null;
        };
        return (INMSBinding) Proxy.newProxyInstance(
                INMSBinding.class.getClassLoader(),
                new Class[]{INMSBinding.class},
                handler
        );
    }

    @FunctionalInterface
    private interface RuntimeStemFactory {
        Object create(Object registryAccess, ChunkGenerator generator);
    }

    private static final class MissingDimensionTypeRegistry {
    }

    private static final class TestingPlatformChunkGenerator extends ChunkGenerator implements PlatformChunkGenerator {
        @Override
        public Engine getEngine() {
            return null;
        }

        @Override
        public IrisData getData() {
            return null;
        }

        @Override
        public EngineTarget getTarget() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isStudio() {
            return true;
        }

        @Override
        public CompletableFuture<Integer> getSpawnChunks() {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public void hotload() {
        }
    }
}
