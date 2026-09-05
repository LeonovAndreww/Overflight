package dev.overflight.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
        String formation) implements CustomPacketPayload {

    public static final int ACTION_SPAWN = 0;
    public static final int ACTION_CLEAR = 1;

    // createType() takes a path and puts it under minecraft:, so a mod has to
    // build the identifier itself or end up with a colon inside the path.
    public static final CustomPacketPayload.Type<AirspacePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(OverflightCommon.MOD_ID, "airspace"));

    public static final StreamCodec<FriendlyByteBuf, AirspacePayload> CODEC =
            CustomPacketPayload.codec(AirspacePayload::write, AirspacePayload::new);

    private AirspacePayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(64), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(), buffer.readDouble(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUtf(16));
    }

    private void write(FriendlyByteBuf buffer) {
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

    @Override
    public CustomPacketPayload.Type<AirspacePayload> type() {
        return TYPE;
    }
}
