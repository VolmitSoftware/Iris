package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Desc("Regional drainage trunks shared across generation tiles and lowland channels between two coasts.")
public class IrisRiverRegionalConfig {
    @Desc("Plan regional drainage before local rivers.")
    private boolean enabled = true;

    @MinNumber(128)
    @MaxNumber(1024)
    @Desc("Coarse regional terrain sample spacing in blocks; a power of two.")
    private int sampleSpacing = 256;

    @MinNumber(256)
    @MaxNumber(32768)
    @Desc("Minimum length of a regional trunk or coastal channel.")
    private int minimumLength = 2048;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("Maximum regional trunks accepted in one drainage basin.")
    private int maximumTrunks = 2;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Maximum regional basin graphs retained in memory.")
    private int maximumCachedBasins = 8;

    @MinNumber(16384)
    @MaxNumber(1048576)
    @Desc("Maximum complete hydraulic stations retained across cached regional basins.")
    private int maximumCachedStations = 131072;

    @Desc("Allow a sea-level lowland channel with an ocean connection at both ends.")
    private boolean coastalChannels = true;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Chance that a basin tries a coastal channel before ordinary drainage trunks.")
    private double coastalChannelChance = 0.25D;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("Maximum terrain incision along a channel between two coasts.")
    private int maximumCoastalIncision = 8;
}
