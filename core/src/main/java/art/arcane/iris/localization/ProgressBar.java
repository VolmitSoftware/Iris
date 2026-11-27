package art.arcane.iris.localization;

/** The one progress bar Iris draws: a themed bar for action bars and a plain one for the console. */
public final class ProgressBar {
    public static final int PLAYER_WIDTH = 44;
    public static final int CONSOLE_WIDTH = 20;

    private ProgressBar() {
    }

    public static String player(double progress) {
        int filled = filledCells(progress, PLAYER_WIDTH);
        StringBuilder bar = new StringBuilder(PLAYER_WIDTH * 3 + 8);
        bar.append(C.DARK_GRAY).append("[");
        for (int cell = 0; cell < PLAYER_WIDTH; cell++) {
            bar.append(cell < filled ? C.IRIS : C.DARK_GRAY).append("|");
        }
        return bar.append(C.DARK_GRAY).append("]").toString();
    }

    public static String console(double progress) {
        int filled = filledCells(progress, CONSOLE_WIDTH);
        StringBuilder bar = new StringBuilder(CONSOLE_WIDTH + 2).append("[");
        for (int cell = 0; cell < CONSOLE_WIDTH; cell++) {
            bar.append(cell < filled ? "#" : "-");
        }
        return bar.append("]").toString();
    }

    private static int filledCells(double progress, int width) {
        double clamped = Double.isNaN(progress) ? 0.0D : Math.max(0.0D, Math.min(1.0D, progress));
        return (int) Math.round(clamped * width);
    }
}
