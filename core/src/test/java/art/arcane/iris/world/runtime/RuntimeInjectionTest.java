package art.arcane.iris.world.runtime;

import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.testsupport.BukkitTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Attaching the agent and retransforming server classes costs about a second of every boot, and a server
 * that never loads an Iris world never needs either. Deferring it only stays safe while a deferred failure
 * locks the runtime exactly like a boot-time one did, so the world it was about to generate is refused
 * rather than handed to uninstrumented server code.
 */
public class RuntimeInjectionTest {
    @Before
    public void resetInjection() {
        BukkitTestServer.install();
        RuntimeInjection.reset();
    }

    @After
    public void clearValidation() {
        RuntimeInjection.reset();
        IrisStartupValidation.disable();
    }

    @Test
    public void anInstallWithoutAUsableNmsBindingIsRefusedAndNamesTheReason() {
        RuntimeInjection.Outcome outcome = RuntimeInjection.install();

        assertFalse(outcome.installed());
        assertEquals(RuntimeInjection.Failure.NO_BINDING, outcome.failure());
        assertTrue(outcome.lockReason(), outcome.lockReason().contains("NMS runtime"));
    }

    @Test
    public void theOutcomeIsResolvedOnceUntilReset() {
        RuntimeInjection.Outcome first = RuntimeInjection.install();

        assertSame(first, RuntimeInjection.install());

        RuntimeInjection.reset();
        assertFalse(RuntimeInjection.install() == first);
    }

    @Test
    public void aDeferredInstallFailureLocksTheRuntimeWithItsReason() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.markRuntimeReady();
        assertTrue(IrisStartupValidation.isReady());

        RuntimeInjection.installIfDeferred();

        assertFalse(IrisStartupValidation.isReady());
        assertEquals(RuntimeInjection.install().lockReason(),
                IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void anUninstalledRuntimeReportsItself() {
        RuntimeInjection.install();

        assertFalse(RuntimeInjection.isInstalled());
    }
}
