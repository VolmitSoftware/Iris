package art.arcane.iris.core.service;

import art.arcane.iris.core.link.Identifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExternalDataSVCStateTest {
    @Test
    public void aDeferredBlockFindsItsProviderByTheIdWithoutItsState() {
        assertEquals(new Identifier("craftengine", "chair"),
                ExternalDataSVC.withoutState(new Identifier("craftengine", "chair[yaw=90,randomPitch=true]")));
    }

    @Test
    public void anIdWithoutStateStaysAsItIs() {
        Identifier chair = new Identifier("craftengine", "chair");

        assertEquals(chair, ExternalDataSVC.withoutState(chair));
    }
}
