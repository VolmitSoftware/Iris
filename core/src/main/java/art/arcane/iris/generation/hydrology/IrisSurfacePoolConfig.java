package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("A standing surface pool: a bowl cut into open ground and filled with a fluid, placed where the river policy allows it.")
@Data
public class IrisSurfacePoolConfig {
    @Required
    @Description("Pool identifier. Policies list it under surfacePools and locators accept it as a selector.")
    private String id = "lava_pool";

    @Required
    @Description("Fluid palette filling the pool.")
    private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("lava");

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected pools per hydrology tile where the policy allows them.")
    private double density = 0.75D;

    @MinNumber(32)
    @MaxNumber(8192)
    @Description("Nominal spacing in blocks between candidate pool sites.")
    private int spacing = 384;

    @MinNumber(2)
    @MaxNumber(16)
    @Description("Smallest pool radius in blocks.")
    private int minimumRadius = 4;

    @MinNumber(2)
    @MaxNumber(16)
    @Description("Largest pool radius in blocks.")
    private int maximumRadius = 7;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("Fluid depth at the centre of the pool.")
    private int depth = 2;

    @RegistryListResource(IrisBiome.class)
    @Description("Biome applied to the pool bed and its rim. Leave empty to keep the surrounding biome.")
    private String biome = "";
}
