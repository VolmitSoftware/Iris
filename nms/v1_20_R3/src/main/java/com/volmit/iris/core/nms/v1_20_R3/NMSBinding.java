package com.volmit.iris.core.nms.v1_20_R3;

import java.awt.Color;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.container.IPackRepository;
import com.volmit.iris.core.nms.v1_20_R3.mca.ChunkSerializer;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.io.IO;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.IdMapper;
import net.minecraft.core.MappedRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import com.volmit.iris.core.nms.container.BiomeColor;
import net.minecraft.world.level.LevelReader;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftDolphin;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftNamespacedKey;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.Vector3d;
import com.volmit.iris.util.matter.MatterBiomeInject;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.mca.palette.*;
import com.volmit.iris.util.nbt.tag.CompoundTag;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import sun.misc.Unsafe;

import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class NMSBinding implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private final BlockData AIR = Material.AIR.createBlockData();
    private final WPackRepository packRepository = new WPackRepository();
    private final KMap<ResourceKey<?>, Boolean> changedRegistries = new KMap<>();
    private final AtomicCache<MCAIdMap<net.minecraft.world.level.biome.Biome>> biomeMapCache = new AtomicCache<>();
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
    public boolean hasTile(Location l) {
        return ((CraftWorld) l.getWorld()).getHandle().getBlockEntity(new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ()), false) != null;
    }

    @Override
    public CompoundTag serializeTile(Location location) {
        BlockEntity e = ((CraftWorld) location.getWorld()).getHandle().getBlockEntity(new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), true);

        if (e == null) {
            return null;
        }

        net.minecraft.nbt.CompoundTag tag = e.saveWithFullMetadata();
        return convert(tag);
    }

    private CompoundTag convert(net.minecraft.nbt.CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(boas);
            tag.write(dos);
            dos.close();
            return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(boas.toByteArray()), false).getTag();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private net.minecraft.nbt.CompoundTag convert(CompoundTag tag) {
        try {
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            NBTUtil.write(tag, boas, false);
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(boas.toByteArray()));
            net.minecraft.nbt.CompoundTag c = NbtIo.read(din);
            din.close();
            return c;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private RegistryAccess getRegistryAccess(World world) {
        try {
            var field = getField(Level.class, RegistryAccess.class);
            field.setAccessible(true);
            return  (RegistryAccess) field.get(((CraftWorld) world).getHandle());
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deserializeTile(CompoundTag c, Location pos) {
        ((CraftWorld) pos.getWorld()).getHandle().getChunkAt(new BlockPos(pos.getBlockX(), 0, pos.getBlockZ())).setBlockEntityNbt(convert(c));
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
        return registry().registry(Registries.BIOME).orElse(null);
    }

    private Registry<Block> getBlockRegistry() {
        return registry().registry(Registries.BLOCK).orElse(null);
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return getCustomBiomeRegistry().getHolder(id);
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
        return getCustomBiomeRegistry().get(new ResourceLocation(mckey));
    }

    @Override
    public Object getCustomBiomeBaseHolderFor(String mckey) {
        return getCustomBiomeRegistry().getHolder(getTrueBiomeBaseId(getCustomBiomeRegistry().get(new ResourceLocation(mckey)))).get();
    }

    public int getBiomeBaseIdForKey(String key) {
        return getCustomBiomeRegistry().getId(getCustomBiomeRegistry().get(new ResourceLocation(key)));
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return getCustomBiomeRegistry().getKey((net.minecraft.world.level.biome.Biome) biomeBase).getPath(); // something, not something:something
    }

    @Override
    public Object getBiomeBase(World world, Biome biome) {
        return biomeToBiomeBase(getRegistryAccess(world).registry(Registries.BIOME).orElse(null), biome);
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
                var registry = getRegistryAccess(i).registry(Registries.BIOME).orElse(null);
                if (registry != null) {
                    var holder = (Holder<net.minecraft.world.level.biome.Biome>) getBiomeBase(registry, biome);
                    return registry.getId(holder.value());
                }
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
        c.setUnsaved(true);
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
                    chunk.setBiome(x, y, z, getCustomBiomeRegistry().getHolder(b.getBiomeId()).get());
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
                net.minecraft.nbt.CompoundTag tag = TagParser.parseTag((new JSONObject(customNbt)).toString());
                tag.merge(s.getOrCreateTag());
                s.setTag(tag);
            } catch (CommandSyntaxException var5) {
                throw new IllegalArgumentException(var5);
            }

            return CraftItemStack.asBukkitCopy(s);
        } else {
            return itemStack;
        }
    }

    public void setTreasurePos(Dolphin dolphin, com.volmit.iris.core.nms.container.BlockPos pos) {
        CraftDolphin cd = (CraftDolphin)dolphin;
        cd.getHandle().setTreasurePos(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        cd.getHandle().setGotFish(true);
    }

    public void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException {
        ServerLevel serverLevel = ((CraftWorld)world).getHandle();
        Class<?> clazz = serverLevel.getChunkSource().chunkMap.generator.getClass();
        Field biomeSource = getField(clazz, BiomeSource.class);
        biomeSource.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe)unsafeField.get(null);
        CustomBiomeSource customBiomeSource = new CustomBiomeSource(seed, engine, world);
        unsafe.putObject(biomeSource.get(serverLevel.getChunkSource().chunkMap.generator), unsafe.objectFieldOffset(biomeSource), customBiomeSource);
        biomeSource.set(serverLevel.getChunkSource().chunkMap.generator, customBiomeSource);
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
    public boolean registerDimension(String name, IrisDimension dimension) {
        var registry = registry(Registries.DIMENSION_TYPE);
        var baseLocation = switch (dimension.getEnvironment()) {
            case NORMAL -> new ResourceLocation("minecraft", "overworld");
            case NETHER -> new ResourceLocation("minecraft", "the_nether");
            case THE_END -> new ResourceLocation("minecraft", "the_end");
            case CUSTOM -> throw new IllegalArgumentException("Cannot register custom dimension");
        };
        var base = registry.getHolder(ResourceKey.create(Registries.DIMENSION_TYPE, baseLocation)).orElse(null);
        if (base == null) return false;
        var json = encode(DimensionType.CODEC, base).orElse(null);
        if (json == null) return false;
        var object = json.getAsJsonObject();
        var height = dimension.getDimensionHeight();
        object.addProperty("min_y", height.getMin());
        object.addProperty("height", height.getMax() - height.getMin());
        object.addProperty("logical_height", dimension.getLogicalHeight());
        var value = decode(DimensionType.CODEC, object.toString()).map(Holder::value).orElse(null);
        if (value == null) return false;
        return register(Registries.DIMENSION_TYPE, new ResourceLocation("iris", name), value, true);
    }

    @Override
    public boolean registerBiome(String dimensionId, IrisBiomeCustom biome, boolean replace) {
        var biomeBase = decode(net.minecraft.world.level.biome.Biome.CODEC, biome.generateJson()).map(Holder::value).orElse(null);
        if (biomeBase == null) return false;
        return register(Registries.BIOME, new ResourceLocation(dimensionId, biome.getId()), biomeBase, replace);
    }

    private <T> Optional<T> decode(Codec<T> codec, String json) {
        return codec.decode(JsonOps.INSTANCE, GsonHelper.parse(json)).get().left().map(Pair::getFirst);
    }

    private <T> Optional<JsonElement> encode(Codec<T> codec, T value) {
        return codec.encode(value, JsonOps.INSTANCE, new JsonObject()).result();
    }

    private <T> boolean register(ResourceKey<Registry<T>> registryKey, ResourceLocation location, T value, boolean replace) {
        Preconditions.checkArgument(registryKey != null, "The registry cannot be null!");
        Preconditions.checkArgument(location != null, "The location cannot be null!");
        Preconditions.checkArgument(value != null, "The value cannot be null!");
        var registry = registry(registryKey);
        var key = ResourceKey.create(registryKey, location);
        try {
            if (registry.containsKey(key)) {
                if (!replace) return false;
                return replace(registryKey, location, value);
            }
            Field field = getField(MappedRegistry.class, boolean.class);
            field.setAccessible(true);
            boolean frozen = field.getBoolean(registry);
            field.setBoolean(registry, false);
            Field valueField = getField(Holder.Reference.class, "T");
            valueField.setAccessible(true);

            try {
                var holder = registry.register(key, value, Lifecycle.stable());
                if (frozen) valueField.set(holder, value);
                changedRegistries.put(registryKey, true);
                return true;
            } finally {
                field.setBoolean(registry, frozen);
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> boolean replace(ResourceKey<Registry<T>> registryKey, ResourceLocation location, T value) {
        Preconditions.checkArgument(registryKey != null, "The registryKey cannot be null!");
        Preconditions.checkArgument(location != null, "The location cannot be null!");
        Preconditions.checkArgument(value != null, "The value cannot be null!");
        var registry = registry(registryKey);
        var key = ResourceKey.create(registryKey, location);
        try {
            var holder = registry.getHolder(key).orElse(null);
            if (holder == null) return false;
            var oldValue = holder.value();
            Field valueField = getField(Holder.Reference.class, "T");
            valueField.setAccessible(true);
            Field toIdField = getField(MappedRegistry.class, buildType(Reference2IntMap.class, "T"));
            toIdField.setAccessible(true);
            Field byValueField = getField(MappedRegistry.class, buildType(Map.class, "T", buildType(Holder.Reference.class, "T")));
            byValueField.setAccessible(true);
            Field lifecyclesField = getField(MappedRegistry.class, buildType(Map.class, "T", Lifecycle.class.getName()));
            lifecyclesField.setAccessible(true);
            var toId = (Reference2IntMap<T>) toIdField.get(registry);
            var byValue = (Map<T, Holder.Reference<T>>) byValueField.get(registry);
            var lifecycles = (Map<T, Lifecycle>) lifecyclesField.get(registry);

            valueField.set(holder, value);
            toId.put(value, toId.removeInt(oldValue));
            byValue.put(value, byValue.remove(oldValue));
            lifecycles.put(value, lifecycles.remove(oldValue));
            changedRegistries.put(registryKey, true);
            return true;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> MappedRegistry<T> registry(ResourceKey<Registry<T>> registryKey) {
        var rawRegistry = registry().registry(registryKey).orElse(null);
        if (!(rawRegistry instanceof MappedRegistry<T> registry))
            throw new IllegalStateException("The Registry is not a mapped Registry!");
        return registry;
    }

    private static String buildType(Class<?> clazz, String... parameterTypes) {
        if (parameterTypes.length == 0) return clazz.getName();
        var builder = new StringBuilder(clazz.getName())
                .append("<");
        for (int i = 0; i < parameterTypes.length; i++) {
            builder.append(parameterTypes[i]).append(parameterTypes.length - 1 == i ? ">" : ", ");
        }
        return builder.toString();
    }

    private static Field getField(Class<?> clazz, String type) throws NoSuchFieldException {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getGenericType().getTypeName().equals(type))
                    return f;
            }
            throw new NoSuchFieldException(type);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) throw e;
            return getField(superClass, type);
        }
    }

    @Override
    public Color getBiomeColor(Location location, BiomeColor type) {
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
        return registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, CraftNamespacedKey.toMinecraft(biome.getKey())));
    }

    @Override
    public boolean dumpRegistry(File... folders) {
        var biomes = collect(Registries.BIOME, net.minecraft.world.level.biome.Biome.DIRECT_CODEC);
        var dimensions = collect(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC);

        if (biomes.isEmpty() && dimensions.isEmpty())
            return false;

        for (File folder : folders) {
            if (folder.getName().equals("datapacks"))
                folder = new File(folder, "iris");
            File data = new File(folder, "data");

            for (var entry : biomes.entrySet()) {
                File file = new File(data, entry.getKey().getNamespace() + "/worldgen/biome/" + entry.getKey().getPath() + ".json");
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs())
                    continue;

                try {
                    IO.writeAll(file, entry.getValue().toString());
                } catch (IOException e) {
                    Iris.error("Failed to write biome " + entry.getKey().toString() + " to " + file.getPath());
                }
            }

            for (var entry : dimensions.entrySet()) {
                File file = new File(data, entry.getKey().getNamespace() + "/dimension_type/" + entry.getKey().getPath() + ".json");
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs())
                    continue;

                try {
                    IO.writeAll(file, entry.getValue().toString());
                } catch (IOException e) {
                    Iris.error("Failed to write dimension " + entry.getKey().toString() + " to " + file.getPath());
                }
            }

            File meta = new File(folder, "pack.mcmeta");
            if (!meta.getParentFile().exists() && !meta.getParentFile().mkdirs())
                continue;

            try {
                IO.writeAll(meta, "{\"pack\": {\"pack_format\": "+getDataVersion().getPackFormat()+", \"description\": \"Iris Compat Data Pack. This pack contains all installed Iris Packs' resources for plugin compat.\"}}");
            } catch (IOException e) {
                Iris.error("Failed to write pack.mcmeta to " + meta.getPath());
            }
        }

        return true;
    }

    private <T> Map<ResourceLocation, JsonElement> collect(ResourceKey<Registry<T>> registryKey, Codec<T> codec) {
        var registry = registry().registry(registryKey).orElse(null);
        if (registry == null || !changedRegistries.getOrDefault(registryKey, false))
            return Map.of();
        try {
            return registry
                    .registryKeySet()
                    .stream()
                    .filter(id -> !id.location().getNamespace().equals("minecraft"))
                    .collect(Collectors.toMap(ResourceKey::location, id -> encode(codec, registry.get(id)).orElse(JsonNull.INSTANCE)));
        } finally {
            changedRegistries.put(registryKey, false);
        }
    }

    public void injectBukkit() {
        try {
            Iris.info("Injecting Bukkit");

            new ByteBuddy()
                    .redefine(CraftServer.class)
                    .visit(Advice.to(CraftServerAdvice.class).on(ElementMatchers.isMethod().and(ElementMatchers.takesArguments(WorldCreator.class))))
                    .make()
                    .load(CraftServer.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            new ByteBuddy()
                    .redefine(ServerLevel.class)
                    .visit(Advice.to(ServerLevelAdvice.class).on(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(MinecraftServer.class, Executor.class, LevelStorageSource.LevelStorageAccess.class,
                            PrimaryLevelData.class, ResourceKey.class, LevelStem.class, ChunkProgressListener.class, boolean.class, long.class,
                            List.class, boolean.class, RandomSequences.class, World.Environment.class, ChunkGenerator.class, BiomeProvider.class))))
                    .make()
                    .load(ServerLevel.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            Iris.info("Injected Bukkit Successfully!");
        } catch (Exception e) {
            Iris.info(C.RED + "Failed to Inject Bukkit!");
            e.printStackTrace();
            Iris.reportError(e);
        }

    }

    @Override
    public IHeadless createHeadless(Engine engine) {
        return new Headless(this, engine);
    }

    @Override
    public IPackRepository getPackRepository() {
        return packRepository;
    }

    private static class ServerLevelAdvice {
        @Advice.OnMethodEnter
        static void enter(@Advice.Argument(0) MinecraftServer server, @Advice.Argument(2) LevelStorageSource.LevelStorageAccess access, @Advice.Argument(4) ResourceKey<Level> key, @Advice.Argument(value = 5, readOnly = false) LevelStem levelStem) {
            File iris = new File(access.levelDirectory.path().toFile(), "iris");
            if (!iris.exists() && !key.location().getPath().startsWith("iris/")) return;
            ResourceKey<DimensionType> typeKey = ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation("iris", key.location().getPath()));
            RegistryAccess registryAccess = server.registryAccess();
            Registry<DimensionType> registry = registryAccess.registry(Registries.DIMENSION_TYPE).orElse(null);
            if (registry == null) throw new IllegalStateException("Unable to find registry for dimension type " + typeKey);
            Holder<DimensionType> holder = registry.getHolder(typeKey).orElse(null);
            if (holder == null) throw new IllegalStateException("Unable to find dimension type " + typeKey);
            levelStem = new LevelStem(holder, levelStem.generator());
        }
    }

    private static class CraftServerAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean enter(@Advice.This CraftServer self, @Advice.Argument(0) WorldCreator creator) {
            File isIrisWorld = new File(self.getWorldContainer(), creator.name() + "/iris");
            boolean isFromIris = false;
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement stack : stackTrace) {
                if (stack.getClassName().contains("Iris")) {
                    isFromIris = true;
                    break;
                }
            }
            if (isIrisWorld.exists() && !isFromIris) {
                var logger = Logger.getLogger("Iris");
                logger.warning("detected another Plugin trying to load " + creator.name() + ". This is not supported and will be ignored.");

                if (System.getProperty("iris.debug", "false").equals("true")) {
                    new RuntimeException().printStackTrace();
                }
                return true;
            }
            return false;
        }

        @Advice.OnMethodExit
        static void exit(@Advice.Enter boolean bool, @Advice.Return(readOnly = false) World returned) {
            if (bool) {
                returned = null;
            }
        }
    }

    Holder.Reference<net.minecraft.world.level.biome.Biome> getBiomeHolder(NamespacedKey key) {
        return getBiomeHolder(key.getNamespace(), key.getKey());
    }

    Holder.Reference<net.minecraft.world.level.biome.Biome> getBiomeHolder(String namespace, String id) {
        return getCustomBiomeRegistry().getHolder(ResourceKey.create(Registries.BIOME, new ResourceLocation(namespace, id))).orElse(null);
    }

    ProtoChunk createProtoChunk(ChunkPos pos, LevelHeightAccessor heightAccessor) {
        return new ProtoChunk(pos, UpgradeData.EMPTY, heightAccessor, getCustomBiomeRegistry(), null);
    }

    net.minecraft.nbt.CompoundTag serializeChunk(ChunkAccess chunkAccess, LevelHeightAccessor heightAccessor) {
        return ChunkSerializer.write(chunkAccess, heightAccessor, getCustomBiomeRegistry());
    }
}
