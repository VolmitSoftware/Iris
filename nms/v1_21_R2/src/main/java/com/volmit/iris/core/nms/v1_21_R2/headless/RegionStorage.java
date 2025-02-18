package com.volmit.iris.core.nms.v1_21_R2.headless;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.headless.IRegion;
import com.volmit.iris.core.nms.headless.IRegionStorage;
import com.volmit.iris.core.nms.headless.SerializableChunk;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.J;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.FileUtil;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R2.CraftServer;
import org.bukkit.craftbukkit.v1_21_R2.block.CraftBiome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RegionStorage implements IRegionStorage, LevelHeightAccessor {
    private static final AtomicCache<RegistryAccess> CACHE = new AtomicCache<>();
    private final KMap<Long, Region> regions = new KMap<>();
    private final Path folder;

    private final Engine engine;
    private final KMap<String, Holder<Biome>> customBiomes = new KMap<>();
    private final KMap<org.bukkit.block.Biome, Holder<Biome>> minecraftBiomes;
    private final RNG biomeRng;
    private final @Getter int minY;
    private final @Getter int height;

    private transient boolean closed = false;

    public RegionStorage(Engine engine) {
        this.engine = engine;
        this.folder = new File(engine.getWorld().worldFolder(), "region").toPath();
        this.biomeRng = new RNG(engine.getSeedManager().getBiome());

        this.minY = engine.getDimension().getMinHeight();
        this.height = engine.getDimension().getMaxHeight() - minY;

        AtomicInteger failed = new AtomicInteger();
        var dimKey = engine.getDimension().getLoadKey();
        for (var biome : engine.getAllBiomes()) {
            if (!biome.isCustom()) continue;
            for (var custom : biome.getCustomDerivitives()) {
                biomeHolder(dimKey, custom.getId()).ifPresentOrElse(holder -> customBiomes.put(custom.getId(), holder), () -> {
                    Iris.error("Failed to load custom biome " + dimKey + " " + custom.getId());
                    failed.incrementAndGet();
                });
            }
        }
        if (failed.get() > 0) {
            throw new IllegalStateException("Failed to load " + failed.get() + " custom biomes");
        }

        minecraftBiomes = new KMap<>(org.bukkit.Registry.BIOME.stream()
                .collect(Collectors.toMap(Function.identity(), CraftBiome::bukkitToMinecraftHolder)));
        minecraftBiomes.values().removeAll(customBiomes.values());
    }

    @Override
    public boolean exists(int x, int z) {
        try (IRegion region = getRegion(x >> 5, z >> 5, true)) {
            return region != null && region.exists(x, z);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IRegion getRegion(int x, int z, boolean existingOnly) throws IOException {
        AtomicReference<IOException> exception = new AtomicReference<>();
        Region region = regions.computeIfAbsent(Cache.key(x, z), k -> {
            trim();

            try {
                FileUtil.createDirectoriesSafe(this.folder);
                Path path = folder.resolve("r." + x + "." + z + ".mca");
                if (existingOnly && !Files.exists(path)) {
                    return null;
                } else {
                    return new Region(path, this.folder);
                }
            } catch (IOException e) {
                exception.set(e);
                return null;
            }
        });

        if (region == null) {
            if (exception.get() != null)
                throw exception.get();
            return null;
        }
        region.references++;
        return region;
    }

    @NotNull
    @Override
    public SerializableChunk createChunk(int x, int z) {
        return new DirectTerrainChunk(new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, this, registryAccess().lookupOrThrow(Registries.BIOME), null));
    }

    @Override
    public void fillBiomes(@NonNull SerializableChunk chunk, @Nullable ChunkContext ctx) {
        if (!(chunk instanceof DirectTerrainChunk tc))
            return;
        tc.getAccess().fillBiomesFromNoise((qX, qY, qZ, sampler) -> getNoiseBiome(engine, ctx, qX << 2, qY << 2, qZ << 2), null);
    }

    @Override
    public synchronized void close() {
        if (closed) return;

        while (!regions.isEmpty()) {
            regions.values().removeIf(Region::remove);
            J.sleep(1);
        }

        closed = true;
        customBiomes.clear();
        minecraftBiomes.clear();
    }

    private void trim() {
        int size = regions.size();
        if (size < 256) return;
        int remove = size - 255;

        var list = regions.values()
                .stream()
                .filter(Region::unused)
                .sorted()
                .collect(Collectors.toList())
                .reversed();

        int skip = list.size() - remove;
        if (skip > 0) list.subList(0, skip).clear();

        if (list.isEmpty()) return;
        regions.values().removeIf(r -> list.contains(r) && r.remove());
    }

    private Holder<Biome> getNoiseBiome(Engine engine, ChunkContext ctx, int x, int y, int z) {
        int m = y - engine.getMinHeight();
        IrisBiome ib = ctx == null ? engine.getSurfaceBiome(x, z) : ctx.getBiome().get(x & 15, z & 15);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(biomeRng, x, m, z).getId());
        } else {
            return minecraftBiomes.get(ib.getSkyBiome(biomeRng, x, m, z));
        }
    }

    private static RegistryAccess registryAccess() {
        return CACHE.aquire(() -> ((CraftServer) Bukkit.getServer()).getServer().registryAccess());
    }

    private static Optional<Holder.Reference<Biome>> biomeHolder(String namespace, String path) {
        return registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    static CompoundTag serialize(ChunkAccess chunk) {
        RegistryAccess access = registryAccess();
        List<SerializableChunkData.SectionData> list = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();

        int minLightSection = chunk.getMinSectionY() - 1;
        int maxLightSection = minLightSection + chunk.getSectionsCount() + 2;
        for(int y = minLightSection; y < maxLightSection; ++y) {
            int index = chunk.getSectionIndexFromSectionY(y);
            if (index < 0 || index >= sections.length) continue;
            LevelChunkSection section = sections[index].copy();
            list.add(new SerializableChunkData.SectionData(y, section, null, null));
        }

        List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntitiesPos().size());

        for(BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag nbt = chunk.getBlockEntityNbtForSaving(blockPos, access);
            if (nbt != null) {
                blockEntities.add(nbt);
            }
        }
        Map<Heightmap.Types, long[]> heightMap = new EnumMap<>(Heightmap.Types.class);
        for(Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                heightMap.put(entry.getKey(), entry.getValue().getRawData().clone());
            }
        }

        ChunkAccess.PackedTicks packedTicks = chunk.getTicksForSerialization(0);
        ShortList[] postProcessing = Arrays.stream(chunk.getPostProcessing()).map((shortlist) -> shortlist != null ? new ShortArrayList(shortlist) : null).toArray(ShortList[]::new);
        CompoundTag structureData = new CompoundTag();
        structureData.put("starts", new CompoundTag());
        structureData.put("References", new CompoundTag());

        CompoundTag persistentDataContainer = null;
        if (!chunk.persistentDataContainer.isEmpty()) {
            persistentDataContainer = chunk.persistentDataContainer.toTagCompound();
        }

        return new SerializableChunkData(access.lookupOrThrow(Registries.BIOME), chunk.getPos(),
                chunk.getMinSectionY(), 0, chunk.getInhabitedTime(), chunk.getPersistedStatus(),
                Optionull.map(chunk.getBlendingData(), BlendingData::pack), chunk.getBelowZeroRetrogen(),
                chunk.getUpgradeData().copy(), null, heightMap, packedTicks, postProcessing,
                chunk.isLightCorrect(), list, new ArrayList<>(), blockEntities, structureData, persistentDataContainer)
                .write();
    }
}
