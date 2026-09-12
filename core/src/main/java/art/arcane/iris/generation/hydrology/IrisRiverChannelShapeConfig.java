package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls rounded beds and rough, coherent channel walls.")
@Data
public class IrisRiverChannelShapeConfig {
    @MinNumber(1)
    @MaxNumber(6)
    @Description("Cross-section exponent. Larger values broaden the rounded U-shaped bed.")
    private double bedRoundness = 2.4D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent vertical variation applied to the bed as a fraction of channel depth.")
    private double bedRoughness = 0.28D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coherent radial variation applied to carved walls and banks.")
    private double wallRoughness = 0.24D;

    @MinNumber(3)
    @MaxNumber(128)
    @Description("Wavelength in blocks of bed and wall roughness.")
    private int roughnessWavelength = 11;

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
    @Description("Coherent variation of the carved ceiling height as a fraction of the headroom; 0 keeps the ceiling smooth and evaluates no extra noise.")
    private double ceilingRoughness = 0D;

    @MinNumber(0.2)
    @MaxNumber(1)
    @Description("Narrowest plan aspect of a chamber, the short axis as a fraction of the long axis; 1 makes every chamber circular in plan. Applies to grottos only.")
    private double aspectMinimum = 0.62D;

    @MinNumber(0)
    @MaxNumber(0.8)
    @Description("How much the plan aspect may vary above aspectMinimum from one chamber to the next; 0 gives every chamber the same aspect. Applies to grottos only.")
    private double aspectRange = 0.2D;
}
