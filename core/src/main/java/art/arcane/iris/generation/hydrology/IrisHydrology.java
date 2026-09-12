package art.arcane.iris.generation.hydrology;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Dimension-owned physical hydrology configuration.")
@Data
public class IrisHydrology {
    @Description("Terrain-guided surface and underground river configuration.")
    private IrisRiverHydrology rivers = new IrisRiverHydrology();

    @ArrayType(type = IrisDeepFluidConfig.class)
    @Description("Independent deep-fluid systems that do not consume the surface or underground river source budgets.")
    private KList<IrisDeepFluidConfig> deepFluids = new KList<>();

    @ArrayType(type = IrisSurfacePoolConfig.class, min = 0)
    @Description("Standing surface pools such as lava pools. Each region or biome opts in through riverPolicy.surfacePools.")
    private KList<IrisSurfacePoolConfig> surfacePools = new KList<>();
}
