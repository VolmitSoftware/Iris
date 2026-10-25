package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.function.Function;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Hydrology settings for a dimension, region or biome. A value left unset is inherited from the region, then from the dimension.")
@Data
public class IrisHydrologyPolicy {
    @MinNumber(0)
    @MaxNumber(16)
    @Description("Multiplier on the water that falls in this area. Higher values grow more and larger streams and keep ponds fuller. With no value set anywhere it is 1.")
    private Double rainfall = null;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Multiplier on how many ponds form in this area. With no value set anywhere it is 1.")
    private Double pondDensity = null;

    @Description("Whether streams and rivers may form in this area. With no value set anywhere they are allowed.")
    private IrisHydrologyPermission streams = null;

    @Description("Whether ponds may form in this area. With no value set anywhere they are allowed.")
    private IrisHydrologyPermission ponds = null;

    @Description("Name of a fluid profile that fills ponds and lakes whose floor lies in this area, instead of the ponds' own fluid. Channels always use the fluid of their tier.")
    private String fluid = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("Biomes placed along the water's edge in this area. An empty list clears the inherited biomes.")
    private KList<String> shoreBiomes = null;

    @MinNumber(0)
    @MaxNumber(31)
    @Description("Width in blocks of the shore biome band along the water. 0 places no shore biome.")
    private Integer shoreBiomeWidth = null;

    @ArrayType(type = IrisDecorator.class)
    @Description("Plants for the rivers, streams, ponds and lakes in this area, replacing the decorators of the tiers and ponds there. Written like the tier and pond decorators: partOf SEA_FLOOR, SEA_SURFACE or SHORE_LINE. An empty list grows no plants here. With no value set anywhere, the tiers and ponds use their own decorators.")
    private KList<IrisDecorator> decorators = null;

    public static <T> T inherited(IrisBiome biome, IrisRegion region, IrisDimension dimension, Function<IrisHydrologyPolicy, T> field) {
        T value = value(biome == null ? null : biome.getHydrologyPolicy(), field);
        if (value == null) {
            value = value(region == null ? null : region.getHydrologyPolicy(), field);
        }
        return value != null ? value : value(dimension == null ? null : dimension.getHydrologyPolicy(), field);
    }

    private static <T> T value(IrisHydrologyPolicy policy, Function<IrisHydrologyPolicy, T> field) {
        return policy == null ? null : field.apply(policy);
    }
}
