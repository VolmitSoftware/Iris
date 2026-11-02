package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Bounds on how wet the dimension's wet and dry zones make this area. The zone wetness runs from the climate's dryRainfall in dry zones to 1 in wet zones and is held between min and max here. The bounded wetness scales rainfall and pond density, and a pond or lake whose floor lies here drops ponds.climateDrawdown times one minus it, rounded, below its freeboard.")
@Data
public class IrisHydrologyWetness {
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Lowest wetness this area falls to, even inside a dry zone. 0.7 keeps 70% of the rainfall and pond density in dry zones and lowers ponds and lakes by at most 30% of ponds.climateDrawdown. Left out, it is 0.")
    private double min = 0D;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Highest wetness this area reaches, even inside a wet zone. 0.35 keeps at most 35% of the rainfall and pond density and lowers ponds and lakes by at least 65% of ponds.climateDrawdown. Must not be below min. Left out, it is 1.")
    private double max = 1D;
}
