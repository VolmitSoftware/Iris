package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.value.IrisRange;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("cave-field-module")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a modular cave-density layer.")
@Data
public class IrisCaveFieldModule {
    @Description("Density style used by this module.")
    private IrisGeneratorStyle style = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

    @MinNumber(0)
    @Description("Layer contribution multiplier.")
    private double weight = 1;

    @MinNumber(-1)
    @MaxNumber(1)
    @Description("Threshold offset applied to this layer before blending.")
    private double threshold = 0;

    @Description("Vertical bounds where this module can contribute, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private IrisRange verticalRange = new IrisRange(0, 384);

    @Description("Invert this module before weighting.")
    private boolean invert = false;
}
