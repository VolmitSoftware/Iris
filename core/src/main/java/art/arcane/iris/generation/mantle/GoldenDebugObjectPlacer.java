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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;

final class GoldenDebugObjectPlacer implements IObjectPlacer {
    private static final int[] GOLDEN_DEBUG_TARGET = parseGoldenDebugTarget(resolveGoldenDebugSpec());
    private static final boolean GOLDEN_DEBUG = GOLDEN_DEBUG_TARGET != null;

    private final IObjectPlacer delegate;
    private final String tag;

    GoldenDebugObjectPlacer(IObjectPlacer delegate, String tag) {
        this.delegate = delegate;
        this.tag = tag;
    }

    private static String resolveGoldenDebugSpec() {
        String property = System.getProperty("iris.goldendebug");
        if (property != null && !property.isBlank()) {
            return property;
        }
        try {
            java.io.File marker = new java.io.File("plugins/Iris/goldendebug.txt");
            if (marker.isFile()) {
                return java.nio.file.Files.readString(marker.toPath()).trim();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] parseGoldenDebugTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2 && parts.length != 3) {
            return null;
        }
        try {
            int radius = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 0;
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), radius};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isGoldenDebugChunk(int x, int z) {
        return GOLDEN_DEBUG
                && Math.abs(GOLDEN_DEBUG_TARGET[0] - x) <= GOLDEN_DEBUG_TARGET[2]
                && Math.abs(GOLDEN_DEBUG_TARGET[1] - z) <= GOLDEN_DEBUG_TARGET[2];
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        int result = delegate.getHighest(x, z, data);
        IrisLogging.debug("Goldendebug query: tag=" + tag + " getHighest(" + x + "," + z + ")=" + result);
        return result;
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        int result = delegate.getHighest(x, z, data, ignoreFluid);
        IrisLogging.debug("Goldendebug query: tag=" + tag + " getHighest(" + x + "," + z + ",ignoreFluid=" + ignoreFluid + ")=" + result);
        return result;
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState d) {
        delegate.set(x, y, z, d);
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return delegate.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return delegate.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        boolean result = delegate.isCarved(x, y, z);
        IrisLogging.debug("Goldendebug query: tag=" + tag + " isCarved(" + x + "," + y + "," + z + ")=" + result);
        return result;
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return delegate.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        boolean result = delegate.isSolid(x, y, z);
        IrisLogging.debug("Goldendebug query: tag=" + tag + " isSolid(" + x + "," + y + "," + z + ")=" + result);
        return result;
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return delegate.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return delegate.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return delegate.isDebugSmartBore();
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
        delegate.setData(xx, yy, zz, data);
    }

    @Override
    public <T> T getData(int xx, int yy, int zz, Class<T> t) {
        return delegate.getData(xx, yy, zz, t);
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        delegate.setTile(xx, yy, zz, tile);
    }

    @Override
    public Engine getEngine() {
        return delegate.getEngine();
    }
}
