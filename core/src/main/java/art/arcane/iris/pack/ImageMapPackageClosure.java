package art.arcane.iris.pack;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ImageMapPackageClosure {
    private ImageMapPackageClosure() {
    }

    public static String writeAll(IrisData data, File targetRoot, boolean minify) throws IOException {
        String[] possibleKeys = data.getImageMapLoader().getPossibleKeys();
        Arrays.sort(possibleKeys);
        Set<String> imageKeys = new LinkedHashSet<>();
        StringBuilder hashes = new StringBuilder();
        for (String mapKey : possibleKeys) {
            IrisImageMap map = data.getImageMapLoader().load(mapKey);
            File mapFile = data.getImageMapLoader().findFile(mapKey);
            if (map == null || mapFile == null || !mapFile.isFile()) {
                throw new IOException("Image-map resource '" + mapKey + "' could not be packaged");
            }
            String json = new JSONObject(IO.readAll(mapFile)).toString(minify ? 0 : 4);
            IO.writeAll(new File(targetRoot, "image-maps/" + mapKey + ".json"), json);
            hashes.append(IO.hash(json));
            String source = map.getSource();
            if (source == null || source.isBlank()) {
                throw new IOException("Image-map resource '" + mapKey + "' has no source");
            }
            imageKeys.add(source);
        }
        for (String imageKey : imageKeys) {
            File imageFile = data.getImageLoader().findFile(imageKey);
            if (imageFile == null || !imageFile.isFile()) {
                throw new IOException("Image-map source '" + imageKey + "' could not be packaged");
            }
            IO.copyFile(imageFile, new File(targetRoot, "images/" + imageKey + ".png"));
            hashes.append(IO.hash(imageFile));
        }
        return IO.hash(hashes.toString());
    }
}
