package art.arcane.iris.generation.hydrology.config;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Sea inlets on the coast, and water below the surface: pools on cave floors and springs in cave walls.")
@Data
public class IrisHydrologyUnderground {
    @Description("Tidal arms of the sea that wind inland from low coasts at sea level.")
    private IrisSeaInlets seaInlets = new IrisSeaInlets();

    @Description("Standing water on the floors of carved caves.")
    private IrisCavePools cavePools = new IrisCavePools();

    @Description("Seep points in cave walls with one source block that the game's fluid physics spreads.")
    private IrisCaveSprings caveSprings = new IrisCaveSprings();
}
