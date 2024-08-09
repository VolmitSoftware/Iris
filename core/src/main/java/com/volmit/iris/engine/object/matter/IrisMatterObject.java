/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisMatterObject extends IrisRegistrant {
    private final Matter matter;

    public IrisMatterObject() {
        this(1, 1, 1);
    }

    public IrisMatterObject(int w, int h, int d) {
        this(new IrisMatter(w, h, d));
    }

    public IrisMatterObject(Matter matter) {
        this.matter = matter;
    }

    public static IrisMatterObject from(IrisObject object) {
        return new IrisMatterObject(Matter.from(object));
    }

    public static IrisMatterObject from(File j) throws IOException, ClassNotFoundException {
        return new IrisMatterObject(Matter.read(j));
    }

    @Override
    public String getFolderName() {
        return "matter";
    }

    @Override
    public String getTypeName() {
        return "Matter";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
