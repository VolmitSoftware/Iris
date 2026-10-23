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

package art.arcane.iris.builtin.carve;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Range;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A 3D cave profile as packs write it for a dimension, region or biome ({@code caveProfile}) or under
 * {@code snippet/cave-profile/}: the noise fields that decide where caves are, how they meet the surface, where
 * cave fluid may sit and how objects find anchors in them. A level that writes none has no profile, which is the
 * same as one that is not {@code enabled}. IrisApi types only; the carving module builds the noise.
 */
@Snippet("cave-profile")
@Desc("Represents a configurable 3D cave profile.")
@Feature(id = "iris:carve/profile", name = "Cave profile", key = "caveProfile", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class CaveProfile implements IrisFeature {
    @Desc("Enable profile-driven cave carving.")
    private boolean enabled = false;

    @Desc("Global vertical bounds for profile cave carving, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private Range verticalRange = Range.of(0, 384);

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Vertical fade range applied near cave profile min/max bounds to avoid abrupt hard-stop ceilings/floors.")
    private int verticalEdgeFade = 20;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Strength of the vertical edge fade at cave profile min/max bounds.")
    private double verticalEdgeFadeStrength = 0.18;

    @Desc("Base density style for cave field generation.")
    private NoiseStyle baseDensityStyle = NoiseStyle.of("CELLULAR_IRIS_DOUBLE");

    @Desc("Detail density style blended into base caves.")
    private NoiseStyle detailDensityStyle = NoiseStyle.of("SIMPLEX");

    @Desc("Warp style used to distort cave coordinates.")
    private NoiseStyle warpStyle = NoiseStyle.of("FLAT");

    @MinNumber(0)
    @Desc("Base cave field multiplier.")
    private double baseWeight = 1;

    @MinNumber(0)
    @Desc("Detail cave field multiplier.")
    private double detailWeight = 0.35;

    @MinNumber(0)
    @Desc("Coordinate warp strength for cave fields.")
    private double warpStrength = 0;

    @Desc("Threshold range used for carve cutoff decisions.")
    private StyledRange densityThreshold = StyledRange.of(-0.2, 0.2, NoiseStyle.of("CELLULAR_IRIS_DOUBLE"));

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Extra threshold bias subtracted from sampled threshold before carve tests.")
    private double thresholdBias = 0.16;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("Vertical sample step used while evaluating cave density.")
    private int sampleStep = 1;

    @Desc("Use adaptive cave density prediction so only threshold-adjacent cells fall back to full-resolution evaluation.")
    private boolean adaptiveSampling = true;

    @MinNumber(2)
    @MaxNumber(4)
    @Desc("Horizontal adaptive predictor tuning. The carver always samples the predictor grid at a fixed step of 8; values below 8 only widen the ambiguity margin slightly and do not tighten the grid.")
    private int adaptiveSampleStep = 2;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Additional adaptive ambiguity margin used before the cave predictor falls back to exact sampling.")
    private double adaptiveThresholdMargin = 0.04;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Minimum solid clearance below terrain surface where carving may occur.")
    private int surfaceClearance = 4;

    @Desc("Allow profile-driven cave carving to break through terrain surface in selected columns.")
    private boolean allowSurfaceBreak = true;

    @Desc("Noise style used to decide where surface-breaking cave columns are allowed.")
    private NoiseStyle surfaceBreakStyle = NoiseStyle.of("SIMPLEX").withZoom(0.08);

    @MinNumber(-1)
    @MaxNumber(1)
    @Desc("Minimum signed surface-break noise value required before near-surface carving is allowed.")
    private double surfaceBreakNoiseThreshold = 0.62;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Near-surface depth window used for surface-break carve logic.")
    private int surfaceBreakDepth = 18;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Additional threshold boost applied while carving in the surface-break depth window.")
    private double surfaceBreakThresholdBoost = 0.2;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Minimum depth below terrain surface required for cave-only object anchor placement.")
    private int objectMinDepthBelowSurface = 6;

    @MinItems(1)
    @Desc("Additional layered cave-density modules.")
    private List<CaveFieldModule> modules = List.of();

    @Desc("Default cave anchor mode for cave-only object placement.")
    private CaveAnchorMode defaultObjectAnchor = CaveAnchorMode.FLOOR;

    @Desc("Default placement mode for cave objects. Only applies to placements that are still on the default CENTER_HEIGHT mode; an explicit mode on the placement always wins. Stilt modes tile the object base block down to the cave floor surface. FAST_MIN_STILT is recommended for cave objects to prevent floating.")
    private ObjectPlaceMode defaultObjectPlaceMode;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("Vertical scan step used while searching cave anchors.")
    private int anchorScanStep = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Maximum random column retries while searching a valid cave object anchor in the chunk.")
    private int anchorSearchAttempts = 6;

    @Desc("Allow cave fluid placement from the dimension fluid palette below fluid level.")
    private boolean allowFluid = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Minimum depth below terrain surface required before cave fluid may be placed.")
    private int fluidMinDepthBelowSurface = 12;

    @Desc("Require solid floor support below cave fluid to reduce unsupported fluid flows.")
    private boolean fluidRequiresFloor = true;

    @Desc("Allow cave lava placement based on lava height.")
    private boolean allowLava = true;


    public CaveProfile() {
    }

    public boolean enabled() {
        return enabled;
    }

    public Range verticalRange() {
        return verticalRange;
    }

    public int verticalEdgeFade() {
        return verticalEdgeFade;
    }

    public double verticalEdgeFadeStrength() {
        return verticalEdgeFadeStrength;
    }

    public NoiseStyle baseDensityStyle() {
        return baseDensityStyle;
    }

    public NoiseStyle detailDensityStyle() {
        return detailDensityStyle;
    }

    public NoiseStyle warpStyle() {
        return warpStyle;
    }

    public double baseWeight() {
        return baseWeight;
    }

    public double detailWeight() {
        return detailWeight;
    }

    public double warpStrength() {
        return warpStrength;
    }

    public StyledRange densityThreshold() {
        return densityThreshold;
    }

    public double thresholdBias() {
        return thresholdBias;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public boolean adaptiveSampling() {
        return adaptiveSampling;
    }

    public int adaptiveSampleStep() {
        return adaptiveSampleStep;
    }

    public double adaptiveThresholdMargin() {
        return adaptiveThresholdMargin;
    }

    public int surfaceClearance() {
        return surfaceClearance;
    }

    public boolean allowSurfaceBreak() {
        return allowSurfaceBreak;
    }

    public NoiseStyle surfaceBreakStyle() {
        return surfaceBreakStyle;
    }

    public double surfaceBreakNoiseThreshold() {
        return surfaceBreakNoiseThreshold;
    }

    public int surfaceBreakDepth() {
        return surfaceBreakDepth;
    }

    public double surfaceBreakThresholdBoost() {
        return surfaceBreakThresholdBoost;
    }

    public int objectMinDepthBelowSurface() {
        return objectMinDepthBelowSurface;
    }

    public List<CaveFieldModule> modules() {
        return modules == null ? List.of() : Collections.unmodifiableList(modules);
    }

    public CaveAnchorMode defaultObjectAnchor() {
        return defaultObjectAnchor == null ? CaveAnchorMode.FLOOR : defaultObjectAnchor;
    }

    /** The mode cave objects take where their placement has none of its own; empty when the profile sets none. */
    public Optional<ObjectPlaceMode> defaultObjectPlaceMode() {
        return Optional.ofNullable(defaultObjectPlaceMode);
    }

    public int anchorScanStep() {
        return anchorScanStep;
    }

    public int anchorSearchAttempts() {
        return anchorSearchAttempts;
    }

    public boolean allowFluid() {
        return allowFluid;
    }

    public int fluidMinDepthBelowSurface() {
        return fluidMinDepthBelowSurface;
    }

    public boolean fluidRequiresFloor() {
        return fluidRequiresFloor;
    }

    public boolean allowLava() {
        return allowLava;
    }
}
