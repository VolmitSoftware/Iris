package com.volmit.iris.util.data.registry;

import org.bukkit.Particle;

import static com.volmit.iris.util.data.registry.RegistryUtil.find;

public class Particles {
    public static final Particle CRIT_MAGIC = find(Particle.class, "crit_magic", "crit");
    public static final Particle REDSTONE = find(Particle.class,  "redstone", "dust");
    public static final Particle ITEM = find(Particle.class,  "item_crack", "item");
}
