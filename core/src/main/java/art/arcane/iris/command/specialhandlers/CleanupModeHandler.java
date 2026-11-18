package art.arcane.iris.command.specialhandlers;

import java.util.Locale;

public final class CleanupModeHandler extends FixedChoiceHandler {
    public CleanupModeHandler() {
        super("preview", "apply");
    }

    @Override
    protected String canonicalize(String input) {
        String mode = input.trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("preview") && !mode.equals("apply")) {
            throw new IllegalArgumentException("Cleanup mode must be preview or apply.");
        }
        return mode;
    }
}
