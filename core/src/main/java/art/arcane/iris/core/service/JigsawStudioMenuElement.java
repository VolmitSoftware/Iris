package art.arcane.iris.core.service;

import com.volmit.tooling.world.MenuButton;
import com.volmit.tooling.world.MenuClick;
import com.volmit.tooling.world.ToolItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** One slot of a studio menu: an icon with a name and lore, and what each kind of click on it does. */
final class JigsawStudioMenuElement {
    private final String id;
    private final List<String> lore = new ArrayList<>();
    private JigsawStudioMenuIcon icon = JigsawStudioMenuIcon.BARRIER;
    private String name = "";
    private boolean enchanted;
    private Consumer<JigsawStudioMenuElement> onLeftClick;
    private Consumer<JigsawStudioMenuElement> onRightClick;
    private Consumer<JigsawStudioMenuElement> onShiftLeftClick;
    private Consumer<JigsawStudioMenuElement> onShiftRightClick;
    private Consumer<JigsawStudioMenuElement> onMiddleClick;

    JigsawStudioMenuElement(String id) {
        this.id = Objects.requireNonNull(id, "Jigsaw Studio menu element ID");
    }

    String id() {
        return id;
    }

    JigsawStudioMenuElement setIcon(JigsawStudioMenuIcon icon) {
        this.icon = Objects.requireNonNull(icon, "Jigsaw Studio menu icon");
        return this;
    }

    JigsawStudioMenuElement setName(String name) {
        this.name = Objects.requireNonNull(name, "Jigsaw Studio menu name");
        return this;
    }

    JigsawStudioMenuElement setEnchanted(boolean enchanted) {
        this.enchanted = enchanted;
        return this;
    }

    JigsawStudioMenuElement addLore(String line) {
        lore.add(Objects.requireNonNull(line, "Jigsaw Studio menu lore"));
        return this;
    }

    JigsawStudioMenuElement onLeftClick(Consumer<JigsawStudioMenuElement> action) {
        onLeftClick = action;
        return this;
    }

    JigsawStudioMenuElement onRightClick(Consumer<JigsawStudioMenuElement> action) {
        onRightClick = action;
        return this;
    }

    JigsawStudioMenuElement onShiftLeftClick(Consumer<JigsawStudioMenuElement> action) {
        onShiftLeftClick = action;
        return this;
    }

    JigsawStudioMenuElement onShiftRightClick(Consumer<JigsawStudioMenuElement> action) {
        onShiftRightClick = action;
        return this;
    }

    JigsawStudioMenuElement onMiddleClick(Consumer<JigsawStudioMenuElement> action) {
        onMiddleClick = action;
        return this;
    }

    /** The api button for this element on {@code slot}. */
    MenuButton toButton(int slot) {
        ToolItem item = new ToolItem(icon.id(), name, List.copyOf(lore), enchanted, "");
        return new MenuButton(slot, item, this::click);
    }

    private void click(MenuClick click) {
        Consumer<JigsawStudioMenuElement> action = click.middle()
                ? onMiddleClick
                : click.shift()
                ? click.right() ? onShiftRightClick : onShiftLeftClick
                : click.right() ? onRightClick : onLeftClick;
        if (action != null) {
            action.accept(this);
        }
    }
}
