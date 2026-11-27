package dev.overflight.forge17;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;

/**
 * Server command: the sky as everyone sees it. A different root from the client's own /overflight, which answers for
 * the player who typed it and works on any server. /airspace spawn type [fl] [heading] [smoking|burning]
 * | convoy count type [formation] | clear.
 */
public final class AirspaceCommand extends CommandBase {

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();

    @Override
    public String getCommandName() {
        return "airspace";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/airspace spawn <type> [flightLevel] [heading] [smoking|burning] | convoy <count> <type> [line|vee|echelon] | clear";
    }

    /** The bar of /time and /weather. */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "spawn", "convoy", "clear");
        }
        if (args.length == 2 && "spawn".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, typeIds());
        }
        if (args.length == 3 && "convoy".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, typeIds());
        }
        if (args.length == 4 && "convoy".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "line", "vee", "echelon");
        }
        if (args.length == 5 && "spawn".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "smoking", "burning");
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if ("clear".equals(args[0])) {
            AirspaceNet.CHANNEL.sendToAll(AirspaceMessage.clearAll());
            say(sender, "Cleared hand-placed aircraft for everyone.");
        } else if ("spawn".equals(args[0]) && args.length >= 2) {
            AirspaceMessage message = request(sender, args[1]);
            if (args.length > 2) {
                message.flightLevel = parseIntBounded(sender, args[2], 50, 700);
            }
            if (args.length > 3) {
                message.headingDeg = parseDoubleBounded(sender, args[3], 0.0, 360.0);
            }
            if (args.length > 4) {
                if ("smoking".equals(args[4])) {
                    message.condition = Flight.Condition.SMOKING.ordinal();
                } else if ("burning".equals(args[4])) {
                    message.condition = Flight.Condition.BURNING.ordinal();
                } else {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
            }
            AirspaceNet.CHANNEL.sendToAll(message);
            say(sender, "Put a " + message.typeId + " over the sky for everyone.");
        } else if ("convoy".equals(args[0]) && args.length >= 3) {
            AirspaceMessage message = request(sender, args[2]);
            message.count = parseIntBounded(sender, args[1], 1, 24);
            message.formation = args.length > 3 ? args[3] : "vee";
            AirspaceNet.CHANNEL.sendToAll(message);
            say(sender, "Put a " + message.formation + " of " + message.count + " " + message.typeId
                + " over the sky for everyone.");
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static AirspaceMessage request(ICommandSender sender, String typeId) {
        if (CATALOG.byId(typeId) == null) {
            throw new WrongUsageException("No such aircraft type: " + typeId);
        }
        AirspaceMessage message = new AirspaceMessage();
        message.action = AirspaceMessage.ACTION_SPAWN;
        message.typeId = typeId;
        ChunkCoordinates at = sender.getPlayerCoordinates();
        message.overX = at.posX + 0.5;
        message.overZ = at.posZ + 0.5;
        message.headingDeg = 90.0;
        message.formation = "line";
        return message;
    }

    private static String[] typeIds() {
        List<String> ids = new ArrayList<String>();
        for (AircraftType type : CATALOG.types()) {
            ids.add(type.id);
        }
        return ids.toArray(new String[0]);
    }

    private static void say(ICommandSender sender, String text) {
        ChatComponentText line = new ChatComponentText(text);
        line.getChatStyle().setColor(EnumChatFormatting.AQUA);
        sender.addChatMessage(line);
    }
}
