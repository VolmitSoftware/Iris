package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Desc("Terrain integration applied after a structure graph is assembled and before its pieces are placed.")
public class IrisStructureTerrain {
    private static final double AUTO_LOBE_FREQUENCY_RATIO = 0.3D;
    private static final double DEFAULT_EROSION_STRENGTH = 0.8D;
    private static final double DEFAULT_EROSION_FREQUENCY = 0.07D;
    private static final double DEFAULT_LOBE_STRENGTH = 0.85D;
    private static final double MIN_EROSION_FREQUENCY = 0.001D;
    private static final double MAX_EROSION_FREQUENCY = 1D;
    private static final double MAX_LOBE_FREQUENCY = 1D;

    @Desc("Terrain operation. SOURCE applies the registered native structure's authored terrain adaptation and is a no-op for editable Iris structures. PRESERVE disables terrain integration. FLATTEN cuts and fills exposed native foundations within flattenRange, blending across horizontalPadding blocks. VACUUM raises terrain from processed rigid-template foundations with a fixed 12-block falloff without lowering ground. BORE and FORCE_CARVE clear the requested envelope, while ENCASE fills it before placement.")
    private IrisStructureTerrainMode mode = IrisStructureTerrainMode.SOURCE;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Horizontal clearance for BORE, FORCE_CARVE, and ENCASE, or the terrain blend distance for FLATTEN, in blocks. VACUUM uses its fixed 12-block terrain falloff.")
    private int horizontalPadding = 0;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Maximum vertical cut, fill, and foundation support depth for FLATTEN, in blocks. Other terrain modes ignore this setting.")
    private int flattenRange = 64;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Air clearance above the assembled pieces.")
    private int ceilingPadding = 0;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Terrain cleared below the assembled pieces. Zero preserves their supporting floor.")
    private int floorPadding = 0;

    @Desc("Shape used by FORCE_CARVE.")
    private IrisStructureCarveShape shape = IrisStructureCarveShape.BOX;

    @Desc("Block palette used by ENCASE. When unset, the Overworld uses stone or deepslate, the Nether uses netherrack, and the End uses end stone.")
    private IrisMaterialPalette encasePalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Boundary erosion strength for ERODED force carving.")
    private double erosionStrength = DEFAULT_EROSION_STRENGTH;

    @MinNumber(MIN_EROSION_FREQUENCY)
    @MaxNumber(MAX_EROSION_FREQUENCY)
    @Desc("Boundary noise frequency for ERODED force carving.")
    private double erosionFrequency = DEFAULT_EROSION_FREQUENCY;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Low frequency lobe wavelength for ERODED force carving. Zero derives it from the erosion frequency.")
    private double lobeFrequency = 0D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Fraction of the horizontal padding the lobes may remove for ERODED force carving. Zero keeps a uniform boundary.")
    private double lobeStrength = DEFAULT_LOBE_STRENGTH;

    public IrisStructureTerrainMode resolvedMode() {
        return mode == null ? IrisStructureTerrainMode.SOURCE : mode;
    }

    public IrisStructureCarveShape resolvedShape() {
        return shape == null ? IrisStructureCarveShape.BOX : shape;
    }

    public int resolvedFlattenRange() {
        return Math.max(0, Math.min(128, flattenRange));
    }

    public double resolvedErosionStrength() {
        if (!Double.isFinite(erosionStrength)) {
            return DEFAULT_EROSION_STRENGTH;
        }
        return Math.max(0D, Math.min(1D, erosionStrength));
    }

    public double resolvedErosionFrequency() {
        if (!Double.isFinite(erosionFrequency)) {
            return DEFAULT_EROSION_FREQUENCY;
        }
        return Math.max(MIN_EROSION_FREQUENCY, Math.min(MAX_EROSION_FREQUENCY, erosionFrequency));
    }

    public double resolvedLobeFrequency() {
        if (!Double.isFinite(lobeFrequency) || lobeFrequency <= 0D) {
            return resolvedErosionFrequency() * AUTO_LOBE_FREQUENCY_RATIO;
        }
        return Math.min(MAX_LOBE_FREQUENCY, lobeFrequency);
    }

    public double resolvedLobeStrength() {
        if (!Double.isFinite(lobeStrength)) {
            return DEFAULT_LOBE_STRENGTH;
        }
        return Math.max(0D, Math.min(1D, lobeStrength));
    }
}
