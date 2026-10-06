package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListNativeJigsawPool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Desc("Optional overrides applied to a live vanilla, datapack, or modded jigsaw definition before Minecraft assembles it. Omitted values preserve the registered source definition.")
public class IrisJigsawConfiguration {
    @Desc("Optional registered template-pool key used instead of the source jigsaw's start pool. Empty preserves the source pool.")
    @RegistryListNativeJigsawPool
    private String startPool = "";

    @Desc("Optional start connector name. Empty preserves the source value; the literal NONE removes the source constraint.")
    private String startJigsawName = "";

    @MinNumber(0)
    @MaxNumber(20)
    @Desc("Maximum jigsaw recursion depth. Null preserves the source value.")
    private Integer maxDepth = null;

    @MinNumber(1)
    @MaxNumber(128)
    @Desc("Exact horizontal block distance allowed from the start. Null preserves the source value.")
    private Integer maxDistanceHorizontal = null;

    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("Exact vertical block distance allowed from the start. Null preserves the source value.")
    private Integer maxDistanceVertical = null;

    @Desc("Expansion-hack override. Null preserves the source value.")
    private Boolean useExpansionHack = null;

    @Desc("Heightmap projection override. SOURCE preserves the registered value and NONE removes projection.")
    private IrisJigsawHeightmap projectStartToHeightmap = IrisJigsawHeightmap.SOURCE;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("Minimum distance retained above the dimension floor. Null preserves the source value.")
    private Integer dimensionPaddingBottom = null;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("Minimum distance retained below the dimension ceiling. Null preserves the source value.")
    private Integer dimensionPaddingTop = null;

    @Desc("Waterlogging override. SOURCE preserves the registered value.")
    private IrisJigsawLiquidSettings liquidSettings = IrisJigsawLiquidSettings.SOURCE;

    public boolean hasOverrides() {
        return (startPool != null && !startPool.isBlank())
                || (startJigsawName != null && !startJigsawName.isBlank())
                || maxDepth != null
                || maxDistanceHorizontal != null
                || maxDistanceVertical != null
                || useExpansionHack != null
                || (projectStartToHeightmap != null && projectStartToHeightmap != IrisJigsawHeightmap.SOURCE)
                || dimensionPaddingBottom != null
                || dimensionPaddingTop != null
                || (liquidSettings != null && liquidSettings != IrisJigsawLiquidSettings.SOURCE);
    }
}
