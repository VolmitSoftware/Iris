package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Desc("Maps a vanilla structure replacement target to a source structure key from an external datapack")
public class IrisExternalDatapackStructureAlias {
    @Desc("Vanilla replacement target structure id")
    private String target = "";

    @Desc("Source structure id to clone when the target id is not provided directly")
    private String source = "";
}
