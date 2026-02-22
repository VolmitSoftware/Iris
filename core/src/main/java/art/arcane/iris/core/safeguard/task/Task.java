package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.core.safeguard.Mode;
import art.arcane.volmlib.util.format.Form;

import java.util.Locale;
import java.util.function.Supplier;

public abstract class Task {
    private final String id;
    private final String name;

    public Task(String id) {
        this(id, Form.capitalizeWords(id.replace(" ", "_").toLowerCase(Locale.ROOT)));
    }

    public Task(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public abstract ValueWithDiagnostics<Mode> run();

    public static Task of(String id, String name, Supplier<ValueWithDiagnostics<Mode>> action) {
        return new Task(id, name) {
            @Override
            public ValueWithDiagnostics<Mode> run() {
                return action.get();
            }
        };
    }

    public static Task of(String id, Supplier<ValueWithDiagnostics<Mode>> action) {
        return new Task(id) {
            @Override
            public ValueWithDiagnostics<Mode> run() {
                return action.get();
            }
        };
    }
}
