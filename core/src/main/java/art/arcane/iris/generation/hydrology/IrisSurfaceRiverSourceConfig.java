package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls surface-river source allocation.")
@Data
public class IrisSurfaceRiverSourceConfig {
    @MinNumber(0)
    @MaxNumber(64)
    @Description("Expected natural surface headwaters per qualifying hydrology tile.")
    private double density = 0.5D;

    @MinNumber(-2048)
    @MaxNumber(2048)
    @Description("Minimum natural terrain elevation eligible for a surface headwater.")
    private int minimumElevation = 88;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Minimum source quota considered for qualifying tiles when policy requires headwaters and a legal outlet exists.")
    private int minimumPerTile = 0;

    @MinNumber(0)
    @MaxNumber(8192)
    @Description("Minimum distance in blocks between natural surface headwaters.")
    private int minimumSpacing = 384;
}
