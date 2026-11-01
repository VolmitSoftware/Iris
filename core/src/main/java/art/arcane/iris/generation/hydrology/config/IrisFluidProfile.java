package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A named fluid. Tiers, ponds, cave pools and hydrology policies pick a fluid by its name.")
@Data
public class IrisFluidProfile {
    @Required
    @Description("Name that tiers, ponds, cave pools and hydrology policies use to pick this fluid.")
    private String name = "water";

    @Required
    @Description("Blocks that fill the water feature.")
    private IrisMaterialPalette palette = new IrisMaterialPalette().qclear().qadd("minecraft:water");
    @Description("Name of another fluid profile that fills this fluid's edge where it touches a different fluid, such as obsidian where lava meets water. Worldgen places fluids without updates, so unset leaves the two fluids side by side.")
    private String seam = null;
}
