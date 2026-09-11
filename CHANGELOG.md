# Changelog

## 0.2.0

Minecraft 26.2, Fabric.

### Trails you can see without a shader pack

In 0.1.0 they came out grey, and the sky was only right with a pack installed.
Minecraft draws entities through a shader that does three things a contrail
cannot survive: it applies fog, so a trail beyond the fog line arrives painted
the colour of the sky it is meant to stand against; it discards anything fainter
than a cutoff, which is most of a contrail's soft edge; and it shades by surface
angle, which floors a white trail at grey. No vanilla pipeline is free of all
three.

The sky now has a pipeline of its own, running a copy of Minecraft's own entity
shader with exactly those three removed. Colour, fog and edges are correct
without a pack, and packs keep the path they already had.

### The sky after sunset

Trails are lit per aircraft, by how much sun reaches its own altitude rather
than by one figure for the whole sky. From ten kilometres up you can see three
degrees further round the earth, so a trail keeps the sun for minutes after the
ground has lost it: the sky empties from the bottom, the highest trails go out
last, and while they last they burn orange, because light arriving at that hour
has come the long way through the atmosphere and only the red end survives. In
the earth's shadow they fall back to moonlight, which is cooler and much fainter
— a dense trail is capped after dark, since at full daytime strength it reads as
a lit strip rather than as something the moon is catching.

### The sun where the pack put it

Shader packs usually lean the sun's daily path to one side rather than running
it overhead; Complementary Unbound tilts it by forty degrees, which drops the
noon sun to fifty. Trails were lit from where Minecraft keeps its sun, so the
bright half of the sky was the wrong half. The tilt is read from Iris now, which
knows it because it needs it for shadows, so every pack that declares one works
without the mod knowing anything about that pack.

### The ground reaches the sky

Humidity at cruise now depends on what is underneath, through circulation rather
than directly. Warm wet ground drives convection that carries moisture upward;
the sinking air that makes a subtropical desert leaves the air above it among
the driest on the planet. Rainforests trail readily, deserts barely. Sampled
over a spread of points and eased in over time, so a coastline does not put a
hard edge across the sky.

### A preset for watching, not measuring

A Minecraft day is twenty minutes, so this sky runs seventy-two times faster
than the real one, and a persistent contrail that really lasts forty minutes
lasts two Minecraft days here. `realistic` keeps that. The new `fancy` preset
winds it to seven minutes, which is long enough to watch one form, spread, fray
and disperse; over half the flights leave one, damp patches are smaller than
your view so the sky holds trails of several ages at once, and aircraft are
drawn larger than life so there is something to follow across the sky.

### Aircraft at the size they really are

`realistic` now draws them true to scale, which past sixty kilometres is under
half a pixel — what you see up there is the trail, not the aircraft, as in life.
A spyglass resolves them, because the sky is world geometry and zoom magnifies
it like anything else. Navigation lights fade with the square of distance
instead of burning at one strength to the horizon, so the sky is no longer a
field of stars that are not there.

### Shape

Old trails fray into strands that drift apart, sag and wander, which is most of
what makes old cirrus look like cirrus. The oldest end fades away and its
strands draw back together, so a short-lived trail reads as a spindle rather
than a ribbon cut off square. No two trails age alike: how fast one spreads is
drawn from the flight, so some stay sharp while others soften quickly.

### Fixed

- Trails no longer vanish whole. Flights over the on-screen limit were ranked by
  a measure that moved every frame, so one could drop past the limit between
  frames and take its entire trail with it.
- Trails no longer punch holes in clouds, flicker, or break into a crawling
  dashed line. They were writing depth, so anything drawn after them was
  discarded where they lay.
- The gap between an aircraft and its own trail was three fuselage lengths. The
  plume cools in a fraction of a second, not four wingspans.
- A dense trail no longer saturates to flat white. A contrail is optically thin:
  even a young one passes about a third of the light behind it.

### Configuration

A settings screen through Cloth Config and Mod Menu, holding the settings worth
reaching for and saying what each preset gives up rather than only naming it.
The config file remains the whole truth.

New commands: `/overflight shell` to find the depth the sky is drawn at by eye,
and `/overflight debug`, `rendertype` and `depth` for telling a rendering fault
apart from an empty sky.

## 0.1.0

First release. Minecraft 26.2, Fabric.

### The sky

- Air traffic at real cruising altitudes, from FL180 for turboprops to FL600 and
  above for high-altitude reconnaissance, eastbound on odd levels and westbound
  on even ones as the semicircular rule requires.
- Eleven aircraft categories with independent weights, from narrowbody airliners
  to tankers. Fighters fly in groups of up to four and draw parallel trails.
- Aircraft are drawn as a plan view sized from the real wingspan and slant
  range — one overhead at FL350 subtends about a third of a degree — with red
  and green wingtip lights and a tail strobe after dark.

### The trails

- Formation is decided by the Schmidt-Appleman criterion against simulated
  temperature and pressure, so nothing trails below roughly FL300 and a
  turboprop at FL200 never does, whatever the weather.
- Persistence is a separate question answered by ice supersaturation. A trail
  either spreads for half an hour or sublimates in fifteen seconds.
- A trail outlives its aircraft: one left half an hour ago is still overhead
  after the aircraft that drew it went over the horizon.
- The details that make one recognisable: a gap of several wingspans before it
  begins, one ribbon per engine merging within seconds, the Crow instability
  bulging and breaking an ageing trail into puffs, and forward scattering that
  makes a trail towards the sun four times brighter than the same trail away
  from it.

### Working with other mods

- Geometry is submitted through vanilla render types with no custom GLSL, so
  shader packs light and fog it with their own programs.
- Everything is drawn on a fixed-radius shell around the camera rather than in
  world coordinates, and after terrain, which leaves level-of-detail mods free
  to write their depth first.
- No entities, so nothing ticks.

### Everyone sees the same sky

The traffic is a function of the dimension and the game clock, both of which
every client already agrees on. Two players standing together watch the same
aircraft cross the same patch of sky, on a vanilla server, without a packet
being sent.

### Control

- `config/overflight.json` with presets: `realistic`, `busy`, `quiet`,
  `chemtrail`, `coldwar`, `abandoned`, `custom`.
- `/overflight` — client-side, works on any server: `status`, `list`, `probe`,
  `spawn`, `convoy`, `preset`, `density`, `reload`, `clear`.
- `/airspace` — appears when the mod is on the server, and puts aircraft over
  every player: `spawn`, `convoy`, `clear`.
- `dev.overflight.api.Overflight` for other mods: read what is in the sky, put
  aircraft into it, ask whether a trail would form.

### Cost

Geometry built per frame, on one thread, before the GPU draws any of it:
`realistic` 0.25 ms and 750 quads, `busy` 0.43 ms, `chemtrail` 1.19 ms and 8 700
quads. Only `chemtrail` can be felt, and it says so in the README.
