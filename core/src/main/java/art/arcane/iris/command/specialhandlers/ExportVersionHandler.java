package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;

public final class ExportVersionHandler extends FixedChoiceHandler {
    public ExportVersionHandler() {
        super(WorldgenExportTargets.SUPPORTED.stream().map(DataVersion::getVersion).toArray(String[]::new));
    }

    @Override
    protected String canonicalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return WorldgenExportTargets.resolve(input).getVersion();
    }
}
