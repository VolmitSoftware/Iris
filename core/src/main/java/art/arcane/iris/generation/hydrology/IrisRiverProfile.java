package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Reusable river fluid profile selected by river policies.")
@Data
public class IrisRiverProfile {
    @Required
    @Description("Profile identifier referenced by dimension, region, and biome river policies.")
    private String id = "default";

    @Required
    @Description("Fluid palette used by accepted river footprints using this profile.")
    private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("water");
}
