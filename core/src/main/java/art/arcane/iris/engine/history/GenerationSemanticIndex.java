package art.arcane.iris.engine.history;

import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.io.Durability;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public final class GenerationSemanticIndex {
    private static final String FILE_SUFFIX = ".isem";
    private static final String JOURNAL_SUFFIX = ".iswal";
    private static final String CATALOG_FILE_NAME = "index.isix";
    private static final int MAGIC = 0x4953454D;
    private static final int FORMAT_VERSION = 6;
    private static final int CATALOG_MAGIC = 0x49534958;
    private static final int CATALOG_FORMAT_VERSION = 1;
    private static final int CATALOG_FIXED_BODY_BYTES = 12;
    private static final int FIXED_BODY_BYTES = 28;
    private static final int CHECKSUM_BYTES = Integer.BYTES;
    private static final int MAX_FILE_BYTES = 64 * 1_024 * 1_024;
    private static final long MAX_JOURNAL_BYTES = 512L * 1_024L * 1_024L;
    private static final long JOURNAL_COMPACTION_BYTES = 4L * 1_024L * 1_024L;
    private static final int JOURNAL_COMPACTION_ENTRIES = 256;
    private static final int MAXIMUM_CACHED_REGIONS = 64;
    private static final int MAXIMUM_CACHED_SUMMARIES = 256;
    private static final int MAX_CATALOG_BYTES = 64 * 1_024 * 1_024;
    private static final int MAX_PALETTE_KEYS = 65_535;
    private static final int CHUNKS_PER_REGION_SIDE = 32;
    private static final int CHUNKS_PER_REGION = CHUNKS_PER_REGION_SIDE * CHUNKS_PER_REGION_SIDE;
    private static final int MINIMUM_REGION_COORDINATE = Math.floorDiv(Integer.MIN_VALUE, CHUNKS_PER_REGION_SIDE);
    private static final int MAXIMUM_REGION_COORDINATE = Math.floorDiv(Integer.MAX_VALUE, CHUNKS_PER_REGION_SIDE);
    private static final Pattern SHARD_FILE_PATTERN = Pattern.compile(
            "r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.([0-9a-f]{64})\\.isem"
    );
    private static final Pattern POINTER_FILE_PATTERN = Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.isix");
    private static final Pattern JOURNAL_FILE_PATTERN = Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.iswal");

    private final Path dimensionRoot;
    private final Path directory;
    private final LinkedHashMap<Long, RegionShard> regions;
    private final LinkedHashMap<Long, RegionSummary> summaries;
    private final Long2ObjectOpenHashMap<String> shardHashes;
    private final Long2IntOpenHashMap journalEntries;
    private final Long2IntOpenHashMap regionRecordCounts;
    private final ReentrantReadWriteLock lock;
    private final ReentrantLock publicationLock;
    private final ShardPublisher publisher;
    private final PointerPublisher pointerPublisher;
    private final CatalogPublisher catalogPublisher;
    private long regionDecodeCount;
    private volatile IOException journalFailure;
    private long appendingRegionKey;
    private RegionShard appendingBase;

    private GenerationSemanticIndex(
            Path dimensionRoot,
            ShardPublisher publisher,
            PointerPublisher pointerPublisher,
            CatalogPublisher catalogPublisher
    ) {
        this.dimensionRoot = dimensionRoot.toAbsolutePath().normalize();
        directory = this.dimensionRoot.resolve("iris").resolve("generation").resolve("semantics");
        regions = new LinkedHashMap<>(MAXIMUM_CACHED_REGIONS, 0.75F, true);
        summaries = new LinkedHashMap<>(MAXIMUM_CACHED_SUMMARIES, 0.75F, true);
        shardHashes = new Long2ObjectOpenHashMap<>();
        journalEntries = new Long2IntOpenHashMap();
        regionRecordCounts = new Long2IntOpenHashMap();
        lock = new ReentrantReadWriteLock();
        publicationLock = new ReentrantLock();
        this.publisher = publisher;
        this.pointerPublisher = pointerPublisher;
        this.catalogPublisher = catalogPublisher;
    }

    public static GenerationSemanticIndex load(Path dimensionRoot) throws IOException {
        return load(dimensionRoot, RegionShard::publish, ShardPointer::publish, Catalog::publish);
    }

    public static GenerationSemanticIndex initialize(Path dimensionRoot) throws IOException {
        GenerationSemanticIndex index = new GenerationSemanticIndex(
                Objects.requireNonNull(dimensionRoot, "dimensionRoot"),
                RegionShard::publish,
                ShardPointer::publish,
                Catalog::publish
        );
        index.ensureStorageDirectory();
        Path catalog = index.directory.resolve(CATALOG_FILE_NAME);
        if (!Files.exists(catalog, LinkOption.NOFOLLOW_LINKS)) {
            Catalog.publish(index.directory, Set.of());
        }
        index.loadShards();
        return index;
    }

    public static GenerationSemanticIndex loadRequired(Path dimensionRoot) throws IOException {
        GenerationSemanticIndex index = load(dimensionRoot);
        Path catalog = index.directory.resolve(CATALOG_FILE_NAME);
        if (!Files.isRegularFile(catalog, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation semantic shard catalog is missing: " + catalog);
        }
        return index;
    }

    static GenerationSemanticIndex load(Path dimensionRoot, ShardPublisher publisher) throws IOException {
        return load(dimensionRoot, publisher, ShardPointer::publish, Catalog::publish);
    }

    static GenerationSemanticIndex loadWithCatalogPublisher(
            Path dimensionRoot,
            CatalogPublisher catalogPublisher
    ) throws IOException {
        return load(dimensionRoot, RegionShard::publish, ShardPointer::publish, catalogPublisher);
    }

    static GenerationSemanticIndex load(
            Path dimensionRoot,
            ShardPublisher publisher,
            PointerPublisher pointerPublisher,
            CatalogPublisher catalogPublisher
    ) throws IOException {
        GenerationSemanticIndex index = new GenerationSemanticIndex(
                Objects.requireNonNull(dimensionRoot, "dimensionRoot"),
                Objects.requireNonNull(publisher, "publisher"),
                Objects.requireNonNull(pointerPublisher, "pointerPublisher"),
                Objects.requireNonNull(catalogPublisher, "catalogPublisher")
        );
        index.loadShards();
        return index;
    }

    public Path storageDirectory() {
        return directory;
    }

    public boolean claimAndPersist(ChunkGenerationSemantics claim) throws IOException {
        ChunkGenerationSemantics requiredClaim = Objects.requireNonNull(claim, "claim");
        if (!requiredClaim.sealed()) {
            throw new IllegalArgumentException("Generation semantic claims must be sealed.");
        }
        Claim pending = new Claim(requiredClaim);
        claimAndPersistBatch(List.of(pending));
        return pending.result();
    }

    void claimAndPersistBatch(List<Claim> claims) {
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            Map<Long, List<Claim>> batches = new LinkedHashMap<>();
            for (Claim pending : claims) {
                if (!pending.update.sealed()) {
                    pending.fail(new IllegalArgumentException("Generation semantic claims must be sealed."));
                    continue;
                }
                long regionKey = packRegion(pending.update.chunkX() >> 5, pending.update.chunkZ() >> 5);
                batches.computeIfAbsent(regionKey, ignored -> new ArrayList<>()).add(pending);
            }
            for (Map.Entry<Long, List<Claim>> batch : batches.entrySet()) {
                persistClaimBatch(batch.getKey(), batch.getValue());
            }
        } catch (IOException failure) {
            for (Claim pending : claims) {
                pending.fail(failure);
            }
        } catch (RuntimeException | Error failure) {
            journalFailure = new IOException("Generation semantic batch failed unexpectedly.", failure);
            for (Claim pending : claims) {
                pending.fail(failure);
            }
            throw failure;
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    private void persistClaimBatch(long regionKey, List<Claim> claims) {
        try {
            requireJournalUsable();
            int regionX = regionX(regionKey);
            int regionZ = regionZ(regionKey);
            RegionShard baseRegion = loadRegionLocked(regionKey, regionX, regionZ);
            RegionShard nextRegion = baseRegion;
            List<ChunkGenerationSemantics> updates = new ArrayList<>(claims.size());
            for (Claim pending : claims) {
                ChunkGenerationSemantics update = pending.update;
                ChunkGenerationSemantics existing = nextRegion.get(update.chunkX(), update.chunkZ());
                ChunkGenerationSemantics merged;
                try {
                    merged = existing == null ? update : existing.merge(update);
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    pending.fail(failure);
                    continue;
                }
                if (merged == existing || merged.equals(existing)) {
                    pending.completed = existing == baseRegion.get(update.chunkX(), update.chunkZ());
                    continue;
                }
                nextRegion = nextRegion.withRecord(merged);
                updates.add(merged);
                pending.persisted = true;
            }
            if (updates.isEmpty()) {
                return;
            }
            ensureStorageDirectory();
            appendingRegionKey = regionKey;
            appendingBase = baseRegion;
            publicationLock.unlock();
            try {
                SemanticJournal.append(directory, regionX, regionZ, updates);
            } catch (JournalAppendFailure failure) {
                journalFailure = failure;
                throw failure;
            } catch (RuntimeException | Error failure) {
                journalFailure = new IOException("Generation semantic batch failed unexpectedly.", failure);
                throw failure;
            } finally {
                publicationLock.lock();
                appendingBase = null;
            }
            cacheRegion(regionKey, nextRegion);
            regionRecordCounts.put(regionKey, nextRegion.recordCount());
            journalEntries.addTo(regionKey, updates.size());
            for (Claim pending : claims) {
                pending.completed = true;
            }
        } catch (IOException failure) {
            for (Claim pending : claims) {
                pending.fail(failure);
            }
        }
    }

    public int discardUnstoredClaims(WorldChunkInventory stored, Set<Long> activationIds) throws IOException {
        Objects.requireNonNull(stored, "stored chunks");
        Set<Long> selected = Set.copyOf(activationIds);
        for (long activation : selected) {
            if (activation <= 0L) {
                throw new IllegalArgumentException("Generation activation IDs must be positive.");
            }
        }
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            compactJournals();
            int removed = 0;
            for (long regionKey : allRegionKeys()) {
                RegionSummary summary = loadSummaryLocked(regionKey);
                boolean relevant = false;
                for (long activation : selected) {
                    if (summary.activations.contains(activation)) {
                        relevant = true;
                        break;
                    }
                }
                if (!relevant) {
                    continue;
                }
                int regionX = regionX(regionKey);
                int regionZ = regionZ(regionKey);
                RegionShard previous = loadRegionLocked(regionKey, regionX, regionZ);
                RegionShard replacement = previous.retainingStoredClaims(stored, selected);
                int count = previous.recordCount() - replacement.recordCount();
                if (count == 0) {
                    continue;
                }
                byte[] encoded = replacement.encode();
                String hash = sha256(encoded);
                publisher.publish(directory, regionX, regionZ, encoded);
                pointerPublisher.publish(directory, regionX, regionZ, hash);
                String previousHash = shardHashes.put(regionKey, hash);
                regionRecordCounts.put(regionKey, replacement.recordCount());
                cacheRegion(regionKey, replacement);
                removed += count;
                deleteUnreferencedShard(regionX, regionZ, previousHash, hash);
            }
            return removed;
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public void compactJournals() throws IOException {
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            ArrayList<Long> regionKeys = new ArrayList<>(journalEntries.keySet());
            regionKeys.sort(GenerationSemanticIndex::compareRegionKeys);
            for (long regionKey : regionKeys) {
                compactJournal(regionKey);
            }
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public void forEachSealedClaim(long activationId, SealedClaimConsumer consumer) throws IOException {
        if (activationId <= 0L) {
            throw new IllegalArgumentException("Generation activation IDs must be positive.");
        }
        SealedClaimConsumer requiredConsumer = Objects.requireNonNull(consumer, "sealed claim consumer");
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            for (long regionKey : allRegionKeys()) {
                if (!loadSummaryLocked(regionKey).hasSealedActivation(activationId)) {
                    continue;
                }
                RegionShard region = loadRegionLocked(
                        regionKey,
                        regionX(regionKey),
                        regionZ(regionKey)
                );
                for (ChunkGenerationSemantics semantics : region.records()) {
                    if (semantics.sealed() && semantics.activationId() == activationId) {
                        requiredConsumer.accept(semantics.chunkX(), semantics.chunkZ());
                    }
                }
            }
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public boolean hasSealedClaim(int chunkX, int chunkZ, long activationId) {
        publicationLock.lock();
        try {
            ChunkGenerationSemantics semantics = getRecordLocked(chunkX, chunkZ);
            return semantics != null
                    && semantics.sealed()
                    && semantics.activationId() == activationId;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to resolve a sealed generation semantic claim.", error);
        } finally {
            publicationLock.unlock();
        }
    }

    public boolean recordAndPersist(ChunkGenerationSemantics update) throws IOException {
        ChunkGenerationSemantics requiredUpdate = Objects.requireNonNull(update, "update");
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            ChunkGenerationSemantics existing = getRecordLocked(
                    requiredUpdate.chunkX(),
                    requiredUpdate.chunkZ()
            );
            ChunkGenerationSemantics merged = existing == null ? requiredUpdate : existing.merge(requiredUpdate);
            if (merged == existing || merged.equals(existing)) {
                return false;
            }

            int regionX = requiredUpdate.chunkX() >> 5;
            int regionZ = requiredUpdate.chunkZ() >> 5;
            long regionKey = packRegion(regionX, regionZ);
            RegionShard baseRegion = loadRegionLocked(regionKey, regionX, regionZ);
            RegionShard nextRegion = baseRegion.withRecord(merged);
            byte[] encoded = nextRegion.encode();
            String shardHash = sha256(encoded);
            ensureStorageDirectory();
            publisher.publish(directory, regionX, regionZ, encoded);
            pointerPublisher.publish(directory, regionX, regionZ, shardHash);
            String previousHash = shardHashes.get(regionKey);
            if (previousHash == null) {
                TreeSet<Long> nextRegions = new TreeSet<>(GenerationSemanticIndex::compareRegionKeys);
                nextRegions.addAll(shardHashes.keySet());
                nextRegions.add(regionKey);
                catalogPublisher.publish(directory, nextRegions);
            }

            cacheRegion(regionKey, nextRegion);
            if (existing == null) {
                regionRecordCounts.addTo(regionKey, 1);
            }
            shardHashes.put(regionKey, shardHash);
            deleteUnreferencedShard(regionX, regionZ, previousHash, shardHash);
            return true;
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public Optional<ChunkGenerationSemantics> get(int chunkX, int chunkZ) {
        publicationLock.lock();
        try {
            return Optional.ofNullable(getRecordLocked(chunkX, chunkZ));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load generation semantics for chunk "
                    + chunkX + "," + chunkZ + ".", error);
        } finally {
            publicationLock.unlock();
        }
    }

    public int recordCount() {
        lock.readLock().lock();
        publicationLock.lock();
        try {
            return totalRecordCountLocked();
        } finally {
            publicationLock.unlock();
            lock.readLock().unlock();
        }
    }

    public List<ChunkGenerationSemantics> recordsSnapshot() {
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            List<ChunkGenerationSemantics> snapshot = new ArrayList<>(totalRecordCountLocked());
            for (long regionKey : allRegionKeys()) {
                RegionShard region = loadRegionLocked(
                        regionKey,
                        regionX(regionKey),
                        regionZ(regionKey)
                );
                snapshot.addAll(region.records());
            }
            snapshot.sort(Comparator.comparingInt(ChunkGenerationSemantics::chunkX)
                    .thenComparingInt(ChunkGenerationSemantics::chunkZ));
            return List.copyOf(snapshot);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load generation semantic snapshot.", error);
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public void forEachRecord(RecordConsumer consumer) throws IOException {
        RecordConsumer requiredConsumer = Objects.requireNonNull(consumer, "consumer");
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            requireJournalUsable();
            for (long regionKey : allRegionKeys()) {
                RegionShard region = loadRegionLocked(
                        regionKey,
                        regionX(regionKey),
                        regionZ(regionKey)
                );
                for (ChunkGenerationSemantics semantics : region.records()) {
                    requiredConsumer.accept(semantics);
                }
            }
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    int cachedRegionCount() {
        lock.readLock().lock();
        publicationLock.lock();
        try {
            return regions.size();
        } finally {
            publicationLock.unlock();
            lock.readLock().unlock();
        }
    }

    long regionDecodeCount() {
        lock.readLock().lock();
        publicationLock.lock();
        try {
            return regionDecodeCount;
        } finally {
            publicationLock.unlock();
            lock.readLock().unlock();
        }
    }

    int cachedSummaryCount() {
        lock.readLock().lock();
        publicationLock.lock();
        try {
            return summaries.size();
        } finally {
            publicationLock.unlock();
            lock.readLock().unlock();
        }
    }

    public Optional<Match> findNearest(Query query) {
        return findNearest(query, semantics -> true);
    }

    public Optional<Match> findNearest(
            Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        Query requiredQuery = Objects.requireNonNull(query, "query");
        Predicate<ChunkGenerationSemantics> requiredEligibility = Objects.requireNonNull(
                eligibility,
                "eligibility"
        );
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            if (requiredQuery.kind() == SemanticKind.STRUCTURE) {
                return findNearestStructure(requiredQuery, requiredEligibility);
            }
            return findNearestChunk(requiredQuery, requiredEligibility);
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    public Optional<RiverMatch> findNearestRiver(
            RiverQuery query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        RiverQuery requiredQuery = Objects.requireNonNull(query, "query");
        Predicate<ChunkGenerationSemantics> requiredEligibility = Objects.requireNonNull(
                eligibility,
                "eligibility"
        );
        lock.writeLock().lock();
        publicationLock.lock();
        try {
            RiverMatch best = null;
            BigInteger bestDistance = null;
            int originChunkX = Math.floorDiv(requiredQuery.origin().x(), 16);
            int originChunkZ = Math.floorDiv(requiredQuery.origin().z(), 16);
            List<RegionCandidate> candidateRegions = orderedCandidateRegions(
                    requiredQuery.origin(),
                    requiredQuery.maxChunkRadius(),
                    true
            );
            for (RegionCandidate candidateRegion : candidateRegions) {
                if (best != null && candidateRegion.blockDistanceSquared().compareTo(bestDistance) > 0) {
                    break;
                }
                RegionSummary summary = loadSummaryLocked(candidateRegion.regionKey());
                if (!summary.hasAnyRiverType(requiredQuery.types())
                        || requiredQuery.profileKey() != null
                        && !summary.keys(SemanticKind.RIVER_PROFILE).contains(requiredQuery.profileKey())
                        || !summary.mayContainActivation(requiredQuery.activationFilter())) {
                    continue;
                }
                RegionShard region = loadRegionLocked(
                        candidateRegion.regionKey(),
                        regionX(candidateRegion.regionKey()),
                        regionZ(candidateRegion.regionKey())
                );
                for (ChunkGenerationSemantics semantics : region.records()) {
                    if (!requiredEligibility.test(semantics)
                            || !matchesActivation(semantics.activationId(), requiredQuery.activationFilter())) {
                        continue;
                    }
                    for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : semantics.riverFeatures()) {
                        if (!requiredQuery.types().contains(occurrence.type())
                                || requiredQuery.profileKey() != null
                                && !requiredQuery.profileKey().equals(occurrence.profileKey())) {
                            continue;
                        }
                        ChunkGenerationSemantics.BlockPosition position = occurrence.position();
                        long deltaChunkX = (long) Math.floorDiv(position.x(), 16) - originChunkX;
                        long deltaChunkZ = (long) Math.floorDiv(position.z(), 16) - originChunkZ;
                        if (!withinRadius(deltaChunkX, deltaChunkZ, requiredQuery.maxChunkRadius())) {
                            continue;
                        }
                        BigInteger distance = horizontalDistanceSquared(requiredQuery.origin(), position);
                        RiverMatch candidate = new RiverMatch(
                                occurrence,
                                new ChunkReference(
                                        semantics.chunkX(),
                                        semantics.chunkZ(),
                                        semantics.activationId()
                                )
                        );
                        if (best == null || compareRiverCandidate(
                                candidate,
                                distance,
                                best,
                                bestDistance
                        ) < 0) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return Optional.ofNullable(best);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to search recorded river semantics.", error);
        } finally {
            publicationLock.unlock();
            lock.writeLock().unlock();
        }
    }

    private Optional<Match> findNearestChunk(
            Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        int originChunkX = Math.floorDiv(query.origin().x(), 16);
        int originChunkZ = Math.floorDiv(query.origin().z(), 16);
        ChunkCandidate best = null;
        BigInteger bestDistance = null;
        try {
            List<RegionCandidate> candidateRegions = orderedCandidateRegions(
                    query.origin(),
                    query.maxChunkRadius(),
                    false
            );
            for (RegionCandidate candidateRegion : candidateRegions) {
                if (best != null && candidateRegion.chunkDistanceSquared().compareTo(bestDistance) > 0) {
                    break;
                }
                RegionSummary summary = loadSummaryLocked(candidateRegion.regionKey());
                if (!summary.keys(query.kind()).contains(query.key())
                        || !summary.mayContainActivation(query.activationFilter())) {
                    continue;
                }
                RegionShard region = loadRegionLocked(
                        candidateRegion.regionKey(),
                        regionX(candidateRegion.regionKey()),
                        regionZ(candidateRegion.regionKey())
                );
                for (ChunkGenerationSemantics semantics : region.records()) {
                    if (!matchesActivation(semantics.activationId(), query.activationFilter())
                            || !eligibility.test(semantics)
                            || !keys(semantics, query.kind()).contains(query.key())) {
                        continue;
                    }
                    long deltaX = (long) semantics.chunkX() - originChunkX;
                    long deltaZ = (long) semantics.chunkZ() - originChunkZ;
                    if (!withinRadius(deltaX, deltaZ, query.maxChunkRadius())) {
                        continue;
                    }
                    BigInteger distance = squared(deltaX).add(squared(deltaZ));
                    if (best == null || compareChunkCandidate(
                            semantics.chunkX(),
                            semantics.chunkZ(),
                            semantics.activationId(),
                            distance,
                            best,
                            bestDistance
                    ) < 0) {
                        best = new ChunkCandidate(
                                semantics.chunkX(),
                                semantics.chunkZ(),
                                semantics.activationId()
                        );
                        bestDistance = distance;
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to search recorded chunk semantics.", error);
        }
        if (best == null) {
            return Optional.empty();
        }
        ChunkReference chunk = new ChunkReference(
                best.chunkX(),
                best.chunkZ(),
                best.activationId()
        );
        return Optional.of(new Match(query.kind(), query.key(), chunk, Optional.empty()));
    }

    private Optional<Match> findNearestStructure(
            Query query,
            Predicate<ChunkGenerationSemantics> eligibility
    ) {
        int originChunkX = Math.floorDiv(query.origin().x(), 16);
        int originChunkZ = Math.floorDiv(query.origin().z(), 16);
        StructureCandidate best = null;
        BigInteger bestDistance = null;
        try {
            List<RegionCandidate> candidateRegions = orderedCandidateRegions(
                    query.origin(),
                    query.maxChunkRadius(),
                    true
            );
            for (RegionCandidate candidateRegion : candidateRegions) {
                if (best != null && candidateRegion.blockDistanceSquared().compareTo(bestDistance) > 0) {
                    break;
                }
                RegionSummary summary = loadSummaryLocked(candidateRegion.regionKey());
                if (!summary.keys(SemanticKind.STRUCTURE).contains(query.key())
                        || !summary.mayContainActivation(query.activationFilter())) {
                    continue;
                }
                RegionShard region = loadRegionLocked(
                        candidateRegion.regionKey(),
                        regionX(candidateRegion.regionKey()),
                        regionZ(candidateRegion.regionKey())
                );
                for (ChunkGenerationSemantics semantics : region.records()) {
                    if (!eligibility.test(semantics)
                            || !matchesActivation(semantics.activationId(), query.activationFilter())) {
                        continue;
                    }
                    for (ChunkGenerationSemantics.StructureOccurrence occurrence : semantics.structures()) {
                        if (!query.key().equals(occurrence.key())) {
                            continue;
                        }
                        ChunkGenerationSemantics.BlockPosition position = occurrence.position();
                        long deltaChunkX = (long) Math.floorDiv(position.x(), 16) - originChunkX;
                        long deltaChunkZ = (long) Math.floorDiv(position.z(), 16) - originChunkZ;
                        if (!withinRadius(deltaChunkX, deltaChunkZ, query.maxChunkRadius())) {
                            continue;
                        }
                        StructureCandidate candidate = new StructureCandidate(
                                semantics.chunkX(),
                                semantics.chunkZ(),
                                semantics.activationId(),
                                occurrence
                        );
                        BigInteger distance = horizontalDistanceSquared(query.origin(), position);
                        if (best == null || compareStructureCandidate(candidate, distance, best, bestDistance) < 0) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to search recorded structure semantics.", error);
        }
        if (best == null) {
            return Optional.empty();
        }
        ChunkReference chunk = new ChunkReference(
                best.chunkX(),
                best.chunkZ(),
                best.activationId()
        );
        return Optional.of(new Match(
                SemanticKind.STRUCTURE,
                query.key(),
                chunk,
                Optional.of(best.occurrence().position())
        ));
    }

    private void loadShards() throws IOException {
        validateDimensionRoot();
        validateStorageAncestors();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation semantic index path is not a safe directory: " + directory);
        }

        List<Path> shardFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + FILE_SUFFIX)) {
            for (Path file : stream) {
                shardFiles.add(file);
            }
        }
        shardFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path file : shardFiles) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation semantic shard is not a regular file: " + file);
            }
            validateShardFileName(file);
        }
        List<Path> pointerFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "r.*.isix")) {
            for (Path file : stream) {
                pointerFiles.add(file);
            }
        }
        pointerFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path file : pointerFiles) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation semantic shard pointer is not a regular file: " + file);
            }
            validatePointerFileName(file);
        }

        Long2ObjectOpenHashMap<Path> journals = discoverJournals();
        Path catalogFile = directory.resolve(CATALOG_FILE_NAME);
        if (!Files.exists(catalogFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!shardFiles.isEmpty() || !pointerFiles.isEmpty()) {
                throw new IOException("Generation semantic shard catalog is missing: " + catalogFile);
            }
            loadRegions(journals);
            return;
        }
        if (!Files.isRegularFile(catalogFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation semantic shard catalog is not a regular file: " + catalogFile);
        }

        Set<Long> catalog = Catalog.read(catalogFile);
        for (long regionKey : catalog) {
            int regionX = regionX(regionKey);
            int regionZ = regionZ(regionKey);
            Path pointerFile = directory.resolve(pointerFileName(regionX, regionZ));
            if (!Files.isRegularFile(pointerFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation semantic catalog references a missing shard pointer: " + pointerFile);
            }
            String shardHash = ShardPointer.read(pointerFile, regionX, regionZ);
            Path file = directory.resolve(fileName(regionX, regionZ, shardHash));
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation semantic catalog references a missing shard: " + file);
            }
            RegionShard.Metadata metadata = RegionShard.readMetadata(file, shardHash);
            if (metadata.regionX() != regionX || metadata.regionZ() != regionZ) {
                throw new IOException("Generation semantic shard name does not match its coordinates: " + file);
            }
            shardHashes.put(regionKey, shardHash);
            regionRecordCounts.put(regionKey, metadata.recordCount());
        }
        loadRegions(journals);
    }

    private Long2ObjectOpenHashMap<Path> discoverJournals() throws IOException {
        Long2ObjectOpenHashMap<Path> journals = new Long2ObjectOpenHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + JOURNAL_SUFFIX)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Generation semantic journal is not a regular file: " + file);
                }
                Matcher matcher = JOURNAL_FILE_PATTERN.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    throw new IOException("Generation semantic journal has a noncanonical file name: " + file);
                }
                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(matcher.group(1));
                    regionZ = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException error) {
                    throw new IOException("Generation semantic journal has invalid coordinates: " + file, error);
                }
                RegionShard.validateRegionCoordinate(file, regionX);
                RegionShard.validateRegionCoordinate(file, regionZ);
                long regionKey = packRegion(regionX, regionZ);
                if (journals.putIfAbsent(regionKey, file) != null) {
                    throw new IOException("Duplicate generation semantic journal region: " + file);
                }
            }
        }
        return journals;
    }

    private void loadRegions(Long2ObjectOpenHashMap<Path> journals) throws IOException {
        ArrayList<Long> compact = new ArrayList<>();
        ArrayList<Long> ordered = new ArrayList<>(journals.keySet());
        ordered.sort(GenerationSemanticIndex::compareRegionKeys);
        for (long regionKey : ordered) {
            int regionX = regionX(regionKey);
            int regionZ = regionZ(regionKey);
            RegionShard region = readBaseRegion(regionKey, regionX, regionZ);
            Path journal = journals.get(regionKey);
            if (journal != null) {
                SemanticJournal.Replay replay = SemanticJournal.replay(journal, regionX, regionZ);
                region = replayClaims(journal, region, replay.claims());
                if (replay.entryCount() > 0) {
                    journalEntries.put(regionKey, replay.entryCount());
                }
                if (replay.entryCount() >= JOURNAL_COMPACTION_ENTRIES
                        || replay.validBytes() >= JOURNAL_COMPACTION_BYTES) {
                    compact.add(regionKey);
                }
            }
            installLoadedRegion(regionKey, region);
        }
        for (long regionKey : compact) {
            compactJournal(regionKey);
        }
        regionDecodeCount = 0L;
    }

    private RegionShard replayClaims(
            Path file,
            RegionShard base,
            List<ChunkGenerationSemantics> claims
    ) throws IOException {
        RegionShard region = base;
        for (ChunkGenerationSemantics claim : claims) {
            ChunkGenerationSemantics existing = region.get(claim.chunkX(), claim.chunkZ());
            ChunkGenerationSemantics merged;
            try {
                merged = existing == null ? claim : existing.merge(claim);
            } catch (IllegalStateException | IllegalArgumentException error) {
                throw new IOException("Generation semantic journal conflicts with existing truth: " + file, error);
            }
            region = region.withRecord(merged);
        }
        return region;
    }

    private void installLoadedRegion(long regionKey, RegionShard region) throws IOException {
        regionRecordCounts.put(regionKey, region.recordCount());
        cacheRegion(regionKey, region);
    }

    private void compactJournal(long regionKey) throws IOException {
        if (!journalEntries.containsKey(regionKey)) {
            return;
        }
        int regionX = regionX(regionKey);
        int regionZ = regionZ(regionKey);
        RegionShard region = loadRegionLocked(regionKey, regionX, regionZ);
        byte[] encoded = region.encode();
        String shardHash = sha256(encoded);
        publisher.publish(directory, regionX, regionZ, encoded);
        pointerPublisher.publish(directory, regionX, regionZ, shardHash);
        String previousHash = shardHashes.get(regionKey);
        if (previousHash == null) {
            TreeSet<Long> nextRegions = new TreeSet<>(GenerationSemanticIndex::compareRegionKeys);
            nextRegions.addAll(shardHashes.keySet());
            nextRegions.add(regionKey);
            catalogPublisher.publish(directory, nextRegions);
        }
        shardHashes.put(regionKey, shardHash);
        Path journal = directory.resolve(journalFileName(regionX, regionZ));
        if (Files.deleteIfExists(journal)) {
            RegionShard.forceDirectory(directory);
        }
        journalEntries.remove(regionKey);
        deleteUnreferencedShard(regionX, regionZ, previousHash, shardHash);
    }

    private ChunkGenerationSemantics getRecordLocked(int chunkX, int chunkZ) throws IOException {
        requireJournalUsable();
        long regionKey = packRegion(chunkX >> 5, chunkZ >> 5);
        if (!regionRecordCounts.containsKey(regionKey)) {
            return null;
        }
        return loadRegionLocked(regionKey, chunkX >> 5, chunkZ >> 5).get(chunkX, chunkZ);
    }

    private RegionShard loadRegionLocked(long regionKey, int regionX, int regionZ) throws IOException {
        requireJournalUsable();
        if (appendingBase != null && appendingRegionKey == regionKey) {
            return appendingBase;
        }
        RegionShard cached = regions.get(regionKey);
        if (cached != null) {
            return cached;
        }
        RegionShard region = readBaseRegion(regionKey, regionX, regionZ);
        if (journalEntries.containsKey(regionKey)) {
            Path journal = directory.resolve(journalFileName(regionX, regionZ));
            SemanticJournal.Replay replay = SemanticJournal.replay(journal, regionX, regionZ);
            region = replayClaims(journal, region, replay.claims());
        }
        regionDecodeCount++;
        cacheRegion(regionKey, region);
        return region;
    }

    private RegionSummary loadSummaryLocked(long regionKey) throws IOException {
        requireJournalUsable();
        RegionSummary cached = summaries.get(regionKey);
        if (cached != null) {
            return cached;
        }
        RegionShard cachedRegion = regions.get(regionKey);
        if (cachedRegion != null) {
            RegionSummary summary = RegionSummary.from(cachedRegion);
            cacheSummary(regionKey, summary);
            return summary;
        }
        if (journalEntries.containsKey(regionKey)) {
            RegionShard region = loadRegionLocked(regionKey, regionX(regionKey), regionZ(regionKey));
            return RegionSummary.from(region);
        }
        String shardHash = shardHashes.get(regionKey);
        if (shardHash == null) {
            return RegionSummary.empty();
        }
        int regionX = regionX(regionKey);
        int regionZ = regionZ(regionKey);
        Path file = directory.resolve(fileName(regionX, regionZ, shardHash));
        RegionShard.Summary stored = RegionShard.readSummary(file, shardHash);
        if (stored.regionX() != regionX || stored.regionZ() != regionZ) {
            throw new IOException("Generation semantic shard name does not match its coordinates: " + file);
        }
        cacheSummary(regionKey, stored.semantics());
        return stored.semantics();
    }

    private RegionShard readBaseRegion(long regionKey, int regionX, int regionZ) throws IOException {
        String shardHash = shardHashes.get(regionKey);
        if (shardHash == null) {
            return new RegionShard(regionX, regionZ);
        }
        Path file = directory.resolve(fileName(regionX, regionZ, shardHash));
        RegionShard region = RegionShard.read(file, shardHash);
        if (region.regionX() != regionX || region.regionZ() != regionZ) {
            throw new IOException("Generation semantic shard name does not match its coordinates: " + file);
        }
        return region;
    }

    private void cacheRegion(long regionKey, RegionShard region) {
        regions.put(regionKey, region);
        cacheSummary(regionKey, RegionSummary.from(region));
        while (regions.size() > MAXIMUM_CACHED_REGIONS) {
            Iterator<Map.Entry<Long, RegionShard>> entries = regions.entrySet().iterator();
            entries.next();
            entries.remove();
        }
    }

    private void cacheSummary(long regionKey, RegionSummary summary) {
        summaries.put(regionKey, summary);
        while (summaries.size() > MAXIMUM_CACHED_SUMMARIES) {
            Iterator<Map.Entry<Long, RegionSummary>> entries = summaries.entrySet().iterator();
            entries.next();
            entries.remove();
        }
    }

    private void requireJournalUsable() throws IOException {
        if (journalFailure != null) {
            throw new IOException("Generation semantic journal requires recovery before further access.", journalFailure);
        }
    }

    private List<Long> allRegionKeys() {
        ArrayList<Long> ordered = new ArrayList<>(regionRecordCounts.keySet());
        ordered.sort(GenerationSemanticIndex::compareRegionKeys);
        return ordered;
    }

    private int totalRecordCountLocked() {
        int total = 0;
        for (int count : regionRecordCounts.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }

    private static void validateShardFileName(Path file) throws IOException {
        String name = file.getFileName().toString();
        Matcher matcher = SHARD_FILE_PATTERN.matcher(name);
        if (!matcher.matches()) {
            throw new IOException("Generation semantic shard has a noncanonical file name: " + file);
        }
        try {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            RegionShard.validateRegionCoordinate(file, regionX);
            RegionShard.validateRegionCoordinate(file, regionZ);
            if (!name.equals(fileName(regionX, regionZ, matcher.group(3)))) {
                throw new IOException("Generation semantic shard has a noncanonical file name: " + file);
            }
        } catch (NumberFormatException error) {
            throw new IOException("Generation semantic shard has a noncanonical file name: " + file, error);
        }
    }

    private static void validatePointerFileName(Path file) throws IOException {
        String name = file.getFileName().toString();
        Matcher matcher = POINTER_FILE_PATTERN.matcher(name);
        if (!matcher.matches()) {
            throw new IOException("Generation semantic shard pointer has a noncanonical file name: " + file);
        }
        try {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            RegionShard.validateRegionCoordinate(file, regionX);
            RegionShard.validateRegionCoordinate(file, regionZ);
            if (!name.equals(pointerFileName(regionX, regionZ))) {
                throw new IOException("Generation semantic shard pointer has a noncanonical file name: " + file);
            }
        } catch (NumberFormatException error) {
            throw new IOException("Generation semantic shard pointer has a noncanonical file name: " + file, error);
        }
    }

    private static Set<String> keys(ChunkGenerationSemantics semantics, SemanticKind kind) {
        return switch (kind) {
            case SURFACE_BIOME -> semantics.surfaceBiomeKeys();
            case CAVE_BIOME -> semantics.caveBiomeKeys();
            case REGION -> semantics.regionKeys();
            case RIVER_PROFILE -> semantics.riverProfileKeys();
            case OBJECT -> semantics.objectKeys();
            case STRUCTURE -> throw new IllegalArgumentException("Structure keys require exact positions");
        };
    }

    private void validateDimensionRoot() throws IOException {
        if (!Files.isDirectory(dimensionRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Dimension root is not a safe directory: " + dimensionRoot);
        }
    }

    private void validateStorageAncestors() throws IOException {
        Path irisDirectory = dimensionRoot.resolve("iris");
        if (!validateExistingDirectory(irisDirectory)) {
            return;
        }
        Path generationDirectory = irisDirectory.resolve("generation");
        if (!validateExistingDirectory(generationDirectory)) {
            return;
        }
        validateExistingDirectory(generationDirectory.resolve("semantics"));
    }

    private static boolean validateExistingDirectory(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation semantic index path is not a safe directory: " + path);
        }
        return true;
    }

    private void ensureStorageDirectory() throws IOException {
        validateDimensionRoot();
        Path irisDirectory = ensureChildDirectory(dimensionRoot, "iris");
        Path generationDirectory = ensureChildDirectory(irisDirectory, "generation");
        Path semanticsDirectory = ensureChildDirectory(generationDirectory, "semantics");
        if (!semanticsDirectory.equals(directory)) {
            throw new IOException("Generation semantic index resolved outside its storage path: " + semanticsDirectory);
        }
    }

    private static Path ensureChildDirectory(Path parent, String name) throws IOException {
        Path child = parent.resolve(name);
        if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation semantic index path is not a safe directory: " + child);
            }
            return child;
        }
        Files.createDirectory(child);
        if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation semantic index path is not a safe directory: " + child);
        }
        return child;
    }

    private static int compareChunkCandidate(
            int candidateChunkX,
            int candidateChunkZ,
            long candidateActivationId,
            BigInteger candidateDistance,
            ChunkCandidate current,
            BigInteger currentDistance
    ) {
        int distanceComparison = candidateDistance.compareTo(currentDistance);
        if (distanceComparison != 0) {
            return distanceComparison;
        }
        int xComparison = Integer.compare(candidateChunkX, current.chunkX());
        if (xComparison != 0) {
            return xComparison;
        }
        int zComparison = Integer.compare(candidateChunkZ, current.chunkZ());
        if (zComparison != 0) {
            return zComparison;
        }
        return Long.compare(candidateActivationId, current.activationId());
    }

    private static int compareStructureCandidate(
            StructureCandidate candidate,
            BigInteger candidateDistance,
            StructureCandidate current,
            BigInteger currentDistance
    ) {
        int distanceComparison = candidateDistance.compareTo(currentDistance);
        if (distanceComparison != 0) {
            return distanceComparison;
        }
        ChunkGenerationSemantics.BlockPosition candidatePosition = candidate.occurrence().position();
        ChunkGenerationSemantics.BlockPosition currentPosition = current.occurrence().position();
        int xComparison = Integer.compare(candidatePosition.x(), currentPosition.x());
        if (xComparison != 0) {
            return xComparison;
        }
        int zComparison = Integer.compare(candidatePosition.z(), currentPosition.z());
        if (zComparison != 0) {
            return zComparison;
        }
        int yComparison = Integer.compare(candidatePosition.y(), currentPosition.y());
        if (yComparison != 0) {
            return yComparison;
        }
        int chunkXComparison = Integer.compare(candidate.chunkX(), current.chunkX());
        if (chunkXComparison != 0) {
            return chunkXComparison;
        }
        int chunkZComparison = Integer.compare(candidate.chunkZ(), current.chunkZ());
        if (chunkZComparison != 0) {
            return chunkZComparison;
        }
        return Long.compare(candidate.activationId(), current.activationId());
    }

    private static int compareRiverCandidate(
            RiverMatch candidate,
            BigInteger candidateDistance,
            RiverMatch current,
            BigInteger currentDistance
    ) {
        int distanceComparison = candidateDistance.compareTo(currentDistance);
        if (distanceComparison != 0) {
            return distanceComparison;
        }
        int occurrenceComparison = ChunkGenerationSemantics.riverFeatureComparator().compare(
                candidate.occurrence(),
                current.occurrence()
        );
        if (occurrenceComparison != 0) {
            return occurrenceComparison;
        }
        int chunkXComparison = Integer.compare(candidate.chunk().chunkX(), current.chunk().chunkX());
        if (chunkXComparison != 0) {
            return chunkXComparison;
        }
        int chunkZComparison = Integer.compare(candidate.chunk().chunkZ(), current.chunk().chunkZ());
        if (chunkZComparison != 0) {
            return chunkZComparison;
        }
        return Long.compare(candidate.chunk().activationId(), current.chunk().activationId());
    }

    private List<RegionCandidate> orderedCandidateRegions(
            ChunkGenerationSemantics.BlockPosition origin,
            int maximumChunkRadius,
            boolean orderByBlockDistance
    ) throws IOException {
        requireJournalUsable();
        int originChunkX = Math.floorDiv(origin.x(), 16);
        int originChunkZ = Math.floorDiv(origin.z(), 16);
        long minimumChunkX = (long) originChunkX - maximumChunkRadius;
        long maximumChunkX = (long) originChunkX + maximumChunkRadius;
        long minimumChunkZ = (long) originChunkZ - maximumChunkRadius;
        long maximumChunkZ = (long) originChunkZ + maximumChunkRadius;
        List<Long> regionKeys = allRegionKeys();
        ArrayList<RegionCandidate> candidates = new ArrayList<>(regionKeys.size());
        for (long regionKey : regionKeys) {
            int regionX = regionX(regionKey);
            int regionZ = regionZ(regionKey);
            long regionMinimumChunkX = (long) regionX * CHUNKS_PER_REGION_SIDE;
            long regionMaximumChunkX = regionMinimumChunkX + CHUNKS_PER_REGION_SIDE - 1L;
            long regionMinimumChunkZ = (long) regionZ * CHUNKS_PER_REGION_SIDE;
            long regionMaximumChunkZ = regionMinimumChunkZ + CHUNKS_PER_REGION_SIDE - 1L;
            if (regionMaximumChunkX < minimumChunkX || regionMinimumChunkX > maximumChunkX
                    || regionMaximumChunkZ < minimumChunkZ || regionMinimumChunkZ > maximumChunkZ) {
                continue;
            }
            candidates.add(new RegionCandidate(
                    regionKey,
                    distanceSquaredToRegion(
                            originChunkX,
                            originChunkZ,
                            regionMinimumChunkX,
                            regionMaximumChunkX,
                            regionMinimumChunkZ,
                            regionMaximumChunkZ
                    ),
                    distanceSquaredToRegion(
                            origin.x(),
                            origin.z(),
                            regionMinimumChunkX * 16L,
                            regionMaximumChunkX * 16L + 15L,
                            regionMinimumChunkZ * 16L,
                            regionMaximumChunkZ * 16L + 15L
                    )
            ));
        }
        Comparator<RegionCandidate> distanceComparator = orderByBlockDistance
                ? Comparator.comparing(RegionCandidate::blockDistanceSquared)
                : Comparator.comparing(RegionCandidate::chunkDistanceSquared);
        candidates.sort(distanceComparator
                .thenComparingInt(candidate -> regionX(candidate.regionKey()))
                .thenComparingInt(candidate -> regionZ(candidate.regionKey())));
        return candidates;
    }

    private static BigInteger distanceSquaredToRegion(
            long originX,
            long originZ,
            long minimumX,
            long maximumX,
            long minimumZ,
            long maximumZ
    ) {
        long distanceX = distanceToRange(originX, minimumX, maximumX);
        long distanceZ = distanceToRange(originZ, minimumZ, maximumZ);
        return squared(distanceX).add(squared(distanceZ));
    }

    private static long distanceToRange(long value, long minimum, long maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0L;
    }

    private static BigInteger squared(long value) {
        BigInteger integer = BigInteger.valueOf(value);
        return integer.multiply(integer);
    }

    private static BigInteger horizontalDistanceSquared(
            ChunkGenerationSemantics.BlockPosition first,
            ChunkGenerationSemantics.BlockPosition second
    ) {
        BigInteger deltaX = BigInteger.valueOf((long) first.x() - second.x());
        BigInteger deltaZ = BigInteger.valueOf((long) first.z() - second.z());
        return deltaX.multiply(deltaX).add(deltaZ.multiply(deltaZ));
    }

    private static boolean matchesActivation(long activationId, OptionalLong activationFilter) {
        return activationFilter.isEmpty() || activationFilter.getAsLong() == activationId;
    }

    private static boolean withinRadius(long deltaX, long deltaZ, int maxChunkRadius) {
        return Math.abs(deltaX) <= maxChunkRadius && Math.abs(deltaZ) <= maxChunkRadius;
    }

    private static long packRegion(int regionX, int regionZ) {
        return ((long) regionX << Integer.SIZE) | (regionZ & 0xFFFFFFFFL);
    }

    private static int regionX(long regionKey) {
        return (int) (regionKey >> Integer.SIZE);
    }

    private static int regionZ(long regionKey) {
        return (int) regionKey;
    }

    private static int compareRegions(int firstX, int firstZ, int secondX, int secondZ) {
        int xComparison = Integer.compare(firstX, secondX);
        if (xComparison != 0) {
            return xComparison;
        }
        return Integer.compare(firstZ, secondZ);
    }

    private static int compareRegionKeys(long first, long second) {
        return compareRegions(regionX(first), regionZ(first), regionX(second), regionZ(second));
    }

    private static String fileName(int regionX, int regionZ, String hash) {
        return "r." + regionX + "." + regionZ + "." + hash + FILE_SUFFIX;
    }

    private static String pointerFileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".isix";
    }

    private static String journalFileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + JOURNAL_SUFFIX;
    }

    private static String sha256(byte[] encoded) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(encoded));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static byte[] withChecksum(byte[] body) throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(body);
        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream(body.length + CHECKSUM_BYTES);
        encodedBytes.write(body);
        try (DataOutputStream output = new DataOutputStream(encodedBytes)) {
            output.writeInt((int) checksum.getValue());
        }
        return encodedBytes.toByteArray();
    }

    private static boolean checksumMatches(byte[] encoded) {
        int bodyLength = encoded.length - CHECKSUM_BYTES;
        int expected = ByteBuffer.wrap(encoded, bodyLength, CHECKSUM_BYTES).getInt();
        CRC32 checksum = new CRC32();
        checksum.update(encoded, 0, bodyLength);
        return (int) checksum.getValue() == expected;
    }

    private static void publishAtomic(Path target, byte[] encoded, String kind) throws IOException {
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, "." + target.getFileName() + "-", ".tmp");
        try {
            RegionShard.writeForced(temporary, encoded);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Generation semantics require atomic " + kind + " publication", error);
            }
            RegionShard.forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void deleteUnreferencedShard(
            int regionX,
            int regionZ,
            String previousHash,
            String currentHash
    ) {
        if (previousHash == null || previousHash.equals(currentHash)) {
            return;
        }
        Path unreferenced = directory.resolve(fileName(regionX, regionZ, previousHash));
        try {
            Files.deleteIfExists(unreferenced);
        } catch (IOException failure) {
            if (IrisLogging.warnOnce("generation-semantics-shard-delete",
                    "Could not delete the superseded generation semantics shard %s; stale shards accumulate on disk.",
                    unreferenced)) {
                IrisLogging.reportError("Could not delete the superseded generation semantics shard "
                        + unreferenced + ".", failure);
            }
        }
    }

    public enum SemanticKind {
        SURFACE_BIOME,
        CAVE_BIOME,
        REGION,
        RIVER_PROFILE,
        OBJECT,
        STRUCTURE
    }

    public record Query(
            SemanticKind kind,
            String key,
            ChunkGenerationSemantics.BlockPosition origin,
            int maxChunkRadius,
            OptionalLong activationFilter
    ) {
        public Query {
            kind = Objects.requireNonNull(kind, "kind");
            key = ChunkGenerationSemantics.requireResourceKey(key);
            origin = Objects.requireNonNull(origin, "origin");
            if (maxChunkRadius < 0) {
                throw new IllegalArgumentException("Maximum chunk radius must not be negative: " + maxChunkRadius);
            }
            activationFilter = Objects.requireNonNull(activationFilter, "activationFilter");
            if (activationFilter.isPresent() && activationFilter.getAsLong() <= 0L) {
                throw new IllegalArgumentException(
                        "Generation activation IDs must be positive: " + activationFilter.getAsLong()
                );
            }
        }

        public static Query acrossActivations(
                SemanticKind kind,
                String key,
                ChunkGenerationSemantics.BlockPosition origin,
                int maxChunkRadius
        ) {
            return new Query(kind, key, origin, maxChunkRadius, OptionalLong.empty());
        }

        public static Query forActivation(
                SemanticKind kind,
                String key,
                ChunkGenerationSemantics.BlockPosition origin,
                int maxChunkRadius,
                long activationId
        ) {
            return new Query(kind, key, origin, maxChunkRadius, OptionalLong.of(activationId));
        }
    }

    public record ChunkReference(int chunkX, int chunkZ, long activationId) {
        public ChunkReference {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Generation activation IDs must be positive: " + activationId);
            }
        }
    }

    public record Match(
            SemanticKind kind,
            String key,
            ChunkReference chunk,
            Optional<ChunkGenerationSemantics.BlockPosition> exactPosition
    ) {
        public Match {
            kind = Objects.requireNonNull(kind, "kind");
            key = ChunkGenerationSemantics.requireResourceKey(key);
            chunk = Objects.requireNonNull(chunk, "chunk");
            exactPosition = Objects.requireNonNull(exactPosition, "exactPosition");
            if ((kind == SemanticKind.STRUCTURE) != exactPosition.isPresent()) {
                throw new IllegalArgumentException("Only structure matches have an exact block position");
            }
        }
    }

    public record RiverQuery(
            Set<HydrologyFeatureType> types,
            String profileKey,
            ChunkGenerationSemantics.BlockPosition origin,
            int maxChunkRadius,
            OptionalLong activationFilter
    ) {
        public RiverQuery {
            types = Set.copyOf(Objects.requireNonNull(types, "types"));
            if (types.isEmpty()) {
                throw new IllegalArgumentException("River semantic queries require a feature type.");
            }
            profileKey = profileKey == null || profileKey.isBlank()
                    ? null
                    : ChunkGenerationSemantics.requireResourceKey(profileKey);
            origin = Objects.requireNonNull(origin, "origin");
            if (maxChunkRadius < 0) {
                throw new IllegalArgumentException("Maximum chunk radius must not be negative.");
            }
            activationFilter = Objects.requireNonNull(activationFilter, "activationFilter");
        }

        public static RiverQuery acrossActivations(
                Set<HydrologyFeatureType> types,
                String profileKey,
                ChunkGenerationSemantics.BlockPosition origin,
                int maxChunkRadius
        ) {
            return new RiverQuery(
                    types,
                    profileKey,
                    origin,
                    maxChunkRadius,
                    OptionalLong.empty()
            );
        }
    }

    public record RiverMatch(
            ChunkGenerationSemantics.RiverFeatureOccurrence occurrence,
            ChunkReference chunk
    ) {
        public RiverMatch {
            occurrence = Objects.requireNonNull(occurrence, "occurrence");
            chunk = Objects.requireNonNull(chunk, "chunk");
        }
    }

    private record ChunkCandidate(int chunkX, int chunkZ, long activationId) {
    }

    private record StructureCandidate(
            int chunkX,
            int chunkZ,
            long activationId,
            ChunkGenerationSemantics.StructureOccurrence occurrence
    ) {
    }

    private record RegionCandidate(
            long regionKey,
            BigInteger chunkDistanceSquared,
            BigInteger blockDistanceSquared
    ) {
    }

    @FunctionalInterface
    public interface RecordConsumer {
        void accept(ChunkGenerationSemantics semantics) throws IOException;
    }

    @FunctionalInterface
    public interface SealedClaimConsumer {
        void accept(int chunkX, int chunkZ) throws IOException;
    }

    private static final class RegionSummary {
        private final EnumMap<SemanticKind, TreeSet<String>> keys;
        private final EnumSet<HydrologyFeatureType> riverTypes;
        private final LongOpenHashSet activations;
        private final LongOpenHashSet sealedActivations;

        private RegionSummary(
                EnumMap<SemanticKind, TreeSet<String>> keys,
                EnumSet<HydrologyFeatureType> riverTypes,
                LongOpenHashSet activations,
                LongOpenHashSet sealedActivations
        ) {
            this.keys = keys;
            this.riverTypes = riverTypes;
            this.activations = activations;
            this.sealedActivations = sealedActivations;
        }

        private static RegionSummary empty() {
            return new RegionSummary(
                    emptyKeySets(),
                    EnumSet.noneOf(HydrologyFeatureType.class),
                    new LongOpenHashSet(),
                    new LongOpenHashSet()
            );
        }

        private static RegionSummary from(RegionShard region) {
            EnumMap<SemanticKind, TreeSet<String>> keys = emptyKeySets();
            EnumSet<HydrologyFeatureType> riverTypes = EnumSet.noneOf(HydrologyFeatureType.class);
            LongOpenHashSet activations = new LongOpenHashSet();
            LongOpenHashSet sealedActivations = new LongOpenHashSet();
            for (ChunkGenerationSemantics semantics : region.records()) {
                activations.add(semantics.activationId());
                mutableKeys(keys, SemanticKind.SURFACE_BIOME).addAll(semantics.surfaceBiomeKeys());
                mutableKeys(keys, SemanticKind.CAVE_BIOME).addAll(semantics.caveBiomeKeys());
                mutableKeys(keys, SemanticKind.REGION).addAll(semantics.regionKeys());
                mutableKeys(keys, SemanticKind.RIVER_PROFILE).addAll(semantics.riverProfileKeys());
                mutableKeys(keys, SemanticKind.OBJECT).addAll(semantics.objectKeys());
                for (ChunkGenerationSemantics.StructureOccurrence occurrence : semantics.structures()) {
                    mutableKeys(keys, SemanticKind.STRUCTURE).add(occurrence.key());
                }
                for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : semantics.riverFeatures()) {
                    riverTypes.add(occurrence.type());
                }
                if (semantics.sealed()) {
                    sealedActivations.add(semantics.activationId());
                }
            }
            return new RegionSummary(keys, riverTypes, activations, sealedActivations);
        }

        private static RegionSummary decode(Path source, byte[] encoded) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
                EnumMap<SemanticKind, TreeSet<String>> keys = emptyKeySets();
                for (SemanticKind kind : SemanticKind.values()) {
                    int count = input.readUnsignedShort();
                    if (count > MAX_PALETTE_KEYS) {
                        throw invalid(source, "invalid summary key count " + count);
                    }
                    TreeSet<String> values = mutableKeys(keys, kind);
                    String previous = null;
                    for (int index = 0; index < count; index++) {
                        int byteLength = input.readUnsignedShort();
                        if (byteLength <= 0 || byteLength > ChunkGenerationSemantics.MAX_KEY_BYTES) {
                            throw invalid(source, "invalid summary resource key length " + byteLength);
                        }
                        byte[] keyBytes = new byte[byteLength];
                        input.readFully(keyBytes);
                        String key = ChunkGenerationSemantics.requireResourceKey(
                                new String(keyBytes, StandardCharsets.UTF_8)
                        );
                        if (previous != null && previous.compareTo(key) >= 0) {
                            throw invalid(source, "summary resource keys are not in canonical order");
                        }
                        values.add(key);
                        previous = key;
                    }
                }
                int riverTypeCount = input.readUnsignedByte();
                if (riverTypeCount > HydrologyFeatureType.values().length) {
                    throw invalid(source, "invalid summary river type count " + riverTypeCount);
                }
                EnumSet<HydrologyFeatureType> riverTypes = EnumSet.noneOf(HydrologyFeatureType.class);
                int previousOrdinal = -1;
                for (int index = 0; index < riverTypeCount; index++) {
                    int ordinal = input.readUnsignedByte();
                    if (ordinal <= previousOrdinal || ordinal >= HydrologyFeatureType.values().length) {
                        throw invalid(source, "invalid summary river type ordinal " + ordinal);
                    }
                    riverTypes.add(HydrologyFeatureType.values()[ordinal]);
                    previousOrdinal = ordinal;
                }
                LongOpenHashSet activations = readActivations(source, input, "summary activation");
                LongOpenHashSet sealedActivations = readActivations(source, input, "sealed summary activation");
                if (input.available() != 0) {
                    throw invalid(source, "unexpected trailing summary data");
                }
                if (!activations.containsAll(sealedActivations)) {
                    throw invalid(source, "sealed summary activations are missing from the activation set");
                }
                return new RegionSummary(keys, riverTypes, activations, sealedActivations);
            } catch (EOFException error) {
                throw invalid(source, "truncated summary data", error);
            } catch (IllegalArgumentException | NullPointerException error) {
                throw invalid(source, "invalid summary data", error);
            }
        }

        private byte[] encode() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (SemanticKind kind : SemanticKind.values()) {
                    Set<String> values = keys(kind);
                    if (values.size() > MAX_PALETTE_KEYS) {
                        throw new IOException("Generation semantic region summary has too many keys");
                    }
                    output.writeShort(values.size());
                    for (String key : new TreeSet<>(values)) {
                        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                        output.writeShort(keyBytes.length);
                        output.write(keyBytes);
                    }
                }
                output.writeByte(riverTypes.size());
                for (HydrologyFeatureType type : riverTypes) {
                    output.writeByte(type.ordinal());
                }
                writeActivations(output, activations);
                writeActivations(output, sealedActivations);
            }
            return bytes.toByteArray();
        }

        private Set<String> keys(SemanticKind kind) {
            Set<String> values = keys.get(kind);
            return values == null ? Set.of() : values;
        }

        private Set<HydrologyFeatureType> riverTypes() {
            return riverTypes;
        }

        private boolean hasAnyRiverType(Set<HydrologyFeatureType> requestedTypes) {
            for (HydrologyFeatureType type : requestedTypes) {
                if (riverTypes.contains(type)) {
                    return true;
                }
            }
            return false;
        }

        private boolean mayContainActivation(OptionalLong activationFilter) {
            return activationFilter.isEmpty() || activations.contains(activationFilter.getAsLong());
        }

        private boolean hasSealedActivation(long activationId) {
            return sealedActivations.contains(activationId);
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof RegionSummary summary)) {
                return false;
            }
            return keys.equals(summary.keys)
                    && riverTypes.equals(summary.riverTypes)
                    && activations.equals(summary.activations)
                    && sealedActivations.equals(summary.sealedActivations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keys, riverTypes, activations, sealedActivations);
        }

        private static EnumMap<SemanticKind, TreeSet<String>> emptyKeySets() {
            EnumMap<SemanticKind, TreeSet<String>> keys = new EnumMap<>(SemanticKind.class);
            for (SemanticKind kind : SemanticKind.values()) {
                keys.put(kind, new TreeSet<>());
            }
            return keys;
        }

        private static TreeSet<String> mutableKeys(
                EnumMap<SemanticKind, TreeSet<String>> keys,
                SemanticKind kind
        ) {
            return keys.get(kind);
        }

        private static LongOpenHashSet readActivations(
                Path source,
                DataInputStream input,
                String label
        ) throws IOException {
            int activationCount = input.readUnsignedShort();
            if (activationCount > CHUNKS_PER_REGION) {
                throw invalid(source, "invalid " + label + " count " + activationCount);
            }
            LongOpenHashSet activations = new LongOpenHashSet(activationCount);
            long previousActivation = 0L;
            for (int index = 0; index < activationCount; index++) {
                long activationId = input.readLong();
                if (activationId <= 0L || activationId <= previousActivation) {
                    throw invalid(source, label + " IDs are not in canonical order");
                }
                activations.add(activationId);
                previousActivation = activationId;
            }
            return activations;
        }

        private static void writeActivations(
                DataOutputStream output,
                LongOpenHashSet activationSet
        ) throws IOException {
            long[] activations = activationSet.toLongArray();
            Arrays.sort(activations);
            output.writeShort(activations.length);
            for (long activationId : activations) {
                output.writeLong(activationId);
            }
        }

        private static IOException invalid(Path source, String reason) {
            return new IOException("Invalid generation semantic shard " + source + ": " + reason);
        }

        private static IOException invalid(Path source, String reason, Throwable cause) {
            return new IOException("Invalid generation semantic shard " + source + ": " + reason, cause);
        }
    }

    @FunctionalInterface
    interface ShardPublisher {
        void publish(Path directory, int regionX, int regionZ, byte[] encoded) throws IOException;
    }

    @FunctionalInterface
    interface PointerPublisher {
        void publish(Path directory, int regionX, int regionZ, String hash) throws IOException;
    }

    @FunctionalInterface
    interface CatalogPublisher {
        void publish(Path directory, Set<Long> regionKeys) throws IOException;
    }

    static final class Claim {
        final ChunkGenerationSemantics update;
        boolean persisted;
        boolean completed;
        private Throwable failure;

        Claim(ChunkGenerationSemantics update) {
            this.update = update;
        }

        boolean fail(Throwable failure) {
            if (!completed) {
                this.failure = failure;
                persisted = false;
                completed = true;
                return true;
            }
            return this.failure == failure;
        }

        boolean result() throws IOException {
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return persisted;
        }
    }

    private static final class SemanticJournal {
        private static final int ENTRY_MAGIC = 0x4953574C;
        private static final int HEADER_BYTES = Integer.BYTES * 2;

        private static void append(
                Path directory,
                int regionX,
                int regionZ,
                List<ChunkGenerationSemantics> claims
        ) throws IOException {
            Path file = directory.resolve(journalFileName(regionX, regionZ));
            boolean created = !Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            if (!created && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw invalid(file, "path is not a regular file");
            }
            FileChannel opened = FileChannel.open(
                    file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
            boolean uncertain = created;
            boolean restored = false;
            try (FileChannel channel = opened) {
                long size = channel.size();
                if (size > MAX_JOURNAL_BYTES) {
                    throw invalid(file, "journal exceeds its size limit");
                }
                uncertain = true;
                try {
                    channel.position(size);
                    long written = size;
                    for (ChunkGenerationSemantics claim : claims) {
                        ByteBuffer entry = encodeEntry(regionX, regionZ, claim);
                        if (written > MAX_JOURNAL_BYTES - entry.remaining()) {
                            throw invalid(file, "journal exceeds its size limit");
                        }
                        written += entry.remaining();
                        while (entry.hasRemaining()) {
                            channel.write(entry);
                        }
                    }
                    Durability.force(channel);
                    if (created) {
                        RegionShard.forceDirectory(directory);
                    }
                } catch (IOException failure) {
                    try {
                        channel.truncate(size);
                        Durability.force(channel);
                        if (created) {
                            RegionShard.forceDirectory(directory);
                        }
                        restored = true;
                    } catch (IOException rollback) {
                        failure.addSuppressed(rollback);
                    }
                    throw failure;
                }
            } catch (IOException failure) {
                if (uncertain && !restored) {
                    throw new JournalAppendFailure(file, failure);
                }
                throw failure;
            }
        }

        private static ByteBuffer encodeEntry(int regionX, int regionZ, ChunkGenerationSemantics claim) throws IOException {
            RegionShard single = new RegionShard(regionX, regionZ).withRecord(claim);
            byte[] payload = single.encode();
            CRC32 checksum = new CRC32();
            checksum.update(payload);
            ByteBuffer entry = ByteBuffer.allocate(HEADER_BYTES + payload.length + CHECKSUM_BYTES);
            entry.putInt(ENTRY_MAGIC);
            entry.putInt(payload.length);
            entry.put(payload);
            entry.putInt((int) checksum.getValue());
            entry.flip();
            return entry;
        }

        private static Replay replay(Path file, int expectedRegionX, int expectedRegionZ) throws IOException {
            long size = Files.size(file);
            if (size > MAX_JOURNAL_BYTES) {
                throw invalid(file, "journal exceeds its size limit");
            }
            ArrayList<ChunkGenerationSemantics> claims = new ArrayList<>();
            long validBytes = 0L;
            try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "rw")) {
                while (validBytes < size) {
                    long entryStart = validBytes;
                    long remaining = size - entryStart;
                    if (remaining < HEADER_BYTES) {
                        truncateTail(input, entryStart);
                        break;
                    }
                    input.seek(entryStart);
                    int magic = input.readInt();
                    int payloadLength = input.readInt();
                    if (magic != ENTRY_MAGIC || payloadLength <= 0 || payloadLength > MAX_FILE_BYTES) {
                        if (hasLaterEntryMagic(input, entryStart + 1L, size)) {
                            throw invalid(file, "corrupt interior entry header at byte " + entryStart);
                        }
                        truncateTail(input, entryStart);
                        break;
                    }
                    long entryLength = HEADER_BYTES + (long) payloadLength + CHECKSUM_BYTES;
                    if (entryLength > remaining) {
                        if (hasLaterEntryMagic(input, entryStart + HEADER_BYTES, size)) {
                            throw invalid(file, "corrupt interior entry length at byte " + entryStart);
                        }
                        truncateTail(input, entryStart);
                        break;
                    }
                    byte[] payload = new byte[payloadLength];
                    input.readFully(payload);
                    int expectedChecksum = input.readInt();
                    CRC32 checksum = new CRC32();
                    checksum.update(payload);
                    long entryEnd = entryStart + entryLength;
                    if ((int) checksum.getValue() != expectedChecksum) {
                        if (entryEnd < size) {
                            throw invalid(file, "checksum mismatch in interior entry at byte " + entryStart);
                        }
                        truncateTail(input, entryStart);
                        break;
                    }
                    RegionShard decoded;
                    try {
                        decoded = RegionShard.decode(file, payload);
                    } catch (IOException error) {
                        if (entryEnd < size) {
                            throw invalid(file, "invalid interior entry at byte " + entryStart, error);
                        }
                        truncateTail(input, entryStart);
                        break;
                    }
                    if (decoded.regionX() != expectedRegionX
                            || decoded.regionZ() != expectedRegionZ
                            || decoded.recordCount != 1) {
                        throw invalid(file, "entry belongs to a different region");
                    }
                    claims.add(decoded.records().getFirst());
                    validBytes = entryEnd;
                }
            }
            return new Replay(List.copyOf(claims), claims.size(), validBytes);
        }

        private static boolean hasLaterEntryMagic(
                RandomAccessFile input,
                long start,
                long fileSize
        ) throws IOException {
            if (fileSize - start < Integer.BYTES) {
                return false;
            }
            input.seek(start);
            int window = 0;
            int loaded = 0;
            for (long position = start; position < fileSize; position++) {
                window = (window << Byte.SIZE) | input.readUnsignedByte();
                loaded++;
                if (loaded >= Integer.BYTES && window == ENTRY_MAGIC) {
                    return true;
                }
            }
            return false;
        }

        private static void truncateTail(RandomAccessFile file, long length) throws IOException {
            file.setLength(length);
            Durability.force(file.getFD());
        }

        private static IOException invalid(Path file, String reason) {
            return new IOException("Invalid generation semantic journal " + file + ": " + reason);
        }

        private static IOException invalid(Path file, String reason, Throwable cause) {
            return new IOException("Invalid generation semantic journal " + file + ": " + reason, cause);
        }

        private record Replay(
                List<ChunkGenerationSemantics> claims,
                int entryCount,
                long validBytes
        ) {
        }
    }

    private static final class JournalAppendFailure extends IOException {
        private JournalAppendFailure(Path file, IOException cause) {
            super("Generation semantic journal could not confirm a durable append or rollback: " + file, cause);
        }
    }

    private static final class ShardPointer {
        private static final int MAGIC = 0x49535054;
        private static final int FORMAT_VERSION = 1;
        private static final int BODY_BYTES = 48;

        private static String read(Path file, int expectedRegionX, int expectedRegionZ) throws IOException {
            byte[] encoded = Files.readAllBytes(file);
            if (encoded.length != BODY_BYTES + CHECKSUM_BYTES) {
                throw invalid(file, "invalid length " + encoded.length);
            }
            validateChecksum(file, encoded);
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(encoded, 0, BODY_BYTES)
            )) {
                int magic = input.readInt();
                if (magic != MAGIC) {
                    throw invalid(file, "invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != FORMAT_VERSION) {
                    throw invalid(file, "unsupported format version " + version);
                }
                int flags = input.readUnsignedShort();
                if (flags != 0) {
                    throw invalid(file, "unsupported format flags " + flags);
                }
                int regionX = input.readInt();
                int regionZ = input.readInt();
                if (regionX != expectedRegionX || regionZ != expectedRegionZ) {
                    throw invalid(file, "coordinates do not match the pointer file name");
                }
                byte[] hash = new byte[32];
                input.readFully(hash);
                if (input.available() != 0) {
                    throw invalid(file, "unexpected trailing data");
                }
                return HexFormat.of().formatHex(hash);
            }
        }

        private static void publish(
                Path directory,
                int regionX,
                int regionZ,
                String hash
        ) throws IOException {
            byte[] encoded = encode(regionX, regionZ, hash);
            Path target = directory.resolve(pointerFileName(regionX, regionZ));
            publishAtomic(target, encoded, "shard pointer");
        }

        private static byte[] encode(int regionX, int regionZ, String hash) throws IOException {
            if (hash == null || !hash.matches("[0-9a-f]{64}")) {
                throw new IOException("Generation semantic shard pointer contains an invalid hash");
            }
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(BODY_BYTES);
            try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
                output.writeInt(MAGIC);
                output.writeShort(FORMAT_VERSION);
                output.writeShort(0);
                output.writeInt(regionX);
                output.writeInt(regionZ);
                output.write(HexFormat.of().parseHex(hash));
            }
            return withChecksum(bodyBytes.toByteArray());
        }

        private static void validateChecksum(Path file, byte[] encoded) throws IOException {
            if (!checksumMatches(encoded)) {
                throw invalid(file, "checksum mismatch");
            }
        }

        private static IOException invalid(Path file, String reason) {
            return new IOException("Invalid generation semantic shard pointer " + file + ": " + reason);
        }
    }

    private static final class Catalog {
        private static Set<Long> read(Path file) throws IOException {
            byte[] encoded = readEncoded(file);
            validateChecksum(file, encoded);
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
                int magic = input.readInt();
                if (magic != CATALOG_MAGIC) {
                    throw invalid(file, "invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != CATALOG_FORMAT_VERSION) {
                    throw invalid(file, "unsupported format version " + version);
                }
                int flags = input.readUnsignedShort();
                if (flags != 0) {
                    throw invalid(file, "unsupported format flags " + flags);
                }
                int count = input.readInt();
                int maximumEntries = (MAX_CATALOG_BYTES - CATALOG_FIXED_BODY_BYTES - CHECKSUM_BYTES) / 8;
                if (count < 0 || count > maximumEntries) {
                    throw invalid(file, "invalid shard count " + count);
                }
                TreeSet<Long> entries = new TreeSet<>(GenerationSemanticIndex::compareRegionKeys);
                int previousRegionX = 0;
                int previousRegionZ = 0;
                boolean first = true;
                for (int index = 0; index < count; index++) {
                    int regionX = input.readInt();
                    int regionZ = input.readInt();
                    RegionShard.validateRegionCoordinate(file, regionX);
                    RegionShard.validateRegionCoordinate(file, regionZ);
                    if (!first && compareRegions(previousRegionX, previousRegionZ, regionX, regionZ) >= 0) {
                        throw invalid(file, "shard entries are not in canonical order");
                    }
                    entries.add(packRegion(regionX, regionZ));
                    previousRegionX = regionX;
                    previousRegionZ = regionZ;
                    first = false;
                }
                if (input.available() != 0) {
                    throw invalid(file, "unexpected trailing data");
                }
                return entries;
            } catch (EOFException error) {
                throw invalid(file, "truncated data", error);
            }
        }

        private static void publish(Path directory, Set<Long> regionKeys) throws IOException {
            byte[] encoded = encode(regionKeys);
            Path target = directory.resolve(CATALOG_FILE_NAME);
            publishAtomic(target, encoded, "catalog");
        }

        private static byte[] encode(Set<Long> regionKeys) throws IOException {
            List<Long> entries = new ArrayList<>(regionKeys);
            entries.sort(GenerationSemanticIndex::compareRegionKeys);
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(CATALOG_FIXED_BODY_BYTES + entries.size() * 8);
            try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
                output.writeInt(CATALOG_MAGIC);
                output.writeShort(CATALOG_FORMAT_VERSION);
                output.writeShort(0);
                output.writeInt(entries.size());
                for (long entry : entries) {
                    output.writeInt(regionX(entry));
                    output.writeInt(regionZ(entry));
                }
            }
            byte[] body = bodyBytes.toByteArray();
            if (body.length > MAX_CATALOG_BYTES - CHECKSUM_BYTES) {
                throw new IOException("Generation semantic catalog exceeds its size limit");
            }
            return withChecksum(body);
        }

        private static byte[] readEncoded(Path file) throws IOException {
            long size = Files.size(file);
            if (size < CATALOG_FIXED_BODY_BYTES + CHECKSUM_BYTES) {
                throw invalid(file, "truncated data");
            }
            if (size > MAX_CATALOG_BYTES) {
                throw invalid(file, "file is too large");
            }
            return Files.readAllBytes(file);
        }

        private static void validateChecksum(Path file, byte[] encoded) throws IOException {
            if (!checksumMatches(encoded)) {
                throw invalid(file, "checksum mismatch");
            }
        }

        private static IOException invalid(Path file, String reason) {
            return new IOException("Invalid generation semantic catalog " + file + ": " + reason);
        }

        private static IOException invalid(Path file, String reason, Throwable cause) {
            return new IOException("Invalid generation semantic catalog " + file + ": " + reason, cause);
        }
    }

    private static final class RegionShard {
        private final int regionX;
        private final int regionZ;
        private final ChunkGenerationSemantics[] records;
        private int recordCount;

        private RegionShard(int regionX, int regionZ) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            records = new ChunkGenerationSemantics[CHUNKS_PER_REGION];
        }

        private static RegionShard read(Path file, String expectedHash) throws IOException {
            byte[] encoded = readEncoded(file);
            String actualHash = sha256(encoded);
            if (!actualHash.equals(expectedHash)) {
                throw invalid(file, "content hash mismatch");
            }
            return decode(file, encoded);
        }

        private static Metadata readMetadata(Path file, String expectedHash) throws IOException {
            byte[] encoded = readEncoded(file);
            String actualHash = sha256(encoded);
            if (!actualHash.equals(expectedHash)) {
                throw invalid(file, "content hash mismatch");
            }
            validateChecksum(file, encoded);
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
                int magic = input.readInt();
                if (magic != MAGIC) {
                    throw invalid(file, "invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != FORMAT_VERSION) {
                    throw invalid(file, "unsupported format version " + version);
                }
                int flags = input.readUnsignedShort();
                if (flags != 0) {
                    throw invalid(file, "unsupported format flags " + flags);
                }
                int regionX = input.readInt();
                int regionZ = input.readInt();
                validateRegionCoordinate(file, regionX);
                validateRegionCoordinate(file, regionZ);
                int recordCount = input.readInt();
                if (recordCount < 0 || recordCount > CHUNKS_PER_REGION) {
                    throw invalid(file, "invalid record count " + recordCount);
                }
                int paletteSize = input.readInt();
                if (paletteSize < 0 || paletteSize > MAX_PALETTE_KEYS) {
                    throw invalid(file, "invalid key palette size " + paletteSize);
                }
                int summaryLength = input.readInt();
                if (summaryLength <= 0 || summaryLength > input.available()) {
                    throw invalid(file, "invalid summary length " + summaryLength);
                }
                return new Metadata(regionX, regionZ, recordCount);
            } catch (EOFException error) {
                throw invalid(file, "truncated data", error);
            }
        }

        private static Summary readSummary(Path file, String expectedHash) throws IOException {
            byte[] encoded = readEncoded(file);
            String actualHash = sha256(encoded);
            if (!actualHash.equals(expectedHash)) {
                throw invalid(file, "content hash mismatch");
            }
            validateChecksum(file, encoded);
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
                int magic = input.readInt();
                if (magic != MAGIC) {
                    throw invalid(file, "invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != FORMAT_VERSION) {
                    throw invalid(file, "unsupported format version " + version);
                }
                int flags = input.readUnsignedShort();
                if (flags != 0) {
                    throw invalid(file, "unsupported format flags " + flags);
                }
                int regionX = input.readInt();
                int regionZ = input.readInt();
                validateRegionCoordinate(file, regionX);
                validateRegionCoordinate(file, regionZ);
                int recordCount = input.readInt();
                if (recordCount < 0 || recordCount > CHUNKS_PER_REGION) {
                    throw invalid(file, "invalid record count " + recordCount);
                }
                int paletteSize = input.readInt();
                if (paletteSize < 0 || paletteSize > MAX_PALETTE_KEYS) {
                    throw invalid(file, "invalid key palette size " + paletteSize);
                }
                int summaryLength = input.readInt();
                if (summaryLength <= 0 || summaryLength > input.available()) {
                    throw invalid(file, "invalid summary length " + summaryLength);
                }
                byte[] summaryBytes = new byte[summaryLength];
                input.readFully(summaryBytes);
                return new Summary(
                        regionX,
                        regionZ,
                        recordCount,
                        RegionSummary.decode(file, summaryBytes)
                );
            } catch (EOFException error) {
                throw invalid(file, "truncated data", error);
            } catch (IllegalArgumentException | NullPointerException error) {
                throw invalid(file, "invalid semantic summary", error);
            }
        }

        private static RegionShard decode(Path source, byte[] encoded) throws IOException {
            if (encoded.length < FIXED_BODY_BYTES + CHECKSUM_BYTES) {
                throw invalid(source, "truncated data");
            }
            if (encoded.length > MAX_FILE_BYTES) {
                throw invalid(source, "file is too large");
            }
            validateChecksum(source, encoded);
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded, 0, bodyLength))) {
                int magic = input.readInt();
                if (magic != MAGIC) {
                    throw invalid(source, "invalid magic");
                }
                int version = input.readUnsignedShort();
                if (version != FORMAT_VERSION) {
                    throw invalid(source, "unsupported format version " + version);
                }
                int flags = input.readUnsignedShort();
                if (flags != 0) {
                    throw invalid(source, "unsupported format flags " + flags);
                }
                int regionX = input.readInt();
                int regionZ = input.readInt();
                validateRegionCoordinate(source, regionX);
                validateRegionCoordinate(source, regionZ);
                int recordCount = input.readInt();
                if (recordCount < 0 || recordCount > CHUNKS_PER_REGION) {
                    throw invalid(source, "invalid record count " + recordCount);
                }
                int paletteSize = input.readInt();
                if (paletteSize < 0 || paletteSize > MAX_PALETTE_KEYS) {
                    throw invalid(source, "invalid key palette size " + paletteSize);
                }
                int summaryLength = input.readInt();
                if (summaryLength <= 0 || summaryLength > input.available()) {
                    throw invalid(source, "invalid summary length " + summaryLength);
                }
                byte[] summaryBytes = new byte[summaryLength];
                input.readFully(summaryBytes);
                RegionSummary storedSummary = RegionSummary.decode(source, summaryBytes);
                String[] palette = readPalette(source, input, paletteSize);
                boolean[] paletteUsed = new boolean[paletteSize];
                RegionShard region = new RegionShard(regionX, regionZ);
                int previousIndex = -1;
                for (int index = 0; index < recordCount; index++) {
                    int localIndex = input.readUnsignedShort();
                    if (localIndex >= CHUNKS_PER_REGION || localIndex <= previousIndex) {
                        throw invalid(source, "chunk records are not in canonical order");
                    }
                    previousIndex = localIndex;
                    long activationId = input.readLong();
                    if (activationId <= 0L) {
                        throw invalid(source, "generation activation IDs must be positive");
                    }
                    int recordFlags = input.readUnsignedByte();
                    if ((recordFlags & ~1) != 0) {
                        throw invalid(source, "unsupported semantic record flags " + recordFlags);
                    }
                    int chunkX = (regionX << 5) + (localIndex & 31);
                    int chunkZ = (regionZ << 5) + (localIndex >>> 5);
                    ChunkGenerationSemantics.Builder builder = ChunkGenerationSemantics.builder(
                            chunkX,
                            chunkZ,
                            activationId
                    );
                    readKeys(source, input, palette, paletteUsed, builder, SemanticKind.SURFACE_BIOME);
                    readKeys(source, input, palette, paletteUsed, builder, SemanticKind.CAVE_BIOME);
                    readKeys(source, input, palette, paletteUsed, builder, SemanticKind.REGION);
                    readKeys(source, input, palette, paletteUsed, builder, SemanticKind.RIVER_PROFILE);
                    readKeys(source, input, palette, paletteUsed, builder, SemanticKind.OBJECT);
                    readRiverFeatures(source, input, palette, paletteUsed, builder);
                    readStructures(source, input, palette, paletteUsed, builder);
                    readPointsOfInterest(source, input, palette, paletteUsed, builder);
                    if ((recordFlags & 1) != 0) {
                        builder.seal();
                    }
                    region.addLoaded(localIndex, builder.build());
                }
                for (boolean used : paletteUsed) {
                    if (!used) {
                        throw invalid(source, "unused resource key palette entry");
                    }
                }
                if (input.available() != 0) {
                    throw invalid(source, "unexpected trailing data");
                }
                if (!RegionSummary.from(region).equals(storedSummary)) {
                    throw invalid(source, "summary does not match semantic records");
                }
                return region;
            } catch (EOFException error) {
                throw invalid(source, "truncated data", error);
            } catch (IllegalArgumentException | NullPointerException error) {
                throw invalid(source, "invalid semantic record", error);
            }
        }

        private int regionX() {
            return regionX;
        }

        private int regionZ() {
            return regionZ;
        }

        private int recordCount() {
            return recordCount;
        }

        private ChunkGenerationSemantics get(int chunkX, int chunkZ) {
            if ((chunkX >> 5) != regionX || (chunkZ >> 5) != regionZ) {
                return null;
            }
            return records[localIndex(chunkX, chunkZ)];
        }

        private RegionShard retainingStoredClaims(WorldChunkInventory stored, Set<Long> activationIds) {
            RegionShard replacement = new RegionShard(regionX, regionZ);
            for (int index = 0; index < records.length; index++) {
                ChunkGenerationSemantics record = records[index];
                if (record != null && (!activationIds.contains(record.activationId())
                        || stored.contains(record.chunkX(), record.chunkZ()))) {
                    replacement.records[index] = record;
                    replacement.recordCount++;
                }
            }
            return replacement;
        }

        private RegionShard withRecord(ChunkGenerationSemantics semantics) {
            if ((semantics.chunkX() >> 5) != regionX || (semantics.chunkZ() >> 5) != regionZ) {
                throw new IllegalArgumentException("Chunk does not belong to generation semantic shard");
            }
            int index = localIndex(semantics.chunkX(), semantics.chunkZ());
            RegionShard replacement = new RegionShard(regionX, regionZ);
            System.arraycopy(records, 0, replacement.records, 0, records.length);
            replacement.recordCount = recordCount;
            if (replacement.records[index] == null) {
                replacement.recordCount++;
            }
            replacement.records[index] = semantics;
            return replacement;
        }

        private void addLoaded(int index, ChunkGenerationSemantics semantics) {
            if (records[index] != null) {
                throw new IllegalArgumentException("Duplicate generation semantic chunk record");
            }
            records[index] = semantics;
            recordCount++;
        }

        private List<ChunkGenerationSemantics> records() {
            List<ChunkGenerationSemantics> snapshot = new ArrayList<>(recordCount);
            for (ChunkGenerationSemantics semantics : records) {
                if (semantics != null) {
                    snapshot.add(semantics);
                }
            }
            return snapshot;
        }

        private static void publish(Path directory, int regionX, int regionZ, byte[] encoded) throws IOException {
            String hash = sha256(encoded);
            Path target = directory.resolve(fileName(regionX, regionZ, hash));
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Generation semantic shard is not a regular file: " + target);
                }
                byte[] existing = readEncoded(target);
                if (!MessageDigest.isEqual(existing, encoded)) {
                    throw new IOException("Generation semantic shard content conflicts with its hash: " + target);
                }
                return;
            }
            Path temporary = Files.createTempFile(directory, "." + target.getFileName() + "-", ".tmp");
            try {
                writeForced(temporary, encoded);
                try {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException error) {
                    throw new IOException("Generation semantics require atomic shard publication", error);
                }
                forceDirectory(directory);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private byte[] encode() throws IOException {
            TreeSet<String> paletteKeys = new TreeSet<>();
            for (ChunkGenerationSemantics semantics : records) {
                if (semantics == null) {
                    continue;
                }
                addPaletteKeys(paletteKeys, semantics.surfaceBiomeKeys());
                addPaletteKeys(paletteKeys, semantics.caveBiomeKeys());
                addPaletteKeys(paletteKeys, semantics.regionKeys());
                addPaletteKeys(paletteKeys, semantics.riverProfileKeys());
                addPaletteKeys(paletteKeys, semantics.objectKeys());
                for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : semantics.riverFeatures()) {
                    addPaletteKey(paletteKeys, occurrence.profileKey());
                }
                for (ChunkGenerationSemantics.StructureOccurrence occurrence : semantics.structures()) {
                    addPaletteKey(paletteKeys, occurrence.key());
                }
                for (ChunkGenerationSemantics.PointOfInterest point : semantics.pointsOfInterest()) {
                    addPaletteKey(paletteKeys, point.key());
                }
            }
            List<String> palette = new ArrayList<>(paletteKeys);
            Map<String, Integer> paletteIndexes = new HashMap<>(palette.size());
            for (int index = 0; index < palette.size(); index++) {
                paletteIndexes.put(palette.get(index), index);
            }

            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream(8_192);
            byte[] summary = RegionSummary.from(this).encode();
            try (DataOutputStream output = new DataOutputStream(bodyBytes)) {
                output.writeInt(MAGIC);
                output.writeShort(FORMAT_VERSION);
                output.writeShort(0);
                output.writeInt(regionX);
                output.writeInt(regionZ);
                output.writeInt(recordCount);
                output.writeInt(palette.size());
                output.writeInt(summary.length);
                output.write(summary);
                for (String key : palette) {
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    output.writeShort(keyBytes.length);
                    output.write(keyBytes);
                    if (bodyBytes.size() > MAX_FILE_BYTES - CHECKSUM_BYTES) {
                        throw new IOException("Generation semantic shard exceeds its size limit");
                    }
                }
                for (int index = 0; index < records.length; index++) {
                    ChunkGenerationSemantics semantics = records[index];
                    if (semantics == null) {
                        continue;
                    }
                    output.writeShort(index);
                    output.writeLong(semantics.activationId());
                    output.writeByte(semantics.sealed() ? 1 : 0);
                    writeKeys(output, semantics.surfaceBiomeKeys(), paletteIndexes);
                    writeKeys(output, semantics.caveBiomeKeys(), paletteIndexes);
                    writeKeys(output, semantics.regionKeys(), paletteIndexes);
                    writeKeys(output, semantics.riverProfileKeys(), paletteIndexes);
                    writeKeys(output, semantics.objectKeys(), paletteIndexes);
                    output.writeShort(semantics.riverFeatures().size());
                    for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : semantics.riverFeatures()) {
                        output.writeShort(paletteIndexes.get(occurrence.profileKey()));
                        output.writeByte(occurrence.type().ordinal());
                        output.writeLong(occurrence.featureId());
                        output.writeInt(occurrence.position().x());
                        output.writeInt(occurrence.position().y());
                        output.writeInt(occurrence.position().z());
                    }
                    output.writeShort(semantics.structures().size());
                    for (ChunkGenerationSemantics.StructureOccurrence occurrence : semantics.structures()) {
                        output.writeShort(paletteIndexes.get(occurrence.key()));
                        output.writeInt(occurrence.position().x());
                        output.writeInt(occurrence.position().y());
                        output.writeInt(occurrence.position().z());
                    }
                    output.writeShort(semantics.pointsOfInterest().size());
                    for (ChunkGenerationSemantics.PointOfInterest point : semantics.pointsOfInterest()) {
                        output.writeShort(paletteIndexes.get(point.key()));
                        output.writeInt(point.position().x());
                        output.writeInt(point.position().y());
                        output.writeInt(point.position().z());
                    }
                    if (bodyBytes.size() > MAX_FILE_BYTES - CHECKSUM_BYTES) {
                        throw new IOException("Generation semantic shard exceeds its size limit");
                    }
                }
            }
            byte[] body = bodyBytes.toByteArray();
            CRC32 checksum = new CRC32();
            checksum.update(body);
            ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream(body.length + CHECKSUM_BYTES);
            encodedBytes.write(body);
            try (DataOutputStream output = new DataOutputStream(encodedBytes)) {
                output.writeInt((int) checksum.getValue());
            }
            return encodedBytes.toByteArray();
        }

        private record Summary(
                int regionX,
                int regionZ,
                int recordCount,
                RegionSummary semantics
        ) {
        }

        private record Metadata(int regionX, int regionZ, int recordCount) {
        }

        private static byte[] readEncoded(Path file) throws IOException {
            long size = Files.size(file);
            if (size < FIXED_BODY_BYTES + CHECKSUM_BYTES) {
                throw invalid(file, "truncated data");
            }
            if (size > MAX_FILE_BYTES) {
                throw invalid(file, "file is too large");
            }
            byte[] encoded = Files.readAllBytes(file);
            if (encoded.length > MAX_FILE_BYTES) {
                throw invalid(file, "file is too large");
            }
            return encoded;
        }

        private static void validateChecksum(Path file, byte[] encoded) throws IOException {
            int bodyLength = encoded.length - CHECKSUM_BYTES;
            int expected = ByteBuffer.wrap(encoded, bodyLength, CHECKSUM_BYTES).getInt();
            CRC32 checksum = new CRC32();
            checksum.update(encoded, 0, bodyLength);
            if ((int) checksum.getValue() != expected) {
                throw invalid(file, "checksum mismatch");
            }
        }

        private static String[] readPalette(Path file, DataInputStream input, int paletteSize) throws IOException {
            String[] palette = new String[paletteSize];
            String previous = null;
            for (int index = 0; index < paletteSize; index++) {
                int byteLength = input.readUnsignedShort();
                if (byteLength <= 0 || byteLength > ChunkGenerationSemantics.MAX_KEY_BYTES) {
                    throw invalid(file, "invalid resource key length " + byteLength);
                }
                byte[] keyBytes = new byte[byteLength];
                input.readFully(keyBytes);
                String key = ChunkGenerationSemantics.requireResourceKey(
                        new String(keyBytes, StandardCharsets.UTF_8)
                );
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw invalid(file, "resource key palette is not in canonical order");
                }
                palette[index] = key;
                previous = key;
            }
            return palette;
        }

        private static void readKeys(
                Path file,
                DataInputStream input,
                String[] palette,
                boolean[] paletteUsed,
                ChunkGenerationSemantics.Builder builder,
                SemanticKind kind
        ) throws IOException {
            int count = input.readUnsignedShort();
            if (count > ChunkGenerationSemantics.MAX_KEYS_PER_KIND) {
                throw invalid(file, "too many " + kind.name().toLowerCase() + " keys");
            }
            int previousIndex = -1;
            for (int index = 0; index < count; index++) {
                int paletteIndex = input.readUnsignedShort();
                if (paletteIndex >= palette.length || paletteIndex <= previousIndex) {
                    throw invalid(file, "semantic keys are not in canonical palette order");
                }
                previousIndex = paletteIndex;
                paletteUsed[paletteIndex] = true;
                addDecodedKey(builder, kind, palette[paletteIndex]);
            }
        }

        private static void readStructures(
                Path file,
                DataInputStream input,
                String[] palette,
                boolean[] paletteUsed,
                ChunkGenerationSemantics.Builder builder
        ) throws IOException {
            int count = input.readUnsignedShort();
            if (count > ChunkGenerationSemantics.MAX_STRUCTURES) {
                throw invalid(file, "too many structure occurrences");
            }
            ChunkGenerationSemantics.StructureOccurrence previous = null;
            for (int index = 0; index < count; index++) {
                int paletteIndex = input.readUnsignedShort();
                if (paletteIndex >= palette.length) {
                    throw invalid(file, "structure key palette index is out of range");
                }
                paletteUsed[paletteIndex] = true;
                ChunkGenerationSemantics.StructureOccurrence occurrence =
                        new ChunkGenerationSemantics.StructureOccurrence(
                                palette[paletteIndex],
                                new ChunkGenerationSemantics.BlockPosition(
                                        input.readInt(),
                                        input.readInt(),
                                        input.readInt()
                                )
                        );
                if (previous != null
                        && ChunkGenerationSemantics.structureComparator().compare(previous, occurrence) >= 0) {
                    throw invalid(file, "structure occurrences are not in canonical order");
                }
                builder.addStructure(occurrence);
                previous = occurrence;
            }
        }

        private static void readPointsOfInterest(
                Path file,
                DataInputStream input,
                String[] palette,
                boolean[] paletteUsed,
                ChunkGenerationSemantics.Builder builder
        ) throws IOException {
            int count = input.readUnsignedShort();
            if (count > ChunkGenerationSemantics.MAX_STRUCTURES) {
                throw invalid(file, "too many points of interest");
            }
            ChunkGenerationSemantics.PointOfInterest previous = null;
            for (int index = 0; index < count; index++) {
                int paletteIndex = input.readUnsignedShort();
                if (paletteIndex >= palette.length) {
                    throw invalid(file, "point of interest key palette index is out of range");
                }
                paletteUsed[paletteIndex] = true;
                ChunkGenerationSemantics.PointOfInterest occurrence =
                        new ChunkGenerationSemantics.PointOfInterest(
                                palette[paletteIndex],
                                new ChunkGenerationSemantics.BlockPosition(
                                        input.readInt(),
                                        input.readInt(),
                                        input.readInt()
                                )
                        );
                if (previous != null
                        && ChunkGenerationSemantics.pointComparator().compare(previous, occurrence) >= 0) {
                    throw invalid(file, "points of interest are not in canonical order");
                }
                builder.addPointOfInterest(occurrence);
                previous = occurrence;
            }
        }

        private static void readRiverFeatures(
                Path file,
                DataInputStream input,
                String[] palette,
                boolean[] paletteUsed,
                ChunkGenerationSemantics.Builder builder
        ) throws IOException {
            int count = input.readUnsignedShort();
            if (count > ChunkGenerationSemantics.MAX_RIVER_FEATURES) {
                throw invalid(file, "too many river feature occurrences");
            }
            ChunkGenerationSemantics.RiverFeatureOccurrence previous = null;
            HydrologyFeatureType[] types = HydrologyFeatureType.values();
            for (int index = 0; index < count; index++) {
                int paletteIndex = input.readUnsignedShort();
                if (paletteIndex >= palette.length) {
                    throw invalid(file, "river profile key palette index is out of range");
                }
                int typeOrdinal = input.readUnsignedByte();
                if (typeOrdinal >= types.length) {
                    throw invalid(file, "river feature type ordinal is out of range");
                }
                paletteUsed[paletteIndex] = true;
                ChunkGenerationSemantics.RiverFeatureOccurrence occurrence =
                        new ChunkGenerationSemantics.RiverFeatureOccurrence(
                                palette[paletteIndex],
                                types[typeOrdinal],
                                input.readLong(),
                                new ChunkGenerationSemantics.BlockPosition(
                                        input.readInt(),
                                        input.readInt(),
                                        input.readInt()
                                )
                        );
                if (previous != null
                        && ChunkGenerationSemantics.riverFeatureComparator()
                        .compare(previous, occurrence) >= 0) {
                    throw invalid(file, "river feature occurrences are not in canonical order");
                }
                builder.addRiverFeature(occurrence);
                previous = occurrence;
            }
        }

        private static void addDecodedKey(
                ChunkGenerationSemantics.Builder builder,
                SemanticKind kind,
                String key
        ) {
            switch (kind) {
                case SURFACE_BIOME -> builder.addSurfaceBiome(key);
                case CAVE_BIOME -> builder.addCaveBiome(key);
                case REGION -> builder.addRegion(key);
                case RIVER_PROFILE -> builder.addRiverProfile(key);
                case OBJECT -> builder.addObject(key);
                case STRUCTURE -> throw new IllegalArgumentException("Structure keys require exact positions");
            }
        }

        private static void writeKeys(
                DataOutputStream output,
                Set<String> keys,
                Map<String, Integer> paletteIndexes
        ) throws IOException {
            output.writeShort(keys.size());
            for (String key : keys) {
                output.writeShort(paletteIndexes.get(key));
            }
        }

        private static void addPaletteKeys(TreeSet<String> palette, Set<String> keys) throws IOException {
            for (String key : keys) {
                addPaletteKey(palette, key);
            }
        }

        private static void addPaletteKey(TreeSet<String> palette, String key) throws IOException {
            palette.add(key);
            if (palette.size() > MAX_PALETTE_KEYS) {
                throw new IOException("Generation semantic shard has too many unique resource keys");
            }
        }

        private static void validateRegionCoordinate(Path file, int regionCoordinate) throws IOException {
            if (regionCoordinate < MINIMUM_REGION_COORDINATE || regionCoordinate > MAXIMUM_REGION_COORDINATE) {
                throw invalid(file, "region coordinate is outside the chunk coordinate range");
            }
        }

        private static int localIndex(int chunkX, int chunkZ) {
            return (chunkX & 31) + ((chunkZ & 31) << 5);
        }

        private static void writeForced(Path file, byte[] encoded) throws IOException {
            try (FileChannel channel = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                Durability.force(channel);
            }
        }

        private static void forceDirectory(Path directory) throws IOException {
            if (!Durability.enabled()) {
                return;
            }

            if (File.separatorChar == '\\') {
                return;
            }
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                Durability.force(channel);
            } catch (UnsupportedOperationException error) {
                throw new IOException("Generation semantic directory cannot be durability-synced", error);
            }
        }

        private static IOException invalid(Path file, String reason) {
            return new IOException("Invalid generation semantic shard " + file + ": " + reason);
        }

        private static IOException invalid(Path file, String reason, Throwable cause) {
            return new IOException("Invalid generation semantic shard " + file + ": " + reason, cause);
        }
    }
}
