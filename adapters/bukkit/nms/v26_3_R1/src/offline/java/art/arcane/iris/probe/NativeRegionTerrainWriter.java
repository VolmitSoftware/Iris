package art.arcane.iris.probe;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class NativeRegionTerrainWriter {
    private final Registry<Biome> biomes;
    private final RegistryAccess registries;
    private final PalettedContainerFactory containers;
    private final Map<String, BlockState> tileStates = new ConcurrentHashMap<>();
    private final Map<String, Holder<Biome>> biomeHolders = new ConcurrentHashMap<>();

    NativeRegionTerrainWriter(RegistryAccess registries) {
        this.registries = Objects.requireNonNull(registries, "registries");
        biomes = registries.lookupOrThrow(Registries.BIOME);
        containers = PalettedContainerFactory.create(registries);
    }

    RegistryAccess registries() {
        return registries;
    }

    CompoundTag encode(TerrainInput input) {
        TerrainData terrain = terrain(input, null);
        SerializableChunkData data = new SerializableChunkData(containers,
                new ChunkPos(input.chunkX(), input.chunkZ()), input.minimumY() >> 4, 0L, 0L,
                ChunkStatus.EMPTY, null, null, UpgradeData.EMPTY, Map.of(),
                new ChunkAccess.PackedTicks(List.of(), List.of()), new ShortList[input.height() >> 4], false,
                terrain.sections(), List.of(), terrain.blockEntities(), new CompoundTag(), null);
        CompoundTag tag = data.write();
        tag.putString("iris:checkpoint", "iris:terrain_checkpoint");
        return tag;
    }


    void fill(ProtoChunk chunk, TerrainInput input) {
        if (chunk.getPos().x() != input.chunkX() || chunk.getPos().z() != input.chunkZ()
                || chunk.getMinY() != input.minimumY() || chunk.getHeight() != input.height()) {
            throw new IllegalArgumentException("Generated terrain does not match its planned chunk");
        }
        TerrainData terrain = terrain(input, chunk.getSections());
        for (int i = 0; i < terrain.sections().size(); i++) {
            chunk.getSections()[i] = terrain.sections().get(i).chunkSection();
        }
        for (CompoundTag entity : terrain.blockEntities()) {
            chunk.setBlockEntityNbt(entity);
        }
        chunk.markUnsaved();
    }

    private TerrainData terrain(TerrainInput input, LevelChunkSection[] existingBiomes) {
        Int2ObjectOpenHashMap<TileInput> tiles = new Int2ObjectOpenHashMap<>(input.tiles().size());
        for (TileInput tile : input.tiles()) {
            if (tile.y() >= input.height() || tiles.put(tile.x() | tile.z() << 4 | tile.y() << 8, tile) != null) {
                throw new IllegalArgumentException("Duplicate or out-of-bounds tile data");
            }
        }
        List<CompoundTag> blockEntities = new ArrayList<>();
        List<SerializableChunkData.SectionData> sections = new ArrayList<>(input.height() >> 4);
        for (int sectionY = 0; sectionY < input.height(); sectionY += 16) {
            PalettedContainer<BlockState> blocks = containers.createForBlockStates();
            PalettedContainer<Holder<Biome>> sectionBiomes = existingBiomes == null
                    ? containers.createForBiomes() : existingBiomes[sectionY >> 4].getBiomes().copy();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = Objects.requireNonNull(input.blocks().state(x, sectionY + y, z), "block state");
                        TileInput tile = tiles.isEmpty() ? null : tiles.get(x | z << 4 | (sectionY + y) << 8);
                        if (tile == null && state == Blocks.AIR.defaultBlockState()) {
                            continue;
                        }
                        if (tile != null) {
                            state = tileStates.computeIfAbsent(tile.stateKey(), NativeRegionTerrainWriter::resolveState);
                        }
                        blocks.set(x, y, z, state);
                        if (state.hasBlockEntity()) {
                            BlockPos position = new BlockPos((input.chunkX() << 4) + x,
                                    input.minimumY() + sectionY + y, (input.chunkZ() << 4) + z);
                            CompoundTag entity = encodeBlockEntity(state, position, tile);
                            if (entity != null) {
                                blockEntities.add(entity);
                            }
                        } else if (tile != null) {
                            throw new IllegalArgumentException("Tile data has no native block entity at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
            if (existingBiomes == null) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        for (int x = 0; x < 4; x++) {
                            String key = Objects.requireNonNull(input.biomes().key(x << 2, sectionY + (y << 2), z << 2), "biome key");
                            sectionBiomes.set(x, y, z, biomeHolders.computeIfAbsent(key, this::resolveBiome));
                        }
                    }
                }
            }
            sections.add(new SerializableChunkData.SectionData((input.minimumY() + sectionY) >> 4,
                    new LevelChunkSection(blocks, sectionBiomes), null, null));
        }
        return new TerrainData(sections, blockEntities);
    }

    private CompoundTag encodeBlockEntity(BlockState state, BlockPos position, TileInput tile) {
        BlockEntity entity = ((EntityBlock) state.getBlock()).newBlockEntity(position, state);
        if (entity == null) {
            if (tile != null) {
                throw new IllegalStateException("Native block did not create a tile entity at " + position);
            }
            return null;
        }
        if (tile != null) {
            CompoundTag merged = entity.saveWithoutMetadata(registries);
            merged.merge(tile.payload());
            entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, merged));
        }
        return entity.saveWithFullMetadata(registries);
    }

    BlockState readBlock(CompoundTag tag, int x, int y, int z) {
        for (CompoundTag section : tag.getListOrEmpty("sections").compoundStream().toList()) {
            if (section.getByteOr("Y", (byte) 0) == y >> 4) {
                return containers.blockStatesContainerCodec().parse(NbtOps.INSTANCE,
                        section.getCompoundOrEmpty("block_states")).getOrThrow().get(x & 15, y & 15, z & 15);
            }
        }
        throw new IllegalArgumentException("Missing section at " + y);
    }

    static void write(RegionFile region, CompoundTag tag) throws IOException {
        ChunkPos position = new ChunkPos(tag.getIntOr("xPos", 0), tag.getIntOr("zPos", 0));
        try (DataOutputStream output = region.getChunkDataOutputStream(position)) {
            NbtIo.write(tag, output);
        }
    }

    private static BlockState resolveState(String key) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, key, false).blockState();
        } catch (CommandSyntaxException failure) {
            throw new IllegalArgumentException("Unsupported native block state: " + key, failure);
        }
    }

    private Holder<Biome> resolveBiome(String key) {
        return biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(key)))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported native biome: " + key));
    }

    record TerrainInput(int chunkX, int chunkZ, int minimumY, int height, BlockLookup blocks, KeyLookup biomes, List<TileInput> tiles) {
        TerrainInput {
            if ((minimumY & 15) != 0 || (height & 15) != 0 || height < 16
                    || minimumY < -2032 || (long) minimumY + height > 2032) {
                throw new IllegalArgumentException("Terrain bounds must be section aligned within the native height range");
            }
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(biomes, "biomes");
            tiles = List.copyOf(tiles);
        }
    }

    record TileInput(int x, int y, int z, String stateKey, CompoundTag payload) {
        TileInput {
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0) {
                throw new IllegalArgumentException("Tile position is outside its chunk");
            }
            Objects.requireNonNull(stateKey, "stateKey");
            payload = Objects.requireNonNull(payload, "payload").copy();
        }
    }

    private record TerrainData(List<SerializableChunkData.SectionData> sections, List<CompoundTag> blockEntities) {
    }

    @FunctionalInterface
    interface BlockLookup {
        BlockState state(int x, int y, int z);
    }

    @FunctionalInterface
    interface KeyLookup {
        String key(int x, int y, int z);
    }
}
