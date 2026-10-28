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

package art.arcane.iris.modules.placement;

import art.arcane.iris.modules.blocks.SmartBoreDebugSettings;
import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.placement.StructureVolumes;
import com.volmit.resource.ResourceId;
import com.volmit.world.TerrainSampler;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * What a placement stage asks of the terrain and the world around it: heights, carving and water at a column, the
 * terrain's shape, and the world's own facts. A placement made without a world ({@link #hasWorld()} is false) answers
 * its terrain questions from the placement's terrain alone.
 */
public final class StageTerrain {
    private static final String PROCEDURAL_PREFIX = "procedural/";

    private final PlacementTerrain terrain;
    private final PlacementContext context;
    private final IntBinaryOperator heightmap;

    private StageTerrain(PlacementTerrain terrain, PlacementContext context, IntBinaryOperator heightmap) {
        this.terrain = terrain;
        this.context = context;
        this.heightmap = heightmap;
    }

    static StageTerrain of(PlacementContext context) {
        return new StageTerrain(context.placementTerrain(), context, null);
    }

    /** This terrain with its heights from {@code heightmap}, for a placement that sits on a virtual heightmap. */
    StageTerrain withHeights(IntBinaryOperator heightmap) {
        return new StageTerrain(terrain, context, heightmap);
    }

    /** The surface height of the column, counting fluid unless {@code ignoreFluid}. */
    public int highest(int x, int z, boolean ignoreFluid) {
        return heightmap != null ? heightmap.applyAsInt(x, z) : terrain.highest(x, z, ignoreFluid);
    }

    public boolean isCarved(int x, int y, int z) {
        return terrain.isCarved(x, y, z);
    }

    /** Whether the block is real, solid ground; the height field alone cannot say at mantle time. */
    public boolean isSurfaceSolid(int x, int y, int z) {
        return terrain.isSurfaceSolid(x, y, z);
    }

    public boolean isUnderwater(int x, int z) {
        return terrain.isUnderwater(x, z);
    }

    /** The height up to which the dimension is filled with fluid. */
    public int fluidHeight() {
        return terrain.fluidHeight();
    }

    /** Whether leaves the placement writes never decay. */
    public boolean preventsDecay() {
        return terrain.preventsDecay();
    }

    /** Whether bored air is written as a visible block, to see where smart boring bored. */
    public boolean debugsSmartBore(int x, int z) {
        return context.settings().at(SmartBoreDebugSettings.class, x, z).dimension().map(SmartBoreDebugSettings::show).orElse(false);
    }

    /** Whether the placement is made in a world the host generates, rather than a tool's world. */
    public boolean hasWorld() {
        return context.world().running().isPresent();
    }

    /** The terrain's slope at the column, as the dimension's slope field gives it. */
    public double slopeAt(int x, int z) {
        return sampler().column(x, z).slope();
    }

    /** The terrain height at a position between columns: the height of the column the position lies in, counted toward zero. */
    public double heightAt(double x, double z) {
        return sampler().surfaceY((int) x, (int) z);
    }

    /** The dimension's height in blocks. */
    public int worldHeight() {
        return sampler().bounds().maxY();
    }

    public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) {
        return terrain.structureVolumes(minX, minZ, maxX, maxZ);
    }

    /** The pack object placed at the block; empty when none was, or when a procedural object was. */
    public Optional<ResourceId> objectAt(int x, int y, int z) {
        return terrain.placementAt(x, y, z)
                .map(PlacementRecord::object)
                .filter(object -> !object.path().startsWith(PROCEDURAL_PREFIX));
    }

    private TerrainSampler sampler() {
        return context.terrain();
    }
}
