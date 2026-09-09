# Changelog

## 0.2.0

Minecraft 26.2, Fabric.

### The sky after sunset

Trails are lit per aircraft now, by how much sun reaches its altitude rather
than by a single figure for the whole sky. That is a geometric fact rather than
an effect: from ten kilometres up you can see three degrees further round the
earth, so a trail keeps the sun for minutes after the ground has lost it. The
sky empties from the bottom, the highest trails go out last, and while they last
they burn orange, because light arriving at that hour has come the long way
through the atmosphere and only the red end of it survives the trip. In the
earth's shadow they fall back to moonlight, which is cooler.

### Trails without a shader pack

They were grey. Vanilla's entity shader applies directional lighting unless the
pipeline says otherwise, and the emissive flag only skips the lightmap, so the
term collapsed to its ambient floor of exactly 0.4 and painted a white trail the
grey of 105 out of 255. Drawn through the pipeline that carries
NO_CARDINAL_LIGHTING now, which leaves the colour intact. Shader packs light
emissive geometry themselves and were never affected.

### The ground reaches the sky

Humidity at cruise altitude now depends on what is underneath, because in
reality it does — not directly, but through circulation. Warm wet ground drives
convection that carries moisture upward, and the sinking air that makes a
subtropical desert leaves the air above it among the driest anywhere. A
rainforest trails far more readily than a desert. Sampled broadly and eased into
over time, so the sky never traces a coastline.

### Configuration

A settings screen, through Mod Menu and Cloth Config. Both optional: without
them there is no screen and the config file governs everything as before.

`trails.fibres` is now genuinely a config setting. It had been documented as one
while existing only internally, so nothing written in the file could reach it.

### Underneath

- 61 tests on the physics, checked against published figures rather than against
  whatever the code returned. Two of them guard bugs that shipped: widths
  jumping between neighbouring samples, and a pattern hung on a trail's age
  rather than on when it was emitted, which makes it crawl
- Poisson counts are drawn by normal approximation past a mean of twenty. The
  old walk was capped at 64, which would have quietly held down a dense sky
  rather than failing
- `/overflight shell` moves the sky shell without a restart, for working out
  what should sit in front of what
- CI runs again: gradlew had been committed without its executable bit

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
