package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Where a channel reaches the sea it widens into an estuary: water at sea level that holds the sea's own fluid, so the sea's surface layers and ice continue across it.")
@Data
public class IrisHydrologyEstuary {
    @MinNumber(0)
    @MaxNumber(256)
    @Description("Most blocks upstream of the mouth the estuary reaches. It stops where the lower bank rises more than deformation.maxFill blocks above sea level. 0 ends the channel in a plain mouth.")
    private int length = 48;

    @MinNumber(1)
    @MaxNumber(4)
    @Description("Half-width at the mouth of a full-length estuary as a multiple of the channel's own half-width. The channel widens slowly at first and fastest near the mouth while its depth tapers to one block there; an estuary cut short by higher banks widens less. The half-width never passes 64. 1 keeps the channel's width.")
    private double flare = 2.5D;
}
