package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.decoration.IrisDecorator;
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
@Description("Standing water above sea level. Ponds fill natural hollows, seeded cups and optional dams, each with its own level, so ponds sit at many heights and streams link them into chains. Lakes take their freeboard, dryDrawdown, shore roundness, materials, decorators and fluid from here as well.")
@Data
public class IrisHydrologyPonds {
    @Description("Ponds that fill hollows already present in the terrain.")
    private IrisPondNatural natural = new IrisPondNatural();

    @Description("Ponds cut into the terrain as bowls where the land has few hollows of its own.")
    private IrisPondSeeded seeded = new IrisPondSeeded();

    @Description("Raised rims that hold ponds above their natural spill height.")
    private IrisPondDams dams = new IrisPondDams();

    @ArrayType(type = Integer.class, min = 2)
    @Description("Blocks the pond or lake surface sits below its spill point, written as [min, max]. Each pond and lake picks a value in this range.")
    private KList<Integer> freeboard = new KList<>(1, 3);

    @MinNumber(0)
    @MaxNumber(32)
    @Description("Most extra blocks a pond or lake surface drops below its freeboard, in the driest areas. The drop is this value times the dryness at the floor, rounded: one minus the zone wetness, after the area's wetness bounds, where the dimension's wet and dry zones apply, and one minus the area's rainfall elsewhere, so water at a wetness or rainfall of 1 or more stays full.")
    private int dryDrawdown = 6;

    @Description("Shape of pond shores and floors. Lakes take only its roundness.")
    private IrisPondShore shore = new IrisPondShore();

    @Description("Blocks painted on pond and lake floors and shores.")
    private IrisHydrologyMaterials materials = new IrisHydrologyMaterials();

    @ArrayType(type = IrisDecorator.class)
    @Description("Plants for ponds and lakes, written like biome decorators. partOf chooses where each one grows: SEA_FLOOR on the floor under water (seagrass, kelp, coral; never taller than the water depth minus one, and only where the water is at least two blocks deep), SEA_SURFACE on the still water surface (lily pads) and SHORE_LINE on the dry shore beside the water (reeds, sugar cane, ferns). Nothing grows in lava. Empty by default.")
    private KList<IrisDecorator> decorators = new KList<>();

    @Required
    @Description("Name of the fluid profile that fills ponds and lakes.")
    private String fluid = "water";
}
