package com.volmit.iris.util.uniques;

import com.google.gson.GsonBuilder;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.IO;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Data
@NoArgsConstructor
public class UMeta {
    private transient BufferedImage image;
    private KMap<String, UFeatureMeta> features;
    private long id;
    private double time;
    private int width;
    private int height;

    public void registerFeature(String key, UFeatureMeta feature) {
        if (features == null) {
            features = new KMap<>();
        }

        features.put(key, feature);
    }

    public void export(File destination) throws IOException {

        for (String i : features.k()) {
            if (features.get(i).isEmpty()) {
                features.remove(i);
            }
        }

        width = image.getWidth();
        height = image.getHeight();
        ImageIO.write(image, "PNG", destination);
        IO.writeAll(new File(destination.getParentFile(), destination.getName() + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(this));
    }
}
