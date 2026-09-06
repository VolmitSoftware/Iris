package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.nativegen.WorldgenTerrainHeightmaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
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
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class NativeStructureWorldgenAccess implements WorldGenLevel {
    private static final int WRITE_RADIUS = 1;
    private static final NativeStructurePoiUpdates POI_UPDATES = NativeStructurePoiUpdates.create();

    private final WorldGenLevel delegate;
    private final ChunkPos generationCenter;
    private final IntBinaryOperator surfaceFirstFreeY;
    private final IntBinaryOperator floorFirstFreeY;
    private final boolean virtualizeHeights;
    private final Predicate<BlockPos> protectedPosition;
    private final Holder<Biome> fallbackBiome;
    private final BiomeManager biomeManager;
    private final AABB generationBounds;
    private final LevelTickAccess<Block> blockTicks;
    private final LevelTickAccess<Fluid> fluidTicks;
    private final Long2LongOpenHashMap terrainHeights;
    private final Long2ObjectOpenHashMap<ChunkAccess> outsideChunks;

    private NativeStructureWorldgenAccess(WorldGenLevel delegate, Boundary boundary) {
        this.delegate = Objects.requireNonNull(delegate, "Native structure world access requires a delegate");
        this.generationCenter = Objects.requireNonNull(
                boundary.generationCenter(), "Native structure world access requires a generation center");
        this.surfaceFirstFreeY = Objects.requireNonNull(
                boundary.surfaceFirstFreeY(), "Native structure world access requires a surface resolver");
        this.floorFirstFreeY = Objects.requireNonNull(
                boundary.floorFirstFreeY(), "Native structure world access requires an ocean-floor resolver");
        this.virtualizeHeights = boundary.virtualizeHeights();
        this.protectedPosition = Objects.requireNonNull(
                boundary.protectedPosition(), "World access requires a protected-position predicate");
        BlockPos biomeSample = generationCenter.getMiddleBlockPosition(delegate.getSeaLevel());
        this.fallbackBiome = delegate.getBiome(biomeSample);
        this.biomeManager = delegate.getBiomeManager().withDifferentSource(this);
        this.generationBounds = new AABB(
                generationCenter.getMinBlockX() - 16,
                delegate.getMinY(),
                generationCenter.getMinBlockZ() - 16,
                generationCenter.getMaxBlockX() + 17,
                delegate.getMinY() + delegate.getHeight(),
                generationCenter.getMaxBlockZ() + 17);
        this.blockTicks = new BoundedTickAccess<>(delegate.getBlockTicks(), this::isWritable);
        this.fluidTicks = new BoundedTickAccess<>(delegate.getFluidTicks(), this::isWritable);
        this.terrainHeights = new Long2LongOpenHashMap();
        this.terrainHeights.defaultReturnValue(Long.MIN_VALUE);
        this.outsideChunks = new Long2ObjectOpenHashMap<>();
    }

    static NativeStructureWorldgenAccess create(WorldGenLevel delegate, ChunkPos generationCenter,
                                                IntBinaryOperator surfaceFirstFreeY,
                                                IntBinaryOperator floorFirstFreeY,
                                                boolean virtualizeHeights,
                                                Predicate<BlockPos> protectedPosition) {
        return new NativeStructureWorldgenAccess(delegate, new Boundary(
                generationCenter, surfaceFirstFreeY, floorFirstFreeY, virtualizeHeights, protectedPosition));
    }

    @Override
    public long getSeed() {
        return delegate.getSeed();
    }

    @Override
    public boolean ensureCanWrite(BlockPos position) {
        return isWritable(position) && delegate.ensureCanWrite(position);
    }

    @Override
    public void setCurrentlyGenerating(Supplier<String> description) {
        delegate.setCurrentlyGenerating(description);
    }

    @Override
    public ServerLevel getLevel() {
        return delegate.getLevel();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos position) {
        if (isReadable(position)) {
            return delegate.getCurrentDifficultyAt(position);
        }
        return delegate.getCurrentDifficultyAt(generationCenter.getMiddleBlockPosition(
                Mth.clamp(position.getY(), getMinY(), getMaxY() - 1)));
    }

    @Override
    public long nextSubTickCount() {
        return delegate.nextSubTickCount();
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
        return delegate.getLevelData();
    }

    @Override
    public MinecraftServer getServer() {
        return delegate.getServer();
    }

    @Override
    public ChunkSource getChunkSource() {
        return delegate.getChunkSource();
    }

    @Override
    public RandomSource getRandom() {
        return delegate.getRandom();
    }

    @Override
    public void updateNeighborsAt(BlockPos position, Block block) {
        if (isWritableNeighbourhood(position)) {
            delegate.updateNeighborsAt(position, block);
        }
    }

    @Override
    public void neighborShapeChanged(Direction direction, BlockPos position,
                                     BlockPos neighbourPosition, BlockState neighbourState,
                                     int updateFlags, int updateLimit) {
        if (isWritable(position) && isWritable(neighbourPosition)) {
            delegate.neighborShapeChanged(
                    direction, position, neighbourPosition, neighbourState, updateFlags, updateLimit);
        }
    }

    @Override
    public void playSound(Entity source, BlockPos position, SoundEvent sound,
                          SoundSource soundSource, float volume, float pitch) {
        if (isWritable(position)) {
            delegate.playSound(source, position, sound, soundSource, volume, pitch);
        }
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z,
                            double velocityX, double velocityY, double velocityZ) {
        if (isWritable(BlockPos.containing(x, y, z))) {
            delegate.addParticle(particle, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    @Override
    public void levelEvent(Entity source, int eventId, BlockPos position, int data) {
        if (isWritable(position)) {
            delegate.levelEvent(source, eventId, position, data);
        }
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
        if (isWritable(BlockPos.containing(position))) {
            delegate.gameEvent(event, position, context);
        }
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        if (isInsideGenerationRegion(chunkX, chunkZ)) {
            return delegate.getChunk(chunkX, chunkZ);
        }
        return outsideChunk(chunkX, chunkZ);
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
        if (isInsideGenerationRegion(chunkX, chunkZ)) {
            return delegate.getChunk(chunkX, chunkZ, status, create);
        }
        return create ? outsideChunk(chunkX, chunkZ) : null;
    }

    @Override
    public ChunkAccess getChunkIfLoadedImmediately(int chunkX, int chunkZ) {
        if (!isInsideGenerationRegion(chunkX, chunkZ)) {
            return null;
        }
        return delegate.getChunkIfLoadedImmediately(chunkX, chunkZ);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return isInsideGenerationRegion(chunkX, chunkZ) && delegate.hasChunk(chunkX, chunkZ);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        if (!virtualizeHeights && isInsideGenerationRegion(x >> 4, z >> 4)) {
            return delegate.getHeight(type, x, z);
        }
        return height(type, x, z);
    }

    @Override
    public int getSkyDarken() {
        return delegate.getSkyDarken();
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
        if (isInsideGenerationRegion(QuartPos.toSection(quartX), QuartPos.toSection(quartZ))) {
            return delegate.getUncachedNoiseBiome(quartX, quartY, quartZ);
        }
        return fallbackBiome;
    }

    @Override
    public boolean isClientSide() {
        return delegate.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return delegate.dimensionType();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public RegistryAccess registryAccess() {
        return delegate.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public EnvironmentAttributeReader environmentAttributes() {
        return delegate.environmentAttributes();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos position) {
        if (isReadable(position)) {
            return delegate.getBrightness(layer, position);
        }
        return layer == LightLayer.SKY && canSeeSky(position) ? 15 : 0;
    }

    @Override
    public int getRawBrightness(BlockPos position, int ambientDarkening) {
        if (isReadable(position)) {
            return delegate.getRawBrightness(position, ambientDarkening);
        }
        return Math.max(0, getBrightness(LightLayer.SKY, position) - ambientDarkening);
    }

    @Override
    public boolean canSeeSky(BlockPos position) {
        if (isReadable(position)) {
            return delegate.canSeeSky(position);
        }
        return position.getY() >= height(Heightmap.Types.WORLD_SURFACE_WG, position.getX(), position.getZ());
    }

    @Override
    public WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        if (isInsideGenerationRegion(chunkX, chunkZ)) {
            return delegate.getChunkForCollisions(chunkX, chunkZ);
        }
        return outsideChunk(chunkX, chunkZ);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return isWritable(position) ? delegate.getBlockEntity(position) : null;
    }

    @Override
    public <T extends BlockEntity> Optional<T> getBlockEntity(
            BlockPos position, BlockEntityType<T> type) {
        return isWritable(position) ? delegate.getBlockEntity(position, type) : Optional.empty();
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return isReadable(position) ? delegate.getBlockState(position) : terrainState(position);
    }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos position) {
        return isReadable(position) ? delegate.getBlockStateIfLoaded(position) : null;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return isReadable(position) ? delegate.getFluidState(position) : terrainState(position).getFluidState();
    }

    @Override
    public FluidState getFluidIfLoaded(BlockPos position) {
        return isReadable(position) ? delegate.getFluidIfLoaded(position) : null;
    }

    @Override
    public List<Entity> getEntities(Entity source, AABB area, Predicate<? super Entity> predicate) {
        AABB boundedArea = boundedArea(area);
        return boundedArea == null ? List.of() : delegate.getEntities(source, boundedArea, predicate);
    }

    @Override
    public <T extends Entity> List<T> getEntities(
            EntityTypeTest<Entity, T> type,
            AABB area, Predicate<? super T> predicate) {
        AABB boundedArea = boundedArea(area);
        return boundedArea == null ? List.of() : delegate.getEntities(type, boundedArea, predicate);
    }

    @Override
    public List<? extends Player> players() {
        return delegate.players();
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
        return isWritable(position)
                && (POI_UPDATES != null
                ? POI_UPDATES.setBlock(delegate, position, state, updateFlags, updateLimit)
                : delegate.setBlock(position, state, updateFlags, updateLimit));
    }

    @Override
    public boolean removeBlock(BlockPos position, boolean move) {
        if (!isWritable(position)) {
            return false;
        }
        return POI_UPDATES != null && delegate instanceof WorldGenRegion
                ? setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, Block.UPDATE_LIMIT)
                : delegate.removeBlock(position, move);
    }

    @Override
    public boolean destroyBlock(BlockPos position, boolean drop, Entity source, int updateLimit) {
        if (!isWritable(position)) {
            return false;
        }
        return POI_UPDATES != null && delegate instanceof WorldGenRegion
                ? !delegate.getBlockState(position).isAir()
                && setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL, updateLimit)
                : delegate.destroyBlock(position, drop, source, updateLimit);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return isWritable(entity.blockPosition()) && delegate.addFreshEntity(entity);
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return isWritable(entity.blockPosition()) && delegate.addFreshEntity(entity, reason);
    }

    private boolean isReadable(BlockPos position) {
        return isInsideGenerationRegion(position.getX() >> 4, position.getZ() >> 4)
                && !isOutsideBuildHeight(position);
    }

    private boolean isWritable(BlockPos position) {
        return isReadable(position) && !protectedPosition.test(position);
    }

    private boolean isWritableNeighbourhood(BlockPos position) {
        return isWritable(position)
                && isWritable(position.north())
                && isWritable(position.south())
                && isWritable(position.east())
                && isWritable(position.west())
                && isWritable(position.above())
                && isWritable(position.below());
    }

    private AABB boundedArea(AABB area) {
        double minX = Math.max(area.minX, generationBounds.minX);
        double minY = Math.max(area.minY, generationBounds.minY);
        double minZ = Math.max(area.minZ, generationBounds.minZ);
        double maxX = Math.min(area.maxX, generationBounds.maxX);
        double maxY = Math.min(area.maxY, generationBounds.maxY);
        double maxZ = Math.min(area.maxZ, generationBounds.maxZ);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return null;
        }
        if (minX == area.minX && minY == area.minY && minZ == area.minZ
                && maxX == area.maxX && maxY == area.maxY && maxZ == area.maxZ) {
            return area;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    boolean isInsideGenerationRegion(int chunkX, int chunkZ) {
        return Math.abs(chunkX - generationCenter.x()) <= WRITE_RADIUS
                && Math.abs(chunkZ - generationCenter.z()) <= WRITE_RADIUS;
    }

    @Override
    public boolean isOutsideBuildHeight(BlockPos position) {
        return position.getY() < getMinY() || position.getY() >= getMaxY();
    }

    private BlockState terrainState(BlockPos position) {
        if (isOutsideBuildHeight(position)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        long heights = heights(position.getX(), position.getZ());
        int surface = (int) (heights >> 32);
        int floor = (int) heights;
        if (position.getY() < floor) {
            return Blocks.STONE.defaultBlockState();
        }
        if (position.getY() < surface) {
            return Blocks.WATER.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    private int height(Heightmap.Types type, int x, int z) {
        long heights = heights(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return (int) heights;
        }
        return (int) (heights >> 32);
    }

    private long heights(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        long cached = terrainHeights.get(key);
        if (cached != Long.MIN_VALUE) {
            return cached;
        }
        int floor = Mth.clamp(floorFirstFreeY.applyAsInt(x, z), getMinY(), getMaxY());
        int surface = Mth.clamp(surfaceFirstFreeY.applyAsInt(x, z), floor, getMaxY());
        long heights = ((long) surface << 32) | (floor & 0xffffffffL);
        terrainHeights.put(key, heights);
        return heights;
    }

    private ChunkAccess outsideChunk(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkAccess cached = outsideChunks.get(key);
        if (cached != null) {
            return cached;
        }
        EmptyLevelChunk chunk = new EmptyLevelChunk(getLevel(), new ChunkPos(chunkX, chunkZ), fallbackBiome);
        WorldgenTerrainHeightmaps.primeTerrain(chunk, surfaceFirstFreeY, floorFirstFreeY);
        outsideChunks.put(key, chunk);
        return chunk;
    }

    private record Boundary(ChunkPos generationCenter,
                            IntBinaryOperator surfaceFirstFreeY,
                            IntBinaryOperator floorFirstFreeY,
                            boolean virtualizeHeights,
                            Predicate<BlockPos> protectedPosition) {
    }

    private static final class BoundedTickAccess<T> implements LevelTickAccess<T> {
        private final LevelTickAccess<T> delegate;
        private final Predicate<BlockPos> writable;

        private BoundedTickAccess(LevelTickAccess<T> delegate, Predicate<BlockPos> writable) {
            this.delegate = delegate;
            this.writable = writable;
        }

        @Override
        public void schedule(ScheduledTick<T> tick) {
            if (writable.test(tick.pos())) {
                delegate.schedule(tick);
            }
        }

        @Override
        public boolean hasScheduledTick(BlockPos position, T type) {
            return writable.test(position) && delegate.hasScheduledTick(position, type);
        }

        @Override
        public int count() {
            return delegate.count();
        }

        @Override
        public boolean willTickThisTick(BlockPos position, T type) {
            return writable.test(position) && delegate.willTickThisTick(position, type);
        }
    }
}
