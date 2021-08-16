/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IrisProjectRepo {
    @Builder.Default
    private String user = "IrisDimensions";

    @Builder.Default
    private String repo = "overworld";

    @Builder.Default
    private String branch = "master";

    @Builder.Default
    private String tag = "";

    public static IrisProjectRepo from(String g) {
        // https://github.com/IrisDimensions/overworld
        if (g.startsWith("https://github.com/")) {
            String sub = g.split("\\Qgithub.com/\\E")[1];
            IrisProjectRepo r = IrisProjectRepo.builder()
                    .user(sub.split("\\Q/\\E")[0])
                    .repo(sub.split("\\Q/\\E")[1]).build();

            if (g.contains("/tree/")) {
                r.setBranch(g.split("/tree/")[1]);
            }

            return r;
        } else if (g.contains("/")) {
            String[] f = g.split("\\Q/\\E");

            if (f.length == 1) {
                return from(g);
            } else if (f.length == 2) {
                return IrisProjectRepo.builder()
                        .user(f[0])
                        .repo(f[1])
                        .build();
            } else if (f.length >= 3) {
                IrisProjectRepo r = IrisProjectRepo.builder()
                        .user(f[0])
                        .repo(f[1])
                        .build();

                if (f[2].startsWith("#")) {
                    r.setTag(f[2].substring(1));
                } else {
                    r.setBranch(f[2]);
                }

                return r;
            }
        } else {
            return IrisProjectRepo.builder()
                    .user("IrisDimensions")
                    .repo(g)
                    .branch(g.equals("overworld") ? "stable" : "master")
                    .build();
        }

        return null;
    }

    public String toURL() {
        if (!tag.trim().isEmpty()) {
            return "https://codeload.github.com/" + user + "/" + repo + "/zip/refs/tags/" + tag;
        }

        return "https://codeload.github.com/" + user + "/" + repo + "/zip/refs/heads/" + branch;
    }
}
