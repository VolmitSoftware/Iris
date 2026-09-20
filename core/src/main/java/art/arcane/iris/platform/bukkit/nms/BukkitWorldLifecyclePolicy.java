package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.platform.agent.Agent;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.lifecycle.WorldLifecycleStaging;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicy;
import org.bukkit.generator.ChunkGenerator;

import java.lang.instrument.Instrumentation;
import java.util.Objects;

public enum BukkitWorldLifecyclePolicy implements NativeWorldLifecyclePolicy {
    INSTANCE;

    @Override
    public String pluginName() {
        return "Iris";
    }

    @Override
    public String classLoaderLifecycleBridgeName() {
        return "art.arcane.iris.platform.agent.Installer";
    }

    @Override
    public Instrumentation instrumentation() {
        return Agent.getInstrumentation();
    }

    @Override
    public void requireClassLoaderCloseDeferral() throws ReflectiveOperationException {
        Agent.requireClassLoaderCloseDeferral();
    }

    @Override
    public void retainClassLoader(ClassLoader loader) {
        Agent.retainClassLoader(loader);
    }

    @Override
    public void releaseClassLoader(ClassLoader loader) {
        Agent.releaseClassLoader(loader);
    }

    @Override
    public void reportFailure(String message, Throwable failure) {
        IrisLogging.reportError(message, failure);
    }

    @Override
    public WorldDefinition stagedWorld(String levelId, ChunkGenerator constructorGenerator, boolean consume) {
        ChunkGenerator resolved = constructorGenerator instanceof PlatformChunkGenerator ? constructorGenerator : null;
        if (resolved == null) {
            resolved = consume ? WorldLifecycleStaging.consumeStemGenerator(levelId)
                    : WorldLifecycleStaging.peekStemGenerator(levelId);
        }
        if (!(resolved instanceof PlatformChunkGenerator generator)) {
            return null;
        }
        EngineTarget target = Objects.requireNonNull(generator.getTarget(), "Iris generator engine target");
        String identity = Objects.requireNonNull(target.getWorld(), "Iris generator world").identity();
        int separator = identity.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : identity.substring(0, separator);
        if (!namespace.equals("iris") && !namespace.equals("minecraft")) {
            throw new IllegalStateException("Iris generator has an unmanaged world identity: " + identity);
        }
        return new WorldDefinition(identity, "iris:" + target.getDimension().getDimensionTypeKey());
    }
}
