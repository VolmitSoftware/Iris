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

import art.arcane.iris.builtin.placement.CaveSpace;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.ObjectShape;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.placement.PlaneNoise;
import art.arcane.iris.builtin.placement.SurfaceWarp;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.placer.HeightmapObjectPlacer;
import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisPlacementForm;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.BlockStateRotation;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.SimplexNoise;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.resource.PackResources;
import com.volmit.geometry.BlockPosition;

import java.util.Optional;

/**
 * Iris's own inputs of the object placement being run: the placement settings, object, placer and cave that IrisApi
 * does not describe, answered to Iris's built-in placement stages as an {@link ObjectPlacement}, the extension of the
 * placement invocation. Pack modules cannot see this class.
 */
public final class IrisPlacement implements ObjectPlacement {
    private static final int NOT_ANCHORED = Integer.MIN_VALUE;

    private final IrisObject object;
    private final IrisPlacementForm form;
    private final IObjectPlacer placer;
    private final CarveResult cave;
    private final int x;
    private final int requestedY;
    private final int z;
    private final IrisData data;
    private final PlatformBlockStates states;
    private final ObjectPlacerEdit edit;
    private final IrisObjectShape shape;
    private final IrisPlacementSettings settings;
    private final IrisPlacementTerrain terrain;
    private int anchorY = NOT_ANCHORED;

    IrisPlacement(IrisObject object, art.arcane.iris.builtin.objects.ObjectPlacement config, IrisPlacementForm form,
                  IObjectPlacer placer, CarveResult cave, int x, int requestedY, int z, IrisData data, PlatformBlockStates states,
                  ObjectPlacerEdit edit) {
        this.object = object;
        this.form = form;
        this.placer = placer;
        this.cave = cave;
        this.x = x;
        this.requestedY = requestedY;
        this.z = z;
        this.data = data;
        this.states = states;
        this.edit = edit;
        this.shape = new IrisObjectShape(object, data.getPackResources());
        this.settings = new IrisPlacementSettings(config, data);
        this.terrain = new IrisPlacementTerrain(placer, object.getLoader(), data);
    }


    @Override
    public ObjectShape object() {
        return shape;
    }

    @Override
    public PlacementSettings settings() {
        return settings;
    }

    @Override
    public int x() {
        return x;
    }

    /** The height the caller asked for, negative when the placement samples it. */
    @Override
    public int requestedY() {
        return requestedY;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public Optional<CaveSpace> cave() {
        return Optional.ofNullable(cave).map(space -> new CaveSpace(space.getSurface(), space.getHeight()));
    }

    @Override
    public PlacementTerrain terrain() {
        return terrain;
    }

    @Override
    public PlacementTerrain terrainFor(RNG rng) {
        if (form.heightmap() == null) {
            return terrain;
        }
        return new IrisPlacementTerrain(new HeightmapObjectPlacer(rng, x, requestedY, z, form.heightmap(), placer), object.getLoader(), data);
    }

    @Override
    public Optional<SurfaceWarp> surfaceWarp(RNG rng) {
        if (form.warp().isFlat()) {
            return Optional.empty();
        }
        IrisData loader = object.getLoader();
        CNG warp = form.surfaceWarp(rng, loader, loader.getEngine());
        double multiplier = form.warp().getMultiplier();
        return Optional.of(new SurfaceWarp() {
            @Override
            public double multiplier() {
                return multiplier;
            }

            @Override
            public double fit(double min, double max, double x, double y, double z) {
                return warp.fitDouble(min, max, x, y, z);
            }
        });
    }

    @Override
    public PlaneNoise simplex(long seed) {
        SimplexNoise noise = new SimplexNoise(seed);
        return noise::noiseSigned;
    }

    @Override
    public BlockState rotate(BlockState state, ObjectRotation rotation, int spinX, int spinY, int spinZ) {
        return states.toApi(BlockStateRotation.rotate(rotation, states.toPlatform(state), spinX, spinY, spinZ));
    }

    @Override
    public BlockState merge(BlockState base, BlockState update) {
        return states.toApi(BlockDataMergeSupport.merge(states.toPlatform(base), states.toPlatform(update)));
    }

    @Override
    public boolean matches(BlockState state, BlockState other) {
        return states.toPlatform(state).matches(states.toPlatform(other));
    }

    @Override
    public boolean hasTrait(BlockState state, BlockTrait trait) {
        return IrisBlockRegistry.hasTrait(states.toPlatform(state), trait);
    }

    @Override
    public PackResources resources() {
        return object.getLoader().getPackResources();
    }

    /** Reports a buffered write as one of the object's own blocks, which Iris's placement listener hears after the commit. */
    @Override
    public void objectBlockWritten(BlockPosition position) {
        edit.recordObjectBlock(position);
    }

    /** Reports the height the object was anchored at, which the placement returns to its caller. */
    @Override
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
