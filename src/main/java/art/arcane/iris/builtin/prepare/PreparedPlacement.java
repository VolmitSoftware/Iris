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

package art.arcane.iris.builtin.prepare;

import art.arcane.iris.builtin.anchor.PlaceMode;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.placement.BlockTraits;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.placement.SurfaceWarp;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;

/**
 * What one placement chose before it is anchored: the settings it uses, the rotation it ended up with, the terrain
 * that answers its height, water and carving questions, its random spin and height offsets, and the bounds of the
 * transformed object around its anchor. Object blocks are transformed by rotating them, flipping them for a ceiling
 * hang and translating them.
 */
public final class PreparedPlacement {
    private final ObjectPlacement placement;
    private final PlacementSettings settings;
    private final PlaceMode mode;
    private final ObjectRotation rotation;
    private final BlockTraits traits;
    private final PlacementTerrain terrain;
    private final ObjectSpinKernel spin;
    private final int spinX;
    private final int spinY;
    private final int spinZ;
    private final IrisBlockVector translation;
    private final int rotatedCenterY;
    private final int translatedCenterY;
    private final int randomY;
    private final int warpMargin;
    private final TransformedBounds bounds;
    private final SurfaceWarp surfaceWarp;

    /** Everything the preparation worked out; {@code translation} and {@code surfaceWarp} are null when the placement has none. */
    public PreparedPlacement(ObjectPlacement placement, ObjectRotation rotation, BlockTraits traits, PlacementTerrain terrain,
                             ObjectSpinKernel spin, int spinX, int spinY, int spinZ, IrisBlockVector translation, int rotatedCenterY,
                             int translatedCenterY, int randomY, int warpMargin, TransformedBounds bounds, SurfaceWarp surfaceWarp) {
        this.placement = placement;
        this.settings = placement.settings();
        this.mode = settings.mode().placeMode();
        this.rotation = rotation;
        this.traits = traits;
        this.terrain = terrain;
        this.spin = spin;
        this.spinX = spinX;
        this.spinY = spinY;
        this.spinZ = spinZ;
        this.translation = translation;
        this.rotatedCenterY = rotatedCenterY;
        this.translatedCenterY = translatedCenterY;
        this.randomY = randomY;
        this.warpMargin = warpMargin;
        this.bounds = bounds;
        this.surfaceWarp = surfaceWarp;
    }

    /** The settings the placement was asked for; the rotation may differ, see {@link #rotation()}. */
    public PlacementSettings settings() {
        return settings;
    }

    /** The rotation the placement uses, which may differ from the requested one. */
    public ObjectRotation rotation() {
        return rotation;
    }

    /** The traits of the block states this placement handles. */
    public BlockTraits traits() {
        return traits;
    }

    /** Answers height, water and carving questions; a heightmap placement answers heights from its noise. */
    public PlacementTerrain terrain() {
        return terrain;
    }

    public PlaceMode mode() {
        return mode;
    }

    public int spinX() {
        return spinX;
    }

    public int spinY() {
        return spinY;
    }

    public int spinZ() {
        return spinZ;
    }

    /** The rotated translation, or null when the placement does not translate. */
    public IrisBlockVector translation() {
        return translation;
    }

    /** The height of the object's center after rotation. */
    public int rotatedCenterY() {
        return rotatedCenterY;
    }

    /** The height of the object's center after translation. */
    public int translatedCenterY() {
        return translatedCenterY;
    }

    /** The random height offset added once the anchor is final. */
    public int randomY() {
        return randomY;
    }

    public int warpMargin() {
        return warpMargin;
    }

    public TransformedBounds bounds() {
        return bounds;
    }

    public boolean isWarped() {
        return surfaceWarp != null;
    }

    /** Moves a world column by the surface warp, along X for {@code x} and along Z for the swapped call. */
    public int warpOffset(double along, double y, double across) {
        double half = surfaceWarp.multiplier() / 2D;
        return (int) surfaceWarp.fit(-half, half, along, y, across);
    }

    /** An object block's offset from the anchor after this placement's transforms. */
    public BlockPosition transform(BlockPosition local) {
        IrisBlockVector offset = new IrisBlockVector(local.x(), local.y(), local.z());
        spin.rotate(offset);
        if (mode().has(PlaceTrait.UPSIDE_DOWN)) {
            offset.setY(-offset.getBlockY());
        }
        if (translation != null) {
            offset.add(translation);
        }
        return new BlockPosition((int) Math.round(offset.getX()), (int) Math.round(offset.getY()), (int) Math.round(offset.getZ()));
    }

    /** A block state turned with the object, so stairs and logs face the way the object does. */
    public BlockState rotate(BlockState state) {
        return placement.rotate(state, rotation, spinX, spinY, spinZ);
    }
}
