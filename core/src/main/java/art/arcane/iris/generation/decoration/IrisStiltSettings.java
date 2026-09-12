package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
    @MinNumber(1)
    @MaxNumber(256)
    @Description("For ORGANIC_STILT / CEILING_HANG: the maximum number of blocks to scan toward the cave floor (or ceiling) looking for solid ground before giving up.")
    private int organicMaxScan = 48;
    @MinNumber(0)
    @MaxNumber(32)
    @Description("For ORGANIC_STILT / CEILING_HANG: the maximum number of blocks each column's stilt is randomly shortened by, giving the underside a ragged organic edge instead of a flat disc.")
    private int organicJitter = 3;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("For ORGANIC_STILT / CEILING_HANG: in the deepest fraction of each stilt column, blocks are randomly skipped to break the tip up. Higher = more broken and scratchy. 0 disables.")
    private double organicScratch = 0.55;

}
