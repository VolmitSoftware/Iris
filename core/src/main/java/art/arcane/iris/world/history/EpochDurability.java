package art.arcane.iris.world.history;

import art.arcane.iris.world.storage.Durability;

/**
 * Whether publishing a generation epoch forces its pack files and directories to disk. Unsynced epochs can be
 * lost or torn by a crash, so they are only for histories that are discarded on restart, such as Studio worlds.
 */
public enum EpochDurability {
    SYNCED,
    UNSYNCED;

    public static EpochDurability configured() {
        return Durability.enabled() ? SYNCED : UNSYNCED;
    }

    public boolean syncs() {
        return this == SYNCED;
    }
}
