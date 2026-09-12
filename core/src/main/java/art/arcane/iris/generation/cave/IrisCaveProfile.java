package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.structure.object.ObjectPlaceMode;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("cave-profile")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a configurable 3D cave profile.")
@Data
public class IrisCaveProfile {
    @Description("Enable profile-driven cave carving.")
    private boolean enabled = false;

    @Description("Global vertical bounds for profile cave carving, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private IrisRange verticalRange = new IrisRange(0, 384);

    @MinNumber(0)
    @MaxNumber(128)
    @Description("Vertical fade range applied near cave profile min/max bounds to avoid abrupt hard-stop ceilings/floors.")
    private int verticalEdgeFade = 20;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Strength of the vertical edge fade at cave profile min/max bounds.")
    private double verticalEdgeFadeStrength = 0.18;

    @Description("Base density style for cave field generation.")
    private IrisGeneratorStyle baseDensityStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @Description("Detail density style blended into base caves.")
    private IrisGeneratorStyle detailDensityStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

    @Description("Warp style used to distort cave coordinates.")
    private IrisGeneratorStyle warpStyle = new IrisGeneratorStyle(NoiseStyle.FLAT);

    @MinNumber(0)
    @Description("Base cave field multiplier.")
    private double baseWeight = 1;

    @MinNumber(0)
    @Description("Detail cave field multiplier.")
    private double detailWeight = 0.35;

    @MinNumber(0)
    @Description("Coordinate warp strength for cave fields.")
    private double warpStrength = 0;

    @Description("Threshold range used for carve cutoff decisions.")
    private IrisStyledRange densityThreshold = new IrisStyledRange(-0.2, 0.2, NoiseStyle.CELLULAR_IRIS_DOUBLE.style());

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Extra threshold bias subtracted from sampled threshold before carve tests.")
    private double thresholdBias = 0.16;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("Vertical sample step used while evaluating cave density.")
    private int sampleStep = 1;

    @Description("Use adaptive cave density prediction so only threshold-adjacent cells fall back to full-resolution evaluation.")
    private boolean adaptiveSampling = true;

    @MinNumber(2)
    @MaxNumber(4)
    @Description("Horizontal adaptive predictor tuning. The carver always samples the predictor grid at a fixed step of 8; values below 8 only widen the ambiguity margin slightly and do not tighten the grid.")
    private int adaptiveSampleStep = 2;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Additional adaptive ambiguity margin used before the cave predictor falls back to exact sampling.")
    private double adaptiveThresholdMargin = 0.04;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum solid clearance below terrain surface where carving may occur.")
    private int surfaceClearance = 4;

    @Description("Allow profile-driven cave carving to break through terrain surface in selected columns.")
    private boolean allowSurfaceBreak = true;

    @Description("Noise style used to decide where surface-breaking cave columns are allowed.")
    private IrisGeneratorStyle surfaceBreakStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.08);

    @MinNumber(-1)
    @MaxNumber(1)
    @Description("Minimum signed surface-break noise value required before near-surface carving is allowed.")
    private double surfaceBreakNoiseThreshold = 0.62;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Near-surface depth window used for surface-break carve logic.")
    private int surfaceBreakDepth = 18;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Additional threshold boost applied while carving in the surface-break depth window.")
    private double surfaceBreakThresholdBoost = 0.2;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum depth below terrain surface required for cave-only object anchor placement.")
    private int objectMinDepthBelowSurface = 6;

    @ArrayType(type = IrisCaveFieldModule.class, min = 1)
    @Description("Additional layered cave-density modules.")
    private KList<IrisCaveFieldModule> modules = new KList<>();

    @Description("Default cave anchor mode for cave-only object placement.")
    private IrisCaveAnchorMode defaultObjectAnchor = IrisCaveAnchorMode.FLOOR;

    @Description("Default placement mode for cave objects. Only applies to placements that are still on the default CENTER_HEIGHT mode; an explicit mode on the placement always wins. Stilt modes tile the object base block down to the cave floor surface. FAST_MIN_STILT is recommended for cave objects to prevent floating.")
    private ObjectPlaceMode defaultObjectPlaceMode = null;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("Vertical scan step used while searching cave anchors.")
    private int anchorScanStep = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Maximum random column retries while searching a valid cave object anchor in the chunk.")
    private int anchorSearchAttempts = 6;

    @Description("Allow cave fluid placement from the dimension fluid palette below fluid level.")
    private boolean allowFluid = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum depth below terrain surface required before cave fluid may be placed.")
    private int fluidMinDepthBelowSurface = 12;

    @Description("Require solid floor support below cave fluid to reduce unsupported fluid flows.")
    private boolean fluidRequiresFloor = true;

    @Description("Allow cave lava placement based on lava height.")
    private boolean allowLava = true;
}
