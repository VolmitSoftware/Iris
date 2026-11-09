package art.arcane.iris.core.service;

import com.volmit.tooling.world.MenuButton;
import com.volmit.tooling.world.ToolMenu;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A studio menu the controller builds up element by element, the way it lays out a window: each element sits at a
 * column counted from the middle of the row (left of it negative) and a row from the top. While the window is open every
 * change is shown to the player, and a batch of changes is shown once.
 *
 * <p>Used on the viewer's thread only.
 */
final class JigsawStudioMenuWindow {
    private static final int ROW_WIDTH = ToolMenu.SLOTS_PER_ROW;
    private static final int MIDDLE_COLUMN = ROW_WIDTH / 2;

    private final ToolPlayer viewer;
    private final Map<Integer, JigsawStudioMenuElement> elements = new TreeMap<>();
    private ToolText title = ToolText.EMPTY;
    private int viewportRows = ToolMenu.MAX_ROWS;
    private Runnable onClosed = () -> { };
    private int batchDepth;
    private boolean open;
    private long shownGeneration;

    JigsawStudioMenuWindow(ToolPlayer viewer) {
        this.viewer = Objects.requireNonNull(viewer, "Jigsaw Studio menu viewer");
    }

    ToolPlayer getViewer() {
        return viewer;
    }

    JigsawStudioMenuWindow setViewportHeight(int rows) {
        viewportRows = Math.max(1, Math.min(ToolMenu.MAX_ROWS, rows));
        return this;
    }

    JigsawStudioMenuWindow setTitle(ToolText title) {
        this.title = Objects.requireNonNull(title, "Jigsaw Studio menu title");
        return this;
    }

    JigsawStudioMenuWindow setTitle(String title) {
        return setTitle(ToolText.plain(Objects.requireNonNull(title, "Jigsaw Studio menu title")));
    }

    JigsawStudioMenuWindow onClosed(Runnable onClosed) {
        this.onClosed = Objects.requireNonNull(onClosed, "Jigsaw Studio menu close handler");
        return this;
    }

    JigsawStudioMenuWindow setElement(int position, int row, JigsawStudioMenuElement element) {
        elements.put(slotOf(position, row), element);
        showUnlessBatching();
        return this;
    }

    JigsawStudioMenuWindow clearElements() {
        elements.clear();
        showUnlessBatching();
        return this;
    }

    JigsawStudioMenuWindow batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
        }
        showUnlessBatching();
        return this;
    }

    JigsawStudioMenuWindow open() {
        open = true;
        show();
        return this;
    }

    JigsawStudioMenuWindow close() {
        if (!open) {
            return this;
        }
        open = false;
        shownGeneration++;
        viewer.closeMenu();
        onClosed.run();
        return this;
    }

    private static int slotOf(int position, int row) {
        int column = Math.max(-MIDDLE_COLUMN, Math.min(MIDDLE_COLUMN, position)) + MIDDLE_COLUMN;
        return row * ROW_WIDTH + column;
    }

    private void showUnlessBatching() {
        if (open && batchDepth == 0) {
            show();
        }
    }

    private void show() {
        long generation = ++shownGeneration;
        List<MenuButton> buttons = new ArrayList<>(elements.size());
        for (Map.Entry<Integer, JigsawStudioMenuElement> entry : elements.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < viewportRows * ROW_WIDTH) {
                buttons.add(entry.getValue().toButton(entry.getKey()));
            }
        }
        viewer.openMenu(new ToolMenu(title, viewportRows, buttons, () -> closedByHost(generation)));
    }

    private void closedByHost(long generation) {
        if (generation != shownGeneration || !open) {
            return;
        }
        open = false;
        onClosed.run();
    }
}
