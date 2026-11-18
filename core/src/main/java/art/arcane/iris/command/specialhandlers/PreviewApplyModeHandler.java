package art.arcane.iris.command.specialhandlers;

import java.util.Locale;

public final class PreviewApplyModeHandler extends FixedChoiceHandler {
    public PreviewApplyModeHandler() {
        super("preview", "apply");
    }

    @Override
    protected String canonicalize(String input) {
        String mode = input.trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("preview") && !mode.equals("apply")) {
            throw new IllegalArgumentException("Mode must be preview or apply.");
        }
        return mode;
    }
}
