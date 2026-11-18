package art.arcane.iris.command.specialhandlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PackFolderOrAllHandler extends PackFolderHandler {
    private static final String ALL_PACKS = "*";

    @Override
    protected Collection<String> suggestions() {
        List<String> options = new ArrayList<>();
        options.add(ALL_PACKS);
        options.addAll(super.suggestions());
        return options;
    }
}
