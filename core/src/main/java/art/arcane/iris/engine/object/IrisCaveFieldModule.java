package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("cave-field-module")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a modular cave-density layer.")
@Data
public class IrisCaveFieldModule {
    @Desc("Density style used by this module.")
    private IrisGeneratorStyle style = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @MinNumber(0)
    @Desc("Layer contribution multiplier.")
    private double weight = 1;

    @MinNumber(-1)
    @MaxNumber(1)
    @Desc("Threshold offset applied to this layer before blending.")
    private double threshold = 0;

    @Desc("Vertical bounds where this module can contribute.")
    private IrisRange verticalRange = new IrisRange(0, 384);

    @Desc("Invert this module before weighting.")
    private boolean invert = false;
}
