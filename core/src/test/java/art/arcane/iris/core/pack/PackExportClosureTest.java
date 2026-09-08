package art.arcane.iris.core.pack;

import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionStack;
import art.arcane.iris.engine.object.IrisObjectMarker;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisStaticObject;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackExportClosureTest {
    private static IrisObjectPlacement placement(String objectKey, String... markerKeys) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(objectKey));
        KList<IrisObjectMarker> markers = new KList<>();
        for (String markerKey : markerKeys) {
            IrisObjectMarker marker = new IrisObjectMarker();
            marker.setMarker(markerKey);
            markers.add(marker);
        }
        placement.setMarkers(markers);
        return placement;
    }

    @Test
    public void collectsMarkerAndObjectKeysFromPlacements() {
        List<IrisObjectPlacement> placements = List.of(
                placement("houses/hut", "spawn-point", "loot-marker"),
                placement("trees/oak"),
                placement("houses/hut", "spawn-point"));

        assertEquals(List.of("loot-marker", "spawn-point"),
                PackExportClosure.collectMarkerKeys(placements).stream().sorted().toList());
        assertEquals(List.of("houses/hut", "trees/oak"),
                PackExportClosure.collectObjectKeys(placements).stream().sorted().toList());
    }

    @Test
    public void toleratesNullPlacementsAndBlankKeys() {
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement("", ""));
        placements.add((IrisObjectPlacement) null);

        assertTrue(PackExportClosure.collectMarkerKeys(placements).isEmpty());
        assertTrue(PackExportClosure.collectObjectKeys(placements).isEmpty());
    }

    @Test
    public void collectsStaticObjectKeysWithoutDuplicates() {
        KList<IrisStaticObject> placements = new KList<>();
        placements.add(new IrisStaticObject().setObject("landmarks/tower"));
        placements.add(new IrisStaticObject().setObject("landmarks/tower"));
        placements.add(new IrisStaticObject().setObject("landmarks/bridge"));
        placements.add(new IrisStaticObject().setObject(""));
        placements.add((IrisStaticObject) null);

        assertEquals(List.of("landmarks/bridge", "landmarks/tower"),
                PackExportClosure.collectStaticObjectKeys(placements).stream().sorted().toList());
    }

    @Test
    public void collectsRootAndStackedDimensionKeys() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("main");
        dimension.setDimensionStack(new IrisDimensionStack()
                .setDimensions(new KList<>("sky", "middle", "main", "sky")));

        assertEquals(List.of("main", "middle", "sky"),
                PackExportClosure.collectDimensionKeys(dimension).stream().sorted().toList());
    }

    @Test
    public void collectsTransitiveStackDimensionsWithoutLooping() {
        IrisDimension main = dimension("main", "sky", "main");
        IrisDimension sky = dimension("sky", "cloud", "sky");
        IrisDimension cloud = dimension("cloud", "main", "cloud");
        Map<String, IrisDimension> dimensions = Map.of(
                "main", main,
                "sky", sky,
                "cloud", cloud);

        assertEquals(List.of("cloud", "main", "sky"),
                PackExportClosure.collectDimensionKeys(main, dimensions::get).stream().sorted().toList());
    }

    private static IrisDimension dimension(String key, String... stackKeys) {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey(key);
        dimension.setDimensionStack(new IrisDimensionStack().setDimensions(new KList<>(stackKeys)));
        return dimension;
    }

    /**
     * Source guard: both packagers must export the ambient-spawning graph. Spawner and marker
     * folders were silently omitted from exports, leaving dangling entitySpawners references.
     */
}
