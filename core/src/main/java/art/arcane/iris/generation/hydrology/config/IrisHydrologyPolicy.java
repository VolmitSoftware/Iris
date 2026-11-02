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

import java.util.List;
import java.util.function.Function;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Hydrology settings for a dimension, region or biome. A value a biome leaves unset is inherited from the biome it grows inside as a child, up through that biome's own parents, then from the region, then from the dimension.")
@Data
public class IrisHydrologyPolicy {
    public static final double MAX_MULTIPLIER = 16D;

    @MinNumber(0)
    @MaxNumber(MAX_MULTIPLIER)
    @Description("Multiplier on the water that falls in this area. Higher values grow more and larger streams and keep ponds fuller. With no value set anywhere it is 1.")
    private Double rainfall = null;

    @MinNumber(0)
    @MaxNumber(MAX_MULTIPLIER)
    @Description("Multiplier on how many ponds form in this area. With no value set anywhere it is 1.")
    private Double pondDensity = null;

    @Description("Whether the dimension's wet and dry zones apply in this area. false keeps this area's full rainfall and pond density even inside a dry zone. With no value set anywhere they apply.")
    private Boolean climateZones = null;

    @Description("Bounds on the wetness of the dimension's wet and dry zones in this area: min keeps a swamp wet inside a dry zone, max keeps a desert mostly dry inside a wet zone. It has no effect where the wet and dry zones are off or climateZones is false. With no value set anywhere the zone wetness applies unchanged.")
    private IrisHydrologyWetness wetness = null;

    @Description("Whether streams and rivers may form in this area. With no value set anywhere they are allowed.")
    private IrisHydrologyPermission streams = null;

    @Description("Whether ponds may form in this area. With no value set anywhere they are allowed.")
    private IrisHydrologyPermission ponds = null;

    @Description("Name of a fluid profile that fills the water in this area instead of its usual fluid: ponds and lakes whose floor lies here, and every stretch of stream or river that flows through here.")
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

    public static boolean validMultiplier(double value) {
        return value >= 0D && value <= MAX_MULTIPLIER;
    }

    public static <T> T inherited(List<IrisBiome> biomes, IrisRegion region, IrisDimension dimension, Function<IrisHydrologyPolicy, T> field) {
        for (int index = 0; index < biomes.size(); index++) {
            T value = value(biomes.get(index).getHydrologyPolicy(), field);
            if (value != null) {
                return value;
            }
        }
        T value = value(region == null ? null : region.getHydrologyPolicy(), field);
        return value != null ? value : value(dimension == null ? null : dimension.getHydrologyPolicy(), field);
    }

    private static <T> T value(IrisHydrologyPolicy policy, Function<IrisHydrologyPolicy, T> field) {
        return policy == null ? null : field.apply(policy);
    }
}
