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
@Desc("Defines a pack-scoped external datapack source for structure import and optional vanilla replacement")
public class IrisExternalDatapack {
    @Desc("Stable id for this external datapack entry")
    private String id = "";

    @Desc("Datapack source URL. Modrinth version page URLs are supported.")
    private String url = "";

    @Desc("Enable or disable this external datapack entry")
    private boolean enabled = true;

    @Desc("If true, Iris hard-fails startup when this external datapack cannot be synced/imported/installed")
    private boolean required = false;

    @Desc("If true, minecraft namespace worldgen assets may replace vanilla targets listed in replaceTargets")
    private boolean replaceVanilla = false;

    @Desc("Explicit replacement targets for minecraft namespace assets")
    private IrisExternalDatapackReplaceTargets replaceTargets = new IrisExternalDatapackReplaceTargets();
}
