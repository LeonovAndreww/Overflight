package dev.overflight.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * What a server tells its clients about the sky.
 *
 * Ambient traffic needs none of this -- it follows from the dimension and the
 * game clock, which every client already has. Only aircraft someone asked for by
 * hand have to travel, and then only as the request itself: a type, a place and
 * a heading, from which each client builds the same flight. Positions are never
 * sent, and nothing is streamed.
 */
public record AirspacePayload(
        int action,
        String typeId,
        double overX,
        double overZ,
        int flightLevel,
        double headingDeg,
        int condition,
        int count,
        String formation) {

    public static final int ACTION_SPAWN = 0;
    public static final int ACTION_CLEAR = 1;

    /** The channel the payload travels on: 1.19.4 has no payload types, only identifiers. */
    public static final ResourceLocation TYPE =
            new ResourceLocation(OverflightCommon.MOD_ID, "airspace");

    public static AirspacePayload read(FriendlyByteBuf buffer) {
        return new AirspacePayload(buffer);
    }

    public FriendlyByteBuf toBuffer() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buffer);
        return buffer;
    }

    private AirspacePayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(64), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUtf(16));
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        buffer.writeUtf(typeId, 64);
        buffer.writeDouble(overX);
        buffer.writeDouble(overZ);
        buffer.writeVarInt(flightLevel);
        buffer.writeDouble(headingDeg);
        buffer.writeVarInt(condition);
        buffer.writeVarInt(count);
        buffer.writeUtf(formation, 16);
    }

    public static AirspacePayload clearAll() {
        return new AirspacePayload(ACTION_CLEAR, "", 0.0, 0.0, 0, 0.0, 0, 0, "");
    }
}
