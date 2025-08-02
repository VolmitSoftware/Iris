package com.volmit.iris.core.link;

import lombok.NonNull;
import net.thenextlvl.worlds.api.WorldsProvider;
import net.thenextlvl.worlds.api.generator.GeneratorType;
import net.thenextlvl.worlds.api.generator.LevelStem;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class FoliaWorldsLink {
    private static FoliaWorldsLink instance;
    private final Object provider;

    private FoliaWorldsLink(Object provider) {
        this.provider = provider;
    }

    public static FoliaWorldsLink get() {
        if(instance == null) {
            synchronized (FoliaWorldsLink.class) {
                try {
                    Server.class.getDeclaredMethod("isGlobalTickThread");
                    instance = new FoliaWorldsLink(Bukkit.getServicesManager().load(WorldsProvider.class));
                } catch (Throwable e) {
                    instance = new FoliaWorldsLink(null);
                }
            }
        }

        return instance;
    }

    public boolean isActive() {
        return provider != null;
    }

    @Nullable
    public CompletableFuture<World> createWorld(@NonNull WorldCreator creator) {
        if (provider == null) return null;
        return ((WorldsProvider) provider)
                .levelBuilder(new File(Bukkit.getWorldContainer(), creator.name()).toPath())
                .name(creator.name())
                .seed(creator.seed())
                .levelStem(switch (creator.environment()) {
                    case CUSTOM, NORMAL -> LevelStem.OVERWORLD;
                    case NETHER -> LevelStem.NETHER;
                    case THE_END -> LevelStem.END;
                })
                .chunkGenerator(creator.generator())
                .biomeProvider(creator.biomeProvider())
                .generatorType(switch (creator.type()) {
                    case NORMAL -> GeneratorType.NORMAL;
                    case FLAT -> GeneratorType.FLAT;
                    case LARGE_BIOMES -> GeneratorType.LARGE_BIOMES;
                    case AMPLIFIED -> GeneratorType.AMPLIFIED;
                })
                .structures(creator.generateStructures())
                .hardcore(creator.hardcore())
                .build()
                .createAsync();
    }
}
