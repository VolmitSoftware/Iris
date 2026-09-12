package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls underground-river source allocation independently from surface rivers.")
@Data
public class IrisUndergroundRiverSourceConfig {
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected natural underground sources per qualifying hydrology tile.")
    private double density = 0.25D;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum underground source quota considered for qualifying tiles when policy requires headwaters and a legal outlet exists.")
    private int minimumPerTile = 0;

    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Minimum distance in blocks between natural underground sources.")
    private int minimumSpacing = 512;
}
