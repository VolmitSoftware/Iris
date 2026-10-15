/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.objects.ObjectSink;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;

/** An {@link ObjectSink}, which sees blocks as IrisApi's, as the placer an Iris object places into. */
final class SinkPlacer implements IObjectPlacer {
    private final Engine engine;
    private final ObjectSink sink;
    private final PlatformBlockStates states = new PlatformBlockStates(key -> IrisPlatforms.get().registries().block(key));

    SinkPlacer(Engine engine, ObjectSink sink) {
        this.engine = engine;
        this.sink = sink;
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return sink.highest(x, z, false);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return sink.highest(x, z, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState state) {
        sink.set(x, y, z, states.toApi(state));
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return states.toPlatform(sink.get(x, y, z));
    }

    @Override
    public boolean isPreventingDecay() {
        return true;
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return false;
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return isSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return get(x, y, z).isSolid();
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return false;
    }

    @Override
    public int getFluidHeight() {
        return sink.fluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return false;
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
    }

    @Override
    public <T> T getData(int xx, int yy, int zz, Class<T> t) {
        return null;
    }

    @Override
    public Engine getEngine() {
        return engine;
    }
}
