package com.volmit.iris.nms.v16_3;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.volmit.iris.nms.INMSCreator;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import com.volmit.iris.util.O;
import com.volmit.iris.util.V;
import net.minecraft.server.v1_16_R3.*;
import net.minecraft.server.v1_16_R3.IRegistryCustom.Dimension;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

class NMSCreator16_3 implements INMSCreator
{
    @SuppressWarnings({"unchecked", "rawtypes", "resource"})
    public World createWorld(WorldCreator creator, boolean loadSpawn)
    {
        EngineCompositeGenerator pro = (EngineCompositeGenerator) creator.generator();
        CraftServer server = ((CraftServer) Bukkit.getServer());
        Map<String, World> worlds = new V(server).get("worlds");
        DedicatedServer console = new V(server).get("console");
        Preconditions.checkState(!console.worldServer.isEmpty(), (Object) "Cannot create additional worlds on STARTUP");
        Validate.notNull((Object) creator, "Creator may not be null");
        final String name = creator.name();
        org.bukkit.generator.ChunkGenerator generator = creator.generator();
        final File folder = new File(server.getWorldContainer(), name);
        final World world = server.getWorld(name);

        if(world != null)
        {
            return world;
        }

        if(folder.exists() && !folder.isDirectory())
        {
            throw new IllegalArgumentException("File exists with the name '" + name + "' and isn't a folder");
        }

        if(generator == null)
        {
            generator = server.getGenerator(name);
        }

        ResourceKey<WorldDimension> actualDimension = null;
        switch(creator.environment())
        {
            case NORMAL:
            {
                actualDimension = (ResourceKey<WorldDimension>) WorldDimension.OVERWORLD;
                break;
            }
            case NETHER:
            {
                actualDimension = (ResourceKey<WorldDimension>) WorldDimension.THE_NETHER;
                break;
            }
            case THE_END:
            {
                actualDimension = (ResourceKey<WorldDimension>) WorldDimension.THE_END;
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Illegal dimension");
            }
        }
        Convertable.ConversionSession worldSession;
        try
        {
            worldSession = Convertable.a(server.getWorldContainer().toPath()).c(name, (ResourceKey) actualDimension);
        }
        catch(IOException ex)
        {
            throw new RuntimeException(ex);
        }
        MinecraftServer.convertWorld(worldSession);
        final boolean hardcore = creator.hardcore();
        final RegistryReadOps<NBTBase> registryreadops = (RegistryReadOps<NBTBase>) RegistryReadOps.a((DynamicOps) DynamicOpsNBT.a, console.dataPackResources.h(), getConsoleDimension(console));
        WorldDataServer worlddata = (WorldDataServer) worldSession.a((DynamicOps) registryreadops, console.datapackconfiguration);
        if(worlddata == null)
        {
            final Properties properties = new Properties();
            properties.put("generator-settings", Objects.toString(creator.generatorSettings()));
            properties.put("level-seed", Objects.toString(creator.seed()));
            properties.put("generate-structures", Objects.toString(creator.generateStructures()));
            properties.put("level-type", Objects.toString(creator.type().getName()));
            final GeneratorSettings generatorsettings = GeneratorSettings.a(getConsoleDimension(console), properties);
            @SuppressWarnings("deprecation")
            final WorldSettings worldSettings = new WorldSettings(name, EnumGamemode.getById(server.getDefaultGameMode().getValue()), hardcore, EnumDifficulty.EASY, false, new GameRules(), console.datapackconfiguration);
            worlddata = new WorldDataServer(worldSettings, generatorsettings, Lifecycle.stable());
        }
        worlddata.checkName(name);
        worlddata.a(console.getServerModName(), console.getModded().isPresent());
        if(console.options.has("forceUpgrade"))
        {
            Main.convertWorld(worldSession, DataConverterRegistry.a(), console.options.has("eraseCache"), () -> true, (ImmutableSet) worlddata.getGeneratorSettings().d().d().stream().map(entry -> ResourceKey.a(IRegistry.K, entry.getKey().a())).collect(ImmutableSet.toImmutableSet()));
        }
        final long j = BiomeManager.a(creator.seed());
        final List<MobSpawner> list = (List<MobSpawner>) ImmutableList.of((MobSpawner) new MobSpawnerPhantom(), (MobSpawner) new MobSpawnerPatrol(), (MobSpawner) new MobSpawnerCat(), (MobSpawner) new VillageSiege(), (MobSpawner) new MobSpawnerTrader((IWorldDataServer) worlddata));
        DimensionManager dimensionmanager;
        ChunkGenerator chunkgenerator;
        long ll = creator.seed();
        dimensionmanager = (DimensionManager) getConsoleDimension(console).a().d(DimensionManager.OVERWORLD);
        O<WorldServer> ws = new O<WorldServer>();
        chunkgenerator = new NMSChunkGenerator16_3(ws, creator, (WorldChunkManager)
                new NMSWorldChunkManager16_3(((EngineCompositeGenerator)creator.generator()), creator.name(), ll, false, false, (IRegistry<BiomeBase>) getConsoleDimension(console).b(IRegistry.ay)), ll,
                () -> (GeneratorSettingBase) getConsoleDimension(console).b(IRegistry.ar).d(GeneratorSettingBase.c));
        final ResourceKey<net.minecraft.server.v1_16_R3.World> worldKey = (ResourceKey<net.minecraft.server.v1_16_R3.World>) ResourceKey.a(IRegistry.L, new MinecraftKey(name.toLowerCase(Locale.ENGLISH)));
        //@builder
        final WorldServer internal = new WorldServer((MinecraftServer) console,
                console.executorService, worldSession,
                (IWorldDataServer) worlddata,
                (ResourceKey) worldKey,
                dimensionmanager,
                server.getServer().worldLoadListenerFactory.create(11),
                chunkgenerator,
                worlddata.getGeneratorSettings().isDebugWorld(),
                j,
                (List) ((creator.environment() == Environment.NORMAL) ? list : ImmutableList.of()),
                true,
                creator.environment(),
                server.getGenerator(name));
        //@done
        IrisWorlds.register(internal.getWorld(), pro);
        ws.set(internal);
        if(!worlds.containsKey(name.toLowerCase(Locale.ENGLISH)))
        {
            try
            {
                internal.close();
            }

            catch(IOException e)
            {
                e.printStackTrace();
            }

            return null;
        }

        console.initWorld(internal, (IWorldDataServer) worlddata, (SaveData) worlddata, worlddata.getGeneratorSettings());
        internal.setSpawnFlags(true, true);
        console.worldServer.put(internal.getDimensionKey(), internal);
        server.getPluginManager().callEvent((Event) new WorldInitEvent((World) internal.getWorld()));

        if(loadSpawn)
        {
            server.getServer().loadSpawn(internal.getChunkProvider().playerChunkMap.worldLoadListener, internal);
        }

        else
        {
            MinecraftServer.LOGGER.info("Preparing start region for dimens... Oh wait, We don't do that here anymore.");
        }

        server.getPluginManager().callEvent((Event) new WorldLoadEvent((World) internal.getWorld()));
        return (World) internal.getWorld();
    }

    private Dimension getConsoleDimension(DedicatedServer console)
    {
        Dimension dim = null;

        try
        {
            dim = new V((MinecraftServer) console, true).get("customRegistry");

            if(dim != null)
            {
                return dim;
            }
        }

        catch(Throwable e)
        {

        }

        try
        {
            dim = new V((MinecraftServer) console, true).get("f");

            if(dim != null)
            {
                return dim;
            }
        }

        catch(Throwable e)
        {

        }

        for(Field i : MinecraftServer.class.getDeclaredFields())
        {
            if(i.getType().equals(dim.getClass()))
            {
                i.setAccessible(true);

                if(Modifier.isStatic(i.getModifiers()))
                {
                    try
                    {
                        return (Dimension) i.get(null);
                    }

                    catch(Throwable e)
                    {
                        e.printStackTrace();
                    }
                }

                else
                {
                    try
                    {
                        return (Dimension) i.get((MinecraftServer) console);
                    }

                    catch(Throwable e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        if(dim == null)
        {
            try
            {
                throw new RuntimeException("Cannot find dimension field!");
            }

            catch(Throwable e)
            {
                e.printStackTrace();
            }
        }

        return dim;
    }
}