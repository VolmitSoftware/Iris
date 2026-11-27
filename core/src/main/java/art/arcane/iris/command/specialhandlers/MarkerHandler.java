package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.runtime.Engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class MarkerHandler extends OpenSuggestionHandler {
    @Override
    protected Collection<String> suggestions() {
        Engine activeEngine = engine();
        if (activeEngine == null || activeEngine.getData() == null) {
            return List.of();
        }
        return Arrays.stream(activeEngine.getData().getMarkerLoader().getPossibleKeys()).sorted().toList();
    }
}
