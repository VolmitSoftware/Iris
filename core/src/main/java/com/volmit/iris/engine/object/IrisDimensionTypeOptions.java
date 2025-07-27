package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.json.JSONObject;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

import static com.volmit.iris.engine.object.IrisDimensionTypeOptions.TriState.*;

@Data
@NoArgsConstructor
@Accessors(fluent = true, chain = true)
@Desc("Optional options for the dimension type")
public class IrisDimensionTypeOptions {
    @MinNumber(0.00001)
    @MaxNumber(30000000)
    @Desc("The multiplier applied to coordinates when leaving the dimension. Value between 0.00001 and 30000000.0 (both inclusive).")
    private double coordinateScale = -1;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much light the dimension has. When set to 0, it completely follows the light level; when set to 1, there is no ambient lighting.")
    private float ambientLight = -1;
    @Nullable
    @MinNumber(0)
    @MaxNumber(Long.MAX_VALUE)
    @Desc("If this is set to an int, the time of the day is the specified value. To ensure a normal time cycle, set it to null.")
    private Long fixedTime = -1L;
    @Nullable
    @MinNumber(-2032)
    @MaxNumber(2031)
    @Desc("Optional value between -2032 and 2031. If set, determines the lower edge of the clouds. If set to null, clouds are disabled in the dimension.")
    private Integer cloudHeight = -1;
    @MinNumber(0)
    @MaxNumber(15)
    @Desc("Value between 0 and 15 (both inclusive). Maximum block light required when the monster spawns.")
    private int monsterSpawnBlockLightLimit = -1;

    @NonNull
    @Desc("Whether the dimensions behaves like the nether (water evaporates and sponges dry) or not. Also lets stalactites drip lava and causes lava to spread faster and thinner.")
    private TriState ultrawarm = DEFAULT;
    @NonNull
    @Desc("When false, compasses spin randomly, and using a bed to set the respawn point or sleep, is disabled. When true, nether portals can spawn zombified piglins, and creaking hearts can spawn creakings.")
    private TriState natural = DEFAULT;
    @NonNull
    @Desc("When false, Piglins and hoglins shake and transform to zombified entities.")
    private TriState piglinSafe = DEFAULT;
    @NonNull
    @Desc("When false, the respawn anchor blows up when trying to set spawn point.")
    private TriState respawnAnchorWorks = DEFAULT;
    @NonNull
    @Desc("When false, the bed blows up when trying to sleep.")
    private TriState bedWorks = DEFAULT;
    @NonNull
    @Desc("Whether players with the Bad Omen effect can cause a raid.")
    private TriState raids = DEFAULT;
    @NonNull
    @Desc("Whether the dimension has skylight or not.")
    private TriState skylight = DEFAULT;
    @NonNull
    @Desc("Whether the dimension has a bedrock ceiling. Note that this is only a logical ceiling. It is unrelated with whether the dimension really has a block ceiling.")
    private TriState ceiling = DEFAULT;

    public IrisDimensionTypeOptions(
            @NonNull TriState ultrawarm,
            @NonNull TriState natural,
            @NonNull TriState piglinSafe,
            @NonNull TriState respawnAnchorWorks,
            @NonNull TriState bedWorks,
            @NonNull TriState raids,
            @NonNull TriState skylight,
            @NonNull TriState ceiling,
            double coordinateScale,
            float ambientLight,
            @Nullable Long fixedTime,
            @Nullable Integer cloudHeight,
            int monsterSpawnBlockLightLimit
    ) {
        if (coordinateScale != -1 && (coordinateScale < 0.00001 || coordinateScale > 30000000))
            throw new IllegalArgumentException("Coordinate scale must be between 0.00001 and 30000000");
        if (ambientLight != -1 && (ambientLight < 0 || ambientLight > 1))
            throw new IllegalArgumentException("Ambient light must be between 0 and 1");
        if (cloudHeight != null && cloudHeight != -1 && (cloudHeight < -2032 || cloudHeight > 2031))
            throw new IllegalArgumentException("Cloud height must be between -2032 and 2031");
        if (monsterSpawnBlockLightLimit != -1 && (monsterSpawnBlockLightLimit < 0 || monsterSpawnBlockLightLimit > 15))
            throw new IllegalArgumentException("Monster spawn block light limit must be between 0 and 15");

        this.ultrawarm = ultrawarm;
        this.natural = natural;
        this.piglinSafe = piglinSafe;
        this.respawnAnchorWorks = respawnAnchorWorks;
        this.bedWorks = bedWorks;
        this.raids = raids;
        this.skylight = skylight;
        this.ceiling = ceiling;
        this.coordinateScale = coordinateScale;
        this.ambientLight = ambientLight;
        this.fixedTime = fixedTime;
        this.cloudHeight = cloudHeight;
        this.monsterSpawnBlockLightLimit = monsterSpawnBlockLightLimit;
    }

