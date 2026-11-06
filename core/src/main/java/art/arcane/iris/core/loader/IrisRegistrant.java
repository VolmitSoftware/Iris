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

package art.arcane.iris.core.loader;

import com.google.gson.GsonBuilder;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.object.KeyedResource;
import art.arcane.iris.core.project.StudioEditorLauncher;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class IrisRegistrant extends KeyedResource {
    @EqualsAndHashCode.Exclude
    private transient IrisData loader;

    private transient File loadFile;
    private transient ModuleSettings moduleSettings = ModuleSettings.NONE;

    public void registerTypeAdapters(GsonBuilder builder) {

    }

    /** Opens this resource's file in the configured studio editor; null when nothing was opened. */
    public File openInEditor() {
        File file = getLoadFile();
        if (file == null || loader == null) {
            return null;
        }
        return StudioEditorLauncher.openFile(loader.getDataFolder(), file) ? file : null;
    }
}
