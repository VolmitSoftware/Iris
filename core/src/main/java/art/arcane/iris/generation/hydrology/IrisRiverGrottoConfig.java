package art.arcane.iris.generation.hydrology;

import art.arcane.volmlib.util.documentation.Description;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Controls explicitly accepted coastal and inland river grottos.")
@Data
public class IrisRiverGrottoConfig {
    @Description("Coastal grotto configuration.")
    private IrisCoastalRiverGrottoConfig coastal = new IrisCoastalRiverGrottoConfig();

    @Description("Contained inland grotto configuration.")
    private IrisInlandRiverGrottoConfig inland = new IrisInlandRiverGrottoConfig();
}
