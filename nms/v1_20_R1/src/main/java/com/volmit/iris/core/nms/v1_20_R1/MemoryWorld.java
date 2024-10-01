package com.volmit.iris.core.nms.v1_20_R1;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IMemoryWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_20_R1.generator.CraftWorldInfo;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MemoryWorld implements IMemoryWorld {
    private static final AtomicLong C = new AtomicLong();
    private static final Field WORLDS_FIELD;

    private final AtomicReference<ServerLevel> level = new AtomicReference<>();

    public MemoryWorld(NamespacedKey levelType, WorldCreator creator) throws IOException {
        var name = "memory_world"+C.getAndIncrement();
        while (Bukkit.getWorld(name) != null) {
            name = "memory_world"+C.getAndIncrement();
        }

        var generator = creator.generator();
        var biomeProvider = creator.biomeProvider();
        var hardcore = creator.hardcore();
        var server = getServer();

        var tempDir = Files.createTempDirectory("MemoryGenerator");
        LevelStorageSource source = LevelStorageSource.createDefault(tempDir);
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, new ResourceLocation(levelType.getKey(), levelType.getNamespace()));
        var access = source.createAccess(name, stemKey);

        var worldLoader = server.worldLoader;
        var registry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        var options = new WorldOptions(creator.seed(), creator.generateStructures(), false);
        var properties = new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse(creator.generatorSettings().isEmpty() ? "{}" : creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
        var settings = new LevelSettings(name, GameType.byId(Bukkit.getDefaultGameMode().getValue()), hardcore, Difficulty.EASY, false, new GameRules(), worldLoader.dataConfiguration());
        var dimension = properties.create(worldLoader.datapackWorldgen()).bake(registry);
        Lifecycle lifecycle = dimension.lifecycle().add(worldLoader.datapackWorldgen().allRegistriesLifecycle());

        var worldData = new PrimaryLevelData(settings, options, dimension.specialWorldProperty(), lifecycle);
        worldData.customDimensions = registry;

        long obfSeed = BiomeManager.obfuscateSeed(creator.seed());
        var list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(worldData));
        var levelStem = registry.get(stemKey);
        if (levelStem == null)
            throw new IllegalStateException("Unknown dimension type: " + stemKey);

        var worldInfo = new CraftWorldInfo(worldData, access, creator.environment(), levelStem.type().value());
        if (biomeProvider == null && generator != null) {
            biomeProvider = generator.getDefaultBiomeProvider(worldInfo);
        }

        var levelKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(name));
        var level = new ServerLevel(
                server,
                server.executor,
                access,
                worldData,
                levelKey,
                levelStem,
                server.progressListenerFactory.create(0),
                worldData.isDebugWorld(),
                obfSeed,
                creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(),
                true,
                server.overworld().getRandomSequences(),
                creator.environment(),
                generator,
                biomeProvider
        );
        level.keepSpawnInMemory = false;
        Iris.instance.registerListener(this);
        this.level.set(level);
    }

    public World getBukkit() {
        var level = this.level.get();
        if (level == null)
            throw new IllegalStateException("World is not loaded");

        return level.getWorld();
    }

    public Chunk getChunk(int x, int z) {
        var level = this.level.get();
        if (level == null)
            throw new IllegalStateException("World is not loaded");
        return new CraftChunk(level, x, z);
    }

    public ChunkGenerator.ChunkData getChunkData(int x, int z) {
        var level = this.level.get();
        if (level == null)
            throw new IllegalStateException("World is not loaded");
        return new MemoryChunkData(x, z);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        var level = this.level.get();
        if (level == null || event.getWorld() != level.getWorld())
            return;

        this.level.set(null);
    }

    public boolean isClosed() {
        return this.level.get() == null;
    }

    @Override
    public void close() throws Exception {
        var level = this.level.get();
        if (level == null || !this.level.compareAndSet(level, null))
            return;

        level.getChunkSource().close(false);
        level.entityManager.close(false);
        level.convertable.deleteLevel();
        level.convertable.close();

        var map = (Map<String, World>) WORLDS_FIELD.get(Bukkit.getServer());
        map.remove(level.dimension().location().getPath());
        getServer().removeLevel(level);
        HandlerList.unregisterAll(this);
    }

    private static MinecraftServer getServer() {
        return ((CraftServer) Bukkit.getServer()).getServer();
    }

    static {
        try {
            WORLDS_FIELD = CraftServer.class.getDeclaredField("worlds");
            WORLDS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private class MemoryChunkData implements ChunkGenerator.ChunkData {
        private final int maxHeight;
        private final int minHeight;
        private final ChunkPos pos;
        private WeakReference<LevelChunk> chunk;

        private MemoryChunkData(int x, int z) {
            var level = MemoryWorld.this.level.get();
            if (level == null)
                throw new IllegalStateException("World is not loaded");

            var chunk = level.getChunk(x, z);
            this.minHeight = chunk.getMinBuildHeight();
            this.maxHeight = chunk.getMaxBuildHeight();
            this.pos = new ChunkPos(x, z);
            this.chunk = new WeakReference<>(chunk);
        }

        public LevelChunk getHandle() {
            LevelChunk chunk = this.chunk.get();
            if (chunk == null) {
                var level = MemoryWorld.this.level.get();
                if (level == null)
                    throw new IllegalStateException("World is not loaded");

                chunk = level.getChunk(this.pos.x, this.pos.z);
                this.chunk = new WeakReference<>(chunk);
            }
            return chunk;
        }

        @Override
        public int getMinHeight() {
            return minHeight;
        }

        @Override
        public int getMaxHeight() {
            return maxHeight;
        }

        @NotNull
        @Override
        public Biome getBiome(int x, int y, int z) {
            return CraftBlock.biomeBaseToBiome(this.getHandle().biomeRegistry, this.getHandle().getNoiseBiome(x >> 2, y >> 2, z >> 2));
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull Material material) {
            setBlock(x, y, z, material.createBlockData());
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull MaterialData material) {
            setBlock(x, y, z, CraftMagicNumbers.getBlock(material));
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull BlockData blockData) {
            setBlock(x, y, z, ((CraftBlockData) blockData).getState());
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {
            this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.createBlockData());
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material) {
            this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, CraftMagicNumbers.getBlock(material));
        }

        @Override
        public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {
            this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, ((CraftBlockData)blockData).getState());
        }

        @NotNull
        @Override
        public Material getType(int x, int y, int z) {
            return CraftMagicNumbers.getMaterial(this.getTypeId(x, y, z).getBlock());
        }

        @NotNull
        @Override
        public MaterialData getTypeAndData(int x, int y, int z) {
            return CraftMagicNumbers.getMaterial(this.getTypeId(x, y, z));
        }

        @NotNull
        @Override
        public BlockData getBlockData(int x, int y, int z) {
            return CraftBlockData.fromData(this.getTypeId(x, y, z));
        }

        @Deprecated
        @Override
        public byte getData(int x, int y, int z) {
            return CraftMagicNumbers.toLegacyData(this.getTypeId(x, y, z));
        }

        private void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockState type) {
            if (xMin <= 15 && yMin < this.maxHeight && zMin <= 15) {
                if (xMin < 0) {
                    xMin = 0;
                }

                if (yMin < this.minHeight) {
                    yMin = this.minHeight;
                }

                if (zMin < 0) {
                    zMin = 0;
                }

                if (xMax > 16) {
                    xMax = 16;
                }

                if (yMax > this.maxHeight) {
                    yMax = this.maxHeight;
                }

                if (zMax > 16) {
                    zMax = 16;
                }

                if (xMin < xMax && yMin < yMax && zMin < zMax) {
                    for(int y = yMin; y < yMax; ++y) {
                        for(int x = xMin; x < xMax; ++x) {
                            for(int z = zMin; z < zMax; ++z) {
                                this.setBlock(x, y, z, type);
                            }
                        }
                    }

                }
            }
        }


        private BlockState getTypeId(int x, int y, int z) {
            if (x == (x & 15) && y >= this.minHeight && y < this.maxHeight && z == (z & 15)) {
                ChunkAccess access = this.getHandle();
                return access.getBlockState(new BlockPos(access.getPos().getMinBlockX() + x, y, access.getPos().getMinBlockZ() + z));
            } else {
                return Blocks.AIR.defaultBlockState();
            }
        }

        private void setBlock(int x, int y, int z, BlockState type) {
            if (x == (x & 15) && y >= this.minHeight && y < this.maxHeight && z == (z & 15)) {
                ChunkAccess access = this.getHandle();
                BlockPos blockPosition = new BlockPos(access.getPos().getMinBlockX() + x, y, access.getPos().getMinBlockZ() + z);
                BlockState oldBlockData = access.setBlockState(blockPosition, type, false);
                if (type.hasBlockEntity()) {
                    BlockEntity tileEntity = ((EntityBlock)type.getBlock()).newBlockEntity(blockPosition, type);
                    if (tileEntity == null) {
                        access.removeBlockEntity(blockPosition);
                    } else {
                        access.setBlockEntity(tileEntity);
                    }
                } else if (oldBlockData != null && oldBlockData.hasBlockEntity()) {
                    access.removeBlockEntity(blockPosition);
                }

            }
        }
    }
}
