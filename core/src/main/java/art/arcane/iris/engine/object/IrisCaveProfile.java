package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("cave-profile")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a configurable 3D cave profile.")
@Data
public class IrisCaveProfile {
    @Desc("Enable profile-driven cave carving.")
    private boolean enabled = false;

    @Desc("Global vertical bounds for profile cave carving.")
    private IrisRange verticalRange = new IrisRange(0, 384);

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Vertical fade range applied near cave profile min/max bounds to avoid abrupt hard-stop ceilings/floors.")
    private int verticalEdgeFade = 20;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Strength of the vertical edge fade at cave profile min/max bounds.")
    private double verticalEdgeFadeStrength = 0.18;

    @Desc("Base density style for cave field generation.")
    private IrisGeneratorStyle baseDensityStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @Desc("Detail density style blended into base caves.")
    private IrisGeneratorStyle detailDensityStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);

    @Desc("Warp style used to distort cave coordinates.")
    private IrisGeneratorStyle warpStyle = new IrisGeneratorStyle(NoiseStyle.FLAT);

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
    private IrisStyledRange densityThreshold = new IrisStyledRange(-0.2, 0.2, NoiseStyle.CELLULAR_IRIS_DOUBLE.style());

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Extra threshold bias subtracted from sampled threshold before carve tests.")
    private double thresholdBias = 0.16;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("Vertical sample step used while evaluating cave density.")
    private int sampleStep = 2;

    @MinNumber(0)
    @MaxNumber(4096)
    @Desc("Minimum carved cells expected from this profile before recovery boost applies.")
    private int minCarveCells = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Additional threshold boost used when profile carve output is too sparse.")
    private double recoveryThresholdBoost = 0.08;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Minimum solid clearance below terrain surface where carving may occur.")
    private int surfaceClearance = 4;

    @Desc("Allow profile-driven cave carving to break through terrain surface in selected columns.")
    private boolean allowSurfaceBreak = true;

    @Desc("Noise style used to decide where surface-breaking cave columns are allowed.")
    private IrisGeneratorStyle surfaceBreakStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.08);

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

    @MinNumber(0)
    @MaxNumber(32)
    @Desc("Skip surface-object placement when carved cells exist this many blocks below terrain surface.")
    private int surfaceObjectExclusionDepth = 5;

    @ArrayType(type = IrisCaveFieldModule.class, min = 1)
    @Desc("Additional layered cave-density modules.")
    private KList<IrisCaveFieldModule> modules = new KList<>();

    @Desc("Default cave anchor mode for cave-only object placement.")
    private IrisCaveAnchorMode defaultObjectAnchor = IrisCaveAnchorMode.FLOOR;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("Vertical scan step used while searching cave anchors.")
    private int anchorScanStep = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Maximum random column retries while searching a valid cave object anchor in the chunk.")
    private int anchorSearchAttempts = 6;

    @Desc("Allow cave water placement below fluid level.")
    private boolean allowWater = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Minimum depth below terrain surface required before cave water may be placed.")
    private int waterMinDepthBelowSurface = 12;

    @Desc("Require solid floor support below cave water to reduce cascading cave waterfalls.")
    private boolean waterRequiresFloor = true;

    @Desc("Allow cave lava placement based on lava height.")
    private boolean allowLava = true;
}
