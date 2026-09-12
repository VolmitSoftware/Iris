package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls terrain-guided surface rivers.")
@Data
public class IrisSurfaceRiverConfig {
    @Description("Enable the independent surface-river source budget.")
    private boolean enabled = true;

    @Description("Surface-river source allocation.")
    private IrisSurfaceRiverSourceConfig sources = new IrisSurfaceRiverSourceConfig();

    @Description("Wet channel width, depth, sink and roughness.")
    private IrisSurfaceRiverChannelConfig channel = new IrisSurfaceRiverChannelConfig();

    @Description("Shore band and eroded valley around the channel.")
    private IrisSurfaceRiverBankConfig banks = new IrisSurfaceRiverBankConfig();
    @Description("Material under and beside the water: falling-block replacement and padding.")
    private IrisSurfaceRiverBedConfig bed = new IrisSurfaceRiverBedConfig();

    @Description("Cascade and waterfall thresholds.")
    private IrisSurfaceRiverFlowConfig flow = new IrisSurfaceRiverFlowConfig();

    @Description("Coastal mouth flare and ocean-apron limits.")
    private IrisRiverMouthConfig mouths = new IrisRiverMouthConfig();
    @Description("How the ground around the channel is eroded into a valley.")
    private IrisSurfaceRiverErosionConfig erosion = new IrisSurfaceRiverErosionConfig();
    @Description("Ponds at the source and at the inland end of every surface river.")
    private IrisSurfaceRiverPondsConfig ponds = new IrisSurfaceRiverPondsConfig();
}
