package com.volmit.iris.core.link;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class CitizensLink {

    /**
     * Returns true if the plugin is supported (i.e. Citizens is installed), false if not.
     * @return true if Citizens is installed, false otherwise
     */
    public static boolean supported() {
        return get() != null;
    }

    /**
     * Get the Citizens NPC Registry (to create new NPCs).
     * Make sure to check {@link #supported()} to ensure Citizens is enabled.
     * @return the NPC registry
     */
    public static NPCRegistry getRegistry() {
        return CitizensAPI.getNPCRegistry();
    }

    /**
     * Get the Citizens Plugin.
     * @return citizens plugin
     */
    public static Plugin get() {
        return Bukkit.getPluginManager().getPlugin("Citizens");
    }
}
