package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.WorldCreatorCompat;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

public record WorldLifecycleRequest(
        String worldName,
        NamespacedKey worldKey,
        World.Environment environment,
        ChunkGenerator generator,
        BiomeProvider biomeProvider,
        WorldType worldType,
        boolean generateStructures,
        boolean hardcore,
        long seed,
        boolean studio,
        boolean benchmark,
        WorldLifecycleCaller callerKind
) {
    public static WorldLifecycleRequest fromCreator(WorldCreator creator, boolean studio, boolean benchmark, WorldLifecycleCaller callerKind) {
        return new WorldLifecycleRequest(
                creator.name(),
                WorldCreatorCompat.keyOf(creator),
                creator.environment(),
                creator.generator(),
                creator.biomeProvider(),
                creator.type(),
                creator.generateStructures(),
                creator.hardcore(),
                creator.seed(),
                studio,
                benchmark,
                callerKind
        );
    }

    public WorldCreator toWorldCreator() {
        WorldCreator creator = WorldCreatorCompat.ofKey(worldKey, worldName)
                .environment(environment)
                .generateStructures(generateStructures)
                .hardcore(hardcore)
                .type(worldType)
                .seed(seed)
                .generator(generator);
        if (biomeProvider != null) {
            creator.biomeProvider(biomeProvider);
        }
        return creator;
    }
}
