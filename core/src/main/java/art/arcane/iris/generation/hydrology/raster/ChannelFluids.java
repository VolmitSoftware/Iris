package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

@FunctionalInterface
public interface ChannelFluids {
    ChannelFluids TIER = (x, z) -> null;

    String channelFluid(int x, int z);

    default String reachFluid(Reach reach) {
        String area = channelFluid((int) Math.floor(reach.xs()[0]), (int) Math.floor(reach.zs()[0]));
        return area != null ? area : reach.tier().fluid();
    }
}
