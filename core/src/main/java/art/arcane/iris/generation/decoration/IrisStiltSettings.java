package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Snippet("stilt-settings")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Defines stilting behaviour.")
@Data
public class IrisStiltSettings {
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Defines the maximum amount of blocks the object stilts verticially before overstilting and randomRange.")
    private int yMax;
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Defines the upper boundary for additional blocks after overstilting and/or maxStiltRange.")
    private int yRand;
    @MaxNumber(64)
    @MinNumber(0)
    @Description("If the place mode is set to stilt, you can over-stilt it even further into the ground. Especially useful when using fast stilt due to inaccuracies.")
    private int overStilt;
    @Description("If defined, stilting will be done using this block palette rather than the last layer of the object.")
    private IrisMaterialPalette palette;
    @ArrayType(min = 1, type = String.class)
    @RegistryListBlockType
    @Description("Block materials that must never extend into stilts. Matches all block states of each material, before and after palette replacement or object edits. Original object blocks remain in place. Containers and blocks with tile entities are always excluded.")
    private KList<String> exclude = new KList<>();
    @MinNumber(1)
    @MaxNumber(256)
    @Description("For ORGANIC_STILT / CEILING_HANG: how far to look for solid ground. In caves the object may still move up to 16 blocks to seat, and rock filler never goes beyond this value or 8 blocks.")
    private int organicMaxScan = 48;
    @MinNumber(0)
    @MaxNumber(32)
    @Description("For ORGANIC_STILT / CEILING_HANG: the maximum number of blocks a column that cannot reach ground is randomly shortened by, giving the underside a ragged organic edge instead of a flat disc.")
    private int organicJitter = 3;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("For ORGANIC_STILT / CEILING_HANG: in the deepest fraction of a column that cannot reach ground, the column randomly ends early to break the tip up. Higher = shorter and rougher. 0 disables.")
    private double organicScratch = 0.55;

    public Set<String> excludedMaterials() {
        if (exclude == null || exclude.isEmpty()) {
            return Set.of();
        }
        Set<String> materials = new HashSet<>(exclude.size());
        for (String block : exclude) {
            String material = block.trim().toLowerCase(Locale.ROOT);
            materials.add(material.indexOf(':') < 0 ? "minecraft:" + material : material);
        }
        return materials;
    }

}
