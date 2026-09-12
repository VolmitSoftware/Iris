package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls organic river routing and contained cave geometry.")
@Data
public class IrisRiverGeometryConfig {
    @Description("Surface and underground centerline meanders.")
    private IrisRiverMeanderConfig meanders = new IrisRiverMeanderConfig();

    @Description("Surface river bed shape for waterfall and cascade throats, and the wall shape shared with the erosion compiler; roughness fields default to the channel roughness.")
    private IrisSurfaceRiverShapeConfig surface = new IrisSurfaceRiverShapeConfig();

    @Description("Three-dimensional bank shelves above the protected riverbed and waterline.")
    private IrisRiverBank3DConfig banks3D = new IrisRiverBank3DConfig();

    @Description("Underground channel bed and wall shape.")
    private IrisRiverChannelShapeConfig underground = new IrisRiverChannelShapeConfig();

    @Description("Grotto pool bed and wall shape.")
    private IrisRiverChannelShapeConfig grottos = new IrisRiverChannelShapeConfig();

    @Description("Underground drop, sinkhole and receiving basin shape.")
    private IrisRiverDropShapeConfig drops = new IrisRiverDropShapeConfig();
}
