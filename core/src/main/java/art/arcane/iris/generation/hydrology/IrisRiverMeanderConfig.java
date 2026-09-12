package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls multi-scale terrain-guided river meanders.")
@Data
public class IrisRiverMeanderConfig {
    @MinNumber(8)
    @MaxNumber(512)
    @Description("Broad meander wavelength in blocks.")
    private int primaryWavelength = 64;

    @MinNumber(4)
    @MaxNumber(128)
    @Description("Fine worm wavelength in blocks. Smaller values change direction more frequently.")
    private int detailWavelength = 12;

    @MinNumber(0)
    @MaxNumber(2)
    @Description("Strength of broad meanders.")
    private double primaryStrength = 0.34D;

    @MinNumber(0)
    @MaxNumber(2)
    @Description("Strength of fine worm movement.")
    private double detailStrength = 0.42D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Maximum lateral route displacement as a fraction of one drainage edge.")
    private double maximumOffsetRatio = 0.48D;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Terrain-safe centerline smoothing passes after route solving.")
    private int smoothingPasses = 1;

    @MinNumber(10)
    @MaxNumber(150)
    @Description("Maximum retained centerline turn angle in degrees.")
    private double maximumTurnDegrees = 82D;
}
