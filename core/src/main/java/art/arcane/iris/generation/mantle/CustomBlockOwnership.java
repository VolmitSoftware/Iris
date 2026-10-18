package art.arcane.iris.generation.mantle;

import art.arcane.iris.integration.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CustomBlockOwnership(List<Entry> entries) {
    public CustomBlockOwnership {
        entries = List.copyOf(entries);
    }

    public CustomBlockOwnership add(Entry entry) {
        if (entries.contains(entry)) {
            return this;
        }
        List<Entry> updated = new ArrayList<>(entries);
        updated.add(entry);
        return new CustomBlockOwnership(updated);
    }

    public CustomBlockOwnership invalidate() {
        List<Entry> updated = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            updated.add(new Entry(entry.identifier(), entry.token(), true));
        }
        return new CustomBlockOwnership(updated);
    }

    public CustomBlockOwnership remove(Entry entry) {
        List<Entry> updated = new ArrayList<>(entries);
        updated.remove(entry);
        return updated.isEmpty() ? null : new CustomBlockOwnership(updated);
    }

    public record Entry(Identifier identifier, String token, boolean invalidated) {
        public Entry {
            Objects.requireNonNull(identifier);
            Objects.requireNonNull(token);
        }
    }
}
