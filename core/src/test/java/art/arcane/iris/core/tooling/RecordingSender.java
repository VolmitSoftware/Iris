package art.arcane.iris.core.tooling;

import com.volmit.tooling.world.ToolPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A sender that keeps what it was told, by kind. */
final class RecordingSender implements ToolingSender {
    final List<String> infos = Collections.synchronizedList(new ArrayList<>());
    final List<String> successes = Collections.synchronizedList(new ArrayList<>());
    final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    boolean operator = true;
    Optional<SenderWorld> world = Optional.empty();
    Optional<ServedPack> studio = Optional.empty();

    @Override
    public String name() {
        return "Tester";
    }

    @Override
    public void info(String message) {
        infos.add(message);
    }

    @Override
    public void success(String message) {
        successes.add(message);
    }

    @Override
    public void error(String message) {
        errors.add(message);
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }

    @Override
    public Optional<ToolPlayer> player() {
        return Optional.empty();
    }

    @Override
    public boolean isOperator() {
        return operator;
    }

    @Override
    public Optional<SenderWorld> world() {
        return world;
    }

    @Override
    public Optional<ServedPack> openStudioPack() {
        return studio;
    }
}
