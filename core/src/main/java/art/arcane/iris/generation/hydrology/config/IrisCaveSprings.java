package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Seep points in carved cave walls. Each holds one source block that the game's fluid physics spreads once the chunk is loaded.")
@Data
public class IrisCaveSprings {
    @Description("Places springs in cave walls.")
    private boolean enabled = false;

    @MinNumber(0)
    @MaxNumber(2)
    @Description("Candidate spring sites per chunk.")
    private double density = 0.05D;

    @Required
    @Description("Name of the fluid profile a spring runs with. The hydrology policy fluid of the cave biome at the seep replaces it, then that of its region, then that of the dimension.")
    private String fluid = "lava";

    @MinNumber(0)
    @MaxNumber(256)
    @Description("Fewest blocks of ground kept between the surface and a spring, its opening and the cave it runs into.")
    private int minDepthBelowSurface = 16;
}
