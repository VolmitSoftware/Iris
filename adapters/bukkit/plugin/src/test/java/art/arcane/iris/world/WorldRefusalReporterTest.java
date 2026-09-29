package art.arcane.iris.world;

import art.arcane.iris.Iris;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Whether a refused world stops startup depends on who asked for it: Paper's startup level creation aborts the
 * server, a runtime create only fails its caller. The banner must not claim the wrong one.
 */
public class WorldRefusalReporterTest {
    private static final NamespacedKey MOON = new NamespacedKey("iris", "moon");

    @Test
    public void aRefusalBeforeAnyWorldLoadedSaysStartupStops() {
        String banner = reportAndCapture(new WorldRefusalReporter());

        assertTrue(banner, banner.contains("'moon'"));
        assertTrue(banner, banner.contains("iris:moon"));
        assertTrue(banner, banner.contains("Iris generation history is unusable at /srv/moon."));
        assertTrue(banner, banner.contains("Historical generated registry definition changed for iris:biomes/abc"));
        assertTrue(banner, banner.contains("no chunks"));
        assertTrue(banner, banner.contains("startup stops"));
    }

    /**
     * Paper creates every startup level before it prepares any, and preparing one fires the first WorldLoadEvent.
     * A plugin enabling at POSTWORLD creates its worlds after that, before the first tick, and the server keeps
     * running when that create fails.
     */
    @Test
    public void aRefusalAfterTheFirstWorldLoadSaysTheServerKeepsRunning() {
        WorldRefusalReporter reporter = new WorldRefusalReporter();
        reporter.onWorldLoad(new WorldLoadEvent(world(NamespacedKey.minecraft("overworld"))));

        String banner = reportAndCapture(reporter);

        assertTrue(banner, banner.contains("keeps running"));
        assertFalse(banner, banner.contains("startup stops"));
    }

    @Test
    public void irisEnabledAfterWorldsExistTreatsEveryRefusalAsRuntime() {
        WorldRefusalReporter reporter = new WorldRefusalReporter();
        Plugin plugin = mock(Plugin.class);
        PluginManager plugins = mock(PluginManager.class);
        World overworld = world(NamespacedKey.minecraft("overworld"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(overworld));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            reporter.attach(plugin);
        }

        assertTrue(reporter.startupWorldsCreated());
        verify(plugins).registerEvents(reporter, plugin);
        String banner = reportAndCapture(reporter);
        assertTrue(banner, banner.contains("keeps running"));
    }

    @Test
    public void operatorsHearAboutARefusedWorldOnJoinUntilItLoads() {
        WorldRefusalReporter reporter = new WorldRefusalReporter();
        reportAndCapture(reporter);
        Player operator = player(true);
        Player member = player(false);

        reporter.onPlayerJoin(new PlayerJoinEvent(operator, Component.empty()));
        reporter.onPlayerJoin(new PlayerJoinEvent(member, Component.empty()));

        verify(operator).sendMessage(contains("iris:moon"));
        verify(member, never()).sendMessage(anyString());

        reporter.onWorldLoad(new WorldLoadEvent(world(MOON)));
        Player later = player(true);
        reporter.onPlayerJoin(new PlayerJoinEvent(later, Component.empty()));
        verify(later, never()).sendMessage(anyString());
    }

    @Test
    public void aFailureWhileReportingNeverEscapes() {
        Throwable unreadable = new IllegalStateException() {
            @Override
            public String getMessage() {
                throw new UnsupportedOperationException("message unavailable");
            }
        };

        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            new WorldRefusalReporter().report("moon", MOON, unreadable);
        }
    }

    private static String reportAndCapture(WorldRefusalReporter reporter) {
        IllegalStateException failure = new IllegalStateException("Iris generation history is unusable at /srv/moon.",
                new IOException("Historical generated registry definition changed for iris:biomes/abc"));
        List<String> lines = new ArrayList<>();
        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            iris.when(() -> Iris.error(anyString(), any(Object[].class))).thenAnswer(invocation -> {
                lines.add(invocation.getArgument(0, String.class));
                return null;
            });
            reporter.report("moon", MOON, failure);
        }
        return String.join("\n", lines);
    }

    private static World world(NamespacedKey key) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(key);
        when(world.getName()).thenReturn(key.getKey());
        return world;
    }

    private static Player player(boolean operator) {
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(operator);
        return player;
    }
}
