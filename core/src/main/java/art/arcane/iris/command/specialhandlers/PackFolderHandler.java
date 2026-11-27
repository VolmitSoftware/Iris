package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisPlatforms;

import java.io.File;
import java.util.Collection;

public final class PackFolderHandler extends OpenSuggestionHandler {
    @Override
    protected Collection<String> suggestions() {
        return PackDirectoryResolver.listVisiblePackDirectories(IrisPlatforms.get().packsFolder())
                .stream()
                .map(File::getName)
                .sorted()
                .toList();
    }
}
