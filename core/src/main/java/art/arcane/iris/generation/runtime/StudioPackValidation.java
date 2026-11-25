package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;

/**
 * When a Studio hotload runs the full pack validator. Before activation an invalid pack is rejected and the hotload
 * waits for the validator; after activation the pack goes live first and blocking errors are only reported.
 */
enum StudioPackValidation {
    BEFORE_ACTIVATION,
    AFTER_ACTIVATION;

    static StudioPackValidation configured() {
        return IrisSettings.get().getStudio().isValidateBeforeHotload() ? BEFORE_ACTIVATION : AFTER_ACTIVATION;
    }
}
