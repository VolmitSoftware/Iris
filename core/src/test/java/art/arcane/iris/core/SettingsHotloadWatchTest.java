package art.arcane.iris.core;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.IrisMessages;
import art.arcane.iris.testsupport.Await;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.MessageArgument;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SettingsHotloadWatchTest {
    private static final String PERMISSION = "iris.all";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previousSettings;
    private File dataFolder;
    private File settingsFile;
    private File overrideFolder;
    private SettingsHotloadWatch watch;

    @Before
    public void setUp() throws Exception {
        previousSettings = IrisSettings.settings;
        dataFolder = temporaryFolder.newFolder("iris-hotload");
        settingsFile = new File(dataFolder, "iris.json");
        overrideFolder = new File(dataFolder, "languages");
        Files.createDirectories(overrideFolder.toPath());
        String settings = settings("en_US");
        Files.writeString(settingsFile.toPath(), settings, StandardCharsets.UTF_8);
        IrisSettings.settings = IrisSettings.parseHotloadSnapshot(settings);
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        watch = new SettingsHotloadWatch(
                settingsFile,
                new SettingsHotloadWatch.Timing(100L, 100L, 100L, 100L)
        );
    }

    @After
    public void tearDown() {
        if (watch != null) {
            watch.close();
        }
        IrisLanguage.reload(dataFolder, "en_US");
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void activeLocaleSnapshotAppliesAndInvalidSnapshotUsesEnglish() {
        File override = override("en_US");
        String valid = locale("en_US", "Active {permission}");

        assertTrue(watch.applySnapshot(present(override, valid)));
        assertEquals("Active " + PERMISSION, permissionMessage());

        assertTrue(watch.applySnapshot(present(override, "{ invalid")));
        assertEquals("You lack the permission '" + PERMISSION + "'", permissionMessage());
    }

    @Test
    public void activeLocaleDeletionFallsBackToCodeOwnedEnglish() {
        File override = override("en_US");
        assertTrue(watch.applySnapshot(present(override, locale("en_US", "Temporary {permission}"))));
        assertEquals("Temporary " + PERMISSION, permissionMessage());

        assertTrue(watch.applySnapshot(missing(override)));

        assertEquals("You lack the permission '" + PERMISSION + "'", permissionMessage());
        assertEquals("en_US", IrisLanguage.activeLocale());
    }

    @Test
    public void inactiveLocaleSnapshotDoesNotChangeTheRuntimeCatalog() {
        String before = permissionMessage();

        assertTrue(watch.applySnapshot(present(
                override("de_DE"),
                locale("de_DE", "Inaktiv {permission}")
        )));

        assertEquals(before, permissionMessage());
        assertEquals("en_US", IrisLanguage.activeLocale());
    }

    @Test
    public void settingsSnapshotChangesTheActiveLocaleFromItsImmutableOverride() throws Exception {
        Files.writeString(
                override("de_DE").toPath(),
                locale("de_DE", "Berechtigung {permission}"),
                StandardCharsets.UTF_8
        );

        assertTrue(watch.applySnapshot(present(settingsFile, settings("de_DE"))));

        assertEquals("de_DE", IrisSettings.get().getGeneral().getLanguage());
        assertEquals("de_DE", IrisLanguage.activeLocale());
        assertEquals("Berechtigung " + PERMISSION, permissionMessage());
    }

    @Test
    public void invalidLocaleAllowsItsSettingsSwitchWithEnglishFallback() throws Exception {
        Files.writeString(override("de_DE").toPath(), "{ invalid", StandardCharsets.UTF_8);

        assertTrue(watch.applySnapshot(present(settingsFile, settings("de_DE"))));

        assertEquals("de_DE", IrisSettings.get().getGeneral().getLanguage());
        assertEquals("de_DE", IrisLanguage.activeLocale());
        assertEquals("You lack the permission '" + PERMISSION + "'", permissionMessage());
    }

    @Test(timeout = 8_000L)
    public void sameMetadataLocaleReplacementStillHotloads() throws Exception {
        File override = override("en_US");
        FileTime fixedTime = FileTime.fromMillis(10_000L);
        String first = locale("en_US", "First {permission}");
        String second = locale("en_US", "Other {permission}");
        assertEquals(first.getBytes(StandardCharsets.UTF_8).length, second.getBytes(StandardCharsets.UTF_8).length);

        Files.writeString(override.toPath(), first, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(override.toPath(), fixedTime);
        awaitPermissionMessage("First " + PERMISSION);

        Files.writeString(override.toPath(), second, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(override.toPath(), fixedTime);
        awaitPermissionMessage("Other " + PERMISSION);
    }

    @Test(timeout = 8_000L)
    public void closeWaitsForInFlightSnapshotAndStopsLaterApplies() throws Exception {
        File override = override("en_US");
        CountDownLatch applyEntered = new CountDownLatch(1);
        CountDownLatch releaseApply = new CountDownLatch(1);
        AtomicInteger automaticApplies = new AtomicInteger();
        replaceWatch(new SettingsHotloadWatch.Timing(100L, 100L, 100L, 100L), snapshot -> {
            if (!override.equals(snapshot.file())) {
                return;
            }
            automaticApplies.incrementAndGet();
            applyEntered.countDown();
            try {
                releaseApply.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding the hotload apply boundary", failure);
            }
        });
        Files.writeString(override.toPath(), locale("en_US", "During {permission}"), StandardCharsets.UTF_8);

        Thread checker = new Thread(() -> checkUntilEntered(applyEntered), "Iris-Hotload-Lifecycle-Test");
        checker.start();
        assertTrue(applyEntered.await(5L, TimeUnit.SECONDS));

        CountDownLatch closeStarted = new CountDownLatch(1);
        AtomicBoolean closeCompleted = new AtomicBoolean();
        Thread closer = new Thread(() -> {
            closeStarted.countDown();
            watch.close();
            closeCompleted.set(true);
        }, "Iris-Hotload-Close-Test");
        closer.start();
        assertTrue(closeStarted.await(1L, TimeUnit.SECONDS));
        awaitThreadState(closer, Thread.State.BLOCKED);
        assertFalse(closeCompleted.get());

        releaseApply.countDown();
        checker.join(2_000L);
        closer.join(2_000L);
        assertFalse(checker.isAlive());
        assertFalse(closer.isAlive());
        assertTrue(closeCompleted.get());
        assertEquals("During " + PERMISSION, permissionMessage());

        Files.writeString(override.toPath(), locale("en_US", "After {permission}"), StandardCharsets.UTF_8);
        watch.checkConfigHotload();
        assertEquals(1, automaticApplies.get());
        assertEquals("During " + PERMISSION, permissionMessage());
    }

    @Test(timeout = 8_000L)
    public void manualReloadInvalidatesPendingAutomaticLocaleWork() throws Exception {
        File override = override("en_US");
        AtomicInteger automaticApplies = new AtomicInteger();
        replaceWatch(
                new SettingsHotloadWatch.Timing(100L, 400L, 100L, 100L),
                snapshot -> {
                    if (override.equals(snapshot.file())) {
                        automaticApplies.incrementAndGet();
                    }
                }
        );
        Files.writeString(override.toPath(), locale("en_US", "First {permission}"), StandardCharsets.UTF_8);
        awaitPermissionMessage("First " + PERMISSION);
        assertEquals(1, automaticApplies.get());

        Files.writeString(override.toPath(), locale("en_US", "Manual {permission}"), StandardCharsets.UTF_8);
        pollWithoutChange(125L, "First " + PERMISSION);
        assertEquals("First " + PERMISSION, permissionMessage());
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        assertEquals("Manual " + PERMISSION, permissionMessage());

        pollWithoutChange(650L, "Manual " + PERMISSION);
        assertEquals(1, automaticApplies.get());
        assertEquals("Manual " + PERMISSION, permissionMessage());
    }

    @Test(timeout = 8_000L)
    public void oversizedActiveLanguageReportsOnceAndUsesEnglish() throws Exception {
        File override = override("en_US");
        assertTrue(watch.applySnapshot(present(override, locale("en_US", "Active {permission}"))));
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        Files.write(override.toPath(), oversized);

        String diagnostic = captureErrorsWhilePolling("Hotload file exceeds 2097152 bytes", 1_200L);

        assertEquals(1, countOccurrences(
                diagnostic,
                "Failed to read watched Iris file " + override.getAbsolutePath()
        ));
        assertEquals("You lack the permission '" + PERMISSION + "'", permissionMessage());
    }

    @Test(timeout = 8_000L)
    public void malformedUtf8FailureIsDeduplicatedAndUsesEnglish() throws Exception {
        File override = override("en_US");
        assertTrue(watch.applySnapshot(present(override, locale("en_US", "Active {permission}"))));
        Files.write(override.toPath(), new byte[]{(byte) 0xC3, 0x28});

        String diagnostic = captureErrorsWhilePolling("Hotload file is not valid UTF-8", 1_200L);

        assertEquals(1, countOccurrences(
                diagnostic,
                "Failed to read watched Iris file " + override.getAbsolutePath()
        ));
        assertEquals("You lack the permission '" + PERMISSION + "'", permissionMessage());
    }

    private void awaitPermissionMessage(String expected) {
        Await.reached("the hotload permission message '" + expected + "'", Duration.ofSeconds(6L), () -> {
            watch.checkConfigHotload();
            return expected.equals(permissionMessage());
        });
        assertEquals(expected, permissionMessage());
    }

    private void replaceWatch(
            SettingsHotloadWatch.Timing timing,
            Consumer<ConfigHotloadEngine.StableContentSnapshot> beforeSnapshotApply
    ) {
        watch.close();
        watch = new SettingsHotloadWatch(settingsFile, timing, beforeSnapshotApply);
    }

    private void checkUntilEntered(CountDownLatch entered) {
        Await.until("the hotload apply boundary", Duration.ofSeconds(5L), () -> {
            watch.checkConfigHotload();
            return entered.getCount() == 0L;
        });
    }

    private void awaitThreadState(Thread thread, Thread.State expected) {
        Await.reached(thread.getName() + " to reach " + expected, Duration.ofSeconds(1L),
                () -> thread.getState() == expected);
        assertEquals(expected, thread.getState());
    }

    private void pollWithoutChange(long windowMillis, String expected) {
        assertFalse("The hotload permission message changed inside the quiet window",
                Await.reached("an unexpected hotload apply", Duration.ofMillis(windowMillis), () -> {
                    watch.checkConfigHotload();
                    return !expected.equals(permissionMessage());
                }));
    }

    private String captureErrorsWhilePolling(String expected, long duplicateWindowMillis) throws Exception {
        PrintStream originalError = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            if (!Await.reached("the hotload diagnostic '" + expected + "'", Duration.ofSeconds(4L), () -> {
                watch.checkConfigHotload();
                return captured.toString(StandardCharsets.UTF_8).contains(expected);
            })) {
                fail("Timed out after 4000ms waiting for the hotload diagnostic '" + expected
                        + "'; captured stderr was: " + captured.toString(StandardCharsets.UTF_8));
            }
            Await.reached("a duplicate hotload diagnostic", Duration.ofMillis(duplicateWindowMillis), () -> {
                watch.checkConfigHotload();
                return false;
            });
        } finally {
            System.setErr(originalError);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private int countOccurrences(String value, String target) {
        int count = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(target, cursor)) >= 0) {
            count++;
            cursor += target.length();
        }
        return count;
    }

    private ConfigHotloadEngine.StableContentSnapshot present(File file, String content) {
        return new ConfigHotloadEngine.StableContentSnapshot(file, "present", content.trim(), 0L);
    }

    private ConfigHotloadEngine.StableContentSnapshot missing(File file) {
        return new ConfigHotloadEngine.StableContentSnapshot(file, "missing", null, 0L);
    }

    private File override(String locale) {
        return new File(overrideFolder, locale + ".toml").getAbsoluteFile();
    }

    private String permissionMessage() {
        return IrisLanguage.plain(
                IrisMessages.COMMAND_PERMISSION_DENIED,
                MessageArgument.untrusted("permission", PERMISSION)
        );
    }

    private String settings(String locale) {
        return "{\"general\":{\"language\":\"" + locale + "\"}}";
    }

    private String locale(String locale, String permissionMessage) {
        return "[iris.command]\npermission_denied = \"" + permissionMessage + "\"\n";
    }
}
