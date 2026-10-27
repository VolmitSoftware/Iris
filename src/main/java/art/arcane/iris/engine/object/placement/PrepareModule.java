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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.placement.BlockTraits;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.ObjectShape;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.placement.SurfaceWarp;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectSpinKernel;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import art.arcane.iris.builtin.prepare.TransformedBounds;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.stage.PlacementStageContext;

import java.util.Optional;

/**
 * Module {@code iris:prepare}: checks the slope, turns the object downhill when asked, and chooses the
 * placement's rotation, translation and random height offset.
 */
public final class PrepareModule implements IrisModule {
    private static final int SPIN_DIVISOR = 1000;
    private static final double NORTH = 0;
    private static final double EAST = 90;
    private static final double SOUTH = 180;
    private static final double WEST = 270;
    private static final double QUARTER_TURN = 90;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:prepare", "Placement preparation", PlacementModules.VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var graph = setup.stages();
        graph.define(IrisStages.Prepare.SLOPE).produce(PrepareModule::slope);
        graph.define(IrisStages.Prepare.TRANSFORMS)
                .requires(IrisStages.Prepare.SLOPE)
                .produce(PrepareModule::transforms);
    }

    private static ObjectRotation slope(PlacementStageContext context) {
        ObjectPlacement placement = context.extension(ObjectPlacement.class).orElseThrow();
        PlacementSettings settings = placement.settings();
        ObjectRotation requested = settings.rotation();
        boolean checksSlope = !settings.forcePlace() && !settings.slopeCondition().isDefault();
        if (!checksSlope && !settings.rotateTowardsSlope()) {
            return requested;
        }
        if (!placement.terrain().hasWorld()) {
            throw new IllegalStateException("Object placement requires an active Iris engine for slope settings.");
        }
        if (checksSlope && !settings.slopeCondition().isValid(placement.terrain().slopeAt(placement.x(), placement.z()))) {
            context.reject("slope");
            return null;
        }
        return settings.rotateTowardsSlope() ? turnedDownhill(placement, requested) : requested;
    }

    /** A copy of {@code requested} whose Y rotation faces the lowest side of the rectangle around the object. */
    private static ObjectRotation turnedDownhill(ObjectPlacement placement, ObjectRotation requested) {
        PlacementTerrain terrain = placement.terrain();
        ObjectShape object = placement.object();
        int x = placement.x();
        int z = placement.z();
        double north = terrain.heightAt(x, z + ((float) object.depth()) / 2);
        double east = terrain.heightAt(x + ((float) object.width()) / 2, z);
        double south = terrain.heightAt(x, z - ((float) object.depth()) / 2);
        double west = terrain.heightAt(x - ((float) object.width()) / 2, z);
        double lowest = Math.min(Math.min(north, east), Math.min(south, west));
        double downhill = lowest == north ? NORTH : lowest == east ? EAST : lowest == south ? SOUTH : WEST;

        double turn = requested.yAxis().min() + downhill;
        if (turn == 0) {
            return ObjectRotation.of(requested.canRotateX() || requested.canRotateZ(), requested.xAxis(),
                    AxisRotationClamp.of(false, false, 0, 0, QUARTER_TURN), requested.zAxis());
        }
        return ObjectRotation.of(true, requested.xAxis(), AxisRotationClamp.of(true, false, turn, turn, QUARTER_TURN),
                requested.zAxis());
    }

    private static PreparedPlacement transforms(PlacementStageContext context) {
        ObjectPlacement placement = context.extension(ObjectPlacement.class).orElseThrow();
        PlacementSettings settings = placement.settings();
        ObjectRotation rotation = context.output(IrisStages.Prepare.SLOPE);
        ObjectShape object = placement.object();
        RNG rng = new RNG(context.random().nextLong());
        PlacementTerrain terrain = placement.terrainFor(rng);
        int spinX = rng.imax() / SPIN_DIVISOR;
        int spinY = rng.imax() / SPIN_DIVISOR;
        int spinZ = rng.imax() / SPIN_DIVISOR;
        ObjectTranslate translate = settings.translate();
        ObjectSpinKernel spin = new ObjectSpinKernel(rotation, spinX, spinY, spinZ);
        IrisBlockVector rotatedCenter = new IrisBlockVector(0, object.centerY(), 0);
        spin.rotate(rotatedCenter);
        IrisBlockVector translation = translate.canTranslate() ? rotated(spin, translate) : null;
        IrisBlockVector translatedCenter = new IrisBlockVector(0, object.centerY(), 0);
        if (translation != null) {
            translatedCenter = (IrisBlockVector) translatedCenter.clone().add(translation);
        }
        int randomY = randomHeightOffset(translate.yRandom(), rng);
        Optional<SurfaceWarp> surfaceWarp = placement.surfaceWarp(rng);
        int warpMargin = surfaceWarp.map(warp -> (int) Math.ceil(Math.abs(warp.multiplier()) / 2D)).orElse(0);
        TransformedBounds bounds = transformedBounds(object, spin, translation, settings.mode().placeMode().has(PlaceTrait.UPSIDE_DOWN), warpMargin);
        return new PreparedPlacement(placement, rotation, new BlockTraits(placement), terrain, spin,
                spinX, spinY, spinZ, translation, rotatedCenter.getBlockY(), translatedCenter.getBlockY(), randomY,
                warpMargin, bounds, surfaceWarp.orElse(null));
    }

    private static IrisBlockVector rotated(ObjectSpinKernel spin, ObjectTranslate translate) {
        IrisBlockVector shift = new IrisBlockVector(translate.x(), translate.y(), translate.z());
        spin.rotate(shift);
        return shift;
    }

    /** Within {@code range} of zero, on the side its sign gives. */
    private static int randomHeightOffset(int range, RNG rng) {
        if (range > 0) {
            return rng.i(0, range);
        }
        return range < 0 ? rng.i(range, 0) : 0;
    }

    private static TransformedBounds transformedBounds(ObjectShape object, ObjectSpinKernel spin, IrisBlockVector translation,
                                                       boolean ceilingHang, int margin) {
        int[] sourceX = {-object.centerX(), object.width() - object.centerX() - 1};
        int[] sourceY = {-object.centerY(), object.height() - object.centerY() - 1};
        int[] sourceZ = {-object.centerZ(), object.depth() - object.centerZ() - 1};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : sourceX) {
            for (int y : sourceY) {
                for (int z : sourceZ) {
                    IrisBlockVector corner = new IrisBlockVector(x, y, z);
                    spin.rotate(corner);
                    if (ceilingHang) {
                        corner.setY(-corner.getBlockY());
                    }
                    if (translation != null) {
                        corner.add(translation);
                    }
                    int cornerX = (int) Math.round(corner.getX());
                    int cornerY = (int) Math.round(corner.getY());
                    int cornerZ = (int) Math.round(corner.getZ());
                    minX = Math.min(minX, cornerX);
                    maxX = Math.max(maxX, cornerX);
                    minY = Math.min(minY, cornerY);
                    maxY = Math.max(maxY, cornerY);
                    minZ = Math.min(minZ, cornerZ);
                    maxZ = Math.max(maxZ, cornerZ);
                }
            }
        }
        return new TransformedBounds(minX - margin, maxX + margin, minY, maxY, minZ - margin, maxZ + margin);
    }
}
