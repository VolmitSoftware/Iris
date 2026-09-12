package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls the bed shape of surface river waterfall and cascade throats and the wall shape shared with the erosion compiler. The roughness fields fall back to the surface channel roughness when left null.")
@Data
public class IrisSurfaceRiverShapeConfig {
    @MinNumber(1)
    @MaxNumber(6)
    @Description("Cross-section exponent of the throat bed. Larger values broaden the rounded U-shaped bed.")
    private double bedRoundness = 2D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent vertical variation applied to the throat bed as a fraction of channel depth. Null uses surface.channel.roughness.")
    private Double bedRoughness = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent radial variation applied to the throat walls. Null uses surface.channel.roughness.")
    private Double wallRoughness = null;

    @MinNumber(3)
    @MaxNumber(128)
    @Description("Wavelength in blocks of the throat bed and wall roughness. Null uses surface.channel.roughnessWavelength.")
    private Integer roughnessWavelength = null;

    @MinNumber(0.4)
    @MaxNumber(1.2)
    @Description("Base radial scale of the organic passage outline before the lobes are added; 1 keeps the nominal radius, lower values carve a narrower passage.")
    private double radialBase = 0.86D;

    @MinNumber(0.2)
    @MaxNumber(1)
    @Description("Smallest radial scale the organic outline may shrink to after the lobes are applied, as a fraction of the nominal radius.")
    private double radialMinimum = 0.58D;

    @MinNumber(1)
    @MaxNumber(2)
    @Description("Largest radial scale the organic outline may grow to after the lobes are applied, as a fraction of the nominal radius.")
    private double radialMaximum = 1.18D;

    @MinNumber(0)
    @MaxNumber(0.5)
    @Description("Strength of the broad lobes that bulge and pinch the passage outline along its length; 0 leaves the outline circular.")
    private double primaryLobeStrength = 0.08D;

    @MinNumber(0)
    @MaxNumber(0.5)
    @Description("Strength of the fine lobes layered over the broad ones for small-scale wall detail; 0 leaves only the broad lobes.")
    private double detailLobeStrength = 0.06D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent variation of the carved ceiling height as a fraction of the headroom; 0 keeps the ceiling smooth and evaluates no extra noise. Only the carved segments of a surface course read it; an exposed channel has no ceiling.")
    private double ceilingRoughness = 0D;

    @MinNumber(0.2)
    @MaxNumber(1)
    @Description("Narrowest plan aspect of a chamber, the short axis as a fraction of the long axis; 1 makes every chamber circular in plan. The surface section carries this field so all three shape sections share one shape record; only grotto chambers read it.")
    private double aspectMinimum = 0.62D;

    @MinNumber(0)
    @MaxNumber(0.8)
    @Description("How much the plan aspect may vary above aspectMinimum from one chamber to the next; 0 gives every chamber the same aspect. The surface section carries this field so all three shape sections share one shape record; only grotto chambers read it.")
    private double aspectRange = 0.2D;
}
