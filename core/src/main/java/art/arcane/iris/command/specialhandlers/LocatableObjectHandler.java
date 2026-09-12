package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.volmlib.util.collection.KList;

public class LocatableObjectHandler extends ObjectHandler {
    @Override
    public KList<String> getPossibilities() {
        Engine activeEngine = engine();
        return activeEngine == null ? super.getPossibilities() : GenerationFindCatalog.objectKeys(activeEngine);
    }
}
