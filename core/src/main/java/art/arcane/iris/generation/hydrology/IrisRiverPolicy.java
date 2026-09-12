package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.validation.CompatPools;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Nullable river policy values inherited from dimension to region to biome.")
@Data
public class IrisRiverPolicy {
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected surface headwaters per full tile of eligible policy area. Null inherits the dimension source density.")
    private Double surfaceSourceDensity = null;

    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Minimum surface headwater separation in blocks. Two areas use the larger separation at their boundary.")
    private Integer surfaceSourceSpacing = null;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Extra surface tributaries admitted to one outlet from this policy area.")
    private Integer surfaceTributaries = null;

    @MinNumber(0)
    @MaxNumber(256)
    @Description("Maximum inland surface outlets per full tile of eligible policy area. Null inherits the dimension outlet limit.")
    private Integer surfaceInlandOutlets = null;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Maximum coastal surface outlets per full tile of eligible policy area. Null inherits the dimension coastal outlet limit.")
    private Integer surfaceCoastalOutlets = null;

    @MinNumber(16)
    @MaxNumber(4096)
    @Description("Minimum surface river course length in blocks for headwaters in this area. Null inherits the dimension routing minimum.")
    private Integer surfaceMinimumCourseLength = null;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("Maximum surface river incision in blocks in this area, before the incision multiplier. Null inherits the dimension channel limit.")
    private Integer surfaceMaximumIncision = null;

    @Description("Controls source and transit admission in this area.")
    private IrisRiverPlacementMode placement = null;

    @Description("Controls the routing preference or prohibition applied to this area.")
    private IrisRiverRoutingMode routing = null;

    @Description("Whether this area may contain an accepted river outlet.")
    private Boolean outletAdmission = null;

    @ArrayType(type = String.class)
    @Description("Permitted dimension-owned profiles along surface routes and preferred profiles for underground rivers. An empty list clears inheritance and resolves to the first configured dimension profile.")
    private KList<String> profiles = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("Surface channel biome selection. An empty list explicitly clears the inherited selection.")
    private KList<String> surfaceBiomes = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("River mouth biome selection. An empty list explicitly clears the inherited selection.")
    private KList<String> mouthBiomes = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("Narrow river shore biome selection. An empty list explicitly clears the inherited selection.")
    private KList<String> shoreBiomes = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("Dry river footprint biome selection. An empty list explicitly clears the inherited selection.")
    private KList<String> bankBiomes = null;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("Flooded cave and grotto biome selection. An empty list explicitly clears the inherited selection.")
    private KList<String> floodedCaveBiomes = null;

    @ArrayType(type = String.class)
    @Description("Standing surface pool ids (hydrology.surfacePools) allowed in this area. An empty list explicitly clears the inherited selection.")
    private KList<String> surfacePools = null;

    @MinNumber(0.0001)
    @MaxNumber(16)
    @Description("Multiplier applied to accepted river width.")
    private Double widthMultiplier = null;

    @MinNumber(0.0001)
    @MaxNumber(16)
    @Description("Multiplier applied to accepted river depth.")
    private Double depthMultiplier = null;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Multiplier applied to maximum terrain incision.")
    private Double incisionMultiplier = null;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Multiplier applied to terrain-guided routing cost.")
    private Double routingMultiplier = null;

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Multiplier applied to the eroded bank width outside the shore band.")
    private Double bankMultiplier = null;

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Width in blocks of the shore biome band beside a surface river in this area. Null inherits, and without any value the band is the surface banks shore width. Zero gives the area no shore biome band; the geometric shore and erosion are unchanged.")
    private Double shoreBiomeWidth = null;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Width in blocks of the flattened shore bench beside the water in this area, the geometric beach that is cut level with the bank top. Null inherits, and without any value the bench is the surface banks shoreWidth. Zero gives the area no bench, so the eroded valley side begins at the waterline.")
    private Double shoreWidth = null;

    @Description("Whether surface rivers erode the ground beyond the shore bench into a valley in this area. Null inherits, and without any value erosion follows surface.erosion.enabled. False keeps only the wet channel and the shore bench here, so a river crosses the area in a bare cut.")
    private Boolean erosion = null;

    @Description("Rivers that start in this area stay inside it: their whole course and their outlet lie in this region (when set on a region) or this biome (when set on a biome), and a river that flows into the area never leaves it. A source with no reachable outlet inside the area is rejected. Null inherits, false releases the area.")
    private Boolean confined = null;

    public SurfaceRiverPolicy surfacePolicy(String areaKey) {
        return new SurfaceRiverPolicy(areaKey, surfaceSourceDensity, surfaceSourceSpacing,
                surfaceTributaries, surfaceInlandOutlets, surfaceCoastalOutlets, surfaceMinimumCourseLength, surfaceMaximumIncision);
    }

    /**
     * A declared river biome selection with biomes the version-content gate excluded removed. A declared-but-now-empty
     * selection keeps its "explicitly clears the inherited selection" meaning; null (undeclared) stays null.
     */
    public KList<String> compatBiomes(KList<String> declared, IrisData data, String field) {
        return compatBiomes(declared, data, field, null);
    }

    public KList<String> compatBiomes(KList<String> declared, IrisData data, String field, Runnable unresolvedReference) {
        if (declared == null) {
            return null;
        }

        return CompatPools.surviving(data == null ? null : data.getBiomeLoader(), declared, data,
                "river policy", "", field, unresolvedReference);
    }

    public KSet<String> getAllBiomeIds() {
        KSet<String> biomeIds = new KSet<>();
        addAll(biomeIds, surfaceBiomes);
        addAll(biomeIds, mouthBiomes);
        addAll(biomeIds, shoreBiomes);
        addAll(biomeIds, bankBiomes);
        addAll(biomeIds, floodedCaveBiomes);
        return biomeIds;
    }

    private static void addAll(KSet<String> destination, KList<String> source) {
        if (source != null) {
            destination.addAll(source);
        }
    }
}
