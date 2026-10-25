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
@Description("Flooded passages that open in coastal cliffs and run inland, up the valleys where the drainage allows and onward under higher ground, with the water held at sea level the whole way, ending in a pool chamber. An inlet, its chamber and its seal stay within 512 blocks of the coast.")
@Data
public class IrisSeaInlets {
    @Description("Cuts sea inlets into coastal cliffs.")
    private boolean enabled = true;

    @MinNumber(32)
    @MaxNumber(4096)
    @Description("Size in blocks of the square cells of the site grid. Each cell holds at most one inlet; inlets in neighbouring cells can lie closer than this.")
    private int spacing = 192;

    @MinNumber(1)
    @MaxNumber(128)
    @Description("Height in blocks the coastal land must rise above sea level to be a site. The passage also needs ground at least headroom min + roofClearance + 2 blocks above sea level within about 16 blocks of the coast, so gentle coasts hold no inlets.")
    private int minimumCliffHeight = 8;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Length of an inlet in blocks, written as [min, max]. The max plus 12, plus the larger of halfWidth max and chamberRadius max, plus 3 must not exceed 512.")
    private KList<Integer> length = new KList<>(64, 256);

    @ArrayType(type = Double.class, min = 2)
    @Description("Half-width of the passage in blocks, written as [min, max], each from 1 to 16. The passage narrows toward its inland end.")
    private KList<Double> halfWidth = new KList<>(2D, 5D);

    @ArrayType(type = Integer.class, min = 2)
    @Description("Water depth below sea level in blocks, written as [min, max], each from 2 to 16. Two blocks keep liquid water under an open slot's top sea layer.")
    private KList<Integer> depth = new KList<>(2, 5);

    @ArrayType(type = Integer.class, min = 2)
    @Description("Air space above the water in blocks, written as [min, max], each from 0 to 32. It varies smoothly along the passage and shrinks where the ground above is thin.")
    private KList<Integer> headroom = new KList<>(4, 10);

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Least solid ground in blocks kept between the passage roof and the surface.")
    private int roofClearance = 6;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Radius in blocks of the pool chamber at the inland end, written as [min, max], each from 1 to 32.")
    private KList<Integer> chamberRadius = new KList<>(6, 12);
}
