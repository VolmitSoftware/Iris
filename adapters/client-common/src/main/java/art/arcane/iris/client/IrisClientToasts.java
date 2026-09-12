package art.arcane.iris.client;

import art.arcane.iris.modded.localization.ClientUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayDeque;

public final class IrisClientToasts {
    private static final int MAX_PENDING = 4;

    private final ArrayDeque<Pending> pending;

    public IrisClientToasts() {
        this.pending = new ArrayDeque<>();
    }

    public void enqueue(int kind, String title, String body) {
        Pending entry = new Pending(kind, normalize(title), normalize(body));
        synchronized (pending) {
            while (pending.size() >= MAX_PENDING) {
                pending.pollFirst();
            }
            pending.addLast(entry);
        }
    }

    public void enqueueHotload(String packKey, int changedFiles, boolean failed, String message) {
        int kind = failed ? IrisMessage.Toast.KIND_ERROR : IrisMessage.Toast.KIND_SUCCESS;
        enqueue(kind, IrisLanguage.plain(ClientUiMessages.TOAST_STUDIO_HOTLOAD), hotloadBody(packKey, changedFiles, failed, message));
    }

    public Pending poll() {
        synchronized (pending) {
            return pending.pollFirst();
        }
    }

    public void clear() {
        synchronized (pending) {
            pending.clear();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static String hotloadBody(String packKey, int changedFiles, boolean failed, String message) {
        StringBuilder builder = new StringBuilder();
        String pack = normalize(packKey);
        if (!pack.isEmpty()) {
            builder.append(pack);
        }
        if (changedFiles != 0) {
            append(builder, IrisLanguage.plain(ClientUiMessages.TOAST_CHANGED_FILES, MessageArgument.trusted("count", changedFiles)));
        }
        String text = normalize(message);
        if (!text.isEmpty()) {
            append(builder, text);
        }
        if (builder.isEmpty()) {
            return IrisLanguage.plain(failed ? ClientUiMessages.TOAST_RELOAD_FAILED : ClientUiMessages.TOAST_RELOADED);
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (!builder.isEmpty()) {
            builder.append("  ");
        }
        builder.append(part);
    }

    public record Pending(int kind, String title, String body) {
    }
}
