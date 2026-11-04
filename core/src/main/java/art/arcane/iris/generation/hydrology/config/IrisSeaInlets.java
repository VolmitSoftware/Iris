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
@Description("Tidal arms of the sea: winding water courses open to the sky, flat at sea level and filled with sea water, wide at the coast and narrowing inland between shaped banks. They form on low coasts and keep clear of rivers, ponds and lakes.")
@Data
public class IrisSeaInlets {
    @Description("Cuts sea inlets into low coasts.")
    private boolean enabled = true;

    @MinNumber(32)
    @MaxNumber(4096)
    @Description("Size in blocks of the square cells of the site grid. Each cell holds at most one inlet; inlets in neighbouring cells can lie closer than this.")
    private int spacing = 160;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Length of an inlet in blocks along its centre line, written as [min, max]. An arm that cannot reach the min is left out. The max, plus the meander, plus mouthHalfWidth max, plus bankBand must not exceed 512.")
    private KList<Integer> length = new KList<>(64, 256);

    @MinNumber(2)
    @MaxNumber(128)
    @Description("Highest ground in blocks above sea level an inlet may cut through. An arm also stays under the height its bank band can grade back without a sheer wall, about 1.7 times bankBand. It routes around or stops short of higher ground, so lower values keep inlets out of hills.")
    private int maximumBankHeight = 44;

    @ArrayType(type = Double.class, min = 2)
    @Description("Half-width of the water at the sea in blocks, written as [min, max], each from 2 to 32. The min must not be below headHalfWidth max.")
    private KList<Double> mouthHalfWidth = new KList<>(13D, 15D);

    @ArrayType(type = Double.class, min = 2)
    @Description("Half-width of the water at the inland end in blocks, written as [min, max], each from 1 to 8.")
    private KList<Double> headHalfWidth = new KList<>(2.5D, 3D);

    @MinNumber(0.5)
    @MaxNumber(4)
    @Description("How the width falls from mouth to head. 1 narrows evenly; above 1 stays narrow further from the head and widens late near the sea; below 1 widens early.")
    private double taper = 1.4D;

    @MinNumber(0)
    @MaxNumber(0.5)
    @Description("Slow change in width along the arm, as a fraction of the width at that point.")
    private double widthVariation = 0.18D;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Half-width in blocks that sets the size of the bends, as a river of that half-width would meander. Larger values give longer, wider bends.")
    private double meanderWidth = 4D;

    @ArrayType(type = Integer.class, min = 2)
    @Description("Water depth at the middle of the arm in blocks, written as [min, max], each from 2 to 16. The depth runs from min at the head to max toward the sea, and shoals to 1 at both ends.")
    private KList<Integer> depth = new KList<>(2, 5);

    @MinNumber(4)
    @MaxNumber(64)
    @Description("Width in blocks of the band of ground beside the water that is shaped into banks.")
    private int bankBand = 32;

    @Description("Profile of the banks across bankBand.")
    private IrisBankStyle bankStyle = IrisBankStyle.SMOOTH;
}
