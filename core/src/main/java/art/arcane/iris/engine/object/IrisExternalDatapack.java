package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.volmlib.util.collection.KList;
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

    @Desc("If true, structures projected from this datapack id receive smartbore foundation extension during generation")
    private boolean supportSmartBore = true;

    @Desc("Explicit replacement targets for minecraft namespace assets")
    private IrisExternalDatapackReplaceTargets replaceTargets = new IrisExternalDatapackReplaceTargets();

    @ArrayType(type = IrisExternalDatapackStructureAlias.class, min = 1)
    @Desc("Optional structure alias mappings used to synthesize vanilla structure replacements from non-minecraft source keys")
    private KList<IrisExternalDatapackStructureAlias> structureAliases = new KList<>();

    @ArrayType(type = IrisExternalDatapackStructureSetAlias.class, min = 1)
    @Desc("Optional structure-set alias mappings used to synthesize vanilla structure_set replacements from non-minecraft source keys")
    private KList<IrisExternalDatapackStructureSetAlias> structureSetAliases = new KList<>();

    @ArrayType(type = IrisExternalDatapackStructurePatch.class, min = 1)
    @Desc("Structure placement patches applied when this external datapack is projected")
    private KList<IrisExternalDatapackStructurePatch> structurePatches = new KList<>();
}
