package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public final class SubterrainFeatureHandler extends OpenSuggestionHandler {
    @Override
    protected Collection<String> suggestions() {
        return Arrays.stream(IrisSubterrainFamily.values())
                .map(family -> family.name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
