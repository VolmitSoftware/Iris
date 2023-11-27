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

package com.volmit.iris.engine.object;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import lombok.Data;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
public class TileBanner implements TileData<Banner> {
    public static final int id = 2;

    private List<Pattern> patterns = new ArrayList<>();
    private DyeColor baseColor;

    @Override
    public String getTileId() {
        return "minecraft:banner";
    }

    @Override
    public boolean isApplicable(BlockData data) {
        return isBanner(data.getMaterial());
    }

    @Override
    public void toBukkit(Banner banner) {
        banner.setPatterns(patterns);
        banner.setBaseColor(baseColor);
    }

    @Override
    public void fromBukkit(Banner banner) {
        this.patterns = banner.getPatterns();
        this.baseColor = banner.getBaseColor();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public TileBanner clone() {
        TileBanner ts = new TileBanner();
        ts.setBaseColor(getBaseColor());
        ts.setPatterns(getPatterns());
        return ts;
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        out.writeByte(baseColor.ordinal());
        out.writeByte(patterns.size());
        for (Pattern p : patterns) {
            out.writeByte(p.getColor().ordinal());
            out.writeByte(p.getPattern().ordinal());
        }
    }

    @Override
    public void fromBinary(DataInputStream in) throws IOException {
        baseColor = DyeColor.values()[in.readByte()];
        int listSize = in.readByte();
        patterns = new ArrayList<>();

        for (int i = 0; i < listSize; i++) {
            DyeColor color = DyeColor.values()[in.readByte()];
            PatternType type = PatternType.values()[in.readByte()];
            patterns.add(new Pattern(color, type));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public CompoundTag toNBT(CompoundTag tag) {
        @SuppressWarnings("unchecked") ListTag<CompoundTag> listTag = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        for (Pattern p : patterns) {
            CompoundTag pattern = new CompoundTag();
            pattern.putString("Pattern", p.getPattern().getIdentifier());
            pattern.putByte("Color", p.getColor().getDyeData());
            listTag.add(pattern);
        }
        tag.put("Patterns", listTag);
        return tag;
    }

    public boolean isBanner(Material material) {
        return switch (material) {
            case RED_BANNER, RED_WALL_BANNER, ORANGE_BANNER, ORANGE_WALL_BANNER, YELLOW_BANNER, YELLOW_WALL_BANNER, LIME_BANNER, LIME_WALL_BANNER, GREEN_BANNER, GREEN_WALL_BANNER, CYAN_BANNER, CYAN_WALL_BANNER, LIGHT_BLUE_BANNER, LIGHT_BLUE_WALL_BANNER, BLUE_BANNER, BLUE_WALL_BANNER, PURPLE_BANNER, PURPLE_WALL_BANNER, MAGENTA_BANNER, MAGENTA_WALL_BANNER, PINK_BANNER, PINK_WALL_BANNER, WHITE_BANNER, WHITE_WALL_BANNER, LIGHT_GRAY_BANNER, LIGHT_GRAY_WALL_BANNER, GRAY_BANNER, GRAY_WALL_BANNER, BLACK_BANNER, BLACK_WALL_BANNER, BROWN_BANNER, BROWN_WALL_BANNER ->
                    true;
            default -> false;
        };
    }
}
