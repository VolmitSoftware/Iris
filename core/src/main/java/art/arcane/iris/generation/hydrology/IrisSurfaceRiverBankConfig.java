package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls the shore band and the eroded valley around a surface river.")
@Data
public class IrisSurfaceRiverBankConfig {
    @MinNumber(0)
    @MaxNumber(16)
    @Description("Width in blocks of the flattened shore bench beside the water, the geometric beach cut level with the bank top, and the default width of the shore biome band. 0 removes the bench so the eroded valley side begins at the waterline; riverPolicy.shoreWidth overrides it per area.")
    private double shoreWidth = 1.5D;

    @MinNumber(0.5)
    @MaxNumber(12)
    @Description("Horizontal run in blocks of eroded bank per block of cut depth.")
    private double blendSlope = 3D;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Narrowest eroded band outside the shore, in blocks.")
    private int minimumBlendWidth = 4;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Widest eroded band outside the shore, in blocks.")
    private int maximumBlendWidth = 32;

    @Description("Limits dry bank cut depth, width, and excavated volume independently of the wet channel.")
    private IrisRiverExcavationConfig excavation = new IrisRiverExcavationConfig();

    @Description("Show the biome's deeper layers on eroded banks instead of the surface layer.")
    private boolean exposeCutStrata = true;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Blocks the shore bench rises from the waterline to its landward edge, so the beach climbs instead of lying level; 0 keeps a flat bench. The eroded valley side starts from the raised edge.")
    private double shoreRise = 0D;

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Blocks added to every eroded valley width before the blend width limits apply, so even a shallow cut erodes at least this far beyond the shore; 0 leaves the width proportional to the cut alone.")
    private double blendBaseWidth = 0D;

    @Description("Palette painted over the shore bench columns instead of the biome's own layers. The default is a two-block sand beach.")
    private IrisRiverMaterialConfig shoreMaterial = new IrisRiverMaterialConfig()
            .setEnabled(true)
            .setPalette(new IrisMaterialPalette().qclear().qadd("sand"))
            .setDepth(2);

    @Description("Optional palette painted over the eroded bank columns outside the shore bench instead of the biome's own layers. Disabled by default, which keeps the bank biome's layers.")
    private IrisRiverMaterialConfig bankMaterial = new IrisRiverMaterialConfig();
}
