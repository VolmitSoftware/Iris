package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Desc("An absolute world Y band a native structure is relocated into. Reversed bounds are normalized.")
public class IrisStructureYBand {
    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Lowest absolute world Y of the band.")
    private int min = 0;

    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Highest absolute world Y of the band.")
    private int max = 0;

    public int resolvedMin() {
        return Math.min(min, max);
    }

    public int resolvedMax() {
        return Math.max(min, max);
    }
}
