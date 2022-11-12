/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.pack;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.jobs.DownloadJob;
import com.volmit.iris.util.scheduling.jobs.JobCollection;
import com.volmit.iris.util.scheduling.jobs.SingleJob;
import lombok.Builder;
import lombok.Data;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.UUID;

@Data
@Builder
public class IrisPackRepository {
    @Builder.Default
    private String user = "IrisDimensions";

    @Builder.Default
    private String repo = "overworld";

    @Builder.Default
    private String branch = "master";

    @Builder.Default
    private String tag = "";

    /**
     *
     */
    public static IrisPackRepository from(String g) {
        // https://github.com/IrisDimensions/overworld
        if (g.startsWith("https://github.com/")) {
            String sub = g.split("\\Qgithub.com/\\E")[1];
            IrisPackRepository r = IrisPackRepository.builder()
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
                return IrisPackRepository.builder()
                        .user(f[0])
                        .repo(f[1])
                        .build();
            } else if (f.length >= 3) {
                IrisPackRepository r = IrisPackRepository.builder()
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
            return IrisPackRepository.builder()
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

    public void install(VolmitSender sender, Runnable whenComplete) throws MalformedURLException {
        File pack = Iris.instance.getDataFolderNoCreate(StudioSVC.WORKSPACE_NAME, getRepo());

        if (!pack.exists()) {
            File dl = new File(Iris.getTemp(), "dltk-" + UUID.randomUUID() + ".zip");
            File work = new File(Iris.getTemp(), "extk-" + UUID.randomUUID());
            new JobCollection(Form.capitalize(getRepo()),
                    new DownloadJob(toURL(), pack),
                    new SingleJob("Extracting", () -> ZipUtil.unpack(dl, work)),
                    new SingleJob("Installing", () -> {
                        try {
                            FileUtils.copyDirectory(work.listFiles()[0], pack);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    })).execute(sender, whenComplete);
        } else {
            sender.sendMessage("Pack already exists!");
        }
    }
}
