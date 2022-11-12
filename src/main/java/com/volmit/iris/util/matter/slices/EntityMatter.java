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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.MatterEntity;
import com.volmit.iris.util.matter.MatterEntityGroup;
import com.volmit.iris.util.matter.MatterReader;
import com.volmit.iris.util.matter.Sliced;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class EntityMatter extends RawMatter<MatterEntityGroup> {
    public static final MatterEntityGroup EMPTY = new MatterEntityGroup();
    private transient KMap<IrisPosition, KList<Entity>> entityCache = new KMap<>();

    public EntityMatter() {
        this(1, 1, 1);
    }

    public EntityMatter(int width, int height, int depth) {
        super(width, height, depth, MatterEntityGroup.class);
        registerWriter(World.class, ((w, d, x, y, z) -> {
            for (MatterEntity i : d.getEntities()) {
                Location realPosition = new Location(w, x + i.getXOff(), y + i.getYOff(), z + i.getZOff());
                INMS.get().deserializeEntity(i.getEntityData(), realPosition);
            }
        }));
        registerReader(World.class, (w, x, y, z) -> {
            IrisPosition pos = new IrisPosition(x, y, z);
            KList<Entity> entities = entityCache.get(pos);
            MatterEntityGroup g = new MatterEntityGroup();
            if (entities != null) {
                for (Entity i : entities) {
                    g.getEntities().add(new MatterEntity(
                            Math.abs(i.getLocation().getX()) - Math.abs(i.getLocation().getBlockX()),
                            Math.abs(i.getLocation().getY()) - Math.abs(i.getLocation().getBlockY()),
                            Math.abs(i.getLocation().getZ()) - Math.abs(i.getLocation().getBlockZ()),
                            INMS.get().serializeEntity(i)
                    ));
                }

                return g;
            }

            return null;
        });
    }

    @Override
    public Palette<MatterEntityGroup> getGlobalPalette() {
        return null;
    }

    /**
     * The readFrom is overridden only if W is a Bukkit World, instead of looping
     * across every block position, we simply use getNearbyEntities and cache each
     * block position with a list of entities within that block, and directly feed
     * the reader with the entities we capture.
     *
     * @param w   the world
     * @param x   the x offset
     * @param y   the y offset
     * @param z   the z offset
     * @param <W> the type
     * @return true if we read
     */
    @Override
    public synchronized <W> boolean readFrom(W w, int x, int y, int z) {
        if (!(w instanceof World)) {
            return super.readFrom(w, x, y, z);
        }

        MatterReader<W, MatterEntityGroup> reader = (MatterReader<W, MatterEntityGroup>) readFrom(World.class);

        if (reader == null) {
            return false;
        }

        entityCache = new KMap<>();

        for (Entity i : ((World) w).getNearbyEntities(new BoundingBox(x, y, z, x + getWidth(), y + getHeight(), z + getHeight()))) {
            entityCache.computeIfAbsent(new IrisPosition(i.getLocation()),
                    k -> new KList<>()).add(i);
        }

        for (IrisPosition i : entityCache.keySet()) {
            MatterEntityGroup g = reader.readMatter(w, i.getX(), i.getY(), i.getZ());

            if (g != null) {
                set(i.getX() - x, i.getY() - y, i.getZ() - z, g);
            }
        }

        entityCache.clear();

        return true;
    }

    @Override
    public void writeNode(MatterEntityGroup b, DataOutputStream dos) throws IOException {
        Varint.writeUnsignedVarInt(b.getEntities().size(), dos);
        for (MatterEntity i : b.getEntities()) {
            dos.writeByte((int) (i.getXOff() * 255) + Byte.MIN_VALUE);
            dos.writeByte((int) (i.getYOff() * 255) + Byte.MIN_VALUE);
            dos.writeByte((int) (i.getZOff() * 255) + Byte.MIN_VALUE);
            NBTUtil.write(i.getEntityData(), dos, false);
        }
    }

    @Override
    public MatterEntityGroup readNode(DataInputStream din) throws IOException {
        MatterEntityGroup g = new MatterEntityGroup();
        int c = Varint.readUnsignedVarInt(din);

        while (c-- > 0) {
            g.getEntities().add(new MatterEntity(
                    ((int) din.readByte() - Byte.MIN_VALUE) / 255F,
                    ((int) din.readByte() - Byte.MIN_VALUE) / 255F,
                    ((int) din.readByte() - Byte.MIN_VALUE) / 255F,
                    (CompoundTag) NBTUtil.read(din, false).getTag()));
        }

        return g;
    }
}
