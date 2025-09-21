package com.volmit.iris.core.link.data;

import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.tools.IrisToolbelt;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.mobs.entities.SpawnReason;
import io.lumine.mythic.api.skills.conditions.ILocationCondition;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.adapters.BukkitWorld;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobStack;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.core.utils.annotations.MythicField;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MythicMobsDataProvider extends ExternalDataProvider {
    public MythicMobsDataProvider() {
        super("MythicMobs");
    }

    @Override
    public void init() {
    }

    @Override
    public @Nullable Entity spawnMob(@NotNull Location location, @NotNull Identifier entityId) throws MissingResourceException {
        var mm = spawnMob(BukkitAdapter.adapt(location), entityId);
        return mm == null ? null : mm.getEntity().getBukkitEntity();
    }

    private ActiveMob spawnMob(AbstractLocation location, Identifier entityId) throws MissingResourceException {
        var manager = MythicBukkit.inst().getMobManager();
        var mm = manager.getMythicMob(entityId.key()).orElse(null);
        if (mm == null) {
            var stack = manager.getMythicMobStack(entityId.key());
            if (stack == null) throw new MissingResourceException("Failed to find Mob!", entityId.namespace(), entityId.key());
            return stack.spawn(location, 1d, SpawnReason.OTHER, null);
        }
        return mm.spawn(location, 1d, SpawnReason.OTHER, null, null);
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        if (dataType != DataType.ENTITY) return List.of();
        var manager = MythicBukkit.inst().getMobManager();
        return Stream.concat(manager.getMobNames().stream(),
                        manager.getMobStacks()
                                .stream()
                                .map(MobStack::getName)
                )
                .distinct()
                .map(name -> new Identifier("mythicmobs", name))
                .toList();
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier id, DataType dataType) {
        return id.namespace().equalsIgnoreCase("mythicmobs") && dataType == DataType.ENTITY;
    }

    @EventHandler
    public void on(MythicConditionLoadEvent event) {
        switch (event.getConditionName()) {
            case "irisbiome" -> event.register(new IrisBiomeCondition(event.getConditionName(), event.getConfig()));
            case "irisregion" -> event.register(new IrisRegionCondition(event.getConditionName(), event.getConfig()));
        }
    }

    @MythicCondition(author = "CrazyDev22", name = "irisbiome", description = "Tests if the target is within the given list of biomes")
    public static class IrisBiomeCondition extends SkillCondition implements ILocationCondition {
        @MythicField(name = "biome", aliases = {"b"}, description = "A list of biomes to check")
        private Set<String> biomes = ConcurrentHashMap.newKeySet();
        @MythicField(name = "surface", aliases = {"s"}, description = "If the biome check should only be performed on the surface")
        private boolean surface;

        public IrisBiomeCondition(String line, MythicLineConfig mlc) {
            super(line);
            String b = mlc.getString(new String[]{"biome", "b"}, "");
            biomes.addAll(Arrays.asList(b.split(",")));
            surface = mlc.getBoolean(new String[]{"surface", "s"}, false);
        }

        @Override
        public boolean check(AbstractLocation target) {
            var access = IrisToolbelt.access(((BukkitWorld) target.getWorld()).getBukkitWorld());
            if (access == null) return false;
            var engine = access.getEngine();
            if (engine == null) return false;
            var biome = surface ?
                    engine.getSurfaceBiome(target.getBlockX(), target.getBlockZ()) :
                    engine.getBiomeOrMantle(target.getBlockX(), target.getBlockY() - engine.getMinHeight(), target.getBlockZ());
            return biomes.contains(biome.getLoadKey());
        }
    }

    @MythicCondition(author = "CrazyDev22", name = "irisregion", description = "Tests if the target is within the given list of biomes")
    public static class IrisRegionCondition extends SkillCondition implements ILocationCondition {
        @MythicField(name = "region", aliases = {"r"}, description = "A list of regions to check")
        private Set<String> regions = ConcurrentHashMap.newKeySet();

        public IrisRegionCondition(String line, MythicLineConfig mlc) {
            super(line);
            String b = mlc.getString(new String[]{"region", "r"}, "");
            regions.addAll(Arrays.asList(b.split(",")));
        }

        @Override
        public boolean check(AbstractLocation target) {
            var access = IrisToolbelt.access(((BukkitWorld) target.getWorld()).getBukkitWorld());
            if (access == null) return false;
            var engine = access.getEngine();
            if (engine == null) return false;
            var region = engine.getRegion(target.getBlockX(), target.getBlockZ());
            return regions.contains(region.getLoadKey());
        }
    }
}
