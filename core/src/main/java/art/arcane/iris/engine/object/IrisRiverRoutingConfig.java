package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Controls bounded drainage planning and legal river outlets.")
@Data
public class IrisRiverRoutingConfig {
    @MinNumber(256)
    @MaxNumber(8192)
    @Desc("Hydrology planning tile size in blocks.")
    private int tileSize = 2048;

    @MinNumber(8)
    @MaxNumber(64)
    @Desc("Spacing in blocks between terrain samples used to build the drainage graph.")
    private int sampleSpacing = 64;

    @MinNumber(256)
    @MaxNumber(32768)
    @Desc("Maximum bounded length in blocks of one accepted river route.")
    private int maximumRouteLength = 16384;

    @MinNumber(0)
    @MaxNumber(32768)
    @Desc("Minimum exposed source-to-outlet length in blocks of a published surface river.")
    private int minimumSurfaceCourseLength = 384;

    @MinNumber(0)
    @MaxNumber(32768)
    @Desc("Minimum source-to-outlet length in blocks of a published underground river.")
    private int minimumUndergroundCourseLength = 192;

    @MinNumber(1)
    @MaxNumber(256)
    @Desc("Maximum inland drainage roots (sinkhole grottos) selected per planning tile. Unused when no inland outlet kind is enabled.")
    private int maximumOutletsPerTile = 4;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Maximum sea outlets (river mouths and coastal grottos) selected per planning tile, chosen by type in turn: the best mouth, the best coastal grotto, the next mouth, and so on, each at least two sample spacings from the others. Budgeted separately from maximumOutletsPerTile.")
    private int maximumCoastalOutletsPerTile = 2;

    @Desc("Allow rivers to terminate at an ocean reservoir.")
    private boolean oceanOutlets = true;

    @ArrayType(type = IrisRiverInlandOutlet.class)
    @Desc("Contained inland outlet kinds available when no legal ocean outlet is selected.")
    private KList<IrisRiverInlandOutlet> inlandOutlets = new KList<>();

    @MinNumber(0)
    @MaxNumber(8)
    @Desc("How strongly routes prefer lower ground.")
    private double valleyPreference = 1.5D;

    @MinNumber(0)
    @MaxNumber(128)
    @Desc("Cost per block of rise along a route.")
    private double uphillPenalty = 24D;

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Cost per unit of terrain slope along a route.")
    private double slopePenalty = 2D;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How strongly routes are drawn toward existing drainage.")
    private double confluenceAttraction = 0.2D;

    @MinNumber(0)
    @MaxNumber(8)
    @Desc("How strongly longer source-to-outlet routes are preferred when choosing river sources; 0 ranks by elevation alone.")
    private double lengthPreference = 1D;

    @MinNumber(0)
    @MaxNumber(4)
    @Desc("Extra surface courses an outlet may accept as tributaries joining its main river; 0 keeps one river per outlet.")
    private int tributaries = 1;

    @Desc("Regional drainage and channels between coasts, planned above the local tile lattice.")
    private IrisRiverRegionalConfig regional = new IrisRiverRegionalConfig();
}
