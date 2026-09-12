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

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PackValidationRegistryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        PackValidationRegistry.clear();
    }

    @After
    public void tearDown() {
        PackValidationRegistry.clear();
    }

    @Test
    public void missingValidationFailsClosed() {
        assertBroken("overworld", "has not completed");
    }

    @Test
    public void blockingValidationFailsClosedWithOriginalReasons() {
        PackValidationRegistry.publish(new PackValidationResult(
                "overworld", List.of("replacement graph is not runtime-viable"), List.of(), 1L));

        assertBroken("overworld", "replacement graph is not runtime-viable");
    }

    @Test
    public void successfulValidationAuthorizesUse() {
        PackValidationResult result = new PackValidationResult(
                "overworld", List.of(), List.of("warning"), 1L);
        PackValidationRegistry.publish(result);

        assertEquals(result, PackValidationRegistry.requireLoadable("overworld"));
    }

    @Test
    public void exactRootsWithTheSameBasenameRemainIndependent() throws Exception {
        Path firstRoot = temporaryFolder.newFolder("first").toPath().resolve("pack");
        Path secondRoot = temporaryFolder.newFolder("second").toPath().resolve("pack");
        PackValidationResult loadable = new PackValidationResult(
                "pack", List.of(), List.of(), 1L);
        PackValidationResult broken = new PackValidationResult(
                "pack", List.of("second snapshot is broken"), List.of(), 2L);

        PackValidationRegistry.publish(firstRoot, loadable);
        PackValidationRegistry.publish(secondRoot, broken);

        assertEquals(loadable, PackValidationRegistry.requireLoadable(firstRoot));
        assertEquals(broken, PackValidationRegistry.get(secondRoot));
        assertTrue(PackValidationRegistry.isBroken(secondRoot));
        assertNull(PackValidationRegistry.get("pack"));
        assertBroken(secondRoot, "second snapshot is broken");
    }

    @Test
    public void removingOneExactRootDoesNotEvictItsSameNamedSibling() throws Exception {
        Path firstRoot = temporaryFolder.newFolder("remove-first").toPath().resolve("pack");
        Path secondRoot = temporaryFolder.newFolder("keep-second").toPath().resolve("pack");
        PackValidationResult first = new PackValidationResult("pack", List.of(), List.of(), 1L);
        PackValidationResult second = new PackValidationResult("pack", List.of(), List.of(), 2L);
        PackValidationRegistry.publish(firstRoot, first);
        PackValidationRegistry.publish(secondRoot, second);

        PackValidationRegistry.remove(firstRoot);

        assertNull(PackValidationRegistry.get(firstRoot));
        assertEquals(second, PackValidationRegistry.requireLoadable(secondRoot));
    }

    @Test
    public void existingRootAliasesResolveToTheSameRealPath() throws Exception {
        Path realRoot = temporaryFolder.newFolder("real-pack").toPath();
        Path linkedRoot = realRoot.getParent().resolve("linked-pack");
        try {
            Files.createSymbolicLink(linkedRoot, realRoot);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }
        PackValidationResult result = new PackValidationResult("pack", List.of(), List.of(), 1L);

        PackValidationRegistry.publish(linkedRoot, result);

        assertEquals(result, PackValidationRegistry.requireLoadable(realRoot));
    }

    @Test
    public void copiedValidationPublishesOnlyForTheExactValidatedFingerprint() throws Exception {
        Path sourceRoot = temporaryFolder.newFolder("copy-source").toPath();
        Path matchingTarget = temporaryFolder.newFolder("copy-matching-target").toPath();
        Path mismatchedTarget = temporaryFolder.newFolder("copy-mismatched-target").toPath();
        PackValidationResult result = new PackValidationResult(
                "source", List.of(), List.of("source warning"), 7L);
        PackValidationRegistry.publish(sourceRoot, result, "fingerprint-a");

        assertSame(result, PackValidationRegistry.publishMatchingCopy(
                sourceRoot,
                matchingTarget,
                "fingerprint-a"));
        assertSame(result, PackValidationRegistry.requireLoadable(matchingTarget));
        assertNull(PackValidationRegistry.publishMatchingCopy(
                sourceRoot,
                mismatchedTarget,
                "fingerprint-b"));
        assertNull(PackValidationRegistry.get(mismatchedTarget));
    }

    @Test
    public void unfingerprintedRepublishRevokesCopiedValidationReuse() throws Exception {
        Path sourceRoot = temporaryFolder.newFolder("republished-source").toPath();
        Path targetRoot = temporaryFolder.newFolder("republished-target").toPath();
        PackValidationResult initial = new PackValidationResult("source", List.of(), List.of(), 3L);
        PackValidationResult replacement = new PackValidationResult("source", List.of(), List.of(), 5L);
        PackValidationRegistry.publish(sourceRoot, initial, "old-fingerprint");

        PackValidationRegistry.publish(sourceRoot, replacement);

        assertNull(PackValidationRegistry.publishMatchingCopy(
                sourceRoot,
                targetRoot,
                "old-fingerprint"));
        assertSame(replacement, PackValidationRegistry.requireLoadable(sourceRoot));
        assertNull(PackValidationRegistry.get(targetRoot));
    }

    @Test
    public void rootMutationDefeatsAnInterleavedStaleValidationTicket() throws Exception {
        Path packRoot = temporaryFolder.newFolder("reserved-root").toPath();
        PackValidationResult original = new PackValidationResult(
                "pack", List.of(), List.of("original"), 1L);
        PackValidationResult stale = new PackValidationResult(
                "pack", List.of(), List.of("stale"), 2L);
        PackValidationResult replacement = new PackValidationResult(
                "pack", List.of(), List.of("replacement"), 3L);
        PackValidationRegistry.publish(packRoot, original);
        CountDownLatch ticketReady = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        AtomicReference<PackValidationRegistry.ValidationTicket> ticket = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> stalePublish = executor.submit(() -> {
            PackValidationRegistry.ValidationTicket validationTicket =
                    PackValidationRegistry.tryBeginValidation(packRoot);
            ticket.set(validationTicket);
            ticketReady.countDown();
            if (!mutationStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Root mutation did not begin");
            }
            return PackValidationRegistry.publishIfCurrent(validationTicket, stale);
        });

        try {
            assertTrue(ticketReady.await(5, TimeUnit.SECONDS));
            assertNotNull(ticket.get());
            try (PackValidationRegistry.RootMutation mutation =
                         PackValidationRegistry.beginRootMutation(packRoot)) {
                assertNull(PackValidationRegistry.get(packRoot));
                assertThrows(BrokenPackException.class,
                        () -> PackValidationRegistry.requireLoadable(packRoot));
                assertNull(PackValidationRegistry.tryBeginValidation(packRoot));
                mutationStarted.countDown();

                assertFalse(stalePublish.get(5, TimeUnit.SECONDS));
                assertNull(PackValidationRegistry.get(packRoot));
                assertNull(PackValidationRegistry.tryBeginValidation(packRoot));

                mutation.stage(replacement);
                assertNull(PackValidationRegistry.get(packRoot));
                mutation.commit();
            }

            assertSame(replacement, PackValidationRegistry.requireLoadable(packRoot));
        } finally {
            mutationStarted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void assertBroken(String pack, String expectedReason) {
        try {
            PackValidationRegistry.requireLoadable(pack);
        } catch (BrokenPackException e) {
            assertEquals(pack, e.getPackName());
            assertTrue(e.getReasons().toString(), e.getReasons().stream().anyMatch(
                    reason -> reason.contains(expectedReason)));
            return;
        }
        throw new AssertionError("Expected pack validation to fail closed");
    }

    private void assertBroken(Path packRoot, String expectedReason) throws IOException {
        try {
            PackValidationRegistry.requireLoadable(packRoot);
        } catch (BrokenPackException e) {
            Path expectedRoot = packRoot.getParent().toRealPath().resolve(packRoot.getFileName()).normalize();
            assertEquals(expectedRoot.toString(), e.getPackName());
            assertTrue(e.getReasons().toString(), e.getReasons().stream().anyMatch(
                    reason -> reason.contains(expectedReason)));
            return;
        }
        throw new AssertionError("Expected pack validation to fail closed");
    }
}
