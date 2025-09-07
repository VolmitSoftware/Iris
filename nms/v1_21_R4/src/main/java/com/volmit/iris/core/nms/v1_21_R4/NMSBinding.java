package com.volmit.iris.core.nms.v1_21_R4;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.volmit.iris.Iris;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.core.nms.container.StructurePlacement;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.nms.datapack.DataVersion;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.agent.Agent;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.Vector3d;
import com.volmit.iris.util.matter.MatterBiomeInject;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.mca.palette.*;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.scheduling.J;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_21_R4.CraftChunk;
import org.bukkit.craftbukkit.v1_21_R4.CraftServer;
import org.bukkit.craftbukkit.v1_21_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R4.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_21_R4.block.CraftBlockStates;
import org.bukkit.craftbukkit.v1_21_R4.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_21_R4.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R4.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_21_R4.util.CraftNamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NMSBinding implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private final BlockData AIR = Material.AIR.createBlockData();
    private final AtomicCache<MCAIdMap<net.minecraft.world.level.biome.Biome>> biomeMapCache = new AtomicCache<>();
    private final AtomicBoolean injected = new AtomicBoolean();
    private final AtomicCache<MCAIdMapper<BlockState>> registryCache = new AtomicCache<>();
    private final AtomicCache<MCAPalette<BlockState>> globalCache = new AtomicCache<>();
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final AtomicCache<Method> byIdRef = new AtomicCache<>();
    private Field biomeStorageCache = null;

    private static Object getFor(Class<?> type, Object source) {
        Object o = fieldFor(type, source);

        if (o != null) {
            return o;
        }

        return invokeFor(type, source);
    }

    private static Object invokeFor(Class<?> returns, Object in) {
        for (Method i : in.getClass().getMethods()) {
            if (i.getReturnType().equals(returns)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returns.getSimpleName() + " in " + in.getClass().getSimpleName() + "." + i.getName() + "()");
                    return i.invoke(in);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    private static Object fieldFor(Class<?> returns, Object in) {
        return fieldForClass(returns, in.getClass(), in);
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldForClass(Class<T> returnType, Class<?> sourceType, Object in) {
        for (Field i : sourceType.getDeclaredFields()) {
            if (i.getType().equals(returnType)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returnType.getSimpleName() + " in " + sourceType.getSimpleName() + "." + i.getName());
                    return (T) i.get(in);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
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
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ()), false) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public KMap<String, Object> serializeTile(Location location) {
        BlockEntity e = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), false);

        if (e == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = e.saveWithoutMetadata(registry());
        return (KMap<String, Object>) convertFromTag(tag, 0, 64);
    }

    @Contract(value = "null, _, _ -> null", pure = true)
    private Object convertFromTag(net.minecraft.nbt.Tag tag, int depth, int maxDepth) {
        if (tag == null || depth > maxDepth) return null;
        return switch (tag) {
            case CollectionTag collection -> {
                KList<Object> list = new KList<>();

                for (Object i : collection) {
                    if (i instanceof net.minecraft.nbt.Tag t)
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
        net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) convertToTag(map, 0, 64);
        var level = ((CraftWorld) pos.getWorld()).getHandle();
        var blockPos = new BlockPos(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        J.s(() -> merge(level, blockPos, tag));
    }

    private void merge(ServerLevel level, BlockPos blockPos, net.minecraft.nbt.CompoundTag tag) {
        var blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity == null) {
            Iris.warn("[NMS] BlockEntity not found at " + blockPos);
            var state = level.getBlockState(blockPos);
            if (!state.hasBlockEntity())
                return;

            blockEntity = ((EntityBlock) state.getBlock())
                    .newBlockEntity(blockPos, state);
        }
        var accessor = new BlockDataAccessor(blockEntity, blockPos);
        accessor.setData(accessor.getData().merge(tag));
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
                var tag = new net.minecraft.nbt.ListTag();
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
        return registryAccess.aquire(() -> (RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));
    }

    private Registry<net.minecraft.world.level.biome.Biome> getCustomBiomeRegistry() {
        return registry().lookup(Registries.BIOME).orElse(null);
    }

    private Registry<Block> getBlockRegistry() {
        return registry().lookup(Registries.BLOCK).orElse(null);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().get(id);
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
        return getCustomBiomeRegistry().getValue(ResourceLocation.parse(mckey));
    }

    @Override
    public Object getCustomBiomeBaseHolderFor(String mckey) {
        return getCustomBiomeRegistry().get(getTrueBiomeBaseId(getCustomBiomeRegistry().get(ResourceLocation.parse(mckey)))).orElse(null);
    }

    public int getBiomeBaseIdForKey(String key) {
        return getCustomBiomeRegistry().getId(getCustomBiomeRegistry().get(ResourceLocation.parse(key)).map(Holder::value).orElse(null));
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return getCustomBiomeRegistry().getKey((net.minecraft.world.level.biome.Biome) biomeBase).getPath(); // something, not something:something
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
        return new KList<>(Biome.values()).qadd(Biome.CHERRY_GROVE).qdel(Biome.CUSTOM);
    }

    @Override
    public boolean isBukkit() {
        return true;
    }

    @Override
    public int getBiomeId(Biome biome) {
        for (World i : Bukkit.getWorlds()) {
            if (i.getEnvironment().equals(World.Environment.NORMAL)) {
                Registry<net.minecraft.world.level.biome.Biome> registry = ((CraftWorld) i).getHandle().registryAccess().lookup(Registries.BIOME).orElse(null);
                return registry.getId((net.minecraft.world.level.biome.Biome) getBiomeBase(registry, biome));
            }
        }

        return biome.ordinal();
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
            Iris.debug("Custom Biome: " + i);
        });

        return a.get();
    }

    public boolean supportsDataPacks() {
        return true;
    }

    public void setBiomes(int cx, int cz, World world, Hunk<Object> biomes) {
        LevelChunk c = ((CraftWorld) world).getHandle().getChunk(cx, cz);
        biomes.iterateSync((x, y, z, b) -> c.setBiome(x, y, z, (Holder<net.minecraft.world.level.biome.Biome>) b));
        c.markUnsaved();
    }

    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {
        try {
            ChunkAccess s = (ChunkAccess) getFieldForBiomeStorage(chunk).get(chunk);
            Holder<net.minecraft.world.level.biome.Biome> biome = (Holder<net.minecraft.world.level.biome.Biome>) somethingVeryDirty;
            s.setBiome(x, y, z, biome);
        } catch (IllegalAccessException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    private Field getFieldForBiomeStorage(Object storage) {
        Field f = biomeStorageCache;

        if (f != null) {
            return f;
        }
        try {
            f = storage.getClass().getDeclaredField("biome");
            f.setAccessible(true);
            return f;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error(storage.getClass().getCanonicalName());
        }

        biomeStorageCache = f;
        return null;
    }

    @Override
    public MCAPaletteAccess createPalette() {
        MCAIdMapper<BlockState> registry = registryCache.aquireNasty(() -> {
            Field cf = net.minecraft.core.IdMapper.class.getDeclaredField("tToId");
            Field df = net.minecraft.core.IdMapper.class.getDeclaredField("idToT");
            Field bf = net.minecraft.core.IdMapper.class.getDeclaredField("nextId");
            cf.setAccessible(true);
            df.setAccessible(true);
            bf.setAccessible(true);
            net.minecraft.core.IdMapper<BlockState> blockData = Block.BLOCK_STATE_REGISTRY;
            int b = bf.getInt(blockData);
            Object2IntMap<BlockState> c = (Object2IntMap<BlockState>) cf.get(blockData);
            List<BlockState> d = (List<BlockState>) df.get(blockData);
            return new MCAIdMapper<BlockState>(c, d, b);
        });
        MCAPalette<BlockState> global = globalCache.aquireNasty(() -> new MCAGlobalPalette<>(registry, ((CraftBlockData) AIR).getState()));
        MCAPalettedContainer<BlockState> container = new MCAPalettedContainer<>(global, registry,
                i -> ((CraftBlockData) NBTWorld.getBlockData(i)).getState(),
                i -> NBTWorld.getCompound(CraftBlockData.fromData(i)),
                ((CraftBlockData) AIR).getState());
        return new MCAWrappedPalettedContainer<>(container,
                i -> NBTWorld.getCompound(CraftBlockData.fromData(i)),
                i -> ((CraftBlockData) NBTWorld.getBlockData(i)).getState());
    }

    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle mantle) {
        ChunkAccess chunk = ((CraftChunk) e).getHandle(ChunkStatus.FULL);
        AtomicInteger c = new AtomicInteger();
        AtomicInteger r = new AtomicInteger();
        mantle.iterateChunk(e.getX(), e.getZ(), MatterBiomeInject.class, (x, y, z, b) -> {
            if (b != null) {
                if (b.isCustom()) {
                    chunk.setBiome(x, y, z, getCustomBiomeRegistry().get(b.getBiomeId()).get());
                    c.getAndIncrement();
                } else {
                    chunk.setBiome(x, y, z, (Holder<net.minecraft.world.level.biome.Biome>) getBiomeBase(e.getWorld(), b.getBiome()));
                    r.getAndIncrement();
                }
            }
        });
    }

    public ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException {
        if (customNbt != null && !customNbt.isEmpty()) {
            net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(itemStack);

            try {
                net.minecraft.nbt.CompoundTag tag = TagParser.parseCompoundFully((new JSONObject(customNbt)).toString());
                tag.merge(s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe());
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
        var chunkMap = ((CraftWorld)world).getHandle().getChunkSource().chunkMap;
        var worldGenContextField = getField(chunkMap.getClass(), WorldGenContext.class);
        worldGenContextField.setAccessible(true);
        var worldGenContext = (WorldGenContext) worldGenContextField.get(chunkMap);
        var dimensionType = chunkMap.level.dimensionTypeRegistration().unwrapKey().orElse(null);
        if (dimensionType != null && !dimensionType.location().getNamespace().equals("iris"))
            Iris.error("Loaded world %s with invalid dimension type! (%s)", world.getName(), dimensionType.location().toString());

        var newContext = new WorldGenContext(
                worldGenContext.level(), new IrisChunkGenerator(worldGenContext.generator(), seed, engine, world),
                worldGenContext.structureManager(), worldGenContext.lightEngine(), worldGenContext.mainThreadExecutor(), worldGenContext.unsavedListener());

        worldGenContextField.set(chunkMap, newContext);
    }

    public Vector3d getBoundingbox(org.bukkit.entity.EntityType entity) {
        Field[] fields = EntityType.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(EntityType.class)) {
                try {
                    EntityType entityType = (EntityType) field.get(null);
                    if (entityType.getDescriptionId().equals("entity.minecraft." + entity.name().toLowerCase())) {
                        Vector<Float> v1 = new Vector<>();
                        v1.add(entityType.getHeight());
                        entityType.getDimensions();
                        Vector3d box = new Vector3d( entityType.getWidth(), entityType.getHeight(),  entityType.getWidth());
                        //System.out.println("Entity Type: " + entityType.getDescriptionId() + ", " + "Height: " + height + ", Width: " + width);
                        return box;
                    }
                } catch (IllegalAccessException e) {
                    Iris.error("Unable to get entity dimensions!");
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    @Override
    public Entity spawnEntity(Location location,  org.bukkit.entity.EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        return ((CraftWorld) location.getWorld()).spawn(location, type.getEntityClass(), null, reason);
    }

    @Override
    public java.awt.Color getBiomeColor(Location location, BiomeColor type) {
        LevelReader reader = ((CraftWorld) location.getWorld()).getHandle();
        var holder = reader.getBiome(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        var biome = holder.value();
        if (biome == null) throw new IllegalArgumentException("Invalid biome: " + holder.unwrapKey().orElse(null));

        int rgba = switch (type) {
            case FOG -> biome.getFogColor();
            case WATER -> biome.getWaterColor();
            case WATER_FOG -> biome.getWaterFogColor();
            case SKY -> biome.getSkyColor();
            case FOLIAGE -> biome.getFoliageColor();
            case GRASS -> biome.getGrassColor(location.getBlockX(), location.getBlockZ());
        };
        if (rgba == 0) {
            if (BiomeColor.FOLIAGE == type && biome.getSpecialEffects().getFoliageColorOverride().isEmpty())
                return null;
            if (BiomeColor.GRASS == type && biome.getSpecialEffects().getGrassColorOverride().isEmpty())
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
        return registry.getOrThrow(ResourceKey.create(Registries.BIOME, CraftNamespacedKey.toMinecraft(biome.getKey())));
    }

    @Override
    public DataVersion getDataVersion() {
        return DataVersion.V1213;
    }

    @Override
    public int getSpawnChunkCount(World world) {
        var radius = Optional.ofNullable(world.getGameRuleValue(GameRule.SPAWN_CHUNK_RADIUS))
                .orElseGet(() -> world.getGameRuleDefault(GameRule.SPAWN_CHUNK_RADIUS));
        if (radius == null) throw new IllegalStateException("GameRule.SPAWN_CHUNK_RADIUS is null!");
        return (int) Math.pow(2 * radius + 1, 2);
    }

    @Override
    public KList<String> getStructureKeys() {
        KList<String> keys = new KList<>();

        var registry = registry().lookup(Registries.STRUCTURE).orElse(null);
        if (registry == null) return keys;
        registry.keySet().stream().map(ResourceLocation::toString).forEach(keys::add);
        registry.getTags()
                .map(HolderSet.Named::key)
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .map(s -> "#" + s)
                .forEach(keys::add);

        return keys;
    }

    @Override
    public boolean missingDimensionTypes(String... keys) {
        var type = registry().lookupOrThrow(Registries.DIMENSION_TYPE);
        return !Arrays.stream(keys)
                .map(key -> ResourceLocation.fromNamespaceAndPath("iris", key))
                .allMatch(type::containsKey);
    }

    @Override
    public boolean injectBukkit() {
        if (injected.getAndSet(true))
            return true;
        try {
            Iris.info("Injecting Bukkit");
            var buddy = new ByteBuddy();
            buddy.redefine(ServerLevel.class)
                    .visit(Advice.to(ServerLevelAdvice.class).on(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(
                            MinecraftServer.class, Executor.class, LevelStorageSource.LevelStorageAccess.class, PrimaryLevelData.class,
                            ResourceKey.class, LevelStem.class, ChunkProgressListener.class, boolean.class, long.class, List.class,
                            boolean.class, RandomSequences.class, World.Environment.class, ChunkGenerator.class, BiomeProvider.class))))
                    .make()
                    .load(ServerLevel.class.getClassLoader(), Agent.installed());
            for (Class<?> clazz : List.of(ChunkAccess.class, ProtoChunk.class)) {
                buddy.redefine(clazz)
                        .visit(Advice.to(ChunkAccessAdvice.class).on(ElementMatchers.isMethod().and(ElementMatchers.takesArguments(ShortList.class, int.class))))
                        .make()
                        .load(clazz.getClassLoader(), Agent.installed());
            }

            return true;
        } catch (Throwable e) {
            Iris.error(C.RED + "Failed to inject Bukkit");
            e.printStackTrace();
        }
        return false;
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
    public void placeStructures(Chunk chunk) {
        var craft = ((CraftChunk) chunk);
        var level = craft.getCraftWorld().getHandle();
        var access = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        level.getChunkSource().getGenerator().applyBiomeDecoration(level, access, level.structureManager());
    }

    @Override
    public KMap<Identifier, StructurePlacement> collectStructures() {
        var structureSets = registry().lookupOrThrow(Registries.STRUCTURE_SET);
        var structurePlacements = registry().lookupOrThrow(Registries.STRUCTURE_PLACEMENT);
        return structureSets.keySet()
                .stream()
                .map(structureSets::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(holder -> {
                    var set = holder.value();
                    var placement = set.placement();
                    var key = holder.key().location();
                    StructurePlacement.StructurePlacementBuilder<?, ?> builder;
                    if (placement instanceof RandomSpreadStructurePlacement random) {
                        builder = StructurePlacement.RandomSpread.builder()
                                .separation(random.separation())
                                .spacing(random.spacing())
                                .spreadType(switch (random.spreadType()) {
                                    case LINEAR -> IrisJigsawStructurePlacement.SpreadType.LINEAR;
                                    case TRIANGULAR -> IrisJigsawStructurePlacement.SpreadType.TRIANGULAR;
                                });
                    } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                        builder = StructurePlacement.ConcentricRings.builder()
                                .distance(rings.distance())
                                .spread(rings.spread())
                                .count(rings.count());
                    } else {
                        Iris.warn("Unsupported structure placement for set " + key + " with type " + structurePlacements.getKey(placement.type()));
                        return null;
                    }

                    return new Pair<>(new Identifier(key.getNamespace(), key.getPath()), builder
                            .salt(placement.salt)
                            .frequency(placement.frequency)
                            .structures(set.structures()
                                    .stream()
                                    .map(entry -> new StructurePlacement.Structure(
                                            entry.weight(),
                                            entry.structure()
                                                    .unwrapKey()
                                                    .map(ResourceKey::location)
                                                    .map(ResourceLocation::toString)
                                                    .orElse(null),
                                            entry.structure().tags()
                                                    .map(TagKey::location)
                                                    .map(ResourceLocation::toString)
                                                    .toList()
                                    ))
                                    .filter(StructurePlacement.Structure::isValid)
                                    .toList())
                            .build());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getA, Pair::getB, (a, b) -> a, KMap::new));
    }

    public LevelStem levelStem(RegistryAccess access, ChunkGenerator raw) {
        if (!(raw instanceof PlatformChunkGenerator gen))
            throw new IllegalStateException("Generator is not platform chunk generator!");

        var dimensionKey = ResourceLocation.fromNamespaceAndPath("iris", gen.getTarget().getDimension().getDimensionTypeKey());
        var dimensionType = access.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, dimensionKey));
        return new LevelStem(dimensionType, chunkGenerator(access));
    }

    private net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator(RegistryAccess access) {
        var settings = new FlatLevelGeneratorSettings(Optional.empty(), access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID), List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        settings.updateLayers();
        return new FlatLevelSource(settings);
    }

    private static class ChunkAccessAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean enter(@Advice.This ChunkAccess access, @Advice.Argument(1) int index) {
            return index >= access.getPostProcessing().length;
        }
    }

    private static class ServerLevelAdvice {
        @Advice.OnMethodEnter
        static void enter(
                @Advice.Argument(0) MinecraftServer server,
                @Advice.Argument(3) PrimaryLevelData levelData,
                @Advice.Argument(value = 5, readOnly = false) LevelStem levelStem,
                @Advice.Argument(12) World.Environment env,
                @Advice.Argument(value = 13) ChunkGenerator gen
        ) {
            if (gen == null || !gen.getClass().getPackageName().startsWith("com.volmit.iris"))
                return;

            try {
                Object bindings = Class.forName("com.volmit.iris.core.nms.INMS", true, Bukkit.getPluginManager().getPlugin("Iris")
                                .getClass()
                                .getClassLoader())
                        .getDeclaredMethod("get")
                        .invoke(null);
                levelStem = (LevelStem) bindings.getClass()
                        .getDeclaredMethod("levelStem", RegistryAccess.class, ChunkGenerator.class)
                        .invoke(bindings, server.registryAccess(), gen);

                levelData.customDimensions = null;
            } catch (Throwable e) {
                throw new RuntimeException("Iris failed to replace the levelStem", e instanceof InvocationTargetException ex ? ex.getCause() : e);
            }
        }
    }
}
