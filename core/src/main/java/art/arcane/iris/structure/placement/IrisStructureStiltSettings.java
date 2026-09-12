package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

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
@Description("Defines foundation columns placed beneath a structure.")
@Data
public class IrisStructureStiltSettings {
    @MinNumber(1)
    @MaxNumber(4064)
    @Description("Maximum number of blocks each foundation column may descend while searching for solid ground.")
    private int maxDepth = 64;

    @Description("Block palette used for foundation columns.")
    private IrisMaterialPalette palette = new IrisMaterialPalette().qclear().qadd("minecraft:cobblestone");

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Horizontal spacing between support columns. One fills every eligible foundation column; larger values create sparse stilts.")
    private int spacing = 1;

    @Description("For Iris-authored structures, whether solid partial blocks such as slabs, stairs, and walls may seed foundation columns instead of requiring a fully occluding base block.")
    private boolean supportNonOccluding = false;
}
