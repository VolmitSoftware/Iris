package art.arcane.iris.modded.localization;

import art.arcane.iris.localization.IrisMessageContributor;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.ArrayList;
import java.util.List;

public final class ModdedMessageContributor implements IrisMessageContributor {
    @Override
    public List<MessageKey> keys() {
        List<MessageKey> keys = new ArrayList<>();
        keys.addAll(ModdedCommandMessages.keys());
        keys.addAll(ModdedHelpMessages.keys());
        keys.addAll(ClientUiMessages.keys());
        return List.copyOf(keys);
    }
}
