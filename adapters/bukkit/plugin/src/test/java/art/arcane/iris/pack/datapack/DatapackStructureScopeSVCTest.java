package art.arcane.iris.pack.datapack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DatapackStructureScopeSVCTest {
    @Test
    public void structureScopeRunsAfterIrisGeneratorInjectionAndBeforeSpawnChunks() throws NoSuchMethodException {
        Method handler = DatapackStructureScopeSVC.class.getMethod("onWorldInit", WorldInitEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
    }

    @Test
    public void unloadAbandonsRetainedStudioStateBeforeEngineTeardown() throws NoSuchMethodException {
        Method handler = DatapackStructureScopeSVC.class.getMethod("onWorldUnload", WorldUnloadEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.LOWEST, annotation.priority());
    }

    @Test
    public void emptyScopeStillAppliesToJigsawStudioBootstrap() {
        assertTrue(DatapackStructureScopeSVC.shouldApplyScope(true, true, false));
        assertFalse(DatapackStructureScopeSVC.shouldApplyScope(true, false, false));
        assertTrue(DatapackStructureScopeSVC.shouldApplyScope(false, false, false));
        assertTrue(DatapackStructureScopeSVC.shouldApplyScope(true, false, true));
    }
}
