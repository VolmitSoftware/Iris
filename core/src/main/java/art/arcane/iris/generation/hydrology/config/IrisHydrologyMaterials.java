package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Blocks painted around a body of water. A palette left unset keeps the biome's own layers.")
@Data
public class IrisHydrologyMaterials {
    @Description("Palette painted on the ground under the water. Leave unset to keep the biome's layers.")
    private IrisMaterialPalette bed = null;

    @Description("Palette painted on the strip of ground along the water's edge. Leave unset to keep the biome's layers.")
    private IrisMaterialPalette shore = null;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("Layers of the shore palette painted downward from the surface, in blocks.")
    private int shoreDepth = 2;

    @Description("Palette painted on the reshaped banks beyond the shore. Leave unset to keep the biome's layers.")
    private IrisMaterialPalette bank = null;

    @Description("Allows sand, gravel and other falling blocks in the bed, shore and banks. When false, falling blocks are kept away from the water so nothing can collapse into it.")
    private boolean allowGravityBlocks = true;
}
