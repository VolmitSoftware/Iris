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
import art.arcane.iris.generation.biome.FloatingIslandSample;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.Nullable;

public final class IslandObjectPlacer implements IObjectPlacer {
    private static final int OVERHANG_RADIUS = 2;
    private static final int OVERHANG_HEIGHT_TOLERANCE = 4;

    private final MantleWriter wrapped;
    private final SampleProvider samples;
    private final IrisFloatingChildBiomes entry;
    private final int anchorY;
    private final AnchorFace face;

    private IslandObjectPlacer(MantleWriter wrapped, AnchorSettings settings) {
        this.wrapped = wrapped;
        this.samples = settings.samples();
        this.entry = settings.entry();
        this.anchorY = settings.anchorY();
        this.face = settings.face();
    }

    public static IslandObjectPlacer top(MantleWriter wrapped, SampleProvider samples,
                                         IrisFloatingChildBiomes entry, int anchorY) {
        return new IslandObjectPlacer(wrapped, new AnchorSettings(samples, entry, anchorY, AnchorFace.TOP));
    }

    public static IslandObjectPlacer bottom(MantleWriter wrapped, SampleProvider samples,
                                            IrisFloatingChildBiomes entry, int anchorY) {
        return new IslandObjectPlacer(wrapped, new AnchorSettings(samples, entry, anchorY, AnchorFace.BOTTOM));
    }

    public boolean canWriteObjectBlock(int x, int y, int z) {
        return !shouldSkipAirColumn(x, y, z);
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        FloatingIslandSample sample = samples.sample(x, z);
        if (face == AnchorFace.TOP) {
            return sample == null ? anchorY : sample.topY();
        }
        if (sample == null) {
            return anchorY;
        }
        int bottomY = sample.bottomY();
        return bottomY < 0 ? anchorY : bottomY;
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return getHighest(x, z, data);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return false;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        FloatingIslandSample sample = samples.sample(x, z);
        if (sample != null) {
            int index = y - sample.islandBaseY;
            if (index >= 0 && index < sample.solidMask.length) {
                return sample.solidMask[index];
            }
            return false;
        }
        return wrapped.isSolid(x, y, z);
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        FloatingIslandSample sample = samples.sample(x, z);
        if (sample != null && y >= sample.islandBaseY && y < sample.islandBaseY + sample.solidMask.length) {
            return !sample.solidMask[y - sample.islandBaseY];
        }
        return wrapped.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return samples.sample(x, z) == null ? wrapped.isSurfaceSolid(x, y, z) : isSolid(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState state) {
        if (!shouldSkipAirColumn(x, y, z)) {
            wrapped.set(x, y, z, state);
        }
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return wrapped.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return wrapped.isPreventingDecay();
    }

    @Override
    public int getFluidHeight() {
        return wrapped.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return wrapped.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (!shouldSkipAirColumn(x, y, z)) {
            wrapped.setTile(x, y, z, tile);
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        if (!shouldSkipAirColumn(x, y, z)) {
            wrapped.setData(x, y, z, data);
        }
    }

    @Override
    public <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        return wrapped.getData(x, y, z, type);
    }

    @Override
    public Engine getEngine() {
        return wrapped == null ? null : wrapped.getEngine();
    }

    static boolean matchesAnchor(FloatingIslandSample sample, IrisFloatingChildBiomes entry, AnchorFace face) {
        if (sample == null) {
            return false;
        }
        return face == AnchorFace.TOP ? sample.entry == entry : sample.bottomEntry() == entry;
    }

    private boolean shouldSkipAirColumn(int x, int y, int z) {
        Engine engine = getEngine();
        if (engine != null && (y < 0 || y >= engine.getHeight())) {
            return true;
        }

        FloatingIslandSample sample = samples.sample(x, z);
        if (matchesAnchor(sample, entry, face) && isNearAnchorHeight(sample)) {
            return face == AnchorFace.BOTTOM && y >= anchorY;
        }
        if (face == AnchorFace.TOP && y <= anchorY) {
            return true;
        }
        if (face == AnchorFace.BOTTOM && y >= anchorY) {
            return true;
        }
        return !hasNearbySupport(x, z);
    }

    private boolean hasNearbySupport(int x, int z) {
        for (int dz = -OVERHANG_RADIUS; dz <= OVERHANG_RADIUS; dz++) {
            for (int dx = -OVERHANG_RADIUS; dx <= OVERHANG_RADIUS; dx++) {
                FloatingIslandSample sample = samples.sample(x + dx, z + dz);
                if (matchesAnchor(sample, entry, face) && isNearAnchorHeight(sample)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearAnchorHeight(FloatingIslandSample sample) {
        int faceY = face == AnchorFace.TOP ? sample.topY() : sample.bottomY();
        return faceY >= 0 && Math.abs(faceY - anchorY) <= OVERHANG_HEIGHT_TOLERANCE;
    }

    public enum AnchorFace {
        TOP,
        BOTTOM
    }

    @FunctionalInterface
    public interface SampleProvider {
        @Nullable FloatingIslandSample sample(int x, int z);
    }

    private record AnchorSettings(
            SampleProvider samples,
            IrisFloatingChildBiomes entry,
            int anchorY,
            AnchorFace face
    ) {
    }
}
