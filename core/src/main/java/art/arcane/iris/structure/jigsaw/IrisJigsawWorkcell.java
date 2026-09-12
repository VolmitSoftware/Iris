package art.arcane.iris.structure.jigsaw;

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
@Description("Persistent authoring bounds and runtime availability for one planar jigsaw workcell.")
public class IrisJigsawWorkcell {
    @Description("Optional author-facing name shown by Jigsaw Studio. The canonical archetype name is shown when this is blank.")
    private String displayName = "";

    @Description("The orientation-independent planar connector shape configured by this workcell.")
    private IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.BLANK;

    @MinNumber(3)
    @MaxNumber(128)
    @Description("The maximum canonical variant width that this workcell can contain, in blocks.")
    private int width = 16;

    @MinNumber(1)
    @MaxNumber(192)
    @Description("The maximum variant height that this workcell can contain, in blocks.")
    private int height = 16;

    @MinNumber(3)
    @MaxNumber(128)
    @Description("The maximum canonical variant depth that this workcell can contain, in blocks.")
    private int depth = 16;

    @Description("Whether pieces with this planar connector shape participate in assembly and vanilla export.")
    private boolean enabled = true;
}
