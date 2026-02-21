package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("dimension-carving-entry")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Dimension-level cave biome override with absolute world Y bounds.")
@Data
public class IrisDimensionCarvingEntry {
    @Desc("Stable id for this carving entry")
    private String id = "";

    @Desc("Enable or disable this carving entry")
    private boolean enabled = true;

    @RegistryListResource(IrisBiome.class)
    @Desc("Cave biome to apply when world Y falls within worldYRange")
    private String biome = "";

    @Desc("Absolute world Y bounds where this carving entry applies")
    private IrisRange worldYRange = new IrisRange(-64, 320);
}
