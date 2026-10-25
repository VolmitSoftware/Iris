package art.arcane.iris.generation.hydrology.config;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Water below the surface: flooded passages that run inland from the sea, and pools on cave floors.")
@Data
public class IrisHydrologyUnderground {
    @Description("Flooded passages at sea level that run inland from coastal cliffs.")
    private IrisSeaInlets seaInlets = new IrisSeaInlets();

    @Description("Standing water on the floors of carved caves.")
    private IrisCavePools cavePools = new IrisCavePools();
}
