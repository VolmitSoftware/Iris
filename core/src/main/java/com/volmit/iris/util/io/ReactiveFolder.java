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

package com.volmit.iris.util.io;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.Consumer3;

import java.io.File;

public class ReactiveFolder {
    private final File folder;
    private final Consumer3<KList<File>, KList<File>, KList<File>> hotload;
    private FolderWatcher fw;
    private int checkCycle = 0;

    public ReactiveFolder(File folder, Consumer3<KList<File>, KList<File>, KList<File>> hotload) {
        this.folder = folder;
        this.hotload = hotload;
        this.fw = new FolderWatcher(folder);
        fw.checkModified();
    }

    public void checkIgnore() {
        fw = new FolderWatcher(folder);
    }

    public boolean check() {
        checkCycle++;
        boolean modified = false;

        if (checkCycle % 3 == 0 ? fw.checkModified() : fw.checkModifiedFast()) {
            for (File i : fw.getCreated()) {
                if (i.getName().endsWith(".iob") || i.getName().endsWith(".json") || i.getName().endsWith(".js")) {
                    if (i.getPath().contains(".iris")) {
                        continue;
                    }

                    modified = true;
                    break;
                }
            }

            if (!modified) {
                for (File i : fw.getChanged()) {
                    if (i.getPath().contains(".iris")) {
                        continue;
                    }

                    if (i.getName().endsWith(".iob") || i.getName().endsWith(".json") || i.getName().endsWith(".js")) {
                        modified = true;
                        break;
                    }
                }
            }

            if (!modified) {
                for (File i : fw.getDeleted()) {
                    if (i.getPath().contains(".iris")) {
                        continue;
                    }

                    if (i.getName().endsWith(".iob") || i.getName().endsWith(".json") || i.getName().endsWith(".js")) {
                        modified = true;
                        break;
                    }
                }
            }
        }

        if (modified) {
            hotload.accept(fw.getCreated(), fw.getChanged(), fw.getDeleted());
        }

        return fw.checkModified();
    }

    public void clear() {
        fw.clear();
    }
}
