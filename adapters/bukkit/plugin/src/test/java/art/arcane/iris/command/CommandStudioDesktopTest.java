package art.arcane.iris.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.studio.view.GuiHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandStudioDesktopTest {
    private IrisSettings previousSettings;
    private boolean previousSuppressed;
    private RecordingSender sender;
    private CommandStudio command;

    @Before
    public void setup() {
        previousSettings = IrisSettings.settings;
        previousSuppressed = GuiHost.isDesktopSuppressed();
        IrisSettings.settings = new IrisSettings();
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(true);
        GuiHost.suppressDesktop(true);
        sender = new RecordingSender();
        command = new TestCommand(sender);
    }

    @After
    public void restore() {
        IrisSettings.settings = previousSettings;
        GuiHost.suppressDesktop(previousSuppressed);
    }

    @Test
    public void mapReportsMissingDisplayBeforeAccessingWorld() {
        command.map(null);

        assertDisplayMessage();
    }

    @Test
    public void imageMapReportsMissingDisplayInsteadOfDisabledSetting() {
        command.imagemap(null);

        assertDisplayMessage();
    }

    private void assertDisplayMessage() {
        assertEquals(1, sender.messages.size());
        assertTrue(sender.messages.getFirst(), sender.messages.getFirst().contains("display"));
    }

    private static final class TestCommand extends CommandStudio {
        private final VolmitSender sender;

        private TestCommand(VolmitSender sender) {
            this.sender = sender;
        }

        @Override
        public VolmitSender sender() {
            return sender;
        }
    }

    private static final class RecordingSender extends VolmitSender {
        private final List<String> messages = new ArrayList<>();

        private RecordingSender() {
            super(null);
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }
    }
}
