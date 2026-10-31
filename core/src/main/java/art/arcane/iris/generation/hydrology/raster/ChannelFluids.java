package art.arcane.iris.generation.hydrology.raster;

@FunctionalInterface
public interface ChannelFluids {
    ChannelFluids TIER = (x, z) -> null;

    String channelFluid(int x, int z);
}
