package art.arcane.iris.generation.hydrology;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Dimension-owned terrain-guided river configuration.")
@Data
public class IrisRiverHydrology {
    @Description("Enable river planning for this dimension.")
    private boolean enabled = false;

    @Description("Drainage sampling, refinement, and outlet configuration.")
    private IrisRiverRoutingConfig routing = new IrisRiverRoutingConfig();

    @Description("Organic centerline, bed, wall, and descending-water geometry.")
    private IrisRiverGeometryConfig geometry = new IrisRiverGeometryConfig();

    @Description("Surface river source, channel, bank, flow, and mouth configuration.")
    private IrisSurfaceRiverConfig surface = new IrisSurfaceRiverConfig();

    @Description("Independent underground river source and channel configuration.")
    private IrisUndergroundRiverConfig underground = new IrisUndergroundRiverConfig();

    @Description("Contained coastal and inland grotto configuration.")
    private IrisRiverGrottoConfig grottos = new IrisRiverGrottoConfig();

    @ArrayType(min = 1, type = IrisRiverProfile.class)
    @Description("Reusable river fluid profiles selected by river policies.")
    private KList<IrisRiverProfile> profiles = new KList<IrisRiverProfile>().qadd(new IrisRiverProfile());
}
