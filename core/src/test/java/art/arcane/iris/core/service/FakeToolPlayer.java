package art.arcane.iris.core.service;

import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.Particle;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolMenu;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A player who stands in one fake world and keeps everything a tool told them. */
final class FakeToolPlayer implements ToolPlayer {
    final List<String> messages = new ArrayList<>();
    final List<ToolItem> given = new ArrayList<>();
    final List<List<String>> sidebars = new ArrayList<>();
    final List<ToolMenu> menus = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();
    final List<Sound> sounds = new ArrayList<>();
    final List<Runnable> waiting = new ArrayList<>();
    ToolWorld world;
    BlockPosition position = new BlockPosition(0, 64, 0);
    boolean sneaking;
    boolean ownedElsewhere;
    int sidebarsHidden;
    int menusClosed;
    private final UUID id;
    private final String name;

    FakeToolPlayer(UUID id, String name, ToolWorld world) {
        this.id = id;
        this.name = name;
        this.world = world;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Sender sender() {
        return new Sender() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void info(String message) {
                messages.add(message);
            }

            @Override
            public void success(String message) {
                messages.add(message);
            }

            @Override
            public void error(String message) {
                messages.add(message);
            }

            @Override
            public <T> Optional<T> extension(Class<T> type) {
                return Optional.empty();
            }

            @Override
            public Optional<ToolPlayer> player() {
                return Optional.of(FakeToolPlayer.this);
            }
        };
    }

    @Override
    public Optional<ToolWorld> world() {
        return Optional.ofNullable(world);
    }

    @Override
    public BlockPosition position() {
        return position;
    }

    @Override
    public float yaw() {
        return 0;
    }

    @Override
    public float pitch() {
        return 0;
    }

    @Override
    public boolean sneaking() {
        return sneaking;
    }

    @Override
    public void run(Runnable task) {
        if (ownedElsewhere) {
            waiting.add(task);
            return;
        }
        task.run();
    }

    /** Runs what waited for the player's thread, as that thread does. */
    void runWaiting() {
        ownedElsewhere = false;
        List<Runnable> tasks = List.copyOf(waiting);
        waiting.clear();
        tasks.forEach(Runnable::run);
    }

    @Override
    public CompletableFuture<Boolean> teleport(ToolWorld target, double x, double y, double z, float yaw, float pitch) {
        world = target;
        position = new BlockPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void creative(boolean on) {
    }

    @Override
    public void actionBar(String text) {
    }

    @Override
    public void sound(ResourceId sound, float volume, float pitch) {
        sounds.add(new Sound(sound, volume, pitch));
    }

    record Sound(ResourceId id, float volume, float pitch) {
    }

    @Override
    public void sidebar(String title, List<String> lines) {
        sidebars.add(lines);
    }

    @Override
    public void hideSidebar() {
        sidebarsHidden++;
    }

    @Override
    public void particles(List<Particle> dust) {
        particles.addAll(dust);
    }

    @Override
    public void give(ToolItem item) {
        given.add(item);
    }

    @Override
    public Optional<ToolItem> held() {
        return given.isEmpty() ? Optional.empty() : Optional.of(given.getLast());
    }

    @Override
    public void openMenu(ToolMenu menu) {
        menus.add(menu);
    }

    @Override
    public void closeMenu() {
        menusClosed++;
    }
}
