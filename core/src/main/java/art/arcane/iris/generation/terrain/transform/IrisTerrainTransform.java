package art.arcane.iris.generation.terrain.transform;

import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Data
@Description("An external terrain transformer with its exact implementation version and generation settings.")
public class IrisTerrainTransform {
    @Required
    @Description("The installed terrain transform provider identifier.")
    private String id;

    @Required
    @Description("The exact provider implementation version required to generate this dimension.")
    private String version;

    @Description("Provider settings saved with the generation pack. Values are strings interpreted by the provider.")
    private KMap<String, String> settings = new KMap<>();
}
