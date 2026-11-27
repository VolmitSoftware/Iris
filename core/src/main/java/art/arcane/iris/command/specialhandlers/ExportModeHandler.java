package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.datapack.export.WorldgenExportMode;

import java.util.Locale;

public final class ExportModeHandler extends FixedChoiceHandler {
    public ExportModeHandler() {
        super("preset", "dimension", "overworld");
    }

    @Override
    protected String canonicalize(String input) {
        return WorldgenExportMode.parse(input).name().toLowerCase(Locale.ROOT);
    }
}
