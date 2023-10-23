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

package com.volmit.iris.core.loader;

import com.google.gson.GsonBuilder;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisScript;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Data;

import java.awt.*;
import java.io.File;

@Data
public abstract class IrisRegistrant {
    @Desc("Preprocess this object in-memory when it's loaded, run scripts using the variable 'Iris.getPreprocessorObject()' and modify properties about this object before it's used.")
    @RegistryListResource(IrisScript.class)
    @ArrayType(min = 1, type = String.class)
    private KList<String> preprocessors = new KList<>();

    private transient IrisData loader;

    private transient String loadKey;

    private transient File loadFile;

    public abstract String getFolderName();

    public abstract String getTypeName();

    public void registerTypeAdapters(GsonBuilder builder) {

    }

    public File openInVSCode() {
        try {
            Desktop.getDesktop().open(getLoadFile());
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        return getLoadFile();
    }

    public abstract void scanForErrors(JSONObject p, VolmitSender sender);
}
