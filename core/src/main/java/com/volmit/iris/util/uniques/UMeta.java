/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
