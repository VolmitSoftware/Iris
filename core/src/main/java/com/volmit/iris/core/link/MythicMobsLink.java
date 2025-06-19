/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.link;

import com.google.common.collect.Sets;
import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.ILocationCondition;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.adapters.BukkitWorld;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import io.lumine.mythic.core.utils.annotations.MythicField;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.*;

public class MythicMobsLink {

    public MythicMobsLink() {
        if (getPlugin() == null) return;
        Iris.instance.registerListener(new ConditionListener());
    }

    public boolean isEnabled() {
        return getPlugin() != null;
    }

    public Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs");
    }

    /**
     * Spawn a mythic mob at this location
     *
     * @param mob      The mob
     * @param location The location
     * @return The mob, or null if it can't be spawned
     */
    public @Nullable Entity spawnMob(String mob, Location location) {
        return isEnabled() ? MythicBukkit.inst().getMobManager().spawnMob(mob, location).getEntity().getBukkitEntity() : null;
    }

    public Collection<String> getMythicMobTypes() {
        return isEnabled() ? MythicBukkit.inst().getMobManager().getMobNames() : List.of();
    }

    private static class ConditionListener implements Listener {
        @EventHandler
        public void on(MythicConditionLoadEvent event) {
            switch (event.getConditionName()) {
                case "irisbiome" -> event.register(new IrisBiomeCondition(event.getConditionName(), event.getConfig()));
                case "irisregion" -> event.register(new IrisRegionCondition(event.getConditionName(), event.getConfig()));
            }
        }
    }

    @MythicCondition(author = "CrazyDev22", name = "irisbiome", description = "Tests if the target is within the given list of biomes")
    public static class IrisBiomeCondition extends SkillCondition implements ILocationCondition {
        @MythicField(name = "biome", aliases = {"b"}, description = "A list of biomes to check")
        private Set<String> biomes = Sets.newConcurrentHashSet();
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

    @MythicCondition(author = "CrazyDev22", name = "irisbiome", description = "Tests if the target is within the given list of biomes")
    public static class IrisRegionCondition extends SkillCondition implements ILocationCondition {
        @MythicField(name = "region", aliases = {"r"}, description = "A list of regions to check")
        private Set<String> regions = Sets.newConcurrentHashSet();

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
