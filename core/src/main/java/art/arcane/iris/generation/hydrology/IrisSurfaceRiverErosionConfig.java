package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls how the ground around a surface river is eroded into a valley. The valley's reach comes from banks.blendSlope and the blend widths; this section shapes it.")
@Data
public class IrisSurfaceRiverErosionConfig {
    @Description("Erode the ground beyond the shore band into a valley; false keeps only the wet channel, the shore band and the lip that holds the water.")
    private boolean enabled = true;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Stations along the course the valley width is averaged over, so it widens and narrows gradually; 0 follows the local cut exactly.")
    private int smoothingRadius = 12;

    @MinNumber(0)
    @MaxNumber(0.95)
    @Description("Share of the distance from the thalweg to each waterline that stays at full bed depth before the bed rises to the edge; higher is a flatter, broader bed.")
    private double thalwegFraction = 0.45D;

    @MinNumber(0.25)
    @MaxNumber(4)
    @Description("Exponent on the blend from the bank top out to natural terrain; below 1 hollows the valley sides, above 1 keeps them steep near the shore.")
    private double blendCurve = 1D;

    @MinNumber(0)
    @MaxNumber(2)
    @Description("Share of the channel roughness applied to the bed as depth variation; 0 leaves a smooth bed.")
    private double bedNoise = 0.5D;

    @Description("Shape of the valley side between the shore bench and natural terrain. SMOOTH is the eased curve used before this field existed.")
    private IrisRiverBlendStyle style = IrisRiverBlendStyle.SMOOTH;

    @MinNumber(2)
    @MaxNumber(16)
    @Description("Number of level steps the valley side is cut into when style is TERRACED; ignored by every other style.")
    private int terraceSteps = 4;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Share of the eroded band kept level at the bank top before the vertical wall when style is CLIFF; ignored by every other style.")
    private double cliffFraction = 0.5D;

    @Description("Cross-section of the wet channel bed from the thalweg out to the waterline. Curves shift the thalweg toward the outside bank within the existing depth limit.")
    private IrisRiverBedProfile bedProfile = IrisRiverBedProfile.BOWL;
}
