package art.arcane.iris.core.safeguard;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.safeguard.task.CheckResult;
import art.arcane.iris.core.safeguard.task.Diagnostic;
import art.arcane.iris.core.safeguard.task.Task;
import art.arcane.iris.core.safeguard.task.Tasks;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.BukkitTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * With injection deferred the injection check has nothing to verify yet, so it must say so and stay Stable
 * rather than reporting a Danger the server has not actually hit. Turning the setting on brings the boot
 * verification back.
 */
public class TasksInjectionDeferralTest {
    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;

    @Before
    public void installServerAndSettings() {
        BukkitTestServer.install();
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatforms.bind(mock(IrisPlatform.class));
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void restore() {
        IrisSettings.settings = previousSettings;
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void injectionIsDeferredByDefaultAndSaysSo() {
        assertTrue("deferred injection is the default", !IrisSettings.settings.getGeneral().isEagerRuntimeInjection());

        CheckResult result = injection().run();

        assertEquals(Mode.STABLE, result.mode());
        List<String> lines = result.diagnostics().stream().map(Diagnostic::getMessage).toList();
        assertTrue(lines.toString(), lines.contains("Runtime Injection"));
        assertTrue(lines.toString(), lines.stream().anyMatch(line -> line.contains("general.eagerRuntimeInjection")));
    }

    @Test
    public void theEagerSettingBringsBackBootTimeVerification() {
        IrisSettings.settings.getGeneral().setEagerRuntimeInjection(true);

        CheckResult result = injection().run();

        assertEquals(Mode.UNSTABLE, result.mode());
        assertTrue(result.lockReason(), result.lockReason().contains("NMS runtime"));
    }

    private static Task injection() {
        for (Task task : Tasks.getTasks()) {
            if ("injection".equals(task.getId())) {
                return task;
            }
        }
        throw new IllegalStateException("The injection check is missing from the safeguard task list");
    }
}
