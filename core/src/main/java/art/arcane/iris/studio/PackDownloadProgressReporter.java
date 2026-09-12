package art.arcane.iris.studio;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.PackDownloadMessages;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

final class PackDownloadProgressReporter implements PackDownloader.DownloadProgressListener {
    static final int PROGRESS_BAR_WIDTH = 24;
    private static final int INDETERMINATE_SEGMENT_WIDTH = 5;
    private static final int HUD_PULSE_TICKS = 5;
    private static final int HUD_TERMINAL_TICKS = 60;
    private static final long ACTION_INTERVAL_MILLIS = 250L;
    private static final long CHAT_INTERVAL_MILLIS = 5_000L;
    private static final int CHAT_PERCENT_STEP = 10;
    private static final int MAX_DETAIL_CHARACTERS = 320;
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final AtomicLong SESSION_IDS = new AtomicLong();

    private final VolmitSender sender;
    private final String source;
    private final String sensitiveSource;
    private final String escapedSensitiveSource;
    private final Player player;
    private final UUID playerId;
    private final String hudLaneId;
    private PackDownloader.DownloadPhase phase;
    private PackDownloader.DownloadProgress latestProgress;
    private boolean hudActive;
    private long transferredBytes;
    private long transferElapsedMillis;
    private long phaseStartedMillis;
    private long lastActionMillis;
    private long lastChatMillis;
    private int lastChatPercent;
    private int pulseTaskId;
    private boolean started;
    private boolean finished;
    private boolean listenerDisabled;
    private boolean hudDisabled;

    PackDownloadProgressReporter(VolmitSender sender, String source) {
        this(sender, source, null);
    }

