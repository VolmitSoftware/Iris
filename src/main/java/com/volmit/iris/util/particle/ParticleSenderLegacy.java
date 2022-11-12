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

package com.volmit.iris.util.particle;

import com.volmit.iris.Iris;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Legacy particle sender with NMS for 1.7/1.8 servers
 *
 * @author MrMicky
 */
@SuppressWarnings({"deprecation", "JavaReflectionInvocation"})
class ParticleSenderLegacy implements ParticleSender {

    private static final boolean SERVER_IS_1_8;

    private static final Constructor<?> PACKET_PARTICLE;
    private static final Class<?> ENUM_PARTICLE;

    private static final Method WORLD_GET_HANDLE;
    private static final Method WORLD_SEND_PARTICLE;

    private static final Method PLAYER_GET_HANDLE;
    private static final Field PLAYER_CONNECTION;
    private static final Method SEND_PACKET;
    private static final int[] EMPTY = new int[0];

    static {
        ENUM_PARTICLE = FastReflection.nmsOptionalClass("EnumParticle").orElse(null);
        SERVER_IS_1_8 = ENUM_PARTICLE != null;

        try {
            Class<?> packetParticleClass = FastReflection.nmsClass("PacketPlayOutWorldParticles");
            Class<?> playerClass = FastReflection.nmsClass("EntityPlayer");
            Class<?> playerConnectionClass = FastReflection.nmsClass("PlayerConnection");
            Class<?> worldClass = FastReflection.nmsClass("WorldServer");
            Class<?> entityPlayerClass = FastReflection.nmsClass("EntityPlayer");

            Class<?> craftPlayerClass = FastReflection.obcClass("entity.CraftPlayer");
            Class<?> craftWorldClass = FastReflection.obcClass("CraftWorld");

            if (SERVER_IS_1_8) {
                PACKET_PARTICLE = packetParticleClass.getConstructor(ENUM_PARTICLE, boolean.class, float.class,
                        float.class, float.class, float.class, float.class, float.class, float.class, int.class,
                        int[].class);
                WORLD_SEND_PARTICLE = worldClass.getDeclaredMethod("sendParticles", entityPlayerClass, ENUM_PARTICLE,
                        boolean.class, double.class, double.class, double.class, int.class, double.class, double.class,
                        double.class, double.class, int[].class);
            } else {
                PACKET_PARTICLE = packetParticleClass.getConstructor(String.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class, int.class);
                WORLD_SEND_PARTICLE = worldClass.getDeclaredMethod("a", String.class, double.class, double.class,
                        double.class, int.class, double.class, double.class, double.class, double.class);
            }

            WORLD_GET_HANDLE = craftWorldClass.getDeclaredMethod("getHandle");
            PLAYER_GET_HANDLE = craftPlayerClass.getDeclaredMethod("getHandle");
            PLAYER_CONNECTION = playerClass.getField("playerConnection");
            SEND_PACKET = playerConnectionClass.getMethod("sendPacket", FastReflection.nmsClass("Packet"));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void spawnParticle(Object receiver, ParticleType particle, double x, double y, double z, int count, double offsetX, double offsetY,
                              double offsetZ, double extra, Object data) {
        try {
            int[] datas = toData(particle, data);

            if (data instanceof Color) {
                if (particle.getDataType() == Color.class) {
                    Color color = (Color) data;
                    count = 0;
                    offsetX = color(color.getRed());
                    offsetY = color(color.getGreen());
                    offsetZ = color(color.getBlue());
                    extra = 1.0;
                }
            }

            if (receiver instanceof World) {
                Object worldServer = WORLD_GET_HANDLE.invoke(receiver);

                if (SERVER_IS_1_8) {
                    WORLD_SEND_PARTICLE.invoke(worldServer, null, getEnumParticle(particle), true, x, y, z, count, offsetX, offsetY, offsetZ, extra, datas);
                } else {
                    String particleName = particle.getLegacyName() + (datas.length != 2 ? "" : "_" + datas[0] + "_" + datas[1]);
                    WORLD_SEND_PARTICLE.invoke(worldServer, particleName, x, y, z, count, offsetX, offsetY, offsetZ, extra);
                }
            } else if (receiver instanceof Player) {
                Object packet;

                if (SERVER_IS_1_8) {
                    packet = PACKET_PARTICLE.newInstance(getEnumParticle(particle), true, (float) x, (float) y,
                            (float) z, (float) offsetX, (float) offsetY, (float) offsetZ, (float) extra, count, datas);
                } else {
                    String particleName = particle.getLegacyName() + (datas.length != 2 ? "" : "_" + datas[0] + "_" + datas[1]);
                    packet = PACKET_PARTICLE.newInstance(particleName, (float) x, (float) y, (float) z,
                            (float) offsetX, (float) offsetY, (float) offsetZ, (float) extra, count);
                }

                Object entityPlayer = PLAYER_GET_HANDLE.invoke(receiver);
                Object playerConnection = PLAYER_CONNECTION.get(entityPlayer);
                SEND_PACKET.invoke(playerConnection, packet);
            }
        } catch (ReflectiveOperationException e) {
            Iris.reportError(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isValidData(Object particle, Object data) {
        return true;
    }

    @Override
    public Object getParticle(ParticleType particle) {
        if (!SERVER_IS_1_8) {
            return particle.getLegacyName();
        }

        try {
            return getEnumParticle(particle);
        } catch (IllegalArgumentException e) {
            Iris.reportError(e);
            return null;
        }
    }

    private Object getEnumParticle(ParticleType particleType) {
        return FastReflection.enumValueOf(ENUM_PARTICLE, particleType.toString());
    }

    private int[] toData(ParticleType particle, Object data) {
        Class<?> dataType = particle.getDataType();
        if (dataType == ItemStack.class) {
            if (!(data instanceof ItemStack itemStack)) {
                return SERVER_IS_1_8 ? new int[2] : new int[]{1, 0};
            }

            return new int[]{itemStack.getType().getId(), itemStack.getDurability()};
        }

        if (dataType == MaterialData.class) {
            if (!(data instanceof MaterialData materialData)) {
                return SERVER_IS_1_8 ? new int[1] : new int[]{1, 0};
            }

            if (SERVER_IS_1_8) {
                return new int[]{materialData.getItemType().getId() + (materialData.getData() << 12)};
            } else {
                return new int[]{materialData.getItemType().getId(), materialData.getData()};
            }
        }

        return EMPTY;
    }
}
