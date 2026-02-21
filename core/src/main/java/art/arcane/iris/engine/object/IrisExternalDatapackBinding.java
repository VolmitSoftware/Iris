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
@Desc("Scoped binding to a dimension external datapack id")
public class IrisExternalDatapackBinding {
    @Desc("Target external datapack id defined on the dimension")
    private String id = "";

    @Desc("Enable or disable this scoped binding")
    private boolean enabled = true;

    @Desc("Override replaceVanilla behavior for this scoped binding (null keeps dimension default)")
    private Boolean replaceVanillaOverride = null;

    @Desc("Include child biomes recursively when collecting scoped biome boundaries")
    private boolean includeChildren = true;

    @Desc("Override required behavior for this scoped binding (null keeps dimension default)")
    private Boolean requiredOverride = null;
}
