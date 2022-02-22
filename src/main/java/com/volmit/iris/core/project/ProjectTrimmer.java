package com.volmit.iris.core.project;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisCave;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisEntity;
import com.volmit.iris.engine.object.IrisExpression;
import com.volmit.iris.engine.object.IrisGenerator;
import com.volmit.iris.engine.object.IrisJigsawPiece;
import com.volmit.iris.engine.object.IrisJigsawPool;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisLoot;
import com.volmit.iris.engine.object.IrisLootTable;
import com.volmit.iris.engine.object.IrisMarker;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisRavine;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.engine.object.IrisScript;
import com.volmit.iris.engine.object.IrisSpawner;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.parallel.MultiBurst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProjectTrimmer {

    private IrisDimension dimension;

    private KList<String> biomes;
    private KList<String> regions;
    private KList<String> caves;
    private KList<String> entities;
    private KList<String> objects;
    private KList<String> generators;
    private KList<String> expressions;
    private KList<String> loot;
    private KList<String> spawners;
    private KList<String> jigsawPieces;
    private KList<String> jigsawPools;
    private KList<String> jigsawStructures;
    private KList<String> scripts;
    private KList<String> markers;
    private KList<String> ravines;

    private IrisData data;

    private KList<Runnable> futureTasks = new KList<>();

    public ProjectTrimmer(IrisDimension dimension) {
        this.dimension = dimension;

        data = IrisData.get(Iris.service(StudioSVC.class).getWorkspaceFolder(dimension.getLoadKey()));

        biomes = new KList<>(data.getBiomeLoader().getPossibleKeys());
        regions = new KList<>(data.getRegionLoader().getPossibleKeys());
        caves = new KList<>(data.getCaveLoader().getPossibleKeys());
        entities = new KList<>(data.getEntityLoader().getPossibleKeys());
        objects = new KList<>(data.getObjectLoader().getPossibleKeys());
        generators = new KList<>(data.getGeneratorLoader().getPossibleKeys());
        expressions = new KList<>(data.getExpressionLoader().getPossibleKeys());
        loot = new KList<>(data.getLootLoader().getPossibleKeys());
        spawners = new KList<>(data.getSpawnerLoader().getPossibleKeys());
        jigsawPieces = new KList<>(data.getJigsawPieceLoader().getPossibleKeys());
        jigsawPools = new KList<>(data.getJigsawPoolLoader().getPossibleKeys());
        jigsawStructures = new KList<>(data.getJigsawStructureLoader().getPossibleKeys());
        scripts = new KList<>(data.getScriptLoader().getPossibleKeys());
        markers = new KList<>(data.getMarkerLoader().getPossibleKeys());
        ravines = new KList<>(data.getRavineLoader().getPossibleKeys());

        if (objects.contains("null") || objects.contains(null))
            Iris.warn("Warning! Some objects are null! This means you have encountered the AtomicCache bug! If you delete the files, it may break something!");
    }

    /**
     * Analyze the dimension pack. Do NOT run this on the main thread!
     */
    public void analyze() {

        for (String dimension : data.getDimensionLoader().getPossibleKeys()) {
            File file = data.getDimensionLoader().findFile(dimension);

            try {
                JSONObject json = new JSONObject(IO.readAll(file));
                if (json.keySet().contains("regions")) {
                    regions.removeAll(convertStringArray(json.getJSONArray("regions")));
                }
                if (json.keySet().contains("stronghold")) {
                    jigsawStructures.remove(json.getString("stronghold"));
                }

                analyzeCommonDRB(json); //Objects, structures
                trimExpressions(json);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        KList<String> usedRegions = new KList<>(Arrays.stream(data.getRegionLoader().getPossibleKeys())
                .filter(reg -> !this.regions.contains(reg)).collect(Collectors.toList()));

        //Regions
        for (String region : usedRegions) {
            futureTasks.add(() -> {
                File file = data.getRegionLoader().findFile(region);

                try {
                    JSONObject json = new JSONObject(IO.readAll(file));
                    if (json.keySet().contains("landBiomes")) {
                        analyzeBiomeArray(json.getJSONArray("landBiomes"));
                    }
                    if (json.keySet().contains("seaBiomes")) {
                        analyzeBiomeArray(json.getJSONArray("seaBiomes"));
                    }
                    if (json.keySet().contains("shoreBiomes")) {
                        analyzeBiomeArray(json.getJSONArray("shoreBiomes"));
                    }
                    if (json.keySet().contains("caveBiomes")) {
                        analyzeBiomeArray(json.getJSONArray("caveBiomes"));
                    }

                    analyzeCommonDRB(json); //Objects, structures
                    trimExpressions(json);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            });
        }

        //Burst tasks for all biomes and regions
        burstTasks();


        KList<String> usedBiomes = new KList<>(Arrays.stream(data.getBiomeLoader().getPossibleKeys())
                .filter(biome -> !this.biomes.contains(biome)).collect(Collectors.toList()));

        //Biomes
        for (String biome : usedBiomes) {
            analyzeBiome(biome);
        }

        burstTasks();

        KList<String> usedJigsaws = new KList<>(Arrays.stream(data.getJigsawStructureLoader().getPossibleKeys())
                .filter(jig -> !this.jigsawStructures.contains(jig)).collect(Collectors.toList()));



        for (String jigsaw : usedJigsaws) {
            futureTasks.add(() -> {
                File file = data.getJigsawStructureLoader().findFile(jigsaw);

                try {
                    JSONObject json = new JSONObject(IO.readAll(file));
                    if (json.keySet().contains("pieces")) {
                        JSONArray pieces = json.getJSONArray("pieces");
                        for (int i = 0; i < pieces.length(); i++) {
                            String pieceString = pieces.getString(i);
                            File pieceFile = data.getJigsawPieceLoader().findFile(pieceString);
                            this.jigsawPieces.remove(pieceString);
                            analyzeJigsawObject(new JSONObject(IO.readAll(pieceFile)));
                        }

                    }
                } catch (IOException e) {}
            });
        }

        burstTasks();

        KList<String> usedEntities = new KList<>(Arrays.stream(data.getEntityLoader().getPossibleKeys())
                .filter(jig -> !this.entities.contains(jig)).collect(Collectors.toList()));

        for (String entity : usedEntities) {
            futureTasks.add(() -> {
                File file = data.getEntityLoader().findFile(entity);

                try {
                    JSONObject json = new JSONObject(IO.readAll(file));
                    if (json.keySet().contains("postSpawnScripts")) {
                        scripts.removeAll(convertStringArray(json.getJSONArray("postSpawnScripts")));
                    }
                    if (json.keySet().contains("spawnerScripts")) {
                        scripts.remove(json.getString("spawnerScripts"));
                    }
                } catch (IOException e) {}
            });
        }

        burstTasks();

    }

    /**
     * Get all the unused files in the project after calling {@link #analyze()}
     * @return A map of all the unused files by type
     */
    public Map<Class<? extends IrisRegistrant>, KList<String>> getResult() {
        Map<Class<? extends IrisRegistrant>, KList<String>> map = new HashMap<>();
        map.put(IrisBiome.class, biomes);
        map.put(IrisRegion.class, regions);
        map.put(IrisCave.class, caves);
        map.put(IrisRavine.class, ravines);
        map.put(IrisObject.class, objects);
        map.put(IrisEntity.class, entities);
        map.put(IrisGenerator.class, generators);
        map.put(IrisSpawner.class, spawners);
        map.put(IrisLootTable.class, loot);
        map.put(IrisExpression.class, expressions);
        map.put(IrisScript.class, scripts);
        map.put(IrisJigsawPiece.class, jigsawPieces);
        map.put(IrisJigsawPool.class, jigsawPools);
        map.put(IrisJigsawStructure.class, jigsawStructures);
        map.put(IrisMarker.class, markers);

        return map;
    }

    /**
     * Do all queued tasks in a burst
     */
    private void burstTasks() {
        while (futureTasks.size() > 0) {
            KList<Runnable> clonedTasks = new KList<>(futureTasks);
            futureTasks.clear();

            MultiBurst burster = new MultiBurst();
            burster.burst(clonedTasks); //The tasks themselves can add more tasks to futureTasks
        }
    }



    /**
     * Analyze common things within dimensions, regions and objects
     * @param object The parent json object
     */
    private void analyzeCommonDRB(JSONObject object) {
        //Jigsaws
        if (object.keySet().contains("jigsawStructures")) {
            JSONArray jigsaws = object.getJSONArray("jigsawStructures");
            for (int i = 0; i < jigsaws.length(); i++) {
                JSONObject jigobject = jigsaws.getJSONObject(i);
                jigsawStructures.remove(jigobject.getString("structure"));
            }
        }

        //Objects
        if (object.keySet().contains("objects")) {
            JSONArray objectsArray = object.getJSONArray("objects");
            for (int i = 0; i < objectsArray.length(); i++) {
                JSONObject innerObject = objectsArray.getJSONObject(i); //The actual object within the objects

                analyzeObject(innerObject);
            }
        }

        //Loot
        if (object.keySet().contains("loot")) {
            JSONObject lootObject = object.getJSONObject("loot");
            this.loot.removeAll(convertStringArray(lootObject.getJSONArray("tables")));
        }

        //Spawners
        if (object.keySet().contains("entitySpawners")) {
            JSONArray entitySpawners = object.getJSONArray("entitySpawners");
            analyzeSpawnerArray(entitySpawners);
        }

        //Caves and stuff
        if (object.keySet().contains("carvings")) {
            JSONObject carvings = object.getJSONObject("carvings");

            if (carvings.keySet().contains("caves")) {
                JSONArray array = carvings.getJSONArray("caves");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject cave = array.getJSONObject(i);
                    this.caves.remove(cave.getString("cave"));
                }
            }
            if (carvings.keySet().contains("ravines")) {
                JSONArray array = carvings.getJSONArray("ravines");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject cave = array.getJSONObject(i);
                    this.ravines.remove(cave.getString("ravine"));
                }
            }
        }
    }

    private void analyzeObject(JSONObject object) {
        JSONArray placeArray = object.getJSONArray("place");
        objects.removeAll(convertStringArray(placeArray));

        if (object.keySet().contains("loot")) {
            JSONArray loot = object.getJSONArray("loot");
            for (int j = 0; j < loot.length(); j++) {
                JSONObject lootObject = loot.getJSONObject(j);
                this.loot.remove(lootObject.getString("name"));
            }
        }

        if (object.keySet().contains("markers")) {
            JSONArray markers = object.getJSONArray("markers");
            for (int j = 0; j < markers.length(); j++) {
                JSONObject marker = markers.getJSONObject(j);
                String markerName = marker.getString("marker");
                analyzeMarker(markerName);
            }
        }
    }

    private void analyzeJigsawObject(JSONObject object) {
        objects.remove(object.getString("object"));

        JSONObject placement = object.getJSONObject("placementOptions");
        if (placement.keySet().contains("loot")) {
            JSONArray loot = placement.getJSONArray("loot");
            for (int j = 0; j < loot.length(); j++) {
                JSONObject lootObject = loot.getJSONObject(j);
                this.loot.remove(lootObject.getString("name"));
            }
        }
        if (placement.keySet().contains("markers")) {
            JSONArray markers = placement.getJSONArray("markers");
            for (int j = 0; j < markers.length(); j++) {
                JSONObject marker = markers.getJSONObject(j);
                String markerName = marker.getString("marker");
                analyzeMarker(markerName);
            }
        }

        if (object.keySet().contains("connectors")) {
            JSONArray connectors = object.getJSONArray("connectors");
            for (int j = 0; j < connectors.length(); j++) {
                JSONObject connector = connectors.getJSONObject(j);

                if (connector.keySet().contains("pools")) {
                    JSONArray pools = connector.getJSONArray("pools");
                    analyzePoolArray(pools);
                }
            }

        }
    }

    private void analyzeMarker(String name) {
        if (markers.contains(name)) {
            markers.remove(name);
            futureTasks.add(() -> {
                File file = data.getMarkerLoader().findFile(name);

                try {
                    JSONObject json = new JSONObject(IO.readAll(file));
                    if (json.keySet().contains("spawners")) {
                        analyzeSpawnerArray(json.getJSONArray("spawners"));
                    }
                } catch (IOException e) {

                }
            });
        }
    }

    private void analyzeSpawnerArray(JSONArray spawners) {
        for (int i = 0; i < spawners.length(); i++) {
            String spawner = spawners.getString(i);

            if (this.spawners.contains(spawner)) {
                this.spawners.remove(spawner);

                futureTasks.add(() -> {
                    File file = data.getSpawnerLoader().findFile(spawner);

                    try {
                        JSONObject json = new JSONObject(IO.readAll(file));
                        if (json.keySet().contains("spawns")) {
                            JSONArray spawnArray = json.getJSONArray("spawns");
                            for (int j = 0; j < spawnArray.length(); j++) {
                                JSONObject spawnObject = spawnArray.getJSONObject(j);
                                entities.remove(spawnObject.getString("entity"));
                            }
                        }
                        if (json.keySet().contains("initialSpawns")) {
                            JSONArray spawnArray = json.getJSONArray("initialSpawns");
                            for (int j = 0; j < spawnArray.length(); j++) {
                                JSONObject spawnObject = spawnArray.getJSONObject(j);
                                entities.remove(spawnObject.getString("entity"));
                            }
                        }

                    } catch (IOException e) { }
                });
            }
        }
    }

    private void analyzeBiome(String biome) {
        if (biomes.contains(biome)) {
            biomes.remove(biome);

            futureTasks.add(() -> {
                File file = data.getBiomeLoader().findFile(biome);

                try {
                    JSONObject json = new JSONObject(IO.readAll(file));
                    if (json.keySet().contains("children")) {
                        analyzeBiomeArray(json.getJSONArray("children"));
                    }
                    if (json.keySet().contains("generators")) {
                        JSONArray ogenerators = json.getJSONArray("generators");
                        for (int j = 0; j < ogenerators.length(); j++) {
                            JSONObject genobject = ogenerators.getJSONObject(j);
                            generators.remove(genobject.getString("generator"));
                        }
                    }
                    analyzeCommonDRB(json); //Objects, structures
                    trimExpressions(json);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            });
        }
    }

    private void analyzeBiomeArray(JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            String biome = array.getString(i);

            analyzeBiome(biome);
        }
    }

    private void analyzePoolArray(JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            String pool = array.getString(i);

            if (this.jigsawPools.contains(pool)) {
                this.jigsawPools.remove(pool);

                futureTasks.add(() -> {
                    File file = data.getJigsawPoolLoader().findFile(pool);
                    try {
                        JSONObject json = new JSONObject(IO.readAll(file));

                        if (json.keySet().contains("pieces")) {
                            JSONArray pieces = json.getJSONArray("pieces");

                            for (String piece : convertStringArray(pieces)) {
                                if (this.jigsawPieces.contains(piece)) {
                                    futureTasks.add(() -> {

                                        File pieceFile = data.getJigsawPoolLoader().findFile(pool);
                                        try {
                                            JSONObject pieceJSON = new JSONObject(IO.readAll(pieceFile));
                                            analyzeJigsawObject(pieceJSON);
                                        } catch (IOException e) {

                                        }
                                    });
                                }
                            }
                        }
                    } catch (IOException e) { }
                });
            }

        }
    }

    /**
     * Converts a JSON string array to a java list
     * @param array The JSON string array
     * @return The list
     */
    private List<String> convertStringArray(JSONArray array) {
        List<String> newList = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String s = array.getString(i);
            if (s != null) newList.add(s);
        }
        return newList;
    }

    /**
     * Scans for styles with expressions in them and removes all used expressions
     * @param object The parent json
     */
    private void trimExpressions(JSONObject object) {
        for (String key : object.keySet()) {
            Object o = object.get(key);

            if (key.equalsIgnoreCase("style")) {
                if (o instanceof JSONObject && ((JSONObject) o).keySet().contains("expression")) {
                    this.expressions.remove(((JSONObject) o).getString("expression"));
                }
            }

            if (o instanceof JSONObject) {
                trimExpressions((JSONObject)o);
            } else if (o instanceof JSONArray) {
                JSONArray array = (JSONArray)o;
                for (int i = 0; i < array.length(); i++) {
                    Object o2 = array.get(i);

                    if (o2 instanceof JSONObject) {
                        trimExpressions((JSONObject) o2);
                    }
                }
            }
        }
    }
}