    public IrisDimensionTypeOptions coordinateScale(double coordinateScale) {
        if (coordinateScale != -1 && (coordinateScale < 0.00001 || coordinateScale > 30000000))
            throw new IllegalArgumentException("Coordinate scale must be between 0.00001 and 30000000");
        this.coordinateScale = coordinateScale;
        return this;
    }

    public IrisDimensionTypeOptions ambientLight(float ambientLight) {
        if (ambientLight != -1 && (ambientLight < 0 || ambientLight > 1))
            throw new IllegalArgumentException("Ambient light must be between 0 and 1");
        this.ambientLight = ambientLight;
        return this;
    }

    public IrisDimensionTypeOptions cloudHeight(@Nullable Integer cloudHeight) {
        if (cloudHeight != null && cloudHeight != -1 && (cloudHeight < -2032 || cloudHeight > 2031))
            throw new IllegalArgumentException("Cloud height must be between -2032 and 2031");
        this.cloudHeight = cloudHeight;
        return this;
    }

    public IrisDimensionTypeOptions monsterSpawnBlockLightLimit(int monsterSpawnBlockLightLimit) {
        if (monsterSpawnBlockLightLimit != -1 && (monsterSpawnBlockLightLimit < 0 || monsterSpawnBlockLightLimit > 15))
            throw new IllegalArgumentException("Monster spawn block light limit must be between 0 and 15");
        this.monsterSpawnBlockLightLimit = monsterSpawnBlockLightLimit;
        return this;
    }

    public void write(DataOutput dos) throws IOException {
        int bits = 0;
        int index = 0;

        for (TriState state : new TriState[]{
                ultrawarm,
                natural,
                skylight,
                ceiling,
                piglinSafe,
                bedWorks,
                respawnAnchorWorks,
                raids
        }) {
            if (state == DEFAULT) {
                index++;
                continue;
            }

            bits |= (short) (1 << index++);
            if (state == TRUE)
                bits |= (short) (1 << index++);
        }

        if (coordinateScale != -1)
            bits |= (1 << index++);
        if (ambientLight != -1)
            bits |= (1 << index++);
        if (monsterSpawnBlockLightLimit != -1)
            bits |= (1 << index++);
        if (fixedTime != null) {
            bits |= (1 << index++);
            if (fixedTime != -1L)
                bits |= (1 << index++);
        }
        if (cloudHeight != null) {
            bits |= (1 << index++);
            if (cloudHeight != -1)
                bits |= (1 << index);
        }

        Varint.writeSignedVarInt(bits, dos);

        if (coordinateScale != -1)
            Varint.writeUnsignedVarLong(Double.doubleToLongBits(coordinateScale), dos);
        if (ambientLight != -1)
            Varint.writeUnsignedVarInt(Float.floatToIntBits(ambientLight), dos);
        if (monsterSpawnBlockLightLimit != -1)
            Varint.writeSignedVarInt(monsterSpawnBlockLightLimit, dos);
        if (fixedTime != null && fixedTime != -1L)
            Varint.writeSignedVarLong(fixedTime, dos);
        if (cloudHeight != null && cloudHeight != -1)
            Varint.writeSignedVarInt(cloudHeight, dos);
    }

