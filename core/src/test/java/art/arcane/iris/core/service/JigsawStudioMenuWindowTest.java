package art.arcane.iris.core.service;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.MenuButton;
import com.volmit.tooling.world.MenuClick;
import com.volmit.tooling.world.ToolMenu;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JigsawStudioMenuWindowTest {
    private final FakeToolPlayer viewer = new FakeToolPlayer(UUID.randomUUID(), "editor", new FakeToolWorld("jigsaw"));

    @Test
    public void elementsSitAtColumnsCountedFromTheMiddleOfTheirRow() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer).setTitle("Jigsaw: village");
        window.setElement(0, 0, element("middle"));
        window.setElement(-4, 0, element("left-edge"));
        window.setElement(4, 1, element("right-edge-below"));
        window.setElement(-9, 2, element("clamped-left"));
        window.setElement(9, 3, element("clamped-right"));

        window.open();

        ToolMenu menu = viewer.menus.getLast();
        assertEquals("Jigsaw: village", menu.title());
        assertEquals(List.of(0, 4, 17, 18, 35), slotsOf(menu));
    }

    @Test
    public void buttonCarriesTheIconNameLoreAndGlow() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer);
        window.setElement(0, 0, new JigsawStudioMenuElement("details")
                .setIcon(JigsawStudioMenuIcon.EMERALD)
                .setName("Ready to Assemble")
                .addLore("Seed 1337 assembled 19 pieces")
                .setEnchanted(true));

        window.open();

        MenuButton button = viewer.menus.getLast().buttons().getFirst();
        assertEquals(ResourceId.parse("minecraft:emerald"), button.icon().item());
        assertEquals("Ready to Assemble", button.icon().name());
        assertEquals(List.of("Seed 1337 assembled 19 pieces"), button.icon().lore());
        assertTrue(button.icon().glowing());
    }

    @Test
    public void viewportHidesElementsBelowItsLastRow() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer).setViewportHeight(2);
        window.setElement(0, 0, element("visible"));
        window.setElement(0, 1, element("last-row"));
        window.setElement(0, 2, element("hidden"));

        window.open();

        ToolMenu menu = viewer.menus.getLast();
        assertEquals(2, menu.rows());
        assertEquals(List.of(4, 13), slotsOf(menu));
    }

    @Test
    public void eachKindOfClickRunsItsOwnAction() {
        List<String> clicks = new ArrayList<>();
        JigsawStudioMenuElement element = new JigsawStudioMenuElement("variant")
                .onLeftClick(clicked -> clicks.add("left"))
                .onRightClick(clicked -> clicks.add("right"))
                .onShiftLeftClick(clicked -> clicks.add("shift-left"))
                .onShiftRightClick(clicked -> clicks.add("shift-right"))
                .onMiddleClick(clicked -> clicks.add("middle"));
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer);
        window.setElement(0, 0, element);
        window.open();
        MenuButton button = viewer.menus.getLast().buttons().getFirst();

        button.action().accept(new MenuClick(viewer, false, false, false));
        button.action().accept(new MenuClick(viewer, true, false, false));
        button.action().accept(new MenuClick(viewer, false, true, false));
        button.action().accept(new MenuClick(viewer, true, true, false));
        button.action().accept(new MenuClick(viewer, false, false, true));

        assertEquals(List.of("left", "right", "shift-left", "shift-right", "middle"), clicks);
    }

    @Test
    public void aClickWithoutAnActionDoesNothing() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer);
        window.setElement(0, 0, new JigsawStudioMenuElement("plain").onLeftClick(clicked -> { }));
        window.open();

        viewer.menus.getLast().buttons().getFirst().action().accept(new MenuClick(viewer, true, false, false));

        assertEquals(1, viewer.menus.size());
    }

    @Test
    public void everyChangeIsShownOnceAndABatchIsShownOnce() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer);
        window.open();
        assertEquals(1, viewer.menus.size());

        window.setElement(0, 0, element("one"));
        window.setElement(1, 0, element("two"));
        assertEquals(3, viewer.menus.size());

        window.batch(() -> {
            window.clearElements();
            window.setElement(0, 0, element("three"));
            window.setElement(1, 0, element("four"));
        });
        assertEquals(4, viewer.menus.size());
        assertEquals(2, viewer.menus.getLast().buttons().size());
    }

    @Test
    public void aWindowThatIsNotOpenShowsNothing() {
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer);

        window.setElement(0, 0, element("quiet"));
        window.batch(() -> window.setElement(1, 0, element("quieter")));

        assertTrue(viewer.menus.isEmpty());
    }

    @Test
    public void closingFromTheWindowClosesTheViewerMenuOnceAndRunsTheCloseHandler() {
        AtomicInteger closed = new AtomicInteger();
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer).onClosed(closed::incrementAndGet);
        window.open();

        window.close();
        window.close();

        assertEquals(1, viewer.menusClosed);
        assertEquals(1, closed.get());
    }

    @Test
    public void theHostClosingTheMenuRunsTheCloseHandler() {
        AtomicInteger closed = new AtomicInteger();
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer).onClosed(closed::incrementAndGet);
        window.open();

        viewer.menus.getLast().onClose().run();

        assertEquals(1, closed.get());
        assertEquals(0, viewer.menusClosed);
    }

    @Test
    public void aMenuTheWindowReplacedDoesNotCloseTheWindowWhenTheHostClosesIt() {
        AtomicInteger closed = new AtomicInteger();
        JigsawStudioMenuWindow window = new JigsawStudioMenuWindow(viewer).onClosed(closed::incrementAndGet);
        window.open();
        ToolMenu replaced = viewer.menus.getLast();

        window.setElement(0, 0, element("newer"));
        replaced.onClose().run();

        assertEquals(0, closed.get());
        assertFalse(viewer.menus.getLast() == replaced);
    }

    private static JigsawStudioMenuElement element(String id) {
        return new JigsawStudioMenuElement(id).setName(id);
    }

    private static List<Integer> slotsOf(ToolMenu menu) {
        return menu.buttons().stream().map(MenuButton::slot).toList();
    }
}
