package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;

import java.util.Collection;

public abstract class OpenSuggestionHandler implements DirectorParameterHandler<String> {
    protected abstract Collection<String> suggestions();

    @Override
    public KList<String> getPossibilities() {
        return new KList<>(suggestions());
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String input, boolean force) {
        return input == null ? "" : input.trim();
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == String.class;
    }
}
