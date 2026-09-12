package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.structure.object.IrisObjectScale;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.director.handlers.base.DoubleHandlerBase;

public final class ObjectScaleHandler extends DoubleHandlerBase {
    @Override
    public Double parse(String input, boolean force) throws DirectorParsingException {
        if (input.trim().equalsIgnoreCase("dimension")) {
            return null;
        }
        double factor = super.parse(input, force);
        try {
            return IrisObjectScale.requireValidFactor(factor, "scale");
        } catch (IllegalArgumentException failure) {
            throw new DirectorParsingException(failure.getMessage());
        }
    }

    @Override
    public String toString(Double value) {
        return value == null ? "dimension" : super.toString(value);
    }

    @Override
    public String getRandomDefault() {
        return "dimension";
    }
}
