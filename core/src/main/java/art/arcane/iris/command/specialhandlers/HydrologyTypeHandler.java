package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public final class HydrologyTypeHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        return new KList<>(HydrologyFeatureType.keys());
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String input, boolean force) throws DirectorParsingException {
        try {
            return HydrologyFeatureType.parse(input).key();
        } catch (IllegalArgumentException error) {
            throw new DirectorParsingException(error.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == String.class;
    }

    @Override
    public String getRandomDefault() {
        return HydrologyFeatureType.RIVER.key();
    }
}
