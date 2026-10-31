package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Wet and dry zones across the dimension. A noise field splits the land into zones: wet zones keep the rainfall and pond density of their biome, dry zones lose them. Streams and ponds then cluster in the wet zones instead of reaching every hill, and a river that rises in a wet zone still flows on through dry land.")
@Data
public class IrisHydrologyClimate {
    @Description("Turns wet and dry zones on. When off, rainfall and pond density follow the hydrology policies alone.")
    private boolean enabled = false;

    @Description("Noise that lays out the zones. Land where its value is below dryness is dry. A larger zoom makes larger zones.")
    private IrisGeneratorStyle style = NoiseStyle.SIMPLEX.style().zoomed(24D);

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Noise value below which land is dry. 0 keeps all land wet, and higher values dry out more of it.")
    private double dryness = 0.5D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Width of the noise band over which rainfall fades from dry to wet. 0 gives sharp zone edges.")
    private double transition = 0.1D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Share of the area's rainfall and pond density kept in dry zones. 0 lets dry zones start no streams or ponds of their own.")
    private double dryRainfall = 0D;
}
