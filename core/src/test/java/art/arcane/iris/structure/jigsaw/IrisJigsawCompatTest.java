/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.structure.jigsaw;

import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.structure.placement.IrisStructure;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.loading.IrisData;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IrisJigsawCompatTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CompatTestPlatform platform;
    private IrisSettings previousSettings;
    private IrisData data;

    @Before
    public void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        platform = CompatTestPlatform.bind(temporaryFolder.getRoot(),
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:air"));
    }

    @After
    public void restorePlatform() {
        if (data != null) {
            data.close();
            data = null;
        }
        if (platform != null) {
            platform.unbind();
            platform = null;
        }
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void aPieceWhoseObjectNeedsAMissingBlockIsExcluded() throws Exception {
        File pack = pack("piece");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        CompatTestPlatform.write(pack, "jigsaw-pieces/bad.json", "{\"object\":\"bad\"}");
        data = IrisData.get(pack);

        IrisJigsawPiece piece = data.load(IrisJigsawPiece.class, "bad", false);

        assertNotNull(piece);
        assertTrue(piece.isCompatExcluded());
        assertTrue(data.getCompatReport().findings().stream()
                .anyMatch(finding -> "minecraft:sulfur".equals(finding.key())
                        && finding.action() == CompatAction.EXCLUDED));
    }

    @Test
    public void aPieceWhoseObjectResolvesIsKept() throws Exception {
        File pack = pack("piece-ok");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        CompatTestPlatform.write(pack, "jigsaw-pieces/good.json", "{\"object\":\"good\"}");
        data = IrisData.get(pack);

        IrisJigsawPiece piece = data.load(IrisJigsawPiece.class, "good", false);

        assertNotNull(piece);
        assertFalse(piece.isCompatExcluded());
    }

    @Test
    public void aPoolKeepsGeneratingWhileOnePieceSurvives() throws Exception {
        File pack = pack("pool-partial");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        CompatTestPlatform.write(pack, "jigsaw-pieces/good.json", "{\"object\":\"good\"}");
        CompatTestPlatform.write(pack, "jigsaw-pieces/bad.json", "{\"object\":\"bad\"}");
        CompatTestPlatform.write(pack, "jigsaw-pools/pool.json",
                "{\"pieces\":[{\"piece\":\"good\",\"weight\":1},{\"piece\":\"bad\",\"weight\":1}]}");
        data = IrisData.get(pack);

        IrisJigsawPool pool = data.load(IrisJigsawPool.class, "pool", false);

        assertNotNull(pool);
        assertFalse(pool.isCompatExcluded());
        assertTrue(data.getCompatReport().findings().stream()
                .anyMatch(finding -> finding.action() == CompatAction.DROPPED
                        && "jigsaw pool".equals(finding.subjectType())));
    }

    @Test
    public void pieceExclusionCascadesToThePoolAndTheStructure() throws Exception {
        File pack = pack("cascade");
        CompatTestPlatform.writeObject(pack, "bad", "minecraft:sulfur");
        CompatTestPlatform.write(pack, "jigsaw-pieces/bad.json", "{\"object\":\"bad\"}");
        CompatTestPlatform.write(pack, "jigsaw-pools/pool.json",
                "{\"pieces\":[{\"piece\":\"bad\",\"weight\":1}]}");
        CompatTestPlatform.write(pack, "structures/keep.json", "{\"startPool\":\"pool\"}");
        data = IrisData.get(pack);

        IrisJigsawPool pool = data.load(IrisJigsawPool.class, "pool", false);
        IrisStructure structure = data.load(IrisStructure.class, "keep", false);

        assertNotNull(pool);
        assertNotNull(structure);
        assertTrue(pool.isCompatExcluded());
        assertTrue(structure.isCompatExcluded());
        assertTrue(data.getCompatReport().findings().stream()
                .anyMatch(finding -> "structure".equals(finding.subjectType())
                        && finding.action() == CompatAction.EXCLUDED
                        && "minecraft:sulfur".equals(finding.key())));
    }

    @Test
    public void aStructureOverAHealthyPoolIsKept() throws Exception {
        File pack = pack("structure-ok");
        CompatTestPlatform.writeObject(pack, "good", "minecraft:stone");
        CompatTestPlatform.write(pack, "jigsaw-pieces/good.json", "{\"object\":\"good\"}");
        CompatTestPlatform.write(pack, "jigsaw-pools/pool.json",
                "{\"pieces\":[{\"piece\":\"good\",\"weight\":1}]}");
        CompatTestPlatform.write(pack, "structures/keep.json", "{\"startPool\":\"pool\"}");
        data = IrisData.get(pack);

        IrisStructure structure = data.load(IrisStructure.class, "keep", false);

        assertNotNull(structure);
        assertFalse(structure.isCompatExcluded());
        assertTrue(data.getCompatReport().isEmpty());
    }

    private File pack(String name) throws Exception {
        return temporaryFolder.newFolder(name);
    }
}
