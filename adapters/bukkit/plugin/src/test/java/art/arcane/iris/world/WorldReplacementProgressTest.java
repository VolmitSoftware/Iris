package art.arcane.iris.world;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class WorldReplacementProgressTest {
    @Test
    public void reportsStartStageAndHeartbeatUntilClosed() {
        VolmitSender sender = mock(VolmitSender.class);
        List<String> messages = new ArrayList<>();
        doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(sender).sendMessage(anyString());
        AtomicReference<Runnable> heartbeat = new AtomicReference<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.ar(any(Runnable.class), eq(200))).thenAnswer(invocation -> {
                heartbeat.set(invocation.getArgument(0));
                return 42;
            });

            WorldReplacementProgress progress = WorldReplacementProgress.start(sender, "minecraft:overworld");
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().contains("Preparing Iris replacement for minecraft:overworld"));

            progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PACK);
            progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PACK);
            assertEquals(2, messages.size());
            assertTrue(messages.getLast().contains("Copying and validating the dimension pack"));
            assertTrue(messages.getLast().matches(".*\\([0-9]+s elapsed\\).*"));

            heartbeat.get().run();
            assertEquals(2, messages.size());
            heartbeat.get().run();
            assertEquals(3, messages.size());
            assertTrue(messages.getLast().contains("Copying and validating the dimension pack"));

            progress.close();
            progress.close();
            heartbeat.get().run();
            progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_SAVE);
            assertEquals(3, messages.size());
            scheduler.verify(() -> J.car(42), times(1));
        }
    }

    @Test
    public void schedulingFailureDoesNotPreventPhaseReports() {
        VolmitSender sender = mock(VolmitSender.class);
        List<String> messages = new ArrayList<>();
        doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(sender).sendMessage(anyString());
        IllegalStateException failure = new IllegalStateException("Scheduler stopped");
        try (MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            scheduler.when(() -> J.ar(any(Runnable.class), eq(200))).thenThrow(failure);
            try (WorldReplacementProgress progress = WorldReplacementProgress.start(sender, "minecraft:overworld")) {
                progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PACK);
            }

            assertEquals(2, messages.size());
            logging.verify(() -> IrisLogging.reportError(anyString(), eq(failure)));
            scheduler.verify(() -> J.car(anyInt()), times(0));
        }
    }

    @Test
    public void unavailableTimerLeavesPhaseReportsUsable() {
        VolmitSender sender = mock(VolmitSender.class);
        List<String> messages = new ArrayList<>();
        doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(sender).sendMessage(anyString());
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.ar(any(Runnable.class), eq(200))).thenReturn(-1);
            try (WorldReplacementProgress progress = WorldReplacementProgress.start(sender, "minecraft:overworld")) {
                progress.stage(RuntimeProgressMessages.WORLD_REPLACE_STAGE_PACK);
            }

            assertEquals(2, messages.size());
            scheduler.verify(() -> J.car(anyInt()), times(0));
        }
    }

    @Test
    public void playerReportsUseEntitySchedulingAndRetainAudience() {
        VolmitSender sender = mock(VolmitSender.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        List<String> messages = new ArrayList<>();
        List<Runnable> deliveries = new ArrayList<>();
        List<UUID> audiences = new ArrayList<>();
        AtomicReference<Runnable> heartbeat = new AtomicReference<>();
        doAnswer(invocation -> messages.add(invocation.getArgument(0))).when(sender).sendMessage(anyString());
        try (MockedStatic<J> scheduler = mockStatic(J.class);
             MockedStatic<IrisLanguage> language = mockStatic(IrisLanguage.class)) {
            language.when(() -> IrisLanguage.text(any(UUID.class), any(MessageKey.class), any(MessageArgs.class)))
                    .thenAnswer(invocation -> {
                        audiences.add(invocation.getArgument(0));
                        return "Replacement progress";
                    });
            scheduler.when(() -> J.ar(any(Runnable.class), eq(200))).thenAnswer(invocation -> {
                heartbeat.set(invocation.getArgument(0));
                return 42;
            });
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                deliveries.add(invocation.getArgument(1));
                return true;
            });

            WorldReplacementProgress progress = WorldReplacementProgress.start(sender, "minecraft:overworld");
            assertTrue(messages.isEmpty());
            deliveries.getFirst().run();
            assertEquals(1, messages.size());
            heartbeat.get().run();
            heartbeat.get().run();
            assertEquals(2, deliveries.size());
            assertEquals(List.of(playerId, playerId, playerId), audiences);

            progress.close();
            deliveries.getLast().run();
            assertEquals(1, messages.size());
        }
    }
}
