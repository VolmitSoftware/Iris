package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Raised rims on the downhill side of ponds that hold the water above its natural spill height.")
@Data
public class IrisPondDams {
    @Description("Raises the downhill rim of ponds to hold more water.")
    private boolean enabled = false;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Most blocks a dam raises a pond rim.")
    private int maxRaise = 4;
}
