package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("How the ground around water is reshaped: channels and pond floors are cut, banks are sloped and low edges are raised to hold the water in.")
@Data
public class IrisHydrologyDeformation {
    @MinNumber(0)
    @MaxNumber(32)
    @Description("Most blocks the ground may be raised to hold water in, such as a berm beside a perched stream.")
    private int maxFill = 6;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Base slope of banks, as rise over run.")
    private double bankSlope = 0.2D;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("How much steeper banks become on steep terrain.")
    private double bankSteepFactor = 3.5D;

    @MinNumber(0)
    @MaxNumber(2)
    @Description("Strength of the noise that roughens bank edges.")
    private double bankNoise = 0.2D;

    @Description("Keeps caves from opening under water, so rivers and ponds never drain into them.")
    private boolean caveMask = true;
}
