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
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.ManualTraffic;

/**
 * Client-side debug command, registered through ClientCommandHandler so it works on any server.
 * /overflight status | list | probe [flightLevel] | spawn [type] [fl] [smoking|burning] | clear | types
 */
public final class OverflightCommand extends CommandBase {

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();

    @Override
    public String getCommandName() {
        return "overflight";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/overflight status | list | probe [flightLevel] | spawn [type] [flightLevel] [smoking|burning] | clear | types | get | set <option> <value> | preset <name>";
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
            return getListOfStringsMatchingLastWord(args, "status", "list", "probe", "spawn", "clear", "types", "get", "set", "preset");
        }
        if (args.length == 2 && "set".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, OPTIONS.keySet().toArray(new String[0]));
        }
        if (args.length == 2 && "preset".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "realistic", "fancy", "chemtrail", "custom");
        }
        if (args.length == 3 && "set".equals(args[0]) && OPTIONS.get(args[1]) == Boolean.class) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
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
            case "list":
                list(sender, renderer);
                break;
            case "probe":
                probe(sender, renderer, args);
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
            case "get":
                get(sender, renderer);
                break;
            case "set":
                set(sender, renderer, args);
                break;
            case "preset":
                preset(sender, renderer, args);
                break;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    /** Options reachable from chat: name -> type. Look options leave the look preset, traffic ones the traffic preset. */
    private static final java.util.Map<String, Class<?>> OPTIONS = new java.util.LinkedHashMap<String, Class<?>>();
    static {
        OPTIONS.put("enabled", Boolean.class);
        OPTIONS.put("density", Double.class);
        OPTIONS.put("maxAircraft", Integer.class);
        OPTIONS.put("opacity", Double.class);
        OPTIONS.put("maxSkyCover", Double.class);
        OPTIONS.put("persistence", Double.class);
        OPTIONS.put("trailDetail", Integer.class);
        OPTIONS.put("detailedTrails", Boolean.class);
        OPTIONS.put("drawInSky", Boolean.class);
        OPTIONS.put("visibleRangeKm", Double.class);
    }

    private static Object read(dev.overflight.core.config.OverflightConfig c, String key) {
        switch (key) {
            case "enabled": return c.enabled;
            case "density": return c.traffic.densityPerHour;
            case "maxAircraft": return c.traffic.maxAircraft;
            case "opacity": return c.trails.opacity;
            case "maxSkyCover": return c.trails.maxSkyCover;
            case "persistence": return c.trails.persistenceMultiplier;
            case "trailDetail": return c.graphics.trailDetail;
            case "detailedTrails": return c.graphics.detailedTrails;
            case "drawInSky": return c.graphics.drawInSky;
            case "visibleRangeKm": return c.graphics.visibleRangeM / 1000.0;
            default: return null;
        }
    }

    private static void write(dev.overflight.core.config.OverflightConfig c, String key, Object v) {
        switch (key) {
            case "enabled": c.enabled = (Boolean) v; break;
            case "density": c.traffic.densityPerHour = (Double) v; c.traffic.preset = "custom"; break;
            case "maxAircraft": c.traffic.maxAircraft = (Integer) v; c.traffic.preset = "custom"; break;
            case "opacity": c.trails.opacity = (Double) v; c.preset = "custom"; break;
            case "maxSkyCover": c.trails.maxSkyCover = (Double) v; c.preset = "custom"; break;
            case "persistence": c.trails.persistenceMultiplier = (Double) v; c.preset = "custom"; break;
            case "trailDetail": c.graphics.trailDetail = (Integer) v; c.preset = "custom"; break;
            case "detailedTrails": c.graphics.detailedTrails = (Boolean) v; break;
            case "drawInSky": c.graphics.drawInSky = (Boolean) v; break;
            case "visibleRangeKm": c.graphics.visibleRangeM = (Double) v * 1000.0; break;
            default: break;
        }
    }

    private static void get(ICommandSender sender, SkyRenderer renderer) {
        dev.overflight.core.config.OverflightConfig c = renderer.config();
        say(sender, "Overflight settings (look preset: " + c.preset + ", traffic preset: " + c.traffic.preset + ")",
            EnumChatFormatting.AQUA);
        for (String key : OPTIONS.keySet()) {
            say(sender, "  " + key + " = " + read(c, key), EnumChatFormatting.WHITE);
        }
    }

    private static void set(ICommandSender sender, SkyRenderer renderer, String[] args) {
        if (args.length != 3 || !OPTIONS.containsKey(args[1])) {
            throw new WrongUsageException("/overflight set <" + String.join("|", OPTIONS.keySet()) + "> <value>");
        }
        Class<?> type = OPTIONS.get(args[1]);
        Object value;
        if (type == Boolean.class) {
            if (!"true".equals(args[2]) && !"false".equals(args[2])) {
                throw new WrongUsageException("Expected true or false, got " + args[2]);
            }
            value = Boolean.valueOf(args[2]);
        } else if (type == Integer.class) {
            value = Integer.valueOf(parseInt(sender, args[2]));
        } else {
            value = Double.valueOf(parseDouble(sender, args[2]));
        }
        dev.overflight.core.config.OverflightConfig c = renderer.config();
        write(c, args[1], value);
        c.sanitise();
        renderer.applyConfig(c);
        ConfigIo.save(c);
        say(sender, args[1] + " = " + read(c, args[1]), EnumChatFormatting.GREEN);
    }

    private static void preset(ICommandSender sender, SkyRenderer renderer, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException("/overflight preset <realistic|fancy|chemtrail|custom>");
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!java.util.Arrays.asList("realistic", "fancy", "chemtrail", "custom").contains(name)) {
            throw new WrongUsageException("Unknown preset " + args[1] + ". Try realistic, fancy, chemtrail or custom.");
        }
        dev.overflight.core.config.OverflightConfig c = renderer.config();
        c.preset = name;
        c.applyPreset().sanitise();
        renderer.applyConfig(c);
        ConfigIo.save(c);
        say(sender, "Preset " + name + " applied.", EnumChatFormatting.GREEN);
    }

    private static void status(ICommandSender sender, SkyRenderer renderer) {
        say(sender, "Overflight status", EnumChatFormatting.AQUA);
        say(sender, String.format(Locale.ROOT, "  density: %.1f flights/hour per 1000x1000 km", renderer.config().traffic.densityPerHour),
            EnumChatFormatting.WHITE);
        say(sender, "  manual flights: " + renderer.manualTraffic().size(), EnumChatFormatting.WHITE);
        say(sender, "  aircraft in last frame: " + renderer.lastFlightCount() + ", quads: " + renderer.lastQuadCount(),
            EnumChatFormatting.WHITE);
    }

    private static void list(ICommandSender sender, SkyRenderer renderer) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            say(sender, "Not in a world.", EnumChatFormatting.RED);
            return;
        }
        double timeS = mc.theWorld.getTotalWorldTime() / 20.0;
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY;
        double eyeZ = mc.thePlayer.posZ;
        List<Flight> flights = renderer.collectFlights(mc, timeS);
        say(sender, "Aircraft in range: " + flights.size(), EnumChatFormatting.AQUA);
        if (flights.isEmpty()) {
            say(sender, "  Nothing airborne within " + (int) (renderer.config().graphics.visibleRangeM / 1000.0) + " km.",
                EnumChatFormatting.GRAY);
            return;
        }
        for (int i = 0; i < flights.size(); i++) {
            Flight flight = flights.get(i);
            double x = flight.xAt(timeS);
            double z = flight.zAt(timeS);
            double ground = Math.hypot(x - eyeX, z - eyeZ);
            double bearing = (Math.toDegrees(Math.atan2(x - eyeX, -(z - eyeZ))) + 360.0) % 360.0;
            double elevation = Math.toDegrees(Math.atan2(flight.altitudeM - eyeY, ground));
            double temperature = Isa.temperature(flight.altitudeM);
            double pressure = Isa.pressure(flight.altitudeM);
            double rh = renderer.relativeHumidity(mc, x, z, flight.altitudeM, timeS);
            boolean forms = SchmidtAppleman.formsContrail(temperature, pressure, rh, flight.type.engine);
            boolean persists = SchmidtAppleman.persistenceRatio(temperature, rh) > 1.0;
            String verdict = !forms ? "no trail" : (persists ? "persistent" : "short");
            EnumChatFormatting colour = !forms ? EnumChatFormatting.DARK_GRAY
                : (persists ? EnumChatFormatting.AQUA : EnumChatFormatting.GRAY);
            say(sender, String.format(Locale.ROOT, "  %s FL%03d  %.0f km  %03.0f deg  %.1f deg up  RH %.0f%%  %s",
                flight.type.id, (int) Math.round(flight.altitudeM / 30.48), ground / 1000.0, bearing, elevation, rh * 100.0,
                verdict), colour);
        }
    }

    private static void probe(ICommandSender sender, SkyRenderer renderer, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            say(sender, "Not in a world.", EnumChatFormatting.RED);
            return;
        }
        int level = args.length > 1 ? parseIntBounded(sender, args[1], 50, 700) : 350;
        double timeS = mc.theWorld.getTotalWorldTime() / 20.0;
        double altitude = Isa.flightLevelToMetres(level);
        double temperature = Isa.temperature(altitude);
        double pressure = Isa.pressure(altitude);
        double rh = renderer.relativeHumidity(mc, mc.thePlayer.posX, mc.thePlayer.posZ, altitude, timeS);
        double overIce = SchmidtAppleman.persistenceRatio(temperature, rh);
        say(sender, "Air overhead at FL" + level, EnumChatFormatting.AQUA);
        say(sender, String.format(Locale.ROOT, "  altitude: %.0f m", altitude), EnumChatFormatting.WHITE);
        say(sender, String.format(Locale.ROOT, "  temperature: %.1f C", temperature - 273.15), EnumChatFormatting.WHITE);
        say(sender, String.format(Locale.ROOT, "  pressure: %.0f hPa", pressure / 100.0), EnumChatFormatting.WHITE);
        say(sender, String.format(Locale.ROOT, "  humidity over water: %.0f%%", rh * 100.0), EnumChatFormatting.WHITE);
        say(sender, String.format(Locale.ROOT, "  humidity over ice: %.0f%%", overIce * 100.0), EnumChatFormatting.WHITE);
        AircraftType airliner = CATALOG.byId("airliner_narrowbody");
        boolean forms = SchmidtAppleman.formsContrail(temperature, pressure, rh, airliner.engine);
        say(sender, "  an airliner here: " + (!forms ? "leaves nothing" : (overIce > 1.0 ? "leaves a persistent trail" : "leaves a short trail")),
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
