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
@Description("Water for this dimension: rivers and streams that follow the drainage of the terrain, ponds and lakes at every height, and underground water.")
@Data
public class IrisHydrology {
    @Description("Turns rivers, streams, ponds, lakes and underground water on for this dimension.")
    private boolean enabled = false;

    @Description("How water drains across the terrain before rivers and ponds are placed.")
    private IrisHydrologyDrainage drainage = new IrisHydrologyDrainage();

    @ArrayType(type = IrisHydrologyTier.class, min = 1)
    @Description("Size classes of flowing water, from the smallest stream to the largest river. Each channel takes the largest tier whose minimum catchment it reaches.")
    private KList<IrisHydrologyTier> tiers = new KList<>(
            new IrisHydrologyTier(),
            new IrisHydrologyTier()
                    .setName("river")
                    .setMinCatchment(20000D)
                    .setHalfWidth(new KList<>(2D, 8D))
                    .setDepthRatio(0.45D)
                    .setInset(2)
                    .setBankBand(32),
            new IrisHydrologyTier()
                    .setName("major")
                    .setMinCatchment(320000D)
                    .setHalfWidth(new KList<>(8D, 16D))
                    .setDepthRatio(0.5D)
                    .setInset(3)
                    .setBankBand(48)
                    .setDeltas(true));

    @Description("Standing water above sea level: natural hollows, seeded cups and dams.")
    private IrisHydrologyPonds ponds = new IrisHydrologyPonds();

    @Description("Large lakes that fill the biggest hollows in the terrain.")
    private IrisHydrologyLakes lakes = new IrisHydrologyLakes();

    @Description("How the ground around water is reshaped.")
    private IrisHydrologyDeformation deformation = new IrisHydrologyDeformation();

    @Description("Water below the surface: flooded sea inlets and cave pools.")
    private IrisHydrologyUnderground underground = new IrisHydrologyUnderground();

    @ArrayType(type = IrisFluidProfile.class, min = 1)
    @Description("Named fluids that tiers, ponds, cave pools and hydrology policies fill their water with.")
    private KList<IrisFluidProfile> fluids = new KList<>(new IrisFluidProfile());

    @MinNumber(0)
    @MaxNumber(4)
    @Description("Rings of 512-block planning windows prepared ahead around each window that chunk generation asks for.")
    private int prefetchRings = 1;

    @Description("Saves finished water plans with the world, so they are not planned again after a restart.")
    private boolean persist = true;
}
