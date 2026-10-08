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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import com.volmit.world.BlockPosition;

import java.util.function.Supplier;

/**
 * Iris's own inputs of the object placement being run: the placement settings, object, placer and cave
 * that IrisApi does not describe. Iris's built-in placement stages read them through {@link #current()},
 * bound on the placing thread while the placement graph runs; pack modules cannot see this class.
 */
public final class IrisPlacement {
    private static final ScopedValue<IrisPlacement> CURRENT = ScopedValue.newInstance();
    private static final int NOT_ANCHORED = Integer.MIN_VALUE;

    private final IrisObject object;
    private final IrisObjectPlacement config;
    private final IObjectPlacer placer;
    private final CarveResult cave;
    private final int x;
    private final int requestedY;
    private final int z;
    private final IrisData data;
    private final PlatformBlockStates states;
    private final ObjectPlacerEdit edit;
    private int anchorY = NOT_ANCHORED;

    IrisPlacement(IrisObject object, IrisObjectPlacement config, IObjectPlacer placer, CarveResult cave,
                  int x, int requestedY, int z, IrisData data, PlatformBlockStates states, ObjectPlacerEdit edit) {
        this.object = object;
        this.config = config;
        this.placer = placer;
        this.cave = cave;
        this.x = x;
        this.requestedY = requestedY;
        this.z = z;
        this.data = data;
        this.states = states;
        this.edit = edit;
    }

    /** The placement running on this thread. */
    public static IrisPlacement current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No Iris object placement is running on this thread");
        }
        return CURRENT.get();
    }

    <T> T runBound(Supplier<T> placement) {
        return ScopedValue.where(CURRENT, this).call(placement::get);
    }

    public IrisObject object() {
        return object;
    }

    /** The settings as the caller passed them; the built-in preparation may derive others for this placement. */
    public IrisObjectPlacement config() {
        return config;
    }

    public IObjectPlacer placer() {
        return placer;
    }

    /** The cave the object is placed in, or null outside caves. */
    public CarveResult cave() {
        return cave;
    }

    public int x() {
        return x;
    }

    /** The height the caller asked for, negative when the placement samples it. */
    public int requestedY() {
        return requestedY;
    }

    public int z() {
        return z;
    }

    public IrisData data() {
        return data;
    }

    /** Converts block states for this placement. */
    public PlatformBlockStates states() {
        return states;
    }

    /** Reports a buffered write as one of the object's own blocks, which Iris's placement listener hears after the commit. */
    public void objectBlockWritten(BlockPosition position) {
        edit.recordObjectBlock(position);
    }

    /** Reports the height the object was anchored at, which the placement returns to its caller. */
    public void anchoredAt(int y) {
        anchorY = y;
    }

    int anchorY() {
        if (anchorY == NOT_ANCHORED) {
            throw new IllegalStateException("The placement graph placed " + object.getLoadKey() + " without anchoring it");
        }
        return anchorY;
    }
}
