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

package art.arcane.iris.studio.object;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.generation.geometry.IrisBlockVector;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ObjectStudioLayout {
    public static final int FLOOR_Y = 64;
    private static final int DEFAULT_ROW_WIDTH_CAP = 160;

    private final int padding;
    private final int rowWidthCap;
    private final List<GridCell> cells;
    private final Map<String, GridCell> byKey;

    private ObjectStudioLayout(int padding, int rowWidthCap, List<GridCell> cells) {
        this.padding = padding;
        this.rowWidthCap = rowWidthCap;
        this.cells = Collections.unmodifiableList(cells);
        Map<String, GridCell> index = new LinkedHashMap<>();
        for (GridCell cell : cells) {
            index.putIfAbsent(cell.key(), cell);
            index.putIfAbsent(cell.pack() + "/" + cell.key(), cell);
        }
        this.byKey = Collections.unmodifiableMap(index);
    }

    public static ObjectStudioLayout build(IrisData data, int padding) {
        Map<String, IrisData> sources = new LinkedHashMap<>();
        sources.put(data.getDataFolder().getName(), data);
        return build(sources, padding, DEFAULT_ROW_WIDTH_CAP);
    }

    public static ObjectStudioLayout build(Map<String, IrisData> sources, int padding) {
        return build(sources, padding, DEFAULT_ROW_WIDTH_CAP);
    }

    public static ObjectStudioLayout build(Map<String, IrisData> sources, int padding, int rowWidthCap) {
        List<PackEntry> entries = new ArrayList<>();
        for (Map.Entry<String, IrisData> entry : sources.entrySet()) {
            String packName = entry.getKey();
            IrisData data = entry.getValue();
            String[] possible = data.getObjectLoader().getPossibleKeys();
            if (possible == null) continue;
            List<String> sorted = new ArrayList<>(Arrays.asList(possible));
            Collections.sort(sorted);
            for (String key : sorted) {
                entries.add(new PackEntry(packName, data, key));
            }
        }
        entries.sort((a, b) -> {
            int c = a.pack.compareTo(b.pack);
            if (c != 0) return c;
            return a.key.compareTo(b.key);
        });

        List<GridCell> packed = new ArrayList<>(entries.size());
        int cursorX = 0;
        int rowZ = 0;
        int rowMaxDepth = 0;

        for (PackEntry entry : entries) {
            File file = entry.data.getObjectLoader().findFile(entry.key);
            if (file == null) {
                continue;
            }
            IrisBlockVector size;
            try {
                size = IrisObject.sampleSize(file);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                continue;
            }

            int w = Math.max(1, size.getBlockX());
            int h = Math.max(1, size.getBlockY());
            int d = Math.max(1, size.getBlockZ());

            int cellWidth = w + padding * 2;
            int cellDepth = d + padding * 2;

            if (cursorX > 0 && cursorX + cellWidth > rowWidthCap) {
                rowZ += rowMaxDepth;
                cursorX = 0;
                rowMaxDepth = 0;
            }

            int originX = cursorX + padding;
            int originZ = rowZ + padding;
            int originY = FLOOR_Y + 1;

            packed.add(new GridCell(entry.pack, entry.key, originX, originY, originZ, w, h, d));

            cursorX += cellWidth;
            rowMaxDepth = Math.max(rowMaxDepth, cellDepth);
        }

        return new ObjectStudioLayout(padding, rowWidthCap, packed);
    }

    public static ObjectStudioLayout load(File file, Map<String, IrisData> sources, int padding) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            String raw = Files.readString(file.toPath());
            JSONObject root = new JSONObject(raw);
            int storedPadding = root.optInt("padding", padding);
            int storedCap = root.optInt("rowWidthCap", DEFAULT_ROW_WIDTH_CAP);
            JSONArray arr = root.getJSONArray("cells");

            List<GridCell> stored = new ArrayList<>();
            Set<String> storedIds = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                String pack = c.optString("pack", null);
                if (pack == null || pack.isEmpty()) {
                    return null;
                }
                GridCell cell = new GridCell(
                        pack,
                        c.getString("key"),
                        c.getInt("x"),
                        c.getInt("y"),
                        c.getInt("z"),
                        c.getInt("w"),
                        c.getInt("h"),
                        c.getInt("d")
                );
                IrisData source = sources.get(pack);
                File objectFile = source == null ? null : source.getObjectLoader().findFile(cell.key());
                if (objectFile == null) {
                    return null;
                }
                IrisBlockVector size = IrisObject.sampleSize(objectFile);
                if (cell.w() != Math.max(1, size.getBlockX())
                        || cell.h() != Math.max(1, size.getBlockY())
                        || cell.d() != Math.max(1, size.getBlockZ())) {
                    return null;
                }
                stored.add(cell);
                storedIds.add(cell.pack() + "/" + cell.key());
            }

            Set<String> liveIds = new HashSet<>();
            for (Map.Entry<String, IrisData> entry : sources.entrySet()) {
                String[] live = entry.getValue().getObjectLoader().getPossibleKeys();
                if (live == null) continue;
                for (String k : live) {
                    liveIds.add(entry.getKey() + "/" + k);
                }
            }

            if (liveIds.size() == storedIds.size() && liveIds.containsAll(storedIds)) {
                return new ObjectStudioLayout(storedPadding, storedCap, stored);
            }
            return null;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    public void save(File file) {
        if (file == null) {
            return;
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            JSONObject root = new JSONObject();
            root.put("padding", padding);
            root.put("rowWidthCap", rowWidthCap);
            JSONArray arr = new JSONArray();
            for (GridCell cell : cells) {
                JSONObject c = new JSONObject();
                c.put("pack", cell.pack());
                c.put("key", cell.key());
                c.put("x", cell.originX());
                c.put("y", cell.originY());
                c.put("z", cell.originZ());
                c.put("w", cell.w());
                c.put("h", cell.h());
                c.put("d", cell.d());
                arr.put(c);
            }
            root.put("cells", arr);
            Files.writeString(file.toPath(), root.toString(2));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    public int padding() {
        return padding;
    }

    public ObjectStudioLayout atFloor(int floorY) {
        int originY = Math.addExact(floorY, 1);
        boolean unchanged = true;
        for (GridCell cell : cells) {
            if (cell.originY() != originY) {
                unchanged = false;
                break;
            }
        }
        if (unchanged) {
            return this;
        }
        List<GridCell> placed = new ArrayList<>(cells.size());
        for (GridCell cell : cells) {
            placed.add(new GridCell(cell.pack(), cell.key(), cell.originX(), originY, cell.originZ(),
                    cell.w(), cell.h(), cell.d()));
        }
        return new ObjectStudioLayout(padding, rowWidthCap, placed);
    }

    public List<GridCell> cells() {
        return cells;
    }

    public GridCell findAt(int worldX, int worldZ) {
        for (GridCell cell : cells) {
            if (worldX >= cell.originX() && worldX < cell.originX() + cell.w()
                    && worldZ >= cell.originZ() && worldZ < cell.originZ() + cell.d()) {
                return cell;
            }
        }
        return null;
    }

    public GridCell get(String key) {
        return byKey.get(key);
    }

    private record PackEntry(String pack, IrisData data, String key) {
    }

    public record GridCell(String pack, String key, int originX, int originY, int originZ, int w, int h, int d) {
        public int chunkMinX() {
            return originX >> 4;
        }

        public int chunkMaxX() {
            return (originX + w - 1) >> 4;
        }

        public int chunkMinZ() {
            return originZ >> 4;
        }

        public int chunkMaxZ() {
            return (originZ + d - 1) >> 4;
        }
    }
}
