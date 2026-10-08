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

import art.arcane.iris.builtin.prepare.PrepareStages;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.placer.HeightmapObjectPlacer;
import art.arcane.iris.engine.object.prefab.IrisAxisRotationClamp;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;

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
    public void install(StageGraph graph) {
        graph.define(PrepareStages.SLOPE).produce(PrepareModule::slope);
        graph.define(PrepareStages.TRANSFORMS)
                .requires(PrepareStages.SLOPE)
                .produce(PrepareModule::transforms);
    }

    private static IrisObjectPlacement slope(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        IrisObjectPlacement config = placement.config();
        boolean checksSlope = !config.isForcePlace() && !config.getSlopeCondition().isDefault();
        if (!checksSlope && !config.isRotateTowardsSlope()) {
            return config;
        }
        Engine engine = placement.placer().getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires an active Iris engine for slope settings.");
        }
        if (checksSlope && !config.getSlopeCondition().isValid(engine.getComplex().getSlopeStream().get(placement.x(), placement.z()))) {
            context.reject("slope");
            return config;
        }
        return config.isRotateTowardsSlope() ? turnedDownhill(placement.object(), config, engine, placement.x(), placement.z()) : config;
    }

    /** A copy of {@code config} whose Y rotation faces the lowest side of the rectangle around the object. */
    private static IrisObjectPlacement turnedDownhill(IrisObject object, IrisObjectPlacement config, Engine engine, int x, int z) {
        ProceduralStream<Double> heights = engine.getComplex().getHeightStream();
        double north = heights.get(x, z + ((float) object.d) / 2);
        double east = heights.get(x + ((float) object.w) / 2, z);
        double south = heights.get(x, z - ((float) object.d) / 2);
        double west = heights.get(x - ((float) object.w) / 2, z);
        double lowest = Math.min(Math.min(north, east), Math.min(south, west));
        double downhill = lowest == north ? NORTH : lowest == east ? EAST : lowest == south ? SOUTH : WEST;

        IrisObjectRotation requested = config.getRotation();
        double turn = requested.getYAxis().getMin() + downhill;
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setXAxis(requested.getXAxis());
        rotation.setZAxis(requested.getZAxis());
        if (turn == 0) {
            rotation.setYAxis(new IrisAxisRotationClamp(false, false, 0, 0, QUARTER_TURN));
            rotation.setEnabled(requested.canRotateX() || requested.canRotateZ());
        } else {
            rotation.setYAxis(new IrisAxisRotationClamp(true, false, turn, turn, QUARTER_TURN));
            rotation.setEnabled(true);
        }
        IrisObjectPlacement turned = config.toPlacement(config.getPlace().toArray(new String[0]));
        turned.setRotation(rotation);
        return turned;
    }

    private static PreparedPlacement transforms(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        IrisObjectPlacement config = context.output(PrepareStages.SLOPE);
        IrisObject object = placement.object();
        RNG rng = new RNG(context.random().nextLong());
        IObjectPlacer terrain = config.getHeightmap() == null ? placement.placer()
                : new HeightmapObjectPlacer(rng, placement.x(), placement.requestedY(), placement.z(), config, placement.placer());
        int spinX = rng.imax() / SPIN_DIVISOR;
        int spinY = rng.imax() / SPIN_DIVISOR;
        int spinZ = rng.imax() / SPIN_DIVISOR;
        IrisObjectRotation rotation = config.getRotation();
        IrisObjectTranslate translate = config.getTranslate();
        int centerY = object.getCenter().getBlockY();
        int rotatedCenterY = rotation.rotate(new IrisBlockVector(0, centerY, 0), spinX, spinY, spinZ).getBlockY();
        int translatedCenterY = translate.translate(new IrisBlockVector(0, centerY, 0), rotation, spinX, spinY, spinZ).getBlockY();
        ObjectSpinKernel spin = new ObjectSpinKernel(rotation, spinX, spinY, spinZ);
        IrisBlockVector translation = translate.canTranslate()
                ? rotation.rotate(new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ()), spinX, spinY, spinZ)
                : null;
        int randomY = randomHeightOffset(translate.getYRandom(), rng);
        int warpMargin = config.getWarp().isFlat() ? 0 : (int) Math.ceil(Math.abs(config.getWarp().getMultiplier()) / 2D);
        TransformedBounds bounds = transformedBounds(object, spin, translation, config.getMode() == ObjectPlaceMode.CEILING_HANG, warpMargin);
        CNG surfaceWarp = config.getWarp().isFlat() ? null : config.getSurfaceWarp(rng, object.getLoader());
        return new PreparedPlacement(config, terrain, spin, spinX, spinY, spinZ, translation, rotatedCenterY,
                translatedCenterY, randomY, warpMargin, bounds, surfaceWarp);
    }

    /** Within {@code range} of zero, on the side its sign gives. */
    private static int randomHeightOffset(int range, RNG rng) {
        if (range > 0) {
            return rng.i(0, range);
        }
        return range < 0 ? rng.i(range, 0) : 0;
    }

    private static TransformedBounds transformedBounds(IrisObject object, ObjectSpinKernel spin, IrisBlockVector translation,
                                                       boolean ceilingHang, int margin) {
        int[] sourceX = {-object.getCenter().getBlockX(), object.getW() - object.getCenter().getBlockX() - 1};
        int[] sourceY = {-object.getCenter().getBlockY(), object.getH() - object.getCenter().getBlockY() - 1};
        int[] sourceZ = {-object.getCenter().getBlockZ(), object.getD() - object.getCenter().getBlockZ() - 1};
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
