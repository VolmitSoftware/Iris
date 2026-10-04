package art.arcane.iris.generation.subterrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Snippet("subterrain-feature")
@Description("Bounded, deterministic underground rooms, passages, retained fluids and formations.")
public class IrisSubterrainFeature {
    @Description("Unique stable feature definition id. Placement is derived from this id and the world seed.")
    private String id = "";
    @Description("Enable this feature definition.")
    private boolean enabled = true;
    @Description("Deliberate underground geometry family.")
    private IrisSubterrainFamily family = IrisSubterrainFamily.CENOTE;
    @RegistryListResource(IrisBiome.class)
    @Description("Biome that owns occupied cells in this feature. Solid rims and surrounding terrain retain their biome.")
    private String biome = "";
    @Description("Inclusive absolute world Y bounds. The entire feature including seals must fit inside this band.")
    private IrisRange worldYRange = new IrisRange(-48, 48);
    @MinNumber(32)
    @MaxNumber(8192)
    @Description("Placement grid spacing in blocks. Each grid cell supplies at most one seed-derived candidate.")
    private int spacing = 512;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Probability that each placement grid cell contains this feature.")
    private double probability = 0.35;
    @MinNumber(16)
    @MaxNumber(2048)
    @Description("Passage or fault length in blocks. Tectonic faults require at least 200 blocks.")
    private int length = 256;
    @MinNumber(6)
    @MaxNumber(256)
    @Description("Cenote horizontal radius, or half-width of faults, tubes and terraces.")
    private int radius = 32;
    @MinNumber(8)
    @MaxNumber(192)
    @Description("Main room height in blocks.")
    private int height = 32;
    @Description("Retained fluid. When omitted, lava tubes use LAVA and cenotes and terraces use WATER. Faults remain dry.")
    private IrisSubterrainFluid fluid;
    @MinNumber(0)
    @MaxNumber(32)
    @Description("Depth of retained water or lava above the basin floor. Faults remain dry.")
    private int fluidDepth = 4;
    @MinNumber(0)
    @MaxNumber(96)
    @Description("Height of connected lava-tube hornito shafts above the tube vault.")
    private int chimneyHeight = 16;
    @MinNumber(2)
    @MaxNumber(16)
    @Description("Number of stepped travertine basins.")
    private int terraceCount = 6;
    @MinNumber(0)
    @MaxNumber(128)
    @Description("Grid distance between continuous floor-to-ceiling pillars. Zero disables pillars.")
    private int pillarSpacing = 24;
    @MinNumber(0)
    @MaxNumber(0.4)
    @Description("Fraction of available vault height occupied by each stalactite or stalagmite. Zero disables formations.")
    private double formationFraction = 0.15;
    @Description("Block state used for seals, pillars, rims, walkways and formations.")
    private String solid = "minecraft:stone";
    @Description("Priority between authored features. Higher values win; stable feature ids break ties.")
    private int priority = 0;
}
