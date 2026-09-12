package art.arcane.iris.structure.placement;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Description("An absolute world Y band a native structure is relocated into. Reversed bounds are normalized.")
public class IrisStructureYBand {
    @MinNumber(-4064)
    @MaxNumber(4064)
    @Description("Lowest absolute world Y of the band.")
    private int min = 0;

    @MinNumber(-4064)
    @MaxNumber(4064)
    @Description("Highest absolute world Y of the band.")
    private int max = 0;

    public int resolvedMin() {
        return Math.min(min, max);
    }

    public int resolvedMax() {
        return Math.max(min, max);
    }
}
