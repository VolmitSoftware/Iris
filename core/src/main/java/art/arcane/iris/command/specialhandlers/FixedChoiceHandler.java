package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.List;

public abstract class FixedChoiceHandler implements DirectorParameterHandler<String> {
    private final List<String> choices;

    protected FixedChoiceHandler(String... choices) {
        this.choices = List.of(choices);
    }

    protected abstract String canonicalize(String input);

    @Override
    public KList<String> getPossibilities() {
        return new KList<>(choices);
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String input, boolean force) throws DirectorParsingException {
        try {
            return canonicalize(input);
        } catch (IllegalArgumentException error) {
            throw new DirectorParsingException(error.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == String.class;
    }
}
