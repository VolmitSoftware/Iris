package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Sea caves: water passages at sea level that run from the sea into coastal rock, winding like a river under an irregular natural roof and opening out into caverns. They form where tall rock meets the coast and stay clear of rivers, ponds, lakes and sea inlets.")
@Data
public class IrisGrottos {
    @Description("Cuts grottos into tall coasts.")
    private boolean enabled = true;

    @MinNumber(64)
    @MaxNumber(4096)
    @Description("Size in blocks of the square cells of the site grid. Each cell holds at most one grotto.")
    private int spacing = 176;

    @ArrayType(type = Double.class, min = 2)
    @Description("Half-width of the passage in blocks, written as [min, max], each from 2 to 16. Caverns open wider than this, and the rock around a passage can pinch it narrower.")
    private KList<Double> radius = new KList<>(4D, 7D);

    @ArrayType(type = Integer.class, min = 2)
    @Description("Height of the roof above the water in blocks, written as [min, max], each from 3 to 32. Caverns rise higher than this, and a roof stays under the ground above it.")
    private KList<Integer> headroom = new KList<>(5, 10);

    @ArrayType(type = Double.class, min = 2)
    @Description("Half-width of the opening at the sea in blocks, written as [min, max], each from 3 to 24.")
    private KList<Double> mouthWidth = new KList<>(7D, 11D);

    @ArrayType(type = Integer.class, min = 2)
    @Description("Length of a grotto in blocks along its winding centre line, written as [min, max]. A passage that cannot reach the min is left out. The max must not exceed 384.")
    private KList<Integer> length = new KList<>(64, 240);

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How irregular the walls, roof and floor are. 0 leaves smooth arched tubes; 1 gives deep overhangs, hanging rock and pockets.")
    private double roughness = 0.6D;
}
