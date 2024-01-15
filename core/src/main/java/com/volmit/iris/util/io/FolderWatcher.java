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
import com.volmit.iris.util.collection.KMap;

import java.io.File;

public class FolderWatcher extends FileWatcher {
    private KMap<File, FolderWatcher> watchers;
    private KList<File> changed;
    private KList<File> created;
    private KList<File> deleted;

    public FolderWatcher(File file) {
        super(file);
    }

    protected void readProperties() {
        if (watchers == null) {
            watchers = new KMap<>();
            changed = new KList<>();
            created = new KList<>();
            deleted = new KList<>();
        }

        if (file.isDirectory()) {
            for (File i : file.listFiles()) {
                if (!watchers.containsKey(i)) {
                    watchers.put(i, new FolderWatcher(i));
                }
            }

            for (File i : watchers.k()) {
                if (!i.exists()) {
                    watchers.remove(i);
                }
            }
        } else {
            super.readProperties();
        }
    }

    public boolean checkModified() {
        changed.clear();
        created.clear();
        deleted.clear();

        if (file.isDirectory()) {
            KMap<File, FolderWatcher> w = watchers.copy();
            readProperties();

            for (File i : w.keySet()) {
                if (!watchers.containsKey(i)) {
                    deleted.add(i);
                }
            }

            for (File i : watchers.keySet()) {
                if (!w.containsKey(i)) {
                    created.add(i);
                } else {
                    FolderWatcher fw = watchers.get(i);
                    if (fw.checkModified()) {
                        changed.add(fw.file);
                    }

                    changed.addAll(fw.getChanged());
                    created.addAll(fw.getCreated());
                    deleted.addAll(fw.getDeleted());
                }
            }

            return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
        }

        return super.checkModified();
    }

    public boolean checkModifiedFast() {
        if (watchers == null || watchers.isEmpty()) {
            return checkModified();
        }

        changed.clear();
        created.clear();
        deleted.clear();

        if (file.isDirectory()) {
            for (File i : watchers.keySet()) {
                FolderWatcher fw = watchers.get(i);
                if (fw.checkModifiedFast()) {
                    changed.add(fw.file);
                }

                changed.addAll(fw.getChanged());
                created.addAll(fw.getCreated());
                deleted.addAll(fw.getDeleted());
            }

            return !changed.isEmpty() || !created.isEmpty() || !deleted.isEmpty();
        }

        return super.checkModified();
    }

    public KMap<File, FolderWatcher> getWatchers() {
        return watchers;
    }

    public KList<File> getChanged() {
        return changed;
    }

    public KList<File> getCreated() {
        return created;
    }

    public KList<File> getDeleted() {
        return deleted;
    }

    public void clear() {
        watchers.clear();
        changed.clear();
        deleted.clear();
        created.clear();
    }
}
