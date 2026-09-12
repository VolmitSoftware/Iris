package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Ponds at the two ends of a surface river: the spring it rises from and, for a river that ends inland, the pool it drains into.")
@Data
public class IrisSurfaceRiverPondsConfig {
    @Description("The spring pond every surface river rises from.")
    private IrisSurfaceRiverPondConfig source = new IrisSurfaceRiverPondConfig(6, 12, 3);

    @Description("The pond a river that ends inland drains into; rivers that reach the ocean get none.")
    private IrisSurfaceRiverPondConfig terminal = new IrisSurfaceRiverPondConfig(4, 7, 3);
}
