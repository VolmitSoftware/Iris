package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.engine.history.SavedTerrainChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import art.arcane.iris.core.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.core.nms.DatapackStructureScopeResult;
import art.arcane.iris.engine.object.IrisImportedStructureControl;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.nms.INMSBinding;
import art.arcane.iris.core.nms.MinecraftVersion;
import art.arcane.iris.core.nms.ServerShutdownBoundary;
import art.arcane.iris.core.nms.container.BiomeColor;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.engine.object.IrisDimensionRuntimeContract;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.nativegen.NativeStructureFactory;
import art.arcane.iris.nativegen.NativeStructureGenerationException;
import art.arcane.iris.spi.PlatformStructureHooks.JigsawSourceMetadata;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.hunk.view.ChunkDataHunkHolder;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.nbt.common.mca.NBTWorld;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAChunkBiomeContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAGlobalPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMap;
import art.arcane.volmlib.util.nbt.mca.palette.MCAIdMapper;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalette;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.mca.palette.MCAPalettedContainer;
import art.arcane.volmlib.util.nbt.mca.palette.MCAWrappedPalettedContainer;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.iris.util.common.scheduling.J;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSBinding implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private volatile DataVersion dataVersion;
    private final BlockData AIR = Material.AIR.createBlockData();
    private final AtomicCache<MCAIdMap<net.minecraft.world.level.biome.Biome>> biomeMapCache = new AtomicCache<>();
    private final NmsWorldLifecycle worldLifecycle = new NmsWorldLifecycle();
    private final AtomicCache<MCAIdMapper<BlockState>> registryCache = new AtomicCache<>();
    private final AtomicCache<MCAPalette<BlockState>> globalCache = new AtomicCache<>();
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final AtomicCache<Method> byIdRef = new AtomicCache<>();

    @Override
    public PlatformGenerationRegistry generationRegistry() {
        return new NmsGenerationRegistry(
                this::registry,
                "bukkit-generation-registry-v1",
                "bukkit-v26_2_R1-generated-registry-json-v1"
        );
    }

    private static Object getFor(Class<?> type, Object source) {
        Object o = fieldFor(type, source);

        if (o != null) {
            return o;
        }

        o = invokeFor(type, source);

        if (o != null) {
            return o;
        }

        throw new IllegalStateException("Iris cannot resolve a " + type.getName()
                + " from " + source.getClass().getName() + " on this server version");
    }

    private static Object invokeFor(Class<?> returns, Object in) {
        for (Method i : in.getClass().getMethods()) {
            if (i.getReturnType().equals(returns)) {
                i.setAccessible(true);
                try {
                    IrisLogging.debug("[NMS] Found " + returns.getSimpleName() + " in " + in.getClass().getSimpleName() + "." + i.getName() + "()");
                    return i.invoke(in);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("Iris failed to invoke " + in.getClass().getName() + "."
                            + i.getName() + "() for " + returns.getName(), e);
                }
            }
        }

        return null;
    }

    private static Object fieldFor(Class<?> returns, Object in) {
        for (Class<?> sourceType = in.getClass(); sourceType != null; sourceType = sourceType.getSuperclass()) {
            Object o = fieldForClass(returns, sourceType, in);

            if (o != null) {
                return o;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldForClass(Class<T> returnType, Class<?> sourceType, Object in) {
        for (Field i : sourceType.getDeclaredFields()) {
            if (i.getType().equals(returnType)) {
                i.setAccessible(true);
                try {
                    IrisLogging.debug("[NMS] Found " + returnType.getSimpleName() + " in " + sourceType.getSimpleName() + "." + i.getName());
                    return (T) i.get(in);
                } catch (IllegalAccessException | RuntimeException e) {
                    throw new IllegalStateException("Iris failed to read " + sourceType.getName() + "."
                            + i.getName() + " for " + returnType.getName(), e);
                }
            }
        }
        return null;
    }

    private static Class<?> getClassType(Class<?> type, int ordinal) {
        return type.getDeclaredClasses()[ordinal];
    }

    @Override
    public boolean hasTile(Material material) {
        return !CraftBlockState.class.equals(CraftBlockStates.getBlockStateType(material));
    }

    @Override
    public String getEntitySpawnCategory(String key) {
        Identifier identifier = Identifier.parse(key);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            throw new IllegalArgumentException("Unknown native entity type: " + key);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        return type.getCategory().getSerializedName().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ())) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public KMap<String, Object> serializeTile(Location location) {
        BlockEntity e = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));

        if (e == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = e.saveWithoutMetadata(registry());
        return (KMap<String, Object>) convertFromTag(tag, 0, 64);
    }

    @Contract(value = "null, _, _ -> null", pure = true)
    private Object convertFromTag(Tag tag, int depth, int maxDepth) {
        if (tag == null || depth > maxDepth) return null;
        return switch (tag) {
            case CollectionTag collection -> {
                KList<Object> list = new KList<>();

                for (Object i : collection) {
                    if (i instanceof Tag t)
                        list.add(convertFromTag(t, depth + 1, maxDepth));
                    else list.add(i);
                }
                yield  list;
            }
            case net.minecraft.nbt.CompoundTag compound -> {
                KMap<String, Object> map = new KMap<>();

                for (String key : compound.keySet()) {
                    var child = compound.get(key);
                    if (child == null) continue;
                    var value = convertFromTag(child, depth + 1, maxDepth);
                    if (value == null) continue;
                    map.put(key, value);
                }
                yield map;
            }
            case NumericTag numeric -> numeric.box();
            default -> tag.asString().orElse(null);
        };
    }

    @Override
    public void deserializeTile(KMap<String, Object> map, Location pos) {
        if (map == null || pos == null || pos.getWorld() == null) {
            return;
        }

        Tag converted = convertToTag(map, 0, 64);
        if (!(converted instanceof net.minecraft.nbt.CompoundTag tag)) {
            return;
        }

        ServerLevel level = ((CraftWorld) pos.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        int chunkX = pos.getBlockX() >> 4;
        int chunkZ = pos.getBlockZ() >> 4;
        if (J.isOwnedByCurrentRegion(pos.getWorld(), chunkX, chunkZ)) {
            merge(level, blockPos, tag);
            return;
        }
        if (!J.runAt(pos, () -> merge(level, blockPos, tag))) {
            IrisLogging.warn("[NMS] Failed to schedule tile deserialize at " + blockPos + " in world " + pos.getWorld().getName());
        }
    }

    private void merge(ServerLevel level, BlockPos blockPos, net.minecraft.nbt.CompoundTag tag) {
        if (level == null || blockPos == null || tag == null) {
            return;
        }

        try {
            var blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity == null) {
                IrisLogging.warn("[NMS] BlockEntity not found at " + blockPos);
                var state = level.getBlockState(blockPos);
                if (!state.hasBlockEntity()) {
                    return;
                }

                blockEntity = ((EntityBlock) state.getBlock())
                        .newBlockEntity(blockPos, state);
            }

            var accessor = new BlockDataAccessor(blockEntity, blockPos);
            accessor.setData(accessor.getData().merge(tag));
        } catch (Throwable e) {
            IrisLogging.warn("[NMS] Failed to merge tile data at " + blockPos + ": " + e.getMessage());
            IrisLogging.reportError(e);
        }
    }

    private Tag convertToTag(Object object, int depth, int maxDepth) {
        if (object == null || depth > maxDepth) return EndTag.INSTANCE;
        return switch (object) {
            case Map<?, ?> map -> {
                var tag = new net.minecraft.nbt.CompoundTag();
                for (var i : map.entrySet()) {
                    tag.put(i.getKey().toString(), convertToTag(i.getValue(), depth + 1, maxDepth));
                }
                yield tag;
            }
            case List<?> list -> {
                var tag = new ListTag();
                for (var i : list) {
                    tag.add(convertToTag(i, depth + 1, maxDepth));
                }
                yield tag;
            }
            case Byte number -> ByteTag.valueOf(number);
            case Short number -> ShortTag.valueOf(number);
            case Integer number -> IntTag.valueOf(number);
            case Long number -> LongTag.valueOf(number);
            case Float number -> FloatTag.valueOf(number);
            case Double number -> DoubleTag.valueOf(number);
            case String string -> StringTag.valueOf(string);
            default -> EndTag.INSTANCE;
        };
    }

    @Override
    public CompoundTag serializeEntity(Entity location) {
        return null;// TODO:
    }

    @Override
    public Entity deserializeEntity(CompoundTag s, Location newPosition) {
        return null;// TODO:
    }

    @Override
    public boolean supportsCustomHeight() {
        return true;
    }

    private RegistryAccess registry() {
        RegistryAccess access = registryAccess.aquire(() -> (RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));

        if (access == null) {
            throw new IllegalStateException("Iris cannot resolve the Minecraft registry access on this server version");
        }

        return access;
    }

    private Registry<net.minecraft.world.level.biome.Biome> getCustomBiomeRegistry() {
        return registry().lookup(Registries.BIOME).orElseThrow(() -> new IllegalStateException(
                "Iris cannot resolve the Minecraft biome registry on this server version"));
    }

    private Registry<Block> getBlockRegistry() {
        return registry().lookup(Registries.BLOCK).orElse(null);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        Object raw = getCustomBiomeRegistry().get(id);
        if (raw instanceof java.util.Optional<?> opt) {
            raw = opt.orElse(null);
        }
        if (raw instanceof Holder<?> holder) {
            raw = holder.value();
        }
        return raw;
    }

    @Override
    public int getMinHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public boolean supportsCustomBiomes() {
        return true;
    }

    @Override
    public boolean supportsIrisWorldGeneration() {
        return true;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return getCustomBiomeRegistry().getId(((Holder<net.minecraft.world.level.biome.Biome>) biomeBase).value());
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return ((CraftWorld) location.getWorld()).getHandle().getBiome(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return getKeyForBiomeBase(getTrueBiomeBase(location));
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        return getCustomBiomeRegistry().getValue(net.minecraft.resources.Identifier.parse(mckey));
    }

    @Override
    public Object getCustomBiomeBaseHolderFor(String mckey) {
        return getCustomBiomeRegistry().get(getTrueBiomeBaseId(getCustomBiomeRegistry().get(net.minecraft.resources.Identifier.parse(mckey)))).orElse(null);
    }

    public int getBiomeBaseIdForKey(String key) {
        return getCustomBiomeRegistry().getId(getCustomBiomeRegistry().get(net.minecraft.resources.Identifier.parse(key)).map(Holder::value).orElse(null));
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        net.minecraft.world.level.biome.Biome biome;
        if (biomeBase instanceof Holder<?> holder) {
            Object value = holder.value();
            if (!(value instanceof net.minecraft.world.level.biome.Biome held)) {
                throw new IllegalArgumentException("Iris cannot read a biome key from holder value "
                        + (value == null ? "null" : value.getClass().getName()));
            }
            biome = held;
        } else if (biomeBase instanceof net.minecraft.world.level.biome.Biome direct) {
            biome = direct;
        } else {
            throw new IllegalArgumentException("Iris cannot read a biome key from "
                    + (biomeBase == null ? "null" : biomeBase.getClass().getName()));
        }

        Identifier key = getCustomBiomeRegistry().getKey(biome);
        if (key == null) {
            throw new IllegalStateException("Iris found no registry key for biome " + biome);
        }
        return key.getPath(); // something, not something:something
    }

    @Override
    public Object getBiomeBase(World world, Biome biome) {
        return biomeToBiomeBase(((CraftWorld) world).getHandle()
                .registryAccess().lookup(Registries.BIOME).orElse(null), biome);
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        Object v = baseBiomeCache.get(biome);

        if (v != null) {
            return v;
        }
        //noinspection unchecked
        v = biomeToBiomeBase((Registry<net.minecraft.world.level.biome.Biome>) registry, biome);
        if (v == null) {
            // Ok so there is this new biome name called "CUSTOM" in Paper's new releases.
            // But, this does NOT exist within CraftBukkit which makes it return an error.
            // So, we will just return the ID that the plains biome returns instead.
            //noinspection unchecked
            return biomeToBiomeBase((Registry<net.minecraft.world.level.biome.Biome>) registry, Biome.PLAINS);
        }
        baseBiomeCache.put(biome, v);
        return v;
    }

    @Override
    public KList<Biome> getBiomes() {
        KList<Biome> biomes = new KList<>();
        for (Biome biome : org.bukkit.Registry.BIOME) {
            biomes.add(biome);
        }
        return biomes;
    }

    @Override
    public KList<String> getStructureKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.STRUCTURE).keySet().forEach(k -> keys.add(k.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to read registered structure keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getJigsawStructureKeys() {
        KList<String> keys = new KList<>();
        try {
            Registry<Structure> structures = registry().lookupOrThrow(Registries.STRUCTURE);
            for (Map.Entry<ResourceKey<Structure>, Structure> entry : structures.entrySet()) {
                if (entry.getValue() instanceof JigsawStructure) {
                    keys.add(entry.getKey().identifier().toString());
                }
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Iris failed to read registered jigsaw structure keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getTemplatePoolKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.TEMPLATE_POOL).keySet()
                    .forEach(key -> keys.add(key.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Iris failed to read registered template pool keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public JigsawSourceMetadata getJigsawSourceMetadata(String structureKey) {
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Registry<Structure> structures = registry().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = structures.getValue(identifier);
            if (structure == null) {
                throw new IllegalArgumentException("Registered structure does not exist: " + structureKey);
            }
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeStructureFactory.sourceMetadata(
                    registry(), server.getStructureManager(), jigsaw);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve live jigsaw metadata for registered structure '"
                    + structureKey + "'", error);
        }
    }

    @Override
    public int getTemplatePoolHorizontalSpan(String templatePoolKey) {
        try {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeStructureFactory.templatePoolHorizontalSpan(
                    registry(), server.getStructureManager(), templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve the live horizontal span for registered "
                    + "template pool '" + templatePoolKey + "'", error);
        }
    }

    @Override
    public int getJigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        try {
            Identifier identifier = Identifier.tryParse(structureKey);
            if (identifier == null) {
                throw new IllegalArgumentException("Invalid registered structure key: " + structureKey);
            }
            Structure structure = registry().lookupOrThrow(Registries.STRUCTURE).getValue(identifier);
            if (!(structure instanceof JigsawStructure jigsaw)) {
                throw new IllegalArgumentException("Registered structure is not a jigsaw: " + structureKey);
            }
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getHandle().getServer();
            return NativeStructureFactory.jigsawStartPoolHorizontalSpan(
                    registry(), server.getStructureManager(), jigsaw, templatePoolKey);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Iris failed to resolve the effective start-pool span for registered "
                    + "jigsaw structure '" + structureKey + "' and pool '" + templatePoolKey + "'", error);
        }
    }

    @Override
    public KList<String> getStructureSetKeys() {
        KList<String> keys = new KList<>();
        try {
            registry().lookupOrThrow(Registries.STRUCTURE_SET).keySet().forEach(k -> keys.add(k.toString()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to read registered structure-set keys from the Minecraft registry", e);
        }
        return keys;
    }

    @Override
    public KList<String> getReachableStructureKeys(World world) {
        KList<String> keys = new KList<>();
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            keys.addAll(VanillaStructureBiomes.reachableStructureKeys(level, source));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve reachable structures for Bukkit world '"
                    + (world == null ? "<null>" : world.getName()) + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getStructureBiomeKeys(String structureKey) {
        KList<String> keys = new KList<>();
        try {
            RegistryAccess access = registry();
            if (access == null) {
                throw new IllegalStateException("Minecraft registry access is unavailable");
            }
            keys.addAll(VanillaStructureBiomes.structureBiomeKeys(access, structureKey));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve biome keys for registered structure '"
                    + structureKey + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getPossibleBiomeKeys(World world) {
        KList<String> keys = new KList<>();
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            keys.addAll(VanillaStructureBiomes.possibleBiomeKeys(source));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Iris failed to resolve possible structure biome keys for Bukkit world '"
                    + (world == null ? "<null>" : world.getName()) + "'", e);
        }
        return keys;
    }

    @Override
    public KList<String> getObjectFeatureKeys() {
        KList<String> keys = new KList<>();
        try {
            Registry<ConfiguredFeature<?, ?>> reg = registry().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            for (Identifier id : reg.keySet()) {
                ConfiguredFeature<?, ?> cf = reg.getValue(id);
                if (cf == null) {
                    continue;
                }
                String group = classifyFeature(cf.feature());
                if (group == null) {
                    continue;
                }
                keys.add(group + "|" + id);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        return keys;
    }

    private static String classifyFeature(Feature<?> feature) {
        if (feature instanceof TreeFeature) {
            return "trees";
        }
        if (feature instanceof FallenTreeFeature) {
            return "fallen_trees";
        }
        if (feature instanceof AbstractHugeMushroomFeature) {
            return "mushrooms";
        }
        return null;
    }

    @Override
    public boolean placeFeature(World world, int x, int y, int z, String featureKey, long seed) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<ConfiguredFeature<?, ?>> reg = registry().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> cf = reg.getValue(Identifier.parse(featureKey));
            if (cf == null) {
                return false;
            }
            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            return cf.place(level, generator, random, new BlockPos(x, y, z));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    @Override
    public int[] placeStructure(World world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<net.minecraft.world.level.levelgen.structure.Structure> reg = registry().lookupOrThrow(Registries.STRUCTURE);
            net.minecraft.world.level.levelgen.structure.Structure structure = reg.getValue(Identifier.parse(structureKey));
            if (structure == null) {
                return null;
            }
            net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> holder = reg.wrapAsHolder(structure);
            net.minecraft.world.level.levelgen.RandomState randomState = level.getChunkSource().randomState();
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager = level.getStructureManager();
            net.minecraft.world.level.StructureManager structureManager = level.structureManager();
            net.minecraft.world.level.biome.BiomeSource biomeSource = generator.getBiomeSource();
            net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);

            net.minecraft.world.level.levelgen.structure.StructureStart start = structure.generate(
                    holder,
                    level.dimension(),
                    level.registryAccess(),
                    generator,
                    biomeSource,
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    0,
                    level,
                    biome -> true);

            if (start == null || !start.isValid()) {
                return null;
            }

            BoundingBox box = start.getBoundingBox();
            int spanX = box.maxX() - box.minX() + 1;
            int spanY = box.maxY() - box.minY() + 1;
            int spanZ = box.maxZ() - box.minZ() + 1;
            if (maxSpan > 0 && (spanX > maxSpan || spanZ > maxSpan || spanY > maxSpan)) {
                return null;
            }

            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            int minCX = box.minX() >> 4;
            int maxCX = box.maxX() >> 4;
            int minCZ = box.minZ() >> 4;
            int maxCZ = box.maxZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    level.getChunk(cx, cz);
                    net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(cx, cz);
                    BoundingBox chunkBox = new BoundingBox(
                            cp.getMinBlockX(), box.minY(), cp.getMinBlockZ(),
                            cp.getMaxBlockX(), box.maxY(), cp.getMaxBlockZ());
                    start.placeInChunk(level, structureManager, generator, random, chunkBox, cp);
                }
            }
            return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
        } catch (RuntimeException e) {
            throw NativeStructureGenerationException.failure(
                    "capture placement", structureKey, chunkX, chunkZ, e);
        }
    }

    @Override
    public boolean supportsStructureCapture() {
        return true;
    }

    @Override
    public KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolumeIndex.volumes(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public void invalidateNativeStructureVolumeIndex(Engine engine) {
        NativeStructureVolumeIndex.invalidate(engine);
    }

    @Override
    public int getBiomeId(Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {
                Registry<net.minecraft.world.level.biome.Biome> registry = ((CraftWorld) i).getHandle().registryAccess().lookup(Registries.BIOME).orElse(null);
                if (registry == null) {
                    continue;
                }
                Object baseRaw = getBiomeBase(registry, biome);
                net.minecraft.world.level.biome.Biome base;
                if (baseRaw instanceof Holder<?> holder) {
                    Object value = holder.value();
                    if (!(value instanceof net.minecraft.world.level.biome.Biome resolved)) {
                        continue;
                    }
                    base = resolved;
                } else if (baseRaw instanceof net.minecraft.world.level.biome.Biome resolved) {
                    base = resolved;
                } else {
                    continue;
                }
                return registry.getId(base);
            }
        }

        List<Biome> biomes = new ArrayList<>();
        for (Biome entry : org.bukkit.Registry.BIOME) {
            biomes.add(entry);
        }
        int index = biomes.indexOf(biome);
        return Math.max(index, 0);
    }

    private MCAIdMap<net.minecraft.world.level.biome.Biome> getBiomeMapping() {
        return biomeMapCache.aquire(() -> new MCAIdMap<>() {
            @NotNull
            @Override
            public Iterator<net.minecraft.world.level.biome.Biome> iterator() {
                return getCustomBiomeRegistry().iterator();
            }

            @Override
            public int getId(net.minecraft.world.level.biome.Biome paramT) {
                return getCustomBiomeRegistry().getId(paramT);
            }

            @Override
            public net.minecraft.world.level.biome.Biome byId(int paramInt) {
                return (net.minecraft.world.level.biome.Biome) getBiomeBaseFromId(paramInt);
            }
        });
    }

    @NotNull
    private MCABiomeContainer getBiomeContainerInterface(MCAIdMap<net.minecraft.world.level.biome.Biome> biomeMapping, MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base) {
        return new MCABiomeContainer() {
            @Override
            public int[] getData() {
                return base.writeBiomes();
            }

            @Override
            public void setBiome(int x, int y, int z, int id) {
                base.setBiome(x, y, z, biomeMapping.byId(id));
            }

            @Override
            public int getBiome(int x, int y, int z) {
                return biomeMapping.getId(base.getBiome(x, y, z));
            }
        };
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] data) {
        MCAChunkBiomeContainer<net.minecraft.world.level.biome.Biome> base = new MCAChunkBiomeContainer<>(getBiomeMapping(), min, max, data);
        return getBiomeContainerInterface(getBiomeMapping(), base);
    }

    @Override
    public int countCustomBiomes() {
        AtomicInteger a = new AtomicInteger(0);

        getCustomBiomeRegistry().keySet().forEach((i) -> {
            if (i.getNamespace().equals("minecraft")) {
                return;
            }

            a.incrementAndGet();
            IrisLogging.debug("Custom Biome: " + i);
        });

        return a.get();
    }

    public boolean supportsDataPacks() {
        return true;
    }

    public void setBiomes(int cx, int cz, World world, Hunk<Object> biomes) {
        LevelChunk c = ((CraftWorld) world).getHandle().getChunk(cx, cz);
        biomes.iterateSync((x, y, z, b) -> c.setNoiseBiome(x, y, z, (Holder<net.minecraft.world.level.biome.Biome>) b));
        c.markUnsaved();
    }

    @Override
    public MCAPaletteAccess createPalette() {
        MCAIdMapper<BlockState> registry = registryCache.aquireNastyPrint(() -> {
            Field cf = IdMapper.class.getDeclaredField("tToId");
            Field df = IdMapper.class.getDeclaredField("idToT");
            Field bf = IdMapper.class.getDeclaredField("nextId");
            cf.setAccessible(true);
            df.setAccessible(true);
            bf.setAccessible(true);
            IdMapper<BlockState> blockData = Block.BLOCK_STATE_REGISTRY;
            int b = bf.getInt(blockData);
            Object2IntMap<BlockState> c = (Object2IntMap<BlockState>) cf.get(blockData);
            List<BlockState> d = (List<BlockState>) df.get(blockData);
            return new MCAIdMapper<BlockState>(c, d, b);
        });
        if (registry == null) {
            throw new IllegalStateException("Iris cannot mirror the Minecraft block state id map on this server version");
        }
        MCAPalette<BlockState> global = globalCache.aquireNastyPrint(() -> new MCAGlobalPalette<>(registry, ((CraftBlockData) AIR).getState()));
        if (global == null) {
            throw new IllegalStateException("Iris cannot build the global block state palette on this server version");
        }
        java.util.Map<CompoundTag, BlockState> innerDecodeCache = new java.util.concurrent.ConcurrentHashMap<>(64);
        java.util.Map<CompoundTag, BlockState> outerDecodeCache = new java.util.concurrent.ConcurrentHashMap<>(64);
        MCAPalettedContainer<BlockState> container = new MCAPalettedContainer<>(global, registry,
                i -> innerDecodeCache.computeIfAbsent(i, t -> ((CraftBlockData) NBTWorld.getBlockData(t)).getState()),
                i -> NBTWorld.getCompound(CraftBlockData.createData(i)),
                ((CraftBlockData) AIR).getState());
        return new MCAWrappedPalettedContainer<>(container,
                i -> NBTWorld.getCompound(CraftBlockData.createData(i)),
                i -> outerDecodeCache.computeIfAbsent(i, t -> ((CraftBlockData) NBTWorld.getBlockData(t)).getState()));
    }

    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle<Matter> mantle) {
        ChunkAccess chunk = ((CraftChunk) e).getHandle(ChunkStatus.FULL);
        AtomicInteger c = new AtomicInteger();
        AtomicInteger r = new AtomicInteger();
        mantle.iterateChunk(e.getX(), e.getZ(), MatterBiomeInject.class, (x, y, z, b) -> {
            if (b != null) {
                if (b.isCustom()) {
                    chunk.setNoiseBiome(x, y, z, getCustomBiomeRegistry().get(b.getBiomeId()).get());
                    c.getAndIncrement();
                } else {
                    chunk.setNoiseBiome(x, y, z, getCustomBiomeRegistry()
                            .get(net.minecraft.resources.Identifier.parse(b.getBiomeKey()))
                            .orElseGet(() -> getCustomBiomeRegistry()
                                    .get(net.minecraft.resources.Identifier.parse("minecraft:plains"))
                                    .orElseThrow()));
                    r.getAndIncrement();
                }
            }
        });
    }

    @Override
    public boolean applyChunkBlocks(Chunk bukkitChunk, TerrainChunk data) {
        if (!(data.getChunkData() instanceof CraftChunkData chunkData)) {
            return false;
        }

        try {
            ServerLevel level = ((CraftWorld) bukkitChunk.getWorld()).getHandle();
            LevelChunk chunk = level.getChunk(bukkitChunk.getX(), bukkitChunk.getZ());
            ChunkAccess source = chunkData.getHandle();
            removeBlockEntities(chunk);

            int minY = level.getMinY();
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection target = chunk.getSection(i);
                LevelChunkSection from = source.getSection(i);
                if (from.hasOnlyAir() && target.hasOnlyAir()) {
                    continue;
                }

                int sectionBaseY = minY + (i << 4);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = from.getBlockState(x, y, z);
                            target.setBlockState(x, y, z, state, false);
                            if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock entityBlock) {
                                BlockPos pos = new BlockPos(baseX + x, sectionBaseY + y, baseZ + z);
                                BlockEntity entity = entityBlock.newBlockEntity(pos, state);
                                if (entity != null) {
                                    chunk.setBlockEntity(entity);
                                }
                            }
                        }
                    }
                }
            }

            finishChunkRewrite(level, chunk);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    @Override
    public boolean applyChunkDataBlocks(ChunkGenerator.ChunkData chunkData, Hunk<PlatformBlockState> data) {
        if (!(chunkData instanceof CraftChunkData craftChunkData)) {
            return false;
        }

        try {
            ChunkAccess access = craftChunkData.getHandle();
            int minY = craftChunkData.getMinHeight();
            int height = craftChunkData.getMaxHeight() - minY;
            int accessMinY = access.getMinY();
            int accessMaxY = accessMinY + access.getHeight();
            int yStart = Math.max(0, accessMinY - minY);
            int yEnd = Math.min(height, accessMaxY - minY);
            int baseX = access.getPos().getMinBlockX();
            int baseZ = access.getPos().getMinBlockZ();
            ChunkDataHunkHolder holder = data instanceof ChunkDataHunkHolder chunkDataHolder ? chunkDataHolder : null;
            for (int z = 0; z < 16; z++) {
                for (int y = yStart; y < yEnd; y++) {
                    int blockY = y + minY;
                    int sectionIndex = (blockY - accessMinY) >> 4;
                    LevelChunkSection section = access.getSection(sectionIndex);
                    int sectionY = blockY & 15;
                    for (int x = 0; x < 16; x++) {
                        PlatformBlockState platformState = holder == null ? data.getRaw(x, y, z) : holder.getStoredRaw(x, y, z);
                        if (platformState == null) {
                            continue;
                        }

                        BlockData blockData = (BlockData) platformState.nativeHandle();
                        if (blockData instanceof IrisCustomData customData) {
                            blockData = customData.getBase();
                        }
                        if (!(blockData instanceof CraftBlockData craftBlockData)) {
                            return false;
                        }

                        BlockState state = craftBlockData.getState();
                        BlockState oldState = section.setBlockState(x, sectionY, z, state, false);
                        if (state.hasBlockEntity()) {
                            BlockPos pos = new BlockPos(baseX + x, blockY, baseZ + z);
                            BlockEntity entity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                            if (entity == null) {
                                access.removeBlockEntity(pos);
                            } else {
                                access.setBlockEntity(entity);
                            }
                        } else if (oldState != null && oldState.hasBlockEntity()) {
                            access.removeBlockEntity(new BlockPos(baseX + x, blockY, baseZ + z));
                        }
                    }
                }
            }

            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    @Override
    public boolean forceEvictChunk(World world, int chunkX, int chunkZ) {
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return true;
            }
            return world.unloadChunk(chunkX, chunkZ, true);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> flushSavedTerrainCapture(World world) {
        return NmsSavedTerrainCapture.flush(world);
    }

    @Override
    public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(World world, int chunkX, int chunkZ,
                                                                        int minimumY, int height) {
        return NmsSavedTerrainCapture.capture(world, chunkX, chunkZ, minimumY, height);
    }

    @Override
    public boolean saveAndUnloadChunk(World world, int x, int z) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            ChunkHolderManager chm = level.moonrise$getChunkTaskScheduler().chunkHolderManager;
            chm.processTicketUpdates();
            NewChunkHolder holder = chm.getChunkHolder(x, z);
            if (holder != null) {
                holder.save(false);
            }
            chm.processUnloads();
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    @Override
    public boolean pollChunkTask(World world) {
        if (FoliaScheduler.isRegionizedRuntime(Bukkit.getServer())) {
            return false;
        }
        ServerLevel level = ((CraftWorld) world).getHandle();
        if (Thread.currentThread() != level.getServer().getRunningThread()) {
            throw new IllegalStateException("Native chunk tasks require the server lifecycle thread");
        }
        return level.getChunkSource().pollTask();
    }

    @Override
    public void flushChunkIO(World world) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            MoonriseRegionFileIO.flush(level);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    @Override
    public void reconcileNativeStructurePois(Chunk chunk) {
        NativeStructurePoiUpdates.reconcile((LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
    }

    @Override
    public boolean clearChunkBlocks(Chunk bukkitChunk) {
        try {
            ServerLevel level = ((CraftWorld) bukkitChunk.getWorld()).getHandle();
            LevelChunk chunk = level.getChunk(bukkitChunk.getX(), bukkitChunk.getZ());
            removeBlockEntities(chunk);

            BlockState air = ((CraftBlockData) AIR).getState();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = chunk.getSection(i);
                if (section.hasOnlyAir()) {
                    continue;
                }

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            section.setBlockState(x, y, z, air, false);
                        }
                    }
                }
            }

            finishChunkRewrite(level, chunk);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    private void removeBlockEntities(LevelChunk chunk) {
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(pos);
        }
    }

    private void finishChunkRewrite(ServerLevel level, LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
        chunk.markUnsaved();
        ThreadedLevelLightEngine lightEngine = (ThreadedLevelLightEngine) level.getChunkSource().getLightEngine();
        lightEngine.starlight$serverRelightChunks(List.of(chunk.getPos()), p -> {
        }, c -> {
        });
    }

    public ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException {
        if (customNbt != null && !customNbt.isEmpty()) {
            net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(itemStack);

            try {
                net.minecraft.nbt.CompoundTag tag = TagParser.parseCompoundFully((new JSONObject(customNbt)).toString());
                tag.merge(s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
                s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            } catch (CommandSyntaxException var5) {
                throw new IllegalArgumentException(var5);
            }

            return CraftItemStack.asBukkitCopy(s);
        } else {
            return itemStack;
        }
    }

    public void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel level = ((CraftWorld) world).getHandle();
        validateDimensionContract(engine, world, level);

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Field worldGenContextField = getField(chunkMap.getClass(), WorldGenContext.class);
        worldGenContextField.setAccessible(true);
        WorldGenContext worldGenContext = (WorldGenContext) worldGenContextField.get(chunkMap);

        IrisChunkGenerator irisGenerator = new IrisChunkGenerator(worldGenContext.generator(), seed, engine, world);
        WorldGenContext newContext = new WorldGenContext(
                worldGenContext.level(), irisGenerator,
                worldGenContext.structureManager(), worldGenContext.lightEngine(), worldGenContext.mainThreadExecutor(), worldGenContext.unsavedListener());

        worldGenContextField.set(chunkMap, newContext);
        net.minecraft.world.level.chunk.ChunkGenerator activeGenerator = level.getChunkSource().getGenerator();
        if (activeGenerator != irisGenerator) {
            throw new IllegalStateException("Iris generator injection did not become the active Paper chunk generator; active="
                    + activeGenerator.getClass().getName());
        }
        retargetStructureCheck(level, irisGenerator);
    }

    @Override
    public DatapackStructureScopeResult scopeDatapackStructures(
            World world,
            DatapackStructureScopeIndex scopeIndex,
            Set<String> declaredSources,
            IrisImportedStructureControl importedStructures
    ) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkGeneratorStructureState currentState = level.getChunkSource().getGeneratorState();
        net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState scopedState = createStructureState(level, generator, currentState);
        Registry<StructureSet> structureSetRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                scopedState.possibleStructureSets(),
                scopeIndex,
                declaredSources,
                importedStructures,
                DatapackStructureStateFilter.keyIndex(
                        structureSetRegistry.listElements().toList()));

        Field possibleSetsField = getField(scopedState.getClass(), List.class);
        possibleSetsField.setAccessible(true);
        possibleSetsField.set(scopedState, selection.structureSets());

        Field stateField = getField(chunkMap.getClass(), ChunkGeneratorStructureState.class);
        stateField.setAccessible(true);
        BukkitChunkGenerator platformGenerator = world.getGenerator() instanceof BukkitChunkGenerator bukkitGenerator
                ? bukkitGenerator
                : null;
        boolean studioBootstrap = platformGenerator != null
                && platformGenerator.isStudioEntryBootstrapActive();
        boolean authoringStudio = platformGenerator != null
                && platformGenerator.isAuthoringStudio();
        if (authoringStudio) {
            requireIrisGenerator(generator);
            if (currentState.possibleStructureSets().isEmpty()) {
                currentState.ensureStructuresGenerated();
            } else {
                ChunkGeneratorStructureState bootstrapState = createStructureState(level, generator, currentState);
                Field bootstrapSetsField = getField(bootstrapState.getClass(), List.class);
                bootstrapSetsField.setAccessible(true);
                bootstrapSetsField.set(bootstrapState, List.of());
                bootstrapState.ensureStructuresGenerated();
                stateField.set(chunkMap, bootstrapState);
            }
        } else if (studioBootstrap) {
            IrisChunkGenerator irisGenerator = requireIrisGenerator(generator);
            irisGenerator.retainStudioStructureState(level, chunkMap, scopedState);
            try {
                stateField.set(chunkMap, scopedState);
            } catch (IllegalAccessException | RuntimeException | Error failure) {
                irisGenerator.abandonStudioStructureState();
                throw failure;
            }
        } else {
            initializeAndPublishStructureState(
                    generator,
                    scopedState,
                    () -> stateField.set(chunkMap, scopedState));
        }
        return new DatapackStructureScopeResult(
                selection.retainedManagedSets(),
                selection.excludedManagedSets());
    }

    @Override
    public CompletableFuture<Void> completeStudioStructureBootstrap(World world) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        IrisChunkGenerator generator = requireIrisGenerator(level.getChunkSource().getGenerator());
        IrisChunkGenerator.StudioStructureState retained =
                generator.retainedStudioStructureState(level, chunkMap);
        if (retained == null) {
            return CompletableFuture.completedFuture(null);
        }
        return generator.activateStudioStructureState(retained);
    }

    @Override
    public void abandonStudioStructureBootstrap(World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof IrisChunkGenerator irisGenerator) {
            irisGenerator.abandonStudioStructureState();
        }
    }

    private ChunkGeneratorStructureState createStructureState(
            ServerLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            ChunkGeneratorStructureState currentState
    ) {
        return generator.createState(
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                currentState.randomState(),
                currentState.getLevelSeed(),
                level.spigotConfig);
    }

    private void initializeAndPublishStructureState(
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            ChunkGeneratorStructureState structureState,
            IrisChunkGenerator.StructureStatePublisher publisher
    ) throws IllegalAccessException {
        if (generator instanceof IrisChunkGenerator irisGenerator) {
            irisGenerator.initializeAndPublishStructureState(structureState, publisher);
            return;
        }
        structureState.ensureStructuresGenerated();
        publisher.publish();
    }

    private IrisChunkGenerator requireIrisGenerator(
            net.minecraft.world.level.chunk.ChunkGenerator generator
    ) {
        if (generator instanceof IrisChunkGenerator irisGenerator) {
            return irisGenerator;
        }
        throw new IllegalStateException("Studio native structure state is not owned by the active Iris generator.");
    }

    private void validateDimensionContract(Engine engine, World world, ServerLevel level) {
        DimensionType actualType = level.dimensionType();
        String actualTypeKey = level.dimensionTypeRegistration().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<unregistered>");
        IrisDimensionRuntimeContract expected = IrisDimensionRuntimeContract.expected(engine.getDimension(), "iris");
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract(
                actualTypeKey,
                actualType.minY(),
                actualType.height(),
                actualType.logicalHeight());
        String runtimeName = "Bukkit world '" + world.getName() + "'";
        expected.requireExact(runtimeName, actual);
        expected.requireHeight(runtimeName, level.getMinY(), level.getHeight());
        expected.requireHeight(runtimeName, world.getMinHeight(), world.getMaxHeight() - world.getMinHeight());
        IrisLogging.debug("Loaded world " + world.getName() + " with exact Iris dimension type " + actualTypeKey);
    }

    private static void retargetStructureCheck(ServerLevel level, IrisChunkGenerator generator) throws NoSuchFieldException, IllegalAccessException {
        Field structureCheckField = getField(level.getClass(), StructureCheck.class);
        structureCheckField.setAccessible(true);
        Object structureCheck = structureCheckField.get(level);
        if (structureCheck == null) {
            return;
        }
        Field generatorField = getField(structureCheck.getClass(), net.minecraft.world.level.chunk.ChunkGenerator.class);
        generatorField.setAccessible(true);
        generatorField.set(structureCheck, generator);
        Field biomeSourceField = getField(structureCheck.getClass(), BiomeSource.class);
        biomeSourceField.setAccessible(true);
        biomeSourceField.set(structureCheck, generator.getBiomeSource());
    }

    public Vector3d getBoundingbox(org.bukkit.entity.EntityType entity) {
        if (entity == null) {
            return null;
        }

        try {
            // Registry lookup instead of an EntityType static-field scan: 26.2 moved the constants
            // to a separate EntityTypes holder class that 26.1.2 does not have, while the registry
            // resolves identically on both. ENTITY_TYPE is a DefaultedRegistry, so guard containsKey
            // to avoid silently resolving unknown keys to the default entry.
            Identifier key = Identifier.fromNamespaceAndPath(entity.getKey().getNamespace(), entity.getKey().getKey());
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                return null;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(key);
            if (entityType == null) {
                return null;
            }
            return new Vector3d(entityType.getWidth(), entityType.getHeight(), entityType.getWidth());
        } catch (Throwable e) {
            IrisLogging.error("Unable to get entity dimensions for " + entity + "!");
            IrisLogging.reportError(e);
            return null;
        }
    }


    @Override
    public Entity spawnEntity(Location location,  org.bukkit.entity.EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        if (location == null || location.getWorld() == null || type == null || type.getEntityClass() == null) {
            return null;
        }
        return ((CraftWorld) location.getWorld()).spawn(location, type.getEntityClass(), null, reason);
    }

    @Override
    public Color getBiomeColor(Location location, BiomeColor type) {
        ServerLevel reader = ((CraftWorld) location.getWorld()).getHandle();
        var pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        var holder = reader.getBiome(pos);
        var biome = holder.value();
        if (biome == null) throw new IllegalArgumentException("Invalid biome: " + holder.unwrapKey().orElse(null));

        var attributes = reader.environmentAttributes();
        int rgba = switch (type) {
            case FOG -> attributes.getValue(EnvironmentAttributes.FOG_COLOR, pos);
            case WATER -> biome.getWaterColor();
            case WATER_FOG -> attributes.getValue(EnvironmentAttributes.WATER_FOG_COLOR, pos);
            case SKY -> attributes.getValue(EnvironmentAttributes.SKY_COLOR, pos);
            case FOLIAGE -> biome.getFoliageColor();
            case GRASS -> biome.getGrassColor(location.getBlockX(), location.getBlockZ());
        };
        if (rgba == 0) {
            if (BiomeColor.FOLIAGE == type && biome.getSpecialEffects().foliageColorOverride().isEmpty())
                return null;
            if (BiomeColor.GRASS == type && biome.getSpecialEffects().grassColorOverride().isEmpty())
                return null;
        }
        return new Color(rgba, true);
    }

    private static Field getField(Class<?> clazz, Class<?> fieldType) throws NoSuchFieldException {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType().equals(fieldType))
                    return f;
            }
            throw new NoSuchFieldException(fieldType.getName());
        } catch (NoSuchFieldException var4) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw var4;
            } else {
                return getField(superClass, fieldType);
            }
        }
    }

    public static Holder<net.minecraft.world.level.biome.Biome> biomeToBiomeBase(Registry<net.minecraft.world.level.biome.Biome> registry, Biome biome) {
        if (registry == null || biome == null) {
            return null;
        }

        NamespacedKey biomeKey = resolveBiomeKey(biome);
        if (biomeKey == null) {
            return null;
        }

        ResourceKey<net.minecraft.world.level.biome.Biome> key = ResourceKey.create(Registries.BIOME, CraftNamespacedKey.toMinecraft(biomeKey));
        return registry.get(key).orElse(null);
    }

    private static NamespacedKey resolveBiomeKey(Biome biome) {
        Object keyOrNullValue = invokeNoThrow(biome, "getKeyOrNull", new Class<?>[0]);
        if (keyOrNullValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        Object keyOrThrowValue = invokeNoThrow(biome, "getKeyOrThrow", new Class<?>[0]);
        if (keyOrThrowValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        Object keyValue = invokeNoThrow(biome, "getKey", new Class<?>[0]);
        if (keyValue instanceof NamespacedKey namespacedKey) {
            return namespacedKey;
        }

        return null;
    }

    private static Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public DataVersion getDataVersion() {
        DataVersion cached = dataVersion;
        if (cached == null) {
            MinecraftVersion detected = MinecraftVersion.detect(Bukkit.getServer());
            cached = detected != null && detected.isSameRelease(26, 1, 2) ? DataVersion.V26_1_2 : DataVersion.V26_2;
            dataVersion = cached;
        }
        return cached;
    }

    @Override
    public int getSpawnChunkCount(World world) {
        return 0;
    }

    @Override
    public boolean missingDimensionTypes(String... keys) {
        var type = registry().lookupOrThrow(Registries.DIMENSION_TYPE);
        return !Arrays.stream(keys)
                .map(key -> Identifier.fromNamespaceAndPath("iris", key))
                .allMatch(type::containsKey);
    }

    @Override
    public boolean injectBukkit() {
        return worldLifecycle.injectBukkit();
    }

    @Override
    public void ensureServerLevelInjection() {
        worldLifecycle.ensureServerLevelInjection();
    }

    @Override
    public void uninjectBukkit() {
        worldLifecycle.uninjectBukkit();
    }

    @Override
    public boolean isServerStopping() {
        return worldLifecycle.isServerStopping();
    }

    @Override
    public ServerShutdownBoundary createServerShutdownBoundary() {
        return worldLifecycle.createServerShutdownBoundary();
    }

    @Override
    public void deferPluginClassLoaderClose() {
        worldLifecycle.deferPluginClassLoaderClose();
    }

    @Override
    public void releasePluginClassLoaderClose() {
        worldLifecycle.releasePluginClassLoaderClose();
    }

    @Override
    public KMap<Material, List<BlockProperty>> getBlockProperties() {
        KMap<Material, List<BlockProperty>> states = new KMap<>();

        for (var block : registry().lookupOrThrow(Registries.BLOCK)) {
            var state = block.defaultBlockState();
            if (state == null) state = block.getStateDefinition().any();
            final var finalState = state;

            states.put(CraftMagicNumbers.getMaterial(block), block.getStateDefinition()
                    .getProperties()
                    .stream()
                    .map(p -> createProperty(p, finalState))
                    .toList());
        }
        return states;
    }

    private <T extends Comparable<T>> BlockProperty createProperty(Property<T> property, BlockState state) {
        return new BlockProperty(property.getName(), property.getValueClass(), state.getValue(property), property.getPossibleValues(), property::getName);
    }

    @Override
    public Object createRuntimeLevelStem(Object registryAccess, ChunkGenerator raw) {
        if (!(registryAccess instanceof RegistryAccess access)) {
            throw new IllegalStateException("Runtime LevelStem creation requires a RegistryAccess instance.");
        }
        if (!(raw instanceof PlatformChunkGenerator generator)) {
            throw new IllegalStateException("Generator is not platform chunk generator!");
        }

        Identifier dimensionKey = Identifier.fromNamespaceAndPath("iris", generator.getTarget().getDimension().getDimensionTypeKey());
        Holder.Reference<net.minecraft.world.level.dimension.DimensionType> dimensionType = access.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, dimensionKey));
        return new LevelStem(dimensionType, chunkGenerator(access));
    }

    private net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator(RegistryAccess access) {
        var settings = new FlatLevelGeneratorSettings(Optional.empty(), access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID), List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        settings.updateLayers();
        return new FlatLevelSource(settings);
    }
}
