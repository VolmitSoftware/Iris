package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.IrisEngineStreamType;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("expression-function")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a function to use in your expression. Do not set the name to x, y, or z, also don't duplicate names.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisExpressionFunction {
    @Required
    @Description("The function to assign this value to. Do not set the name to x, y, or z")
    private String name;

    @Description("If defined, this variable will use a generator style as it's value")
    private IrisGeneratorStyle styleValue = null;

    @Description("If defined, iris will use an internal stream from the engine as it's value")
    private IrisEngineStreamType engineStreamValue = null;

    @MinNumber(2)
    @Description("Number of arguments for the function")
    private int args = 2;

    public boolean isValid() {
        return styleValue != null || engineStreamValue != null;
    }

    public int argumentCount() {
        if (engineStreamValue != null) return 2;
        return Math.max(args, 2);
    }
}
