package com.volmit.iris.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Simple Bukkit Particles API with 1.7 to 1.13.2 support !
 * <p>
 * You can find the project on <a href="https://github.com/MrMicky-FR/FastParticles">GitHub</a>
 *
 * @author MrMicky
 */
public final class FastParticle {

    private static final ParticleSender PARTICLE_SENDER;

    static {
        if (FastReflection.optionalClass("org.bukkit.Particle$DustOptions").isPresent()) {
            PARTICLE_SENDER = new ParticleSender.ParticleSender1_13();
        } else if (FastReflection.optionalClass("org.bukkit.Particle").isPresent()) {
            PARTICLE_SENDER = new ParticleSender.ParticleSenderImpl();
        } else {
            PARTICLE_SENDER = new ParticleSenderLegacy();
        }
    }

    private FastParticle() {
        throw new UnsupportedOperationException();
    }

    /*
     * Worlds methods
     */
    public static void spawnParticle(World world, ParticleType particle, Location location, int count) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count);
    }

    public static void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count) {
        spawnParticle(world, particle, x, y, z, count, null);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, Location location, int count, T data) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count, data);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count,
                                         T data) {
        spawnParticle(world, particle, x, y, z, count, 0.0D, 0.0D, 0.0D, data);
    }

    public static void spawnParticle(World world, ParticleType particle, Location location, int count, double offsetX,
                                     double offsetY, double offsetZ) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    public static void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count,
                                     double offsetX, double offsetY, double offsetZ) {
        spawnParticle(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, Location location, int count,
                                         double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY,
                offsetZ, data);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, 1.0D, data);
    }

    public static void spawnParticle(World world, ParticleType particle, Location location, int count, double offsetX,
                                     double offsetY, double offsetZ, double extra) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count,
                                     double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, Location location, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(world, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static <T> void spawnParticle(World world, ParticleType particle, double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra, T data) {
        sendParticle(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
    }

    /*
     * Player methods
     */
    public static void spawnParticle(Player player, ParticleType particle, Location location, int count) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count);
    }

    public static void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count) {
        spawnParticle(player, particle, x, y, z, count, null);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, Location location, int count, T data) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count, data);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count,
                                         T data) {
        spawnParticle(player, particle, x, y, z, count, 0.0D, 0.0D, 0.0D, data);
    }

    public static void spawnParticle(Player player, ParticleType particle, Location location, int count, double offsetX,
                                     double offsetY, double offsetZ) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    public static void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count,
                                     double offsetX, double offsetY, double offsetZ) {
        spawnParticle(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, Location location, int count,
                                         double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, data);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, 1.0D, data);
    }

    public static void spawnParticle(Player player, ParticleType particle, Location location, int count, double offsetX,
                                     double offsetY, double offsetZ, double extra) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count,
                                     double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, Location location, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(player, particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    public static <T> void spawnParticle(Player player, ParticleType particle, double x, double y, double z, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra, T data) {
        sendParticle(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
    }

    private static void sendParticle(Object receiver, ParticleType particle, double x, double y, double z, int count,
                                     double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        if (!particle.isSupported()) {
            throw new IllegalArgumentException("The particle '" + particle + "' is not compatible with your server version");
        }

        PARTICLE_SENDER.spawnParticle(receiver, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
    }
}
