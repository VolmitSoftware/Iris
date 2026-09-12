/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class PackValidationRegistry {
    private static final Map<String, PackValidationResult> RESULTS = new ConcurrentHashMap<>();
    private static final Map<Path, RootState> ROOT_STATES = new ConcurrentHashMap<>();

    private PackValidationRegistry() {
    }

    public static void publish(PackValidationResult result) {
        if (result == null || result.getPackName() == null || result.getPackName().isBlank()) {
            return;
        }
        RESULTS.put(result.getPackName(), result);
    }

    public static void publish(Path packRoot, PackValidationResult result) {
        if (packRoot == null || result == null) {
            return;
        }
        publish(normalize(packRoot), new RootValidation(result, ""));
    }

    public static void publish(Path packRoot, PackValidationResult result, String contentFingerprint) {
        if (packRoot == null || result == null || contentFingerprint == null || contentFingerprint.isBlank()) {
            return;
        }
        publish(normalize(packRoot), new RootValidation(result, contentFingerprint));
    }

    public static PackValidationResult publishMatchingCopy(
            Path sourceRoot,
            Path targetRoot,
            String copiedContentFingerprint
    ) {
        if (sourceRoot == null || targetRoot == null
                || copiedContentFingerprint == null || copiedContentFingerprint.isBlank()) {
            return null;
        }
        RootValidation sourceValidation = matchingValidation(sourceRoot, copiedContentFingerprint);
        if (sourceValidation == null) {
            return null;
        }
        publish(normalize(targetRoot), sourceValidation);
        return sourceValidation.result();
    }

    public static RootMutation beginRootMutation(Path packRoot) {
        Path normalizedRoot = normalize(Objects.requireNonNull(packRoot, "Pack root"));
        AtomicReference<RootMutation> mutation = new AtomicReference<>();
        ROOT_STATES.compute(normalizedRoot, (path, current) -> {
            if (current != null && current.mutating()) {
                throw new IllegalStateException("Iris pack validation is already mutating " + normalizedRoot);
            }
            long generation = nextGeneration(current);
            mutation.set(new RootMutation(normalizedRoot, generation));
            return new RootState(generation, true, null);
        });
        return mutation.get();
    }

    public static ValidationTicket tryBeginValidation(Path packRoot) {
        Path normalizedRoot = normalize(Objects.requireNonNull(packRoot, "Pack root"));
        AtomicReference<ValidationTicket> ticket = new AtomicReference<>();
        ROOT_STATES.compute(normalizedRoot, (path, current) -> {
            RootState state = current == null ? new RootState(0L, false, null) : current;
            if (!state.mutating()) {
                ticket.set(new ValidationTicket(normalizedRoot, state.generation()));
            }
            return state;
        });
        return ticket.get();
    }

    public static boolean publishIfCurrent(ValidationTicket ticket, PackValidationResult result) {
        if (ticket == null || result == null) {
            return false;
        }
        AtomicBoolean published = new AtomicBoolean();
        ROOT_STATES.compute(ticket.packRoot, (path, current) -> {
            if (current == null
                    || current.mutating()
                    || current.generation() != ticket.generation) {
                return current;
            }
            published.set(true);
            return new RootState(
                    current.generation(),
                    false,
                    new RootValidation(result, ""));
        });
        return published.get();
    }

    public static PackValidationResult get(String packName) {
        if (packName == null || packName.isBlank()) {
            return null;
        }
        return RESULTS.get(packName);
    }

    public static PackValidationResult get(Path packRoot) {
        if (packRoot == null) {
            return null;
        }
        RootState state = ROOT_STATES.get(normalize(packRoot));
        return state == null || state.mutating() || state.validation() == null
                ? null
                : state.validation().result();
    }

    public static PackValidationResult requireLoadable(String packName) {
        if (packName == null || packName.isBlank()) {
            throw new IllegalArgumentException("Pack name is required for validation");
        }
        PackValidationResult result = get(packName);
        if (result == null) {
            throw new BrokenPackException(packName, List.of(
                    "Required pack validation has not completed. World creation fails closed until validation succeeds."));
        }
        if (!result.isLoadable()) {
            throw new BrokenPackException(packName, result.getBlockingErrors());
        }
        return result;
    }

    public static PackValidationResult requireLoadable(Path packRoot) {
        if (packRoot == null) {
            throw new IllegalArgumentException("Pack root is required for validation");
        }
        Path normalizedRoot = normalize(packRoot);
        PackValidationResult result = get(normalizedRoot);
        if (result == null) {
            throw new BrokenPackException(normalizedRoot.toString(), List.of(
                    "Required pack validation has not completed. World creation fails closed until validation succeeds."));
        }
        if (!result.isLoadable()) {
            throw new BrokenPackException(normalizedRoot.toString(), result.getBlockingErrors());
        }
        return result;
    }

    public static boolean isBroken(String packName) {
        PackValidationResult result = get(packName);
        return result != null && !result.isLoadable();
    }

    public static boolean isBroken(Path packRoot) {
        PackValidationResult result = get(packRoot);
        return result != null && !result.isLoadable();
    }

    public static Map<String, PackValidationResult> snapshot() {
        return Collections.unmodifiableMap(RESULTS);
    }

    public static void remove(String packName) {
        if (packName == null || packName.isBlank()) {
            return;
        }
        RESULTS.remove(packName);
    }

    public static void remove(Path packRoot) {
        if (packRoot == null) {
            return;
        }
        Path normalizedRoot = normalize(packRoot);
        ROOT_STATES.compute(normalizedRoot, (path, current) -> {
            if (current != null && current.mutating()) {
                return current;
            }
            return new RootState(nextGeneration(current), false, null);
        });
    }

    public static void clear() {
        RESULTS.clear();
        ROOT_STATES.clear();
    }

    private static Path normalize(Path packRoot) {
        Path normalizedRoot = packRoot.toAbsolutePath().normalize();
        try {
            Path existing = normalizedRoot;
            List<Path> missingNames = new ArrayList<>();
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                Path name = existing.getFileName();
                if (name != null) {
                    missingNames.add(name);
                }
                existing = existing.getParent();
            }
            if (existing == null) {
                return normalizedRoot;
            }
            Path resolved = existing.toRealPath();
            for (int index = missingNames.size() - 1; index >= 0; index--) {
                resolved = resolved.resolve(missingNames.get(index));
            }
            return resolved.normalize();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to resolve Iris pack root: " + normalizedRoot, exception);
        }
    }

    private static void publish(Path normalizedRoot, RootValidation validation) {
        ROOT_STATES.compute(normalizedRoot, (path, current) -> {
            if (current != null && current.mutating()) {
                throw new IllegalStateException("Iris pack validation is mutating " + normalizedRoot);
            }
            return new RootState(nextGeneration(current), false, validation);
        });
    }

    private static RootValidation matchingValidation(Path sourceRoot, String copiedContentFingerprint) {
        Path normalizedSource = normalize(sourceRoot);
        RootState sourceState = ROOT_STATES.get(normalizedSource);
        if (sourceState == null
                || sourceState.mutating()
                || sourceState.validation() == null
                || !copiedContentFingerprint.equals(sourceState.validation().contentFingerprint())) {
            return null;
        }
        return sourceState.validation();
    }

    private static long nextGeneration(RootState current) {
        return current == null ? 1L : Math.incrementExact(current.generation());
    }

    private static void closeMutation(Path packRoot, long generation) {
        ROOT_STATES.computeIfPresent(packRoot, (path, current) -> {
            if (!current.mutating() || current.generation() != generation) {
                return current;
            }
            return new RootState(generation, false, null);
        });
    }

    public static final class RootMutation implements AutoCloseable {
        private final Path packRoot;
        private final long generation;
        private RootValidation pendingValidation;
        private boolean closed;

        private RootMutation(Path packRoot, long generation) {
            this.packRoot = packRoot;
            this.generation = generation;
        }

        public synchronized PackValidationResult stageMatchingCopy(
                Path sourceRoot,
                String copiedContentFingerprint
        ) {
            requireOpen();
            RootValidation matching = matchingValidation(sourceRoot, copiedContentFingerprint);
            if (matching == null) {
                return null;
            }
            pendingValidation = matching;
            return matching.result();
        }

        public synchronized void stage(PackValidationResult result) {
            requireOpen();
            pendingValidation = new RootValidation(
                    Objects.requireNonNull(result, "Pack validation result"),
                    "");
        }

        public synchronized void commit() {
            requireOpen();
            if (pendingValidation == null) {
                throw new IllegalStateException("No pack validation is staged for " + packRoot);
            }
            AtomicBoolean published = new AtomicBoolean();
            ROOT_STATES.computeIfPresent(packRoot, (path, current) -> {
                if (!current.mutating() || current.generation() != generation) {
                    return current;
                }
                published.set(true);
                return new RootState(generation, false, pendingValidation);
            });
            if (!published.get()) {
                throw new IllegalStateException("Iris pack validation mutation lost ownership of " + packRoot);
            }
            closed = true;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeMutation(packRoot, generation);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Iris pack validation mutation is already closed for " + packRoot);
            }
        }
    }

    public static final class ValidationTicket {
        private final Path packRoot;
        private final long generation;

        private ValidationTicket(Path packRoot, long generation) {
            this.packRoot = packRoot;
            this.generation = generation;
        }
    }

    private record RootValidation(PackValidationResult result, String contentFingerprint) {
    }

    private record RootState(long generation, boolean mutating, RootValidation validation) {
    }
}
