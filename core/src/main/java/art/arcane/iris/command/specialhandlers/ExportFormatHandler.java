package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.pack.datapack.export.DatapackExportDestinations;

import java.util.Locale;

public final class ExportFormatHandler extends FixedChoiceHandler {
    public ExportFormatHandler() {
        super("zip", "directory");
    }

    @Override
    protected String canonicalize(String input) {
        return DatapackExportDestinations.parseFormat(input).name().toLowerCase(Locale.ROOT);
    }
}
