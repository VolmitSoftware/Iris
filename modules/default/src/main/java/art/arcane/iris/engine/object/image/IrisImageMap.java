package art.arcane.iris.engine.object.image;

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("A reusable typed image-map definition")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisImageMap extends IrisRegistrant {
    public static final double MINIMUM_SCALE = 0.000001D;
    public static final double MAXIMUM_COLOR_TOLERANCE = 441.672956D;

    @RegistryListResource(IrisImage.class)
    @Desc("PNG source under images/, without the file extension")
    private String source = "";

    @Desc("How source pixels are decoded")
    private IrisImageMapType type = IrisImageMapType.GRAYSCALE_HEIGHT;

    @MinNumber(MINIMUM_SCALE)
    @Desc("Minecraft blocks represented by one source pixel")
    private double blocksPerPixel = 1D;

    @Desc("Minecraft X/Z coordinate that maps to sourceOrigin")
    private IrisImageMapOrigin origin = new IrisImageMapOrigin();

    @Desc("Source pixel X/Y coordinate placed at origin")
    private IrisImageMapOrigin sourceOrigin = new IrisImageMapOrigin();

    @Desc("Clockwise quarter-turn rotation around sourceOrigin")
    private IrisImageMapRotation rotation = IrisImageMapRotation.DEG_0;

    @Desc("Mirror image X around sourceOrigin before rotation")
    private boolean mirrorX = false;

    @Desc("Mirror image Y around sourceOrigin before rotation")
    private boolean mirrorZ = false;

    @Desc("Numeric sampling filter; exact color and binary maps require NEAREST")
    private IrisImageMapSampling sampling = IrisImageMapSampling.NEAREST;

    @Desc("Behavior outside the source rectangle")
    private IrisImageMapOutOfBounds outOfBounds = IrisImageMapOutOfBounds.FALLBACK;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Normalized scalar used by FALLBACK coordinates")
    private double fallbackValue = 0D;

    @Desc("Legend target used by FALLBACK or unknown color-map pixels")
    private String fallbackTarget = "";

    @Desc("How alpha affects decoded data")
    private IrisImageMapAlpha alpha = IrisImageMapAlpha.IGNORE;

    @Desc("Minimum absolute world Y for height maps")
    private double minimumHeight = -64D;

    @Desc("Maximum absolute world Y for height maps")
    private double maximumHeight = 320D;

    @Desc("Vertical block offset applied after height decoding")
    private double verticalOffset = 0D;

    @Desc("Clamp decoded height to minimumHeight and maximumHeight after offset")
    private boolean clamp = true;

    @Desc("Invert decoded scalar values before curve evaluation")
    private boolean inverted = false;

    @MinNumber(MINIMUM_SCALE)
    @Desc("Power curve applied after optional inversion; 1 is linear")
    private double curveExponent = 1D;

    @MinNumber(0)
    @MaxNumber(32)
    @Desc("Load-time box smoothing radius in source pixels")
    private int smoothingRadius = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Binary mask threshold")
    private double threshold = 0.5D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Mask transition width above threshold")
    private double falloff = 0D;

    @MinNumber(0)
    @MaxNumber(MAXIMUM_COLOR_TOLERANCE)
    @Desc("Euclidean raw sRGB distance accepted by tolerant color matching; zero is exact")
    private double colorTolerance = 0D;

    @Desc("How colors absent from the legend are handled")
    private IrisImageMapUnknownColor unknownColor = IrisImageMapUnknownColor.ERROR;

    @Desc("Exact #RRGGBB colors mapped to Iris resource or Minecraft block keys")
    private KMap<String, String> colors = new KMap<>();

    @Override
    public String getFolderName() {
        return "image-maps";
    }

    @Override
    public String getTypeName() {
        return "Image Map";
    }
}
