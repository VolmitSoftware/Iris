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

import art.arcane.iris.builtin.anchor.PlaceMode;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.iris.util.project.noise.CNG;
import com.volmit.world.BlockPosition;

/**
 * What one placement chose before it is anchored: the settings it uses, the placer that answers its
 * terrain questions, its random rotation and height offsets, and the bounds of the transformed object
 * around its anchor. Object blocks are transformed by rotating them, flipping them for a ceiling hang and
 * translating them.
 */
public final class PreparedPlacement {
    private final IrisObjectPlacement config;
    private final IObjectPlacer terrain;
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
    private final CNG surfaceWarp;

    PreparedPlacement(IrisObjectPlacement config, IObjectPlacer terrain, ObjectSpinKernel spin, int spinX, int spinY,
                      int spinZ, IrisBlockVector translation, int rotatedCenterY, int translatedCenterY, int randomY,
                      int warpMargin, TransformedBounds bounds, CNG surfaceWarp) {
        this.config = config;
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

    /** The settings this placement uses, which may differ from the requested ones. */
    public IrisObjectPlacement config() {
        return config;
    }

    /** Answers height, water and carving questions; a heightmap placement answers heights from its noise. */
    IObjectPlacer terrain() {
        return terrain;
    }

    PlaceMode mode() {
        return config.getMode().placeMode();
    }

    int spinX() {
        return spinX;
    }

    int spinY() {
        return spinY;
    }

    int spinZ() {
        return spinZ;
    }

    /** The rotated translation, or null when the placement does not translate. */
    IrisBlockVector translation() {
        return translation;
    }

    /** The height of the object's center after rotation. */
    int rotatedCenterY() {
        return rotatedCenterY;
    }

    /** The height of the object's center after translation. */
    int translatedCenterY() {
        return translatedCenterY;
    }

    /** The random height offset added once the anchor is final. */
    int randomY() {
        return randomY;
    }

    int warpMargin() {
        return warpMargin;
    }

    TransformedBounds bounds() {
        return bounds;
    }

    boolean isWarped() {
        return surfaceWarp != null;
    }

    /** Moves a world column by the surface warp, along X for {@code x} and along Z for the swapped call. */
    int warpOffset(double along, double y, double across) {
        double half = config.getWarp().getMultiplier() / 2D;
        return (int) surfaceWarp.fitDouble(-half, half, along, y, across);
    }

    /** An object block's offset from the anchor after this placement's transforms. */
    BlockPosition transform(BlockPosition local) {
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
    PlatformBlockState rotate(PlatformBlockState state) {
        return spin.rotate(state);
    }
}
