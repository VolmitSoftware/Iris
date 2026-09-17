package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.object.material.IrisMaterialPalette;
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
@Desc("Defines foundation columns placed beneath a structure.")
@Data
public class IrisStructureStiltSettings {
    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("Maximum number of blocks each foundation column may descend while searching for solid ground.")
    private int maxDepth = 64;

    @Desc("Block palette used for foundation columns.")
    private IrisMaterialPalette palette = new IrisMaterialPalette().qclear().qadd("minecraft:cobblestone");

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Horizontal spacing between support columns. One fills every eligible foundation column; larger values create sparse stilts.")
    private int spacing = 1;

    @Desc("For Iris-authored structures, whether solid partial blocks such as slabs, stairs, and walls may seed foundation columns instead of requiring a fully occluding base block.")
    private boolean supportNonOccluding = false;
}
