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

package art.arcane.iris.builtin.anchor;

import com.volmit.config.Desc;


@Desc("Object Place modes are useful for positioning objects just right. The default value is CENTER_HEIGHT.")
public enum ObjectPlaceMode {
    @Desc("The default place mode. This mode picks a center point (where the center of the object will be) and takes the height. That height is used for the whole object.")

    CENTER_HEIGHT(PlaceMode.of(AnchorSampling.CENTER, StiltShape.NONE)),

    @Desc("Samples a lot of points where the object will cover (horizontally) and picks the highest height, that height is then used to place the object. This mode is useful for preventing any part of your object from being buried though it will float off of cliffs.")

    MAX_HEIGHT(PlaceMode.of(AnchorSampling.HIGHEST, StiltShape.NONE)),

    @Desc("Samples only 4 points where the object will cover (horizontally) and picks the highest height, that height is then used to place the object. This mode is useful for preventing any part of your object from being buried though it will float off of cliffs.\"")

    FAST_MAX_HEIGHT(PlaceMode.of(AnchorSampling.HIGHEST_CORNER, StiltShape.NONE)),

    @Desc("Samples a lot of points where the object will cover (horizontally) and picks the lowest height, that height is then used to place the object. This mode is useful for preventing any part of your object from overhanging a cliff though it gets buried a lot")
    MIN_HEIGHT(PlaceMode.of(AnchorSampling.LOWEST, StiltShape.NONE)),

    @Desc("Samples only 4 points where the object will cover (horizontally) and picks the lowest height, that height is then used to place the object. This mode is useful for preventing any part of your object from overhanging a cliff though it gets buried a lot")

    FAST_MIN_HEIGHT(PlaceMode.of(AnchorSampling.LOWEST_CORNER, StiltShape.NONE)),

    @Desc("Stilting is MAX_HEIGHT but it repeats the bottom most block of your object until it hits the surface. This is expensive because it has to first sample every height value for each x,z position of your object. Avoid using this unless its structures for performance reasons.")

    STILT(PlaceMode.of(AnchorSampling.HIGHEST, StiltShape.STRAIGHT)),

    @Desc("Just like stilting but very inaccurate. Useful for stilting a lot of objects without too much care on accuracy (you can use the over-stilt value to force stilts under ground further)")

    FAST_STILT(PlaceMode.of(AnchorSampling.HIGHEST_CORNER, StiltShape.STRAIGHT)),

    @Desc("Stilting is MIN_HEIGHT but it repeats the bottom most block of your object until it hits the surface. This is expensive because it has to first sample every height value for each x,z position of your object. Avoid using this unless its structures for performance reasons.")

    MIN_STILT(PlaceMode.of(AnchorSampling.LOWEST, StiltShape.STRAIGHT)),

    @Desc("Just like MIN_STILT but very inaccurate. Useful for stilting a lot of objects without too much care on accuracy (you can use the over-stilt value to force stilts under ground further)")

    FAST_MIN_STILT(PlaceMode.of(AnchorSampling.LOWEST_CORNER, StiltShape.STRAIGHT)),

    @Desc("Stilting is CENTER_HEIGHT but it repeats the bottom most block of your object until it hits the surface. This is expensive because it has to first sample every height value for each x,z position of your object. Avoid using this unless its structures for performance reasons.")

    CENTER_STILT(PlaceMode.of(AnchorSampling.CENTER, StiltShape.STRAIGHT)),

    @Desc("Erode stilting tapers columns downward like an ice cream cone. Blocks near the center extend deepest while edge blocks drop off first. Blocks are randomly skipped in the lower portion for a rough organic eroded texture.")

    ERODE_STILT(PlaceMode.of(AnchorSampling.HIGHEST, StiltShape.ERODE)),

    @Desc("Organic stilting scans straight down from the bottom of the object to the first solid block (the cave floor, or terrain) and fills the gap with the object's own bottom block. Each column stops at a slightly different, noise-varied depth and the lower portion is randomly broken up, so the underside reads as rough organic roots rather than a flat disc. Designed for objects placed inside caves so they connect to the floor instead of floating. Tune with stilt-settings (organicMaxScan, organicJitter, organicScratch).")

    ORGANIC_STILT(PlaceMode.of(AnchorSampling.CENTER, StiltShape.ORGANIC, PlaceTrait.CAVE_ANCHORED)),

