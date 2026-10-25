package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Standing water on the floors of carved caves. A pool fills a dip in the cave floor only up to the highest level the surrounding rock holds.")
@Data
public class IrisCavePools {
    @Description("Fills dips in cave floors with standing water.")
    private boolean enabled = true;

    @MinNumber(0)
    @MaxNumber(8)
    @Description("Candidate pool sites per chunk.")
    private double density = 0.15D;

    @MinNumber(1)
    @MaxNumber(4096)
    @Description("Smallest pool surface in square blocks.")
    private int minArea = 6;

    @MinNumber(1)
    @MaxNumber(65536)
    @Description("Largest pool surface in square blocks.")
    private int maxArea = 400;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Deepest pool in blocks.")
    private int maxDepth = 4;

    @MinNumber(1)
    @MaxNumber(15)
    @Description("Distance in blocks a pool may spread from its site, at most 15.")
    private int radius = 15;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Blocks the pool surface sits below the highest level the cave holds, written as [min, max].")
    private KList<Integer> freeboard = new KList<>(0, 1);

    @Required
    @Description("Name of the fluid profile that fills cave pools. A cave biome's hydrology policy fluid replaces it.")
    private String fluid = "water";
}
