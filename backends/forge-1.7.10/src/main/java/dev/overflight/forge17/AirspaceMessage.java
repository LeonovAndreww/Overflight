package dev.overflight.forge17;

import io.netty.buffer.ByteBuf;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * What a server tells its clients about the sky: only the request for a hand-placed flight (type, place, heading),
 * from which each client builds the same flight. Ambient traffic follows from the dimension and the clock and never
 * travels.
 */
public class AirspaceMessage implements IMessage {

    public static final int ACTION_SPAWN = 0;
    public static final int ACTION_CLEAR = 1;

    public int action;
    public String typeId = "";
    public double overX;
    public double overZ;
    public int flightLevel;
    public double headingDeg;
    public int condition;
    public int count = 1;
    public String formation = "line";

    /** Required by the network wrapper. */
    public AirspaceMessage() {}

    public static AirspaceMessage clearAll() {
        AirspaceMessage message = new AirspaceMessage();
        message.action = ACTION_CLEAR;
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readInt();
        typeId = ByteBufUtils.readUTF8String(buf);
        overX = buf.readDouble();
        overZ = buf.readDouble();
        flightLevel = buf.readInt();
        headingDeg = buf.readDouble();
        condition = buf.readInt();
        count = buf.readInt();
        formation = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, typeId);
        buf.writeDouble(overX);
        buf.writeDouble(overZ);
        buf.writeInt(flightLevel);
        buf.writeDouble(headingDeg);
        buf.writeInt(condition);
        buf.writeInt(count);
        ByteBufUtils.writeUTF8String(buf, formation);
    }
}
