package art.arcane.iris.engine.object.structure.jigsaw;

import com.volmit.config.Desc;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A weighted structure-wide jigsaw theme. One declared theme is selected for an assembly, and pieces may opt into one or more themes.")
@Data
public class IrisJigsawThemeSet {
    @Required
    @Desc("The exact theme key referenced by jigsaw pieces.")
    private String key = "";

    @MinNumber(1)
    @Desc("The relative weight used when selecting one theme for an assembly.")
    private int weight = 1;
}
