package art.arcane.iris.world.history;

import art.arcane.iris.world.storage.Durability;

/**
 * How a history publishes and re-checks its generation epochs. Relaxed and Studio histories skip the fsyncs of epoch
 * packs, epoch metadata and the manifest, so a crash can lose or tear them. Studio epochs are also trusted after this process hashed them once, because Studio worlds
 * are private to the server and discarded on restart.
 */
public enum EpochPolicy {
    DURABLE,
    RELAXED,
    STUDIO;

    public static EpochPolicy configured() {
        return Durability.enabled() ? DURABLE : RELAXED;
    }

    public boolean syncs() {
        return this == DURABLE;
    }

    public boolean trustsVerifiedPacks() {
        return this == STUDIO;
    }
}
