package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A weighted structure-wide jigsaw theme. One declared theme is selected for an assembly, and pieces may opt into one or more themes.")
@Data
public class IrisJigsawThemeSet {
    @Required
    @Description("The exact theme key referenced by jigsaw pieces.")
    private String key = "";

    @MinNumber(1)
    @Description("The relative weight used when selecting one theme for an assembly.")
    private int weight = 1;
}
