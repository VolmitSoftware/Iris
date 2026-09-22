package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Description("Bounds terrain shaping outside the wet river channel.")
public class IrisRiverExcavationConfig {
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Maximum blocks cut or filled vertically in a dry bank column. Fill is also limited by the local channel incision policy. Zero preserves dry bank heights.")
    private int maximumDepth = 8;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Maximum horizontal distance from the channel outline affected by shore and bank shaping.")
    private int maximumWidth = 16;

    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Maximum dry bank blocks removed per block, measured over sixteen-block reaches. Courses exceeding this budget are rejected.")
    private int maximumVolumePerBlock = 256;
}
