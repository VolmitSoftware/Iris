package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.spi.IrisPlatforms;

import java.util.Collection;

public final class RegisteredStructureKeyHandler extends OpenSuggestionHandler {
    @Override
    protected Collection<String> suggestions() {
        return IrisPlatforms.get().structureHooks().structureKeys().stream().sorted().toList();
    }
}
