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
@Desc("Defines a structure-level patch override for external datapack projection")
public class IrisExternalDatapackStructurePatch {
    @Desc("Structure id to patch")
    private String structure = "";

    @Desc("Enable or disable this patch entry")
    private boolean enabled = true;

    @Desc("Absolute start height override for this structure")
    private int startHeightAbsolute = -27;
}
