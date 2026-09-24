package art.arcane.iris.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

final class OfflineFeatureWorld implements WorldGenLevel {
    private final Options options;
    private final Map<Long, ProtoChunk> chunks = new HashMap<>();
    private final RandomSource random;
    private final BiomeManager biomeManager;
    private final WorldBorder border = new WorldBorder();
    private final LevelTickAccess<Block> blockTicks;
    private final LevelTickAccess<Fluid> fluidTicks;
    private long subTick;

    OfflineFeatureWorld(Options options, List<ProtoChunk> chunks) {
        this.options = options;
        for (ProtoChunk chunk : chunks) {
            if (chunk.getPersistedStatus() != ChunkStatus.TERRAIN) {
                throw new IllegalArgumentException("Feature fixture requires TERRAIN chunks");
            }
            if (this.chunks.put(chunk.getPos().pack(), chunk) != null) {
                throw new IllegalArgumentException("Duplicate chunk");
            }
        }
        random = RandomSource.create(options.seed());
        biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(options.seed()));
        blockTicks = ticks(ProtoChunk::getBlockTicks);
        fluidTicks = ticks(ProtoChunk::getFluidTicks);
    }

    @Override
    public long getGameTime() {
        return 0L;
    }

    @Override
    public ServerLevel getMinecraftWorld() {
        throw unsupported("ServerLevel");
    }

    @Override
    public long getSeed() {
        return options.seed();
    }

    @Override
    public boolean ensureCanWrite(BlockPos position) {
        return !isOutsideBuildHeight(position) && hasChunk(position.getX() >> 4, position.getZ() >> 4);
    }

    @Override
    public ServerLevel getLevel() {
        throw unsupported("ServerLevel");
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos position) {
        throw unsupported("getCurrentDifficultyAt");
    }

    @Override
    public long nextSubTickCount() {
        return subTick++;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return blockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return fluidTicks;
    }

    @Override
    public LevelData getLevelData() {
        throw unsupported("getLevelData");
    }

    @Override
    public MinecraftServer getServer() {
        throw unsupported("getServer");
    }

    @Override
    public ChunkSource getChunkSource() {
        throw unsupported("getChunkSource");
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    @Override
    public void updateNeighborsAt(BlockPos position, Block block) {
        throw unsupported("updateNeighborsAt");
    }

    @Override
    public void neighborShapeChanged(Direction direction, BlockPos position, BlockPos neighbourPosition, BlockState neighbourState, int updateFlags, int updateLimit) {
        throw unsupported("neighborShapeChanged");
    }

    @Override
    public void playSound(Entity source, BlockPos position, SoundEvent sound, SoundSource soundSource, float volume, float pitch) {
        throw unsupported("playSound");
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        throw unsupported("addParticle");
    }

    @Override
    public void levelEvent(Entity source, int eventId, BlockPos position, int data) {
        throw unsupported("levelEvent");
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
        throw unsupported("gameEvent");
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ);
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
        ProtoChunk chunk = chunks.get(ChunkPos.pack(chunkX, chunkZ));
        if (chunk == null && !create) {
            return null;
        }
        chunk = chunk(chunkX, chunkZ);
        if (!chunk.getPersistedStatus().isOrAfter(status)) {
            throw unsupported("chunk status " + status);
        }
        return chunk;
    }

    @Override
    public ChunkAccess getChunkIfLoadedImmediately(int chunkX, int chunkZ) {
        return chunks.get(ChunkPos.pack(chunkX, chunkZ));
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunks.containsKey(ChunkPos.pack(chunkX, chunkZ));
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return chunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1;
    }

    @Override
    public int getSkyDarken() {
        throw unsupported("getSkyDarken");
    }

    @Override
    public BiomeManager getBiomeManager() {
        return biomeManager;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return chunk(quartX >> 2, quartZ >> 2).getNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public int getSeaLevel() {
        return options.seaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return options.dimension();
    }

    @Override
    public int getMinY() {
        return options.dimension().minY();
    }

    @Override
    public int getHeight() {
        return options.dimension().height();
    }

    @Override
    public RegistryAccess registryAccess() {
        return options.registries();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        throw unsupported("environmentAttributes");
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw unsupported("getLightEngine");
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos position) {
        throw unsupported("getBrightness");
    }

    @Override
    public int getRawBrightness(BlockPos position, int ambientDarkening) {
        throw unsupported("getRawBrightness");
    }

    @Override
    public boolean canSeeSky(BlockPos position) {
        throw unsupported("canSeeSky");
    }

    @Override
    public WorldBorder getWorldBorder() {
        return border;
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return chunk(position).getBlockEntity(position);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return chunk(position).getBlockState(position);
    }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos position) {
        return hasChunk(position.getX() >> 4, position.getZ() >> 4) ? getBlockState(position) : null;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public FluidState getFluidIfLoaded(BlockPos position) {
        BlockState state = getBlockStateIfLoaded(position);
        return state == null ? null : state.getFluidState();
    }

    @Override
    public List<Entity> getEntities(Entity source, AABB area, Predicate<? super Entity> predicate) {
        throw unsupported("getEntities");
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> predicate) {
        throw unsupported("getEntities");
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }

    @Override
    public boolean isStateAtPosition(BlockPos position, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(position));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos position, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(position));
    }

    @Override
    public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos position) {
        return position.atY(getHeight(type, position.getX(), position.getZ()));
    }

    @Override
    public boolean setBlock(BlockPos position, BlockState state, int updateFlags, int updateLimit) {
        if (!ensureCanWrite(position)) {
            throw new IllegalStateException("Feature write outside prepared bounds: " + position);
        }
        ProtoChunk chunk = chunk(position);
        BlockState previous = chunk.setBlockState(position, state, updateFlags);
        if (previous == state) {
            return false;
        }
        chunk.removeBlockEntity(position);
        if (state.getBlock() instanceof EntityBlock block) {
            BlockEntity entity = block.newBlockEntity(position, state);
            if (entity != null) {
                chunk.setBlockEntity(entity);
            }
        }
        BlockPos postProcess = state.getPostProcessPos(this, position);
        if (postProcess != null) {
            chunk(postProcess).markPosForPostProcessing(postProcess);
        }
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos position, boolean move) {
        return setBlock(position, Blocks.AIR.defaultBlockState(), 2, 0);
    }

    @Override
    public boolean destroyBlock(BlockPos position, boolean drop, Entity source, int updateLimit) {
        throw unsupported("destroyBlock");
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        throw unsupported("addFreshEntity");
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        throw unsupported("addFreshEntity");
    }

    private ProtoChunk chunk(BlockPos position) {
        return chunk(position.getX() >> 4, position.getZ() >> 4);
    }

    private ProtoChunk chunk(int x, int z) {
        ProtoChunk chunk = chunks.get(ChunkPos.pack(x, z));
        if (chunk == null) {
            throw new IllegalStateException("Feature escaped prepared chunk region: " + x + "," + z);
        }
        return chunk;
    }

    private <T> LevelTickAccess<T> ticks(Function<ProtoChunk, TickContainerAccess<T>> containers) {
        return new LevelTickAccess<>() {
            @Override
            public void schedule(ScheduledTick<T> tick) {
                containers.apply(chunk(tick.pos())).schedule(tick);
            }
            @Override
            public boolean hasScheduledTick(BlockPos position, T type) {
                return containers.apply(chunk(position)).hasScheduledTick(position, type);
            }
            @Override
            public int count() {
                int count = 0;
                for (ProtoChunk chunk : chunks.values()) {
                    count += containers.apply(chunk).count();
                }
                return count;
            }
            @Override
            public boolean willTickThisTick(BlockPos position, T type) {
                throw unsupported("live tick execution");
            }
        };
    }

    private static UnsupportedOperationException unsupported(String capability) {
        return new UnsupportedOperationException("Offline feature fixture has no " + capability);
    }

    record Options(RegistryAccess registries, DimensionType dimension, long seed, int seaLevel) {
    }
}
