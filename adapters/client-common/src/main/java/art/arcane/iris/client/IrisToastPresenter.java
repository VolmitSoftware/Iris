package art.arcane.iris.client;

import art.arcane.volmlib.nativelib.client.ClientToastKind;
import art.arcane.volmlib.nativelib.minecraft26_2.client.NativeClientAccess;

import art.arcane.iris.spi.protocol.IrisMessage;

/**
 * CLIENT DIST ONLY. See {@link IrisClientHud} for why the dist marker is a javadoc contract plus a bytecode
 * test rather than an @Environment annotation.
 */
public final class IrisToastPresenter {
    private IrisToastPresenter() {
    }

    public static void pump() {
        if (!NativeClientAccess.guiPresent()) {
            return;
        }
        IrisClientToasts toasts = IrisClient.toasts();
        IrisClientToasts.Pending next = toasts.poll();
        while (next != null) {
            NativeClientAccess.toast(tokenFor(next.kind()), next.title(), next.body());
            next = toasts.poll();
        }
    }

    private static ClientToastKind tokenFor(int kind) {
        return switch (kind) {
            case IrisMessage.Toast.KIND_SUCCESS -> ClientToastKind.SUCCESS;
            case IrisMessage.Toast.KIND_WARNING -> ClientToastKind.WARNING;
            case IrisMessage.Toast.KIND_ERROR -> ClientToastKind.ERROR;
            default -> ClientToastKind.INFORMATION;
        };
    }
}
