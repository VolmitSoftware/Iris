package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("An optional block palette painted over the top layers of one part of a river instead of the biome's own layers.")
@Data
public class IrisRiverMaterialConfig {
    @Description("Paint this palette. When false the biome layers are used as before.")
    private boolean enabled = false;

    @Description("Blocks to paint. Any solid palette.")
    private IrisMaterialPalette palette = new IrisMaterialPalette();

    @MinNumber(1)
    @MaxNumber(8)
    @Description("How many layers down from the surface the palette replaces, in blocks.")
    private int depth = 1;
}
