package art.arcane.iris.world;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.After;
import org.junit.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class IrisStartupAdmissionListenerTest {
    private final IrisStartupAdmissionListener listener = new IrisStartupAdmissionListener();

    @After
    public void disableValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void pendingStartupValidationDeniesLogin() throws Exception {
        IrisStartupValidation.begin();
        AsyncPlayerPreLoginEvent event = event();

        listener.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertNotNull(event.kickMessage());
    }

    @Test
    public void invalidDatapacksDenyLogin() throws Exception {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksInvalid("datapack failure");
        IrisStartupValidation.markPacksReady();
        AsyncPlayerPreLoginEvent event = event();

        listener.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
    }

    @Test
    public void restartRequiredDeniesLogin() throws Exception {
        IrisStartupValidation.begin();
        IrisStartupValidation.requireRestart("restart required");
        IrisStartupValidation.markPacksReady();
        AsyncPlayerPreLoginEvent event = event();

        listener.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
    }

    @Test
    public void readyValidationAllowsLogin() throws Exception {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        AsyncPlayerPreLoginEvent event = event();

        listener.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
    }

    private AsyncPlayerPreLoginEvent event() throws Exception {
        return new AsyncPlayerPreLoginEvent(
                "ValidationTest",
                InetAddress.getLoopbackAddress(),
                UUID.randomUUID(),
                false,
                mock(PlayerProfile.class));
    }
}
