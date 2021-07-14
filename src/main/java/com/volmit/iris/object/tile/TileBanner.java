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

package com.volmit.iris.object.tile;

import com.volmit.iris.scaffold.data.nbt.tag.CompoundTag;
import com.volmit.iris.scaffold.data.nbt.tag.ListTag;
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

    private List<Pattern> patterns = new ArrayList<Pattern>();
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

    @Override
    public void toNBT(CompoundTag tag) {
        ListTag<CompoundTag> listTag = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        for (Pattern p : patterns) {
            CompoundTag pattern = new CompoundTag();
            pattern.putString("Pattern", p.getPattern().getIdentifier());
            pattern.putByte("Color", p.getColor().getDyeData());
            listTag.add(pattern);
        }
        tag.put("Patterns", listTag);
    }

    public boolean isBanner(Material material) {
        switch (material) {

            case RED_BANNER:
            case RED_WALL_BANNER:
            case ORANGE_BANNER:
            case ORANGE_WALL_BANNER:
            case YELLOW_BANNER:
            case YELLOW_WALL_BANNER:
            case LIME_BANNER:
            case LIME_WALL_BANNER:
            case GREEN_BANNER:
            case GREEN_WALL_BANNER:
            case CYAN_BANNER:
            case CYAN_WALL_BANNER:
            case LIGHT_BLUE_BANNER:
            case LIGHT_BLUE_WALL_BANNER:
            case BLUE_BANNER:
            case BLUE_WALL_BANNER:
            case PURPLE_BANNER:
            case PURPLE_WALL_BANNER:
            case MAGENTA_BANNER:
            case MAGENTA_WALL_BANNER:
            case PINK_BANNER:
            case PINK_WALL_BANNER:
            case WHITE_BANNER:
            case WHITE_WALL_BANNER:
            case LIGHT_GRAY_BANNER:
            case LIGHT_GRAY_WALL_BANNER:
            case GRAY_BANNER:
            case GRAY_WALL_BANNER:
            case BLACK_BANNER:
            case BLACK_WALL_BANNER:
            case BROWN_BANNER:
            case BROWN_WALL_BANNER:
                return true;
            default:
                return false;
        }
    }
}
