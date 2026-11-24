package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.datapack.DatapackIngestService;

import java.util.Collection;

public final class InstalledDatapackHandler extends OpenSuggestionHandler {
    @Override
    protected Collection<String> suggestions() {
        return DatapackIngestService.installed().stream().map(entry -> entry.id).toList();
    }
}
