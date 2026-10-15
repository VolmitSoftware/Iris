/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.catalog;

import art.arcane.iris.modded.catalog.ModdedWorldgenCatalogs.CaptureResult;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedWorldgenCatalogsTest {
    private static final CaptureResult RESULT = new CaptureResult(null, Path.of("worldgen-catalog.json"), 1L);

    @Test
    public void scanErrorCompletesTheCaptureAndFreesTheNextOne() throws Exception {
        CompletableFuture<CaptureResult> failed = ModdedWorldgenCatalogs.capture(() -> {
            throw new LinkageError("registry class missing");
        });

        ExecutionException failure = assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof LinkageError);
        assertSame(RESULT, succeed());
    }

    @Test
    public void workerErrorCompletesTheCaptureAndFreesTheNextOne() throws Exception {
        CompletableFuture<CaptureResult> failed = ModdedWorldgenCatalogs.capture(() -> () -> {
            throw new AssertionError("encode exploded");
        });

        ExecutionException failure = assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof AssertionError);
        assertSame(RESULT, succeed());
    }

    @Test
    public void workerWriteFailureCompletesWithTheIoCause() throws Exception {
        CompletableFuture<CaptureResult> failed = ModdedWorldgenCatalogs.capture(() -> () -> {
            throw new IOException("disk full");
        });

        ExecutionException failure = assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof UncheckedIOException);
        assertSame(RESULT, succeed());
    }

    @Test
    public void virtualMachineErrorDuringTheScanIsRethrownAfterFreeingTheCapture() throws Exception {
        assertThrows(OutOfMemoryError.class, () -> ModdedWorldgenCatalogs.capture(() -> {
            throw new OutOfMemoryError("scan heap");
        }));

        assertSame(RESULT, succeed());
    }

    private static CaptureResult succeed() throws InterruptedException, ExecutionException, TimeoutException {
        return ModdedWorldgenCatalogs.capture(() -> () -> RESULT).get(5, TimeUnit.SECONDS);
    }
}
