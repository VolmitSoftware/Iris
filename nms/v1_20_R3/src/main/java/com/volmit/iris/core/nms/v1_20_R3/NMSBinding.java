package com.volmit.iris.core.nms.v1_20_R3;

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
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
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
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class NMSBinding implements INMSBinding {
    private final KMap<Biome, Object> baseBiomeCache = new KMap<>();
    private final BlockData AIR = Material.AIR.createBlockData();
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
        return biomeToBiomeBase(((CraftWorld) world).getHandle()
                .registryAccess().registry(Registries.BIOME).orElse(null), biome);
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
                Registry<net.minecraft.world.level.biome.Biome> registry = ((CraftWorld) i).getHandle().registryAccess().registry(Registries.BIOME).orElse(null);
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
}
