package dev.overflight.forge17;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import dev.overflight.core.atmo.Isa;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.ManualTraffic;

/**
 * Client-side debug command, registered through ClientCommandHandler so it works on any server.
 * /overflight status | spawn [type] [fl] [smoking|burning] | clear | types
 */
public final class OverflightCommand extends CommandBase {

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();

    @Override
    public String getCommandName() {
        return "overflight";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/overflight status | spawn [type] [flightLevel] [smoking|burning] | clear | types";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "spawn", "clear", "types");
        }
        if (args.length == 2 && "spawn".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, typeIds());
        }
        if (args.length == 4 && "spawn".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "smoking", "burning");
        }
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        SkyRenderer renderer = SkyRenderer.get();
        switch (args[0]) {
            case "status":
                status(sender, renderer);
                break;
            case "types":
                say(sender, "Aircraft types: " + String.join(", ", typeIds()), EnumChatFormatting.GRAY);
                break;
            case "clear":
                say(sender, "Removed " + renderer.manualTraffic().clear() + " manual flight(s).", EnumChatFormatting.YELLOW);
                break;
            case "spawn":
                spawn(sender, renderer, args);
                break;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static void status(ICommandSender sender, SkyRenderer renderer) {
        say(sender, "Overflight status", EnumChatFormatting.AQUA);
        say(sender, String.format(Locale.ROOT, "  density: %.1f flights/hour per 1000x1000 km", renderer.config().traffic.densityPerHour),
            EnumChatFormatting.WHITE);
        say(sender, "  manual flights: " + renderer.manualTraffic().size(), EnumChatFormatting.WHITE);
        say(sender, "  aircraft in last frame: " + renderer.lastFlightCount() + ", quads: " + renderer.lastQuadCount(),
            EnumChatFormatting.WHITE);
    }

    private static void spawn(ICommandSender sender, SkyRenderer renderer, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            say(sender, "Not in a world.", EnumChatFormatting.RED);
            return;
        }
        String typeId = args.length > 1 ? args[1] : "airliner_narrowbody";
        AircraftType type = CATALOG.byId(typeId);
        if (type == null) {
            say(sender, "No such aircraft type: " + typeId + ". Try /overflight types", EnumChatFormatting.RED);
            return;
        }
        int level = (type.minFlightLevel + type.maxFlightLevel) / 2;
        if (args.length > 2) {
            level = parseIntBounded(sender, args[2], 50, 700);
        }
        Flight.Condition condition = Flight.Condition.NORMAL;
        if (args.length > 3) {
            if ("smoking".equals(args[3])) {
                condition = Flight.Condition.SMOKING;
            } else if ("burning".equals(args[3])) {
                condition = Flight.Condition.BURNING;
            } else {
                throw new WrongUsageException(new OverflightCommand().getCommandUsage(sender));
            }
        }
        double altitude = Isa.flightLevelToMetres(level);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        double timeS = mc.theWorld.getTotalWorldTime() / 20.0;
        // Comes in from the west so it crosses the sky rather than receding.
        renderer.manualTraffic().add(ManualTraffic.overhead(System.nanoTime(), type, mc.thePlayer.posX, mc.thePlayer.posZ,
            altitude, speed, Math.toRadians(90.0), timeS, 12000.0, 420.0, 3600.0, condition));
        say(sender, String.format(Locale.ROOT, "Spawned %s at FL%d (%.0f m/s), overhead in %d s. Look west and up.", type.id, level,
            speed, Math.round(12000.0 / speed)), EnumChatFormatting.AQUA);
    }

    private static String[] typeIds() {
        List<String> ids = new ArrayList<String>();
        for (AircraftType type : CATALOG.types()) {
            ids.add(type.id);
        }
        return ids.toArray(new String[0]);
    }

    private static void say(ICommandSender sender, String text, EnumChatFormatting colour) {
        ChatComponentText line = new ChatComponentText(text);
        line.getChatStyle().setColor(colour);
        sender.addChatMessage(line);
    }
}
