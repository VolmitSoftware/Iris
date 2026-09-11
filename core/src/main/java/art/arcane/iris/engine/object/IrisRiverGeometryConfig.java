package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Controls organic river routing and contained cave geometry.")
@Data
public class IrisRiverGeometryConfig {
    @Desc("Surface and underground centerline meanders.")
    private IrisRiverMeanderConfig meanders = new IrisRiverMeanderConfig();

    @Desc("Surface river bed shape for waterfall and cascade throats, and the wall shape shared with the erosion compiler; roughness fields default to the channel roughness.")
    private IrisSurfaceRiverShapeConfig surface = new IrisSurfaceRiverShapeConfig();

    @Desc("Three-dimensional bank shelves above the protected riverbed and waterline.")
    private IrisRiverBank3DConfig banks3D = new IrisRiverBank3DConfig();

    @Desc("Underground channel bed and wall shape.")
    private IrisRiverChannelShapeConfig underground = new IrisRiverChannelShapeConfig();

    @Desc("Grotto pool bed and wall shape.")
    private IrisRiverChannelShapeConfig grottos = new IrisRiverChannelShapeConfig();

    @Desc("Underground drop, sinkhole and receiving basin shape.")
    private IrisRiverDropShapeConfig drops = new IrisRiverDropShapeConfig();
}