    public IrisDimensionTypeOptions read(DataInput dis) throws IOException {
        TriState[] states = new TriState[8];
        Arrays.fill(states, DEFAULT);

        int bits = Varint.readSignedVarInt(dis);
        int index = 0;

        for (int i = 0; i < 8; i++) {
            if ((bits & (1 << index++)) == 0)
                continue;
            states[i] = (bits & (1 << index++)) == 0 ? FALSE : TRUE;
        }
        ultrawarm = states[0];
        natural = states[1];
        skylight = states[2];
        ceiling = states[3];
        piglinSafe = states[4];
        bedWorks = states[5];
        respawnAnchorWorks = states[6];
        raids = states[7];

        coordinateScale = (bits & (1 << index++)) != 0 ? Double.longBitsToDouble(Varint.readUnsignedVarLong(dis)) : -1;
        ambientLight = (bits & (1 << index++)) != 0 ? Float.intBitsToFloat(Varint.readUnsignedVarInt(dis)) : -1;
        monsterSpawnBlockLightLimit = (bits & (1 << index++)) != 0 ? Varint.readSignedVarInt(dis) : -1;
        fixedTime = (bits & (1 << index++)) != 0 ? (bits & (1 << index)) != 0 ? Varint.readSignedVarLong(dis) : -1L : null;
        cloudHeight = (bits & (1 << index++)) != 0 ? (bits & (1 << index)) != 0 ? Varint.readSignedVarInt(dis) : -1 : null;

        return this;
    }

    public IrisDimensionTypeOptions resolve(IrisDimensionTypeOptions other) {
        if (ultrawarm == DEFAULT)
            ultrawarm = other.ultrawarm;
        if (natural == DEFAULT)
            natural = other.natural;
        if (piglinSafe == DEFAULT)
            piglinSafe = other.piglinSafe;
        if (respawnAnchorWorks == DEFAULT)
            respawnAnchorWorks = other.respawnAnchorWorks;
        if (bedWorks == DEFAULT)
            bedWorks = other.bedWorks;
        if (raids == DEFAULT)
            raids = other.raids;
        if (skylight == DEFAULT)
            skylight = other.skylight;
        if (ceiling == DEFAULT)
            ceiling = other.ceiling;
        if (coordinateScale == -1)
            coordinateScale = other.coordinateScale;
        if (ambientLight == -1)
            ambientLight = other.ambientLight;
        if (fixedTime != null && fixedTime == -1L)
            fixedTime = other.fixedTime;
        if (cloudHeight != null && cloudHeight == -1)
            cloudHeight = other.cloudHeight;
        if (monsterSpawnBlockLightLimit == -1)
            monsterSpawnBlockLightLimit = other.monsterSpawnBlockLightLimit;
        return this;
    }

    public JSONObject toJson() {
        if (!isComplete()) throw new IllegalStateException("Cannot serialize incomplete options");
        JSONObject json = new JSONObject();
        json.put("ultrawarm", ultrawarm.bool());
        json.put("natural", natural.bool());
        json.put("piglin_safe", piglinSafe.bool());
        json.put("respawn_anchor_works", respawnAnchorWorks.bool());
        json.put("bed_works", bedWorks.bool());
        json.put("has_raids", raids.bool());
        json.put("has_skylight", skylight.bool());
        json.put("has_ceiling", ceiling.bool());
        json.put("coordinate_scale", coordinateScale);
        json.put("ambient_light", ambientLight);
        json.put("monster_spawn_block_light_limit", monsterSpawnBlockLightLimit);
        if (fixedTime != null) json.put("fixed_time", fixedTime);
        if (cloudHeight != null) json.put("cloud_height", cloudHeight);
        return json;
    }

    public IrisDimensionTypeOptions copy() {
        return new IrisDimensionTypeOptions(
                ultrawarm,
                natural,
                piglinSafe,
                respawnAnchorWorks,
                bedWorks,
                raids,
                skylight,
                ceiling,
                coordinateScale,
                ambientLight,
                fixedTime,
                cloudHeight,
                monsterSpawnBlockLightLimit
        );
    }

    public boolean isComplete() {
        return ultrawarm != DEFAULT
                && natural != DEFAULT
                && piglinSafe != DEFAULT
                && respawnAnchorWorks != DEFAULT
                && bedWorks != DEFAULT
                && raids != DEFAULT
                && skylight != DEFAULT
                && ceiling != DEFAULT
                && coordinateScale != -1
                && ambientLight != -1
                && monsterSpawnBlockLightLimit != -1
                && (fixedTime == null || fixedTime != -1L)
                && (cloudHeight == null || cloudHeight != -1);
    }

    @Desc("Allows reusing the behavior of the base dimension")
    public enum TriState {
        @Desc("Follow the behavior of the base dimension")
        DEFAULT,
        @Desc("True")
        TRUE,
        @Desc("False")
        FALSE;

        public boolean bool() {
            return this == TRUE;
        }
    }
}