    PackDownloadProgressReporter(VolmitSender sender, String source, String sensitiveSource) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.source = normalizeUntrusted(source);
        this.sensitiveSource = normalizeSensitive(sensitiveSource);
        escapedSensitiveSource = this.sensitiveSource == null
                ? null
                : escapeLocalizationUntrusted(this.sensitiveSource);
        player = sender.isPlayer() ? sender.player() : null;
        playerId = player == null ? null : player.getUniqueId();
        hudLaneId = "iris:pack-download-" + Long.toUnsignedString(SESSION_IDS.incrementAndGet());
        lastActionMillis = Long.MIN_VALUE;
        lastChatMillis = Long.MIN_VALUE;
        lastChatPercent = -CHAT_PERCENT_STEP;
        pulseTaskId = -1;
    }

    synchronized void start() {
        if (started || finished) {
            return;
        }
        started = true;
        phaseStartedMillis = System.currentTimeMillis();
        deliverChat(IrisLanguage.text(
                PackDownloadMessages.PROGRESS_START,
                MessageArgument.untrusted("source", source)
        ));
        if (player == null || !BukkitPlatform.hasHud()) {
            return;
        }
        hudActive = true;
        int scheduledTaskId = J.ar(this::pulseHud, HUD_PULSE_TICKS);
        pulseTaskId = scheduledTaskId;
        if (finished || hudDisabled) {
            J.car(scheduledTaskId);
            pulseTaskId = -1;
        }
    }

    @Override
    public synchronized void onProgress(PackDownloader.DownloadProgress progress) {
        if (finished || listenerDisabled || progress == null) {
            return;
        }
        try {
            if (!started) {
                start();
            }
            latestProgress = progress;
            if (progress.phase() == PackDownloader.DownloadPhase.DOWNLOADING) {
                transferredBytes = Math.max(transferredBytes, Math.max(0L, progress.transferredBytes()));
                transferElapsedMillis = Math.max(transferElapsedMillis, Math.max(0L, progress.elapsedMillis()));
            }
            long now = System.currentTimeMillis();
            if (phase != progress.phase()) {
                phase = progress.phase();
                phaseStartedMillis = now;
                deliverChat(IrisLanguage.text(
                        PackDownloadMessages.PROGRESS_PHASE,
                        MessageArgument.trusted("phase", phaseLabel(progress.phase())),
                        MessageArgument.untrusted("source", source)
                ));
            }
            if (progress.phase() == PackDownloader.DownloadPhase.DOWNLOADING
                    && shouldSendChatProgress(progress, now)) {
                lastChatMillis = now;
                if (progress.totalBytes() > 0L) {
                    lastChatPercent = percent(progress.transferredBytes(), progress.totalBytes());
                }
                deliverChat(progressLine(progress));
            }
        } catch (RuntimeException failure) {
            listenerDisabled = true;
            disableHud(null);
            throw failure;
        }
    }

    synchronized void detail(String detail) {
        if (finished || listenerDisabled || detail == null || detail.isBlank()) {
            return;
        }
        try {
            String[] lines = detail.split("\\R");
            for (String line : lines) {
                String normalized = normalizeUntrusted(redactSensitiveSource(line));
                if (normalized.isBlank()) {
                    continue;
                }
                deliverChat(IrisLanguage.text(
                        PackDownloadMessages.PROGRESS_DETAIL,
                        MessageArgument.untrusted("detail", normalized)
                ));
            }
        } catch (RuntimeException failure) {
            listenerDisabled = true;
            disableHud(null);
            throw failure;
        }
    }

    synchronized void succeed(PackDownloader.PackInstallResult result) {
        if (finished) {
            return;
        }
        finished = true;
        stopPulse();
        String pack = result == null || result.key() == null || result.key().isBlank()
                ? source
                : normalizeUntrusted(result.key());
        if (result == null || !result.changed()) {
            String unchanged = IrisLanguage.text(
                    PackDownloadMessages.PROGRESS_UNCHANGED,
                    MessageArgument.untrusted("pack", pack)
            );
            deliverChat(unchanged);
            deliverTerminalHud(unchanged, BarColor.YELLOW, 1.0D);
            return;
        }

        String complete = IrisLanguage.text(
                PackDownloadMessages.PROGRESS_COMPLETE,
                MessageArgument.untrusted("pack", pack),
                MessageArgument.trusted("transferred", Form.fileSize(transferredBytes)),
                MessageArgument.trusted("elapsed", Form.duration(transferElapsedMillis, 1))
        );
        deliverChat(complete);
        deliverTerminalHud(complete, BarColor.GREEN, 1.0D);
        if (result.restartRequired()) {
            deliverChat(IrisLanguage.text(PackDownloadMessages.PROGRESS_RESTART));
        }
    }

    synchronized void fail(Throwable failure) {
        if (finished) {
            return;
        }
        finished = true;
        stopPulse();
        String detail = failure == null ? "" : normalizeUntrusted(redactSensitiveSource(failure.getMessage()));
        String failed = detail.isBlank()
                ? IrisLanguage.text(PackDownloadMessages.PROGRESS_FAILED)
                : IrisLanguage.text(
                PackDownloadMessages.PROGRESS_FAILED_DETAIL,
                MessageArgument.untrusted("error", detail)
        );
        deliverChat(failed);
        deliverTerminalHud(failed, BarColor.RED, 1.0D);
    }

    synchronized void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        stopPulse();
        String cancelled = IrisLanguage.text(PackDownloadMessages.PROGRESS_CANCELLED);
        deliverChat(cancelled);
        deliverTerminalHud(cancelled, BarColor.YELLOW, 1.0D);
    }

    synchronized void executionComplete() {
        if (!finished) {
            cancel();
        }
    }

    private void pulseHud() {
        HudSnapshot snapshot;
        synchronized (this) {
            if (finished || hudDisabled || !hudActive) {
                stopPulse();
                return;
            }
            long now = System.currentTimeMillis();
            if (!mayEmitAction(lastActionMillis, now)) {
                return;
            }
            lastActionMillis = now;
            snapshot = hudSnapshot(now);
        }
        boolean scheduled = J.runEntity(player, () -> renderHudPulse(snapshot));
        if (!scheduled) {
            disableHud(null);
        }
    }

    private void renderHudPulse(HudSnapshot snapshot) {
        try {
            if (IrisSettings.get().getGeneral().isProgressBossBar()) {
                BukkitPlatform.hudLanes().show(
                        player,
                        hudLaneId,
                        snapshot.line(),
                        snapshot.progress(),
                        BarColor.BLUE,
                        BarStyle.SEGMENTED_20,
                        1_500L
                );
            }
            sender.sendAction(snapshot.line());
        } catch (RuntimeException failure) {
            disableHud(failure);
        }
    }

    private synchronized HudSnapshot hudSnapshot(long now) {
        PackDownloader.DownloadProgress observed = latestProgress;
        PackDownloader.DownloadPhase currentPhase = observed == null ? phase : observed.phase();
        if (currentPhase == null) {
            currentPhase = PackDownloader.DownloadPhase.CONNECTING;
        }
        long animationMillis = Math.max(0L, now - phaseStartedMillis);
        PackDownloader.DownloadProgress displayed;
        if (observed != null && currentPhase == PackDownloader.DownloadPhase.DOWNLOADING) {
            displayed = observed;
        } else {
            displayed = new PackDownloader.DownloadProgress(
                    currentPhase,
                    transferredBytes,
                    -1L,
                    transferElapsedMillis,
                    false
            );
        }
        double progress = displayed.totalBytes() > 0L
                ? Math.max(0.0D, Math.min(1.0D, (double) displayed.transferredBytes() / displayed.totalBytes()))
                : indeterminateProgress(animationMillis);
        return new HudSnapshot(progressLine(displayed, animationMillis), progress);
    }

    private void deliverChat(String message) {
        if (player == null) {
            sender.sendMessage(message);
            return;
        }
        J.runEntity(player, () -> sender.sendMessage(message));
    }

    private synchronized void deliverTerminalHud(String message, BarColor color, double progress) {
        boolean active = hudActive;
        hudActive = false;
        if (player == null || !active || hudDisabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = elapsedSince(lastActionMillis, now);
        long delayMillis = Math.max(0L, ACTION_INTERVAL_MILLIS - Math.min(ACTION_INTERVAL_MILLIS, elapsed));
        int delayTicks = (int) Math.ceil(delayMillis / 50.0D);
        lastActionMillis = now + delayMillis;
        AtomicBoolean cleaned = new AtomicBoolean();
        Runnable cleanup = () -> releaseHudLane(cleaned);
        Runnable retiredCleanup = () -> retireHudLane(cleaned);
        Runnable display = () -> {
            try {
                if (IrisSettings.get().getGeneral().isProgressBossBar()) {
                    BukkitPlatform.hudLanes().show(
                            player,
                            hudLaneId,
                            message,
                            progress,
                            color,
                            BarStyle.SOLID,
                            4_000L
                    );
                }
                sender.sendAction(message);
            } finally {
                if (!J.runEntity(player, cleanup, HUD_TERMINAL_TICKS, retiredCleanup)) {
                    retiredCleanup.run();
                }
            }
        };
        boolean scheduled = J.runEntity(player, display, delayTicks, retiredCleanup);
        if (!scheduled) {
            hudDisabled = true;
            retiredCleanup.run();
        }
    }

    private void disableHud(Throwable failure) {
        boolean active;
        synchronized (this) {
            if (hudDisabled) {
                return;
            }
            hudDisabled = true;
            stopPulse();
            active = hudActive;
            hudActive = false;
        }
        if (failure != null) {
            IrisLogging.reportError("Pack download HUD disabled after a delivery failure.", failure);
        }
        if (player != null && active) {
            AtomicBoolean cleaned = new AtomicBoolean();
            Runnable cleanup = () -> releaseHudLane(cleaned);
            Runnable retiredCleanup = () -> retireHudLane(cleaned);
            if (!J.runEntity(player, cleanup, 0, retiredCleanup)) {
                retiredCleanup.run();
            }
        }
    }

    private void releaseHudLane(AtomicBoolean cleaned) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        BukkitPlatform.hudLanes().hide(player, hudLaneId);
    }

    private void retireHudLane(AtomicBoolean cleaned) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        BukkitPlatform.hudLanes().retire(playerId, hudLaneId);
    }

    private synchronized void stopPulse() {
        int activeTaskId = pulseTaskId;
        pulseTaskId = -1;
        if (activeTaskId >= 0) {
            J.car(activeTaskId);
        }
    }

    private boolean shouldSendChatProgress(PackDownloader.DownloadProgress progress, long now) {
        if (lastChatMillis == Long.MIN_VALUE) {
            return true;
        }
        if (elapsedSince(lastChatMillis, now) >= CHAT_INTERVAL_MILLIS) {
            return true;
        }
        if (progress.totalBytes() <= 0L) {
            return false;
        }
        return percent(progress.transferredBytes(), progress.totalBytes()) >= lastChatPercent + CHAT_PERCENT_STEP;
    }

    private String redactSensitiveSource(String value) {
        if (value == null || sensitiveSource == null) {
            return value;
        }
        return value.replace(sensitiveSource, source).replace(escapedSensitiveSource, source);
    }

    static String progressLine(PackDownloader.DownloadProgress progress) {
        return progressLine(progress, progress.elapsedMillis());
    }

    static String progressLine(PackDownloader.DownloadProgress progress, long animationMillis) {
        long transferred = Math.max(0L, progress.transferredBytes());
        long rate = bytesPerSecond(transferred, progress.elapsedMillis());
        if (progress.totalBytes() > 0L) {
            int currentPercent = percent(transferred, progress.totalBytes());
            return IrisLanguage.text(
                    PackDownloadMessages.PROGRESS_DETERMINATE,
                    MessageArgument.trusted("bar", determinateBar(currentPercent / 100.0D)),
                    MessageArgument.trusted("percent", currentPercent),
                    MessageArgument.trusted("transferred", Form.fileSize(transferred)),
                    MessageArgument.trusted("total", Form.fileSize(progress.totalBytes())),
                    MessageArgument.trusted("rate", Form.fileSize(rate))
            );
        }
        return IrisLanguage.text(
                PackDownloadMessages.PROGRESS_INDETERMINATE,
                MessageArgument.trusted("bar", indeterminateBar(animationMillis)),
                MessageArgument.trusted("phase", phaseLabel(progress.phase())),
                MessageArgument.trusted("transferred", Form.fileSize(transferred)),
                MessageArgument.trusted("rate", Form.fileSize(rate))
        );
    }

    static String determinateBar(double progress) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * PROGRESS_BAR_WIDTH);
        StringBuilder bar = new StringBuilder(PROGRESS_BAR_WIDTH * 3 + 4);
        bar.append(C.DARK_GRAY).append("[");
        for (int cell = 0; cell < PROGRESS_BAR_WIDTH; cell++) {
            bar.append(cell < filled ? C.GREEN : C.DARK_GRAY).append("|");
        }
        return bar.append(C.DARK_GRAY).append("]").toString();
    }

    static String indeterminateBar(long elapsedMillis) {
        int travel = PROGRESS_BAR_WIDTH - INDETERMINATE_SEGMENT_WIDTH;
        int cycle = travel * 2;
        int step = cycle == 0 ? 0 : (int) ((Math.max(0L, elapsedMillis) / ACTION_INTERVAL_MILLIS) % cycle);
        int start = step <= travel ? step : cycle - step;
        StringBuilder bar = new StringBuilder(PROGRESS_BAR_WIDTH * 3 + 4);
        bar.append(C.DARK_GRAY).append("[");
        for (int cell = 0; cell < PROGRESS_BAR_WIDTH; cell++) {
            boolean active = cell >= start && cell < start + INDETERMINATE_SEGMENT_WIDTH;
            bar.append(active ? C.AQUA : C.DARK_GRAY).append("|");
        }
        return bar.append(C.DARK_GRAY).append("]").toString();
    }

    static double indeterminateProgress(long elapsedMillis) {
        int travel = PROGRESS_BAR_WIDTH - INDETERMINATE_SEGMENT_WIDTH;
        int cycle = travel * 2;
        int step = cycle == 0 ? 0 : (int) ((Math.max(0L, elapsedMillis) / ACTION_INTERVAL_MILLIS) % cycle);
        int start = step <= travel ? step : cycle - step;
        return Math.max(0.0D, Math.min(1.0D,
                (start + INDETERMINATE_SEGMENT_WIDTH / 2.0D) / PROGRESS_BAR_WIDTH));
    }

    static int percent(long transferredBytes, long totalBytes) {
        if (totalBytes <= 0L) {
            return 0;
        }
        double fraction = Math.max(0.0D, Math.min(1.0D, (double) transferredBytes / totalBytes));
        return (int) Math.round(fraction * 100.0D);
    }

    static long bytesPerSecond(long transferredBytes, long elapsedMillis) {
        if (transferredBytes <= 0L || elapsedMillis <= 0L) {
            return 0L;
        }
        double rate = transferredBytes * 1000.0D / elapsedMillis;
        return rate >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(rate);
    }

    static boolean mayEmitAction(long lastEmissionMillis, long nowMillis) {
        return elapsedSince(lastEmissionMillis, nowMillis) >= ACTION_INTERVAL_MILLIS;
    }

    static String phaseLabel(PackDownloader.DownloadPhase phase) {
        return IrisLanguage.text(switch (phase) {
            case CONNECTING -> PackDownloadMessages.PROGRESS_PHASE_CONNECTING;
            case DOWNLOADING -> PackDownloadMessages.PROGRESS_PHASE_DOWNLOADING;
            case UNPACKING -> PackDownloadMessages.PROGRESS_PHASE_UNPACKING;
            case VALIDATING -> PackDownloadMessages.PROGRESS_PHASE_VALIDATING;
            case PUBLISHING -> PackDownloadMessages.PROGRESS_PHASE_PUBLISHING;
        });
    }

    private static long elapsedSince(long earlier, long later) {
        if (earlier == Long.MIN_VALUE || later < earlier) {
            return Long.MAX_VALUE;
        }
        return later - earlier;
    }

    private static String normalizeSensitive(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String escapeLocalizationUntrusted(String value) {
        return LEGACY_COLOR.matcher(value).replaceAll("")
                .replace("&", "＆")
                .replace("<", "‹")
                .replace(">", "›");
    }

    private static String normalizeUntrusted(String value) {
        if (value == null || value.isBlank()) {
            return "Pack";
        }
        StringBuilder normalized = new StringBuilder(Math.min(value.length(), MAX_DETAIL_CHARACTERS));
        for (int index = 0; index < value.length() && normalized.length() < MAX_DETAIL_CHARACTERS; index++) {
            char character = value.charAt(index);
            if (character >= 0x20 && character != 0x7f) {
                normalized.append(character);
            }
        }
        if (value.length() > normalized.length()) {
            normalized.append("...");
        }
        return normalized.toString().trim();
    }

    private record HudSnapshot(String line, double progress) {
    }
}
