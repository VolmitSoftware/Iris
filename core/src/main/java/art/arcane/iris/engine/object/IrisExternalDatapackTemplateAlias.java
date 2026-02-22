package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Desc("Maps missing template-pool element locations from an external datapack to replacement template locations")
public class IrisExternalDatapackTemplateAlias {
    @Desc("Source template location to rewrite")
    private String from = "";

    @Desc("Target template location. Use minecraft:empty to convert the element to an empty pool element")
    private String to = "";

    @Desc("Enable or disable this alias entry")
    private boolean enabled = true;
}
