package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Desc("Bounds terrain removal outside the wet river channel.")
public class IrisRiverExcavationConfig {
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Maximum blocks removed vertically from a dry bank column. Zero preserves dry bank heights.")
    private int maximumDepth = 8;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Maximum horizontal distance from the channel outline affected by shore and bank excavation.")
    private int maximumWidth = 16;

    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("Maximum dry bank blocks removed per block, measured over sixteen-block reaches. Courses exceeding this budget are rejected.")
    private int maximumVolumePerBlock = 256;
}
