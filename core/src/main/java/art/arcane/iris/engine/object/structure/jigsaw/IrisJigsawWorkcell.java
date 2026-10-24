package art.arcane.iris.engine.object.structure.jigsaw;

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
@Desc("Persistent authoring bounds and runtime availability for one planar jigsaw workcell.")
public class IrisJigsawWorkcell {
    @Desc("Optional author-facing name shown by Jigsaw Studio. The canonical archetype name is shown when this is blank.")
    private String displayName = "";

    @Desc("The orientation-independent planar connector shape configured by this workcell.")
    private IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.BLANK;

    @MinNumber(3)
    @MaxNumber(128)
    @Desc("The maximum canonical variant width that this workcell can contain, in blocks.")
    private int width = 16;

    @MinNumber(1)
    @MaxNumber(192)
    @Desc("The maximum variant height that this workcell can contain, in blocks.")
    private int height = 16;

    @MinNumber(3)
    @MaxNumber(128)
    @Desc("The maximum canonical variant depth that this workcell can contain, in blocks.")
    private int depth = 16;

    @Desc("Whether pieces with this planar connector shape participate in assembly and vanilla export.")
    private boolean enabled = true;
}