    @Desc("Hangs the object upside-down from a cave ceiling. The object is flipped vertically (its tip points down like a stalactite) and its top is anchored to the ceiling, while an organic, noise-varied stilt grows up into the ceiling so it never floats below the roof. Use inside caves; tune with stilt-settings (organicMaxScan, organicJitter, organicScratch).")

    CEILING_HANG(PlaceMode.of(AnchorSampling.CENTER, StiltShape.CEILING, PlaceTrait.UPSIDE_DOWN, PlaceTrait.CAVE_ANCHORED)),

    @Desc("Anchors the object at the terrain surface (plus translate.y) like CENTER_HEIGHT, then bends the surrounding terrain to be flush with the object's base. Columns inside the object footprint are raised (or lowered) to meet the base, and the deformation falls off parabolically out to a radius so the terrain smoothly blends back to its natural height. Use this to seat a floating/translated object on the ground without a flat stilt disc, e.g. a cube translated up 10 on a mountain pulls the terrain up to meet it. Tune with vacuum-settings (radius, falloff).")

    VACUUM(PlaceMode.vacuum(VacuumProfile.STANDARD)),

    @Desc("VACUUM with a larger radius and finer per-column sampling for the smoothest terrain blend. More expensive.")

    VACUUM_HIGH(PlaceMode.vacuum(VacuumProfile.HIGH)),

    @Desc("VACUUM with a smaller radius and coarser sampling for performance. Use when seating many objects.")

    VACUUM_FAST(PlaceMode.vacuum(VacuumProfile.FAST)),

    @Desc("VACUUM whose falloff radius is perturbed by noise per column, so the terrain meets the object with an irregular organic edge rather than a clean parabolic bowl.")

    VACUUM_ORGANIC(PlaceMode.vacuum(VacuumProfile.ORGANIC)),

    @Desc("VACUUM whose terrain bend is bent by smooth coherent simplex noise so the surface meets the object with a gently rolling, wavy slope instead of the ragged white-noise edge of VACUUM_ORGANIC or a clean parabolic bowl. The object itself still seats flush; the waves fade to zero under the object and at the outer rim, peaking across the mid-slope. Tune with vacuum-settings (waveAmplitude, waveScale).")

    VACUUM_WAVY(PlaceMode.vacuum(VacuumProfile.WAVY)),

    @Desc("Samples the height of the terrain at every x,z position of your object and pushes it down to the surface. It's pretty much like a melt function over the terrain.")

    PAINT(PlaceMode.of(AnchorSampling.SURFACE, StiltShape.NONE)),

    @Desc("Places the object in pure air at an absolute Y driven entirely by translate.y (plus optional translate.yRandom). Terrain height, underwater, and carving anchor checks are skipped. Use this for floating islands, sky structures, clouds, or blimps where the object must not be translated to the ground.")

    FLOATING(PlaceMode.of(AnchorSampling.TRANSLATION, StiltShape.NONE, PlaceTrait.FREE_HEIGHT)),

    @Desc("Raw stamp at the caller-supplied (x, y, z). No terrain sampling, no stilting, no Y recomputation, no underwater or carving anchor guards. Used internally to route native Minecraft structure pieces (villages etc.) through the Iris object placer.")

    STRUCTURE_PIECE(PlaceMode.of(AnchorSampling.REQUESTED, StiltShape.NONE, PlaceTrait.RAW_STAMP, PlaceTrait.FREE_HEIGHT));

    private final PlaceMode placeMode;

    ObjectPlaceMode(PlaceMode placeMode) {
        this.placeMode = placeMode;
    }

    /** What the mode does, for the placement stages to ask. */
    public PlaceMode placeMode() {
        return placeMode;
    }

    /**
     * The mode an object takes on a floating island, which has no ground below to grow stilts to or to stamp into: a
     * stilt down to the ground becomes the same height sampling without the stilt, a raw stamp the default mode.
     */
    public ObjectPlaceMode onFloatingIsland() {
        if (placeMode.has(PlaceTrait.RAW_STAMP)) {
            return CENTER_HEIGHT;
        }
        if (placeMode.stilt() != StiltShape.STRAIGHT && placeMode.stilt() != StiltShape.ERODE) {
            return this;
        }
        for (ObjectPlaceMode mode : values()) {
            if (mode.placeMode.equals(PlaceMode.of(placeMode.anchor(), StiltShape.NONE))) {
                return mode;
            }
        }
        throw new IllegalStateException("No place mode samples " + placeMode.anchor() + " without a stilt");
    }
}
