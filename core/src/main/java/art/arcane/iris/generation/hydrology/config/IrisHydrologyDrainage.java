package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("How water drains across the terrain. Hollows fill until they spill, every column gets a downhill path to the sea, and flow gathers into channels.")
@Data
public class IrisHydrologyDrainage {
    @MinNumber(0.00001)
    @MaxNumber(0.1)
    @Description("Height step in blocks added across filled flats, so water on level ground still has a downhill path.")
    private double fillEpsilon = 0.001D;

    @MinNumber(0)
    @MaxNumber(256)
    @Description("Hollows deeper than this many blocks are drained by a cut through their lowest rim instead of filling with water.")
    private int breachDepth = 24;

    @Description("Removes some stream heads in dry areas, so dry biomes have fewer streams than wet ones.")
    private boolean wetnessThinning = true;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Smallest share of stream heads kept in the driest areas when wetness thinning is on.")
    private double minimumHeadKeep = 0.25D;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("A stream only starts where the ground falls by at least this many blocks within 64 blocks downhill, so level ground does not sprout streams. 0 lets streams start anywhere.")
    private double headRelief = 1D;

    @MinNumber(1024)
    @MaxNumber(1073741824)
    @Description("Catchment in square blocks at which flow stops growing, which caps the width of rivers far downstream.")
    private double saturationCatchment = 4194304D;
}
