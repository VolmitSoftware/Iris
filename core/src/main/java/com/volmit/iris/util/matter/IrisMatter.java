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

package com.volmit.iris.util.matter;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Getter;

public class IrisMatter extends IrisRegistrant implements Matter {
    protected static final KMap<Class<?>, MatterSlice<?>> slicers = buildSlicers();

    @Getter
    private final MatterHeader header;

    @Getter
    private final int width;

    @Getter
    private final int height;

    @Getter
    private final int depth;

    @Getter
    private final KMap<Class<?>, MatterSlice<?>> sliceMap;

    public IrisMatter(int width, int height, int depth) {
        if (width < 1 || height < 1 || depth < 1) {
            throw new RuntimeException("Invalid Matter Size " + width + "x" + height + "x" + depth);
        }

        this.width = width;
        this.height = height;
        this.depth = depth;
        this.header = new MatterHeader();
        this.sliceMap = new KMap<>();
    }

    private static KMap<Class<?>, MatterSlice<?>> buildSlicers() {
        KMap<Class<?>, MatterSlice<?>> c = new KMap<>();
        for (Object i : Iris.initialize("com.volmit.iris.util.matter.slices", Sliced.class)) {
            MatterSlice<?> s = (MatterSlice<?>) i;
            c.put(s.getType(), s);
        }

        return c;
    }

    @Override
    public <T> MatterSlice<T> createSlice(Class<T> type, Matter m) {
        MatterSlice<?> slice = slicers.get(type);

        if (slice == null) {
            return null;
        }

        try {
            return slice.getClass().getConstructor(int.class, int.class, int.class).newInstance(getWidth(), getHeight(), getDepth());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public String getFolderName() {
        return "matter";
    }

    @Override
    public String getTypeName() {
        return "matter";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
