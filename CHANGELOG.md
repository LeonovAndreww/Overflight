# Changelog

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
