package art.arcane.iris.generation.image;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A reusable typed image-map definition")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisImageMap extends IrisRegistrant {
    public static final double MINIMUM_SCALE = 0.000001D;
    public static final double MAXIMUM_COLOR_TOLERANCE = 441.672956D;

    @RegistryListResource(IrisImage.class)
    @Description("PNG source under images/, without the file extension")
    private String source = "";

    @Description("How source pixels are decoded")
    private IrisImageMapType type = IrisImageMapType.GRAYSCALE_HEIGHT;

    @MinNumber(MINIMUM_SCALE)
    @Description("Minecraft blocks represented by one source pixel")
    private double blocksPerPixel = 1D;

    @Description("Minecraft X/Z coordinate that maps to sourceOrigin")
    private IrisImageMapOrigin origin = new IrisImageMapOrigin();

    @Description("Source pixel X/Y coordinate placed at origin")
    private IrisImageMapOrigin sourceOrigin = new IrisImageMapOrigin();

    @Description("Clockwise quarter-turn rotation around sourceOrigin")
    private IrisImageMapRotation rotation = IrisImageMapRotation.DEG_0;

    @Description("Mirror image X around sourceOrigin before rotation")
    private boolean mirrorX = false;

    @Description("Mirror image Y around sourceOrigin before rotation")
    private boolean mirrorZ = false;

    @Description("Numeric sampling filter; exact color and binary maps require NEAREST")
    private IrisImageMapSampling sampling = IrisImageMapSampling.NEAREST;

    @Description("Behavior outside the source rectangle")
    private IrisImageMapOutOfBounds outOfBounds = IrisImageMapOutOfBounds.FALLBACK;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Normalized scalar used by FALLBACK coordinates")
    private double fallbackValue = 0D;

    @Description("Legend target used by FALLBACK or unknown color-map pixels")
    private String fallbackTarget = "";

    @Description("How alpha affects decoded data")
    private IrisImageMapAlpha alpha = IrisImageMapAlpha.IGNORE;

    @Description("Minimum absolute world Y for height maps")
    private double minimumHeight = -64D;

    @Description("Maximum absolute world Y for height maps")
    private double maximumHeight = 320D;

    @Description("Vertical block offset applied after height decoding")
    private double verticalOffset = 0D;

    @Description("Clamp decoded height to minimumHeight and maximumHeight after offset")
    private boolean clamp = true;

    @Description("Invert decoded scalar values before curve evaluation")
    private boolean inverted = false;

    @MinNumber(MINIMUM_SCALE)
    @Description("Power curve applied after optional inversion; 1 is linear")
    private double curveExponent = 1D;

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Load-time box smoothing radius in source pixels")
    private int smoothingRadius = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Binary mask threshold")
    private double threshold = 0.5D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Mask transition width above threshold")
    private double falloff = 0D;

    @MinNumber(0)
    @MaxNumber(MAXIMUM_COLOR_TOLERANCE)
    @Description("Euclidean raw sRGB distance accepted by tolerant color matching; zero is exact")
    private double colorTolerance = 0D;

    @Description("How colors absent from the legend are handled")
    private IrisImageMapUnknownColor unknownColor = IrisImageMapUnknownColor.ERROR;

    @Description("Exact #RRGGBB colors mapped to Iris resource or Minecraft block keys")
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
