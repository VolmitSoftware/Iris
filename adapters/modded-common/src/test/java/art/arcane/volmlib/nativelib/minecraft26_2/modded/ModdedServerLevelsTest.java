package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedServerLevelsTest {
    @Test
    public void detectsAndInvokesHostLevelPublicationPair() {
        ModdedServerLevels.HostLevelPublication publication =
                ModdedServerLevels.HostLevelPublication.detect(PublishingServer.class, PublishedLevel.class);
        PublishingServer server = new PublishingServer();
        PublishedLevel level = new PublishedLevel();

        assertTrue(publication.supported());
        publication.add(server, level);
        assertSame(level, server.added);
        publication.remove(server, level);
        assertSame(level, server.removed);
    }

    @Test
    public void rejectsPartialHostLevelPublicationCapability() {
        ModdedServerLevels.HostLevelPublication publication =
                ModdedServerLevels.HostLevelPublication.detect(AddOnlyServer.class, PublishedLevel.class);

        assertFalse(publication.supported());
    }

    @Test
    public void propagatesHostPublicationFailureWithoutReflectionWrapper() {
        ModdedServerLevels.HostLevelPublication publication =
                ModdedServerLevels.HostLevelPublication.detect(FailingServer.class, PublishedLevel.class);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publication.add(new FailingServer(), new PublishedLevel()));

        assertNull(failure.getCause());
    }

    public static final class PublishingServer {
        private PublishedLevel added;
        private PublishedLevel removed;

        public void addLevel(PublishedLevel level) {
            added = level;
        }

        public void removeLevel(PublishedLevel level) {
            removed = level;
        }
    }

    public static final class AddOnlyServer {
        public void addLevel(PublishedLevel level) {
        }
    }

    public static final class FailingServer {
        public void addLevel(PublishedLevel level) {
            throw new IllegalStateException("publication failed");
        }

        public void removeLevel(PublishedLevel level) {
        }
    }

    public static final class PublishedLevel {
    }
}
