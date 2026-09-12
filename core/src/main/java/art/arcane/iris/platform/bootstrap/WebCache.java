/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bootstrap;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Download cache helpers over the platform data folder.
 *
 * <p>Every request is bounded: URL.openStream had no connect or read timeout, so a hung mirror parked the
 * calling thread (a command thread, or the boot pack prefetch) forever.
 */
public final class WebCache {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10L);
    private static final DownloadPolicy DOWNLOAD_POLICY = new DownloadPolicy(
            Duration.ofSeconds(10L),
            3,
            Duration.ofSeconds(1L)
    );
    private static final int BUFFER_SIZE = 8192;
    private static final long PROGRESS_INTERVAL_NANOS = Duration.ofMillis(250L).toNanos();
    private static final TransferProgressListener NO_TRANSFER_PROGRESS = progress -> {
    };

    private WebCache() {
    }

    public static File getTemp() {
        return IrisPlatforms.get().dataFolder("cache", "temp");
    }

    public static File getCached(String name, String url) {
        String h = IO.hash(name + "@" + url);
        File f = IrisPlatforms.get().dataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

        if (!f.exists()) {
            download(name, url, f);
        }

        return f.exists() ? f : null;
    }

    public static String getNonCached(String name, String url) {
        String h = IO.hash(name + "*" + url);
        File f = IrisPlatforms.get().dataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

        if (!download(name, url, f)) {
            return "";
        }
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            IrisLogging.reportError(e);
            return "";
        }
    }

    public static File getNonCachedFile(String name, String url) throws IOException {
        return getNonCachedFile(name, url, Long.MAX_VALUE);
    }

    public static File getNonCachedFile(String name, String url, long maxBytes) throws IOException {
        return getNonCachedFile(name, url, maxBytes, NO_TRANSFER_PROGRESS);
    }

    public static File getNonCachedFile(String name, String url,
                                        TransferProgressListener progressListener) throws IOException {
        return getNonCachedFile(name, url, Long.MAX_VALUE, progressListener);
    }

    public static File getNonCachedFile(String name, String url, long maxBytes,
                                        TransferProgressListener progressListener) throws IOException {
        return getNonCachedFile(name, url, maxBytes, DOWNLOAD_POLICY, progressListener);
    }

    static File getNonCachedFile(String name, String url, long maxBytes, DownloadPolicy policy,
                                 TransferProgressListener progressListener) throws IOException {
        String h = IO.hash(name + "*" + url);
        File f = IrisPlatforms.get().dataFile("cache", h.substring(0, 2), h.substring(3, 5), h);
        IrisLogging.debug("Download " + name);
        download(name, url, f, maxBytes, policy, progressListener);
        return f;
    }

    private static boolean download(String name, String url, File target) {
        return download(name, url, target, Long.MAX_VALUE, NO_TRANSFER_PROGRESS);
    }

    private static boolean download(String name, String url, File target, long maxBytes) {
        return download(name, url, target, maxBytes, NO_TRANSFER_PROGRESS);
    }

    private static boolean download(String name, String url, File target, long maxBytes,
                                    TransferProgressListener progressListener) {
        try {
            download(name, url, target, maxBytes, DOWNLOAD_POLICY, progressListener);
            return true;
        } catch (InterruptedIOException exception) {
            if (Thread.currentThread().isInterrupted()) {
                IrisLogging.debug("Download interrupted for " + name);
            } else {
                IrisLogging.reportError(exception);
            }
            return false;
        } catch (IOException exception) {
            IrisLogging.reportError(exception);
            return false;
        }
    }

    private static void download(String name, String url, File target, long maxBytes, DownloadPolicy policy,
                                 TransferProgressListener progressListener) throws IOException {
        if (maxBytes < 1L) {
            throw new IllegalArgumentException("Download size limit must be positive.");
        }
        DownloadPolicy downloadPolicy = Objects.requireNonNull(policy, "policy");
        TransferProgressListener progress = progressListener == null ? NO_TRANSFER_PROGRESS : progressListener;
        DownloadFailure lastFailure = null;
        for (int attempt = 1; attempt <= downloadPolicy.attempts(); attempt++) {
            checkInterrupted();
            try {
                downloadAttempt(name, url, target, maxBytes, downloadPolicy.readTimeout(), progress);
                return;
            } catch (DownloadFailure failure) {
                lastFailure = failure;
                if (!failure.retryable() || attempt == downloadPolicy.attempts()) {
                    if (failure.retryable() && attempt > 1) {
                        throw new DownloadFailure(
                                failure.getMessage() + " Download failed after " + attempt + " attempts.",
                                false,
                                failure
                        );
                    }
                    throw failure;
                }
                awaitRetry(downloadPolicy.retryDelay().multipliedBy(attempt));
            }
        }
        throw Objects.requireNonNull(lastFailure, "lastFailure");
    }

    private static void downloadAttempt(String name, String url, File target, long maxBytes, Duration readTimeout,
                                        TransferProgressListener progress) throws IOException {
        Path staged = null;
        try {
            checkInterrupted();
            Path destination = target.toPath().toAbsolutePath().normalize();
            Path parent = destination.getParent();
            if (parent == null) {
                throw new DownloadFailure("Download target has no parent: " + destination, false);
            }
            try {
                Files.createDirectories(parent);
                staged = Files.createTempFile(parent, ".download-", ".tmp");
            } catch (IOException exception) {
                throw new DownloadFailure("Unable to stage download " + name + ".", false, exception);
            }
            transfer(name, url, staged, maxBytes, readTimeout, progress);
            checkInterrupted();
            try {
                Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new DownloadFailure("Unable to publish download " + name + ".", false, exception);
            }
            staged = null;
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException cleanupFailure) {
                    IrisLogging.reportError("Failed to clean incomplete download " + staged + ".", cleanupFailure);
                }
            }
        }
    }

    private static void transfer(String name, String url, Path staged, long maxBytes, Duration readTimeout,
                                 TransferProgressListener progress) throws IOException {
        HttpURLConnection connection = null;
        Thread interruptionWatchdog = null;
        boolean responseStarted = false;
        try {
            URLConnection opened = URI.create(url).toURL().openConnection();
            if (!(opened instanceof HttpURLConnection httpConnection)) {
                throw new DownloadFailure("Download URL is not HTTP or HTTPS.", false);
            }
            connection = httpConnection;
            connection.setConnectTimeout(timeoutMillis(CONNECT_TIMEOUT));
            connection.setReadTimeout(timeoutMillis(readTimeout));
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            interruptionWatchdog = startInterruptionWatchdog(connection);
            int statusCode = connection.getResponseCode();
            if (statusCode / 100 != 2) {
                closeErrorResponse(connection);
                throw new DownloadFailure(
                        "HTTP " + statusCode + " while downloading " + name + ".",
                        isRetryableStatus(statusCode)
                );
            }
            responseStarted = true;
            long declaredBytes = connection.getContentLengthLong();
            if (declaredBytes > maxBytes) {
                throw new DownloadFailure("Download exceeds the size limit for " + name + ".", false);
            }
            streamResponse(name, connection, staged, maxBytes, declaredBytes, progress);
        } catch (DownloadFailure exception) {
            throw exception;
        } catch (InterruptedIOException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            String message = responseStarted && exception instanceof SocketTimeoutException
                    ? "Download of " + name + " stalled for " + readTimeout.toSeconds()
                    + " seconds without receiving data."
                    : "Connection timed out or was interrupted while downloading " + name + ".";
            throw new DownloadFailure(message, true, exception);
        } catch (IOException exception) {
            if (Thread.currentThread().isInterrupted()) {
                InterruptedIOException interrupted = new InterruptedIOException("Download interrupted.");
                interrupted.initCause(exception);
                throw interrupted;
            }
            throw new DownloadFailure("Network failure while downloading " + name + ": "
                    + errorDetail(exception), true, exception);
        } catch (IllegalArgumentException exception) {
            throw new DownloadFailure("Invalid download URL for " + name + ".", false, exception);
        } finally {
            if (interruptionWatchdog != null) {
                interruptionWatchdog.interrupt();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void streamResponse(String name, HttpURLConnection connection, Path staged, long maxBytes,
                                       long declaredBytes, TransferProgressListener progress) throws IOException {
        long startedNanos = System.nanoTime();
        long lastProgressNanos = startedNanos;
        sendProgress(progress, new TransferProgress(0L, declaredBytes, 0L, false));
        long downloadedBytes = 0L;
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(staged, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while (true) {
                checkInterrupted();
                read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                checkInterrupted();
                if (read > maxBytes - downloadedBytes) {
                    throw new DownloadFailure("Download exceeds the size limit for " + name + ".", false);
                }
                out.write(buffer, 0, read);
                downloadedBytes += read;
                long currentNanos = System.nanoTime();
                if (currentNanos - lastProgressNanos >= PROGRESS_INTERVAL_NANOS) {
                    sendProgress(progress, new TransferProgress(
                            downloadedBytes,
                            declaredBytes,
                            elapsedMillis(startedNanos, currentNanos),
                            false
                    ));
                    lastProgressNanos = currentNanos;
                }
            }
            out.flush();
        }
        if (declaredBytes >= 0L && downloadedBytes != declaredBytes) {
            throw new DownloadFailure(
                    "Download of " + name + " ended after " + downloadedBytes + " of " + declaredBytes + " bytes.",
                    true
            );
        }
        sendProgress(progress, new TransferProgress(
                downloadedBytes,
                declaredBytes,
                elapsedMillis(startedNanos),
                true
        ));
    }

    private static void awaitRetry(Duration delay) throws InterruptedIOException {
        try {
            TimeUnit.MILLISECONDS.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("Download retry interrupted.");
            interrupted.initCause(exception);
            throw interrupted;
        }
    }

    private static Thread startInterruptionWatchdog(HttpURLConnection connection) {
        Thread worker = Thread.currentThread();
        Thread watchdog = new Thread(() -> {
            while (!worker.isInterrupted()) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            connection.disconnect();
        }, "Iris Web Download Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private static int timeoutMillis(Duration timeout) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, timeout.toMillis()));
    }

    private static void closeErrorResponse(HttpURLConnection connection) throws IOException {
        InputStream response = connection.getErrorStream();
        if (response != null) {
            response.close();
        }
    }

    private static String errorDetail(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Download interrupted.");
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return elapsedMillis(startedNanos, System.nanoTime());
    }

    private static long elapsedMillis(long startedNanos, long currentNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, currentNanos - startedNanos));
    }

    private static void sendProgress(TransferProgressListener listener, TransferProgress progress) {
        try {
            listener.onProgress(progress);
        } catch (RuntimeException exception) {
            IrisLogging.reportError("Download progress delivery failed", exception);
        }
    }

    public record TransferProgress(long transferredBytes, long contentLength, long elapsedMillis, boolean complete) {
    }

    record DownloadPolicy(Duration readTimeout, int attempts, Duration retryDelay) {
        DownloadPolicy {
            Objects.requireNonNull(readTimeout, "readTimeout");
            Objects.requireNonNull(retryDelay, "retryDelay");
            if (readTimeout.isZero() || readTimeout.isNegative() || attempts < 1 || retryDelay.isNegative()) {
                throw new IllegalArgumentException("Download policy values are invalid.");
            }
        }
    }

    @FunctionalInterface
    public interface TransferProgressListener {
        void onProgress(TransferProgress progress);
    }

    private static final class DownloadFailure extends IOException {
        private final boolean retryable;

        private DownloadFailure(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        private DownloadFailure(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        private boolean retryable() {
            return retryable;
        }
    }
}
