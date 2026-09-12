package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

public final class IrisWorldInfoFactory {
    private IrisWorldInfoFactory() {
    }

    public static IrisWorldInfo forWorld(World world) {
        if (world == null) {
            return null;
        }

        ChunkGenerator generator = world.getGenerator();
        return generator instanceof PlatformChunkGenerator platform ? from(platform) : null;
    }

    public static IrisWorldInfo from(PlatformChunkGenerator generator) {
        if (generator == null) {
            return null;
        }

        Engine engine = generator.getEngine();
        if (engine == null || engine.isClosed()) {
            return null;
        }

        IrisWorld irisWorld = engine.getWorld();
        IrisDimension dimension = engine.getDimension();
        if (irisWorld == null || dimension == null) {
            return null;
        }

        return build(
                dimension.getLoadKey(),
                irisWorld.identity(),
                irisWorld.getRawWorldSeed(),
                engine.getMinHeight(),
                engine.getMaxHeight(),
                dimension.getFluidHeight(),
                generator.isStudio());
    }

    static IrisWorldInfo build(
            String dimensionKey,
            String worldIdentity,
            long seed,
            int minHeight,
            int maxHeight,
            int fluidHeightAboveMinimum,
            boolean studio) {
        if (dimensionKey == null || worldIdentity == null || maxHeight <= minHeight) {
            return null;
        }

        return new IrisWorldInfo(
                dimensionKey,
                worldIdentity,
                seed,
                minHeight,
                maxHeight,
                fluidHeightAboveMinimum + minHeight,
                studio);
    }
}
