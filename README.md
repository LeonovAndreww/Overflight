# Overflight
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1%20%7C%2026.2-62B47A?style=flat&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client--side-blue?style=flat)](#)
[![License](https://img.shields.io/github/license/LeonovAndreww/Overflight)](LICENSE)

Airliners at their real cruising altitudes, leaving condensation trails that
appear, spread or fail to form depending on what the air up there is actually
doing.

Client-side and purely cosmetic. Nothing is an entity, nothing can be flown,
collided with or shot down. Installing it on a server is optional and adds one
thing: an operator can put an aircraft over everybody at once.

## What it does

Look up on a cold clear day and the sky is crosshatched with trails that spread
into a haze. Look up the next day and it is empty, with the same traffic
overhead. Overflight reproduces that, rather than spawning decorative smoke on a
timer.

- **Trails form only when they physically can.** A trail needs the exhaust plume
  to reach water saturation as it mixes with ambient air, which in practice
  means colder than about −40 °C. Turboprops cruising at FL200 never leave one,
  no matter the weather.
- **Whether a trail lasts is a separate question.** It survives and spreads only
  where the air is supersaturated with respect to ice. Otherwise it sublimates
  within seconds, leaving the short stub behind the aircraft and nothing more.
- **Trails outlive their aircraft.** One left half an hour ago is still overhead
  long after the aircraft that drew it went over the horizon.
- **Aircraft fly where they really fly.** Cruise levels from FL180 for
  turboprops to FL600+ for high-altitude reconnaissance, eastbound on odd levels
  and westbound on even ones.
- **The traffic mix is yours.** Eleven categories with independent weights, from
  narrowbody airliners to tankers, so the sky over a survival world and the sky
  over a Cold War roleplay server can differ.
- **Navigation lights at night.** Red and green wingtips, white strobes.

Detail that is easy to miss and does most of the work: the trail starts a few
wingspans behind the aircraft rather than at the nozzle, one ribbon per engine
merges into one within seconds, the vortex pair drags it down a couple of
hundred metres before it levels off, and a trail seen towards the sun is four
times brighter than the same trail in the opposite half of the sky.

An old trail is not a ruled line and not a flat ribbon either, and this one is
neither. Once it has begun to break up it is drawn as a bundle of strands that
drift apart, sag below the core and wander independently, which is most of what
makes old cirrus look like cirrus.

Nor is it uniform. The air is not
uniform, so neighbouring stretches spread at different rates and get pushed
sideways by different amounts, and both differences accumulate. A fresh trail is
straight and even; a quarter of an hour later the same trail wanders across
several kilometres and swells from 800 metres wide to 3 000 and back. Fine
structure fades as it goes, the way diffusion really does erase it.

## Everyone sees the same sky

The traffic is not stored anywhere. It is a function of the dimension and the
game clock, both of which every client already agrees on, so two players
standing together watch the same aircraft cross the same patch of sky — on a
vanilla server, with nothing installed on it, and without a single packet being
sent.

## Compatibility

Aircraft and trails are drawn on a fixed-radius shell around the camera rather
than in world coordinates — FL350 is 10 668 m, far above the Y=320 build limit.
Angular sizes stay correct, nothing ever reaches the far clip plane, and there
are no entities to tick.

Geometry is submitted with vanilla render types and no custom GLSL, so shader
packs light and fog it with their own programs. It is drawn after terrain, which
leaves level-of-detail mods free to write their depth first.

## Commands

Two roots, because they answer for different people.

`/overflight` is client-side and works anywhere, vanilla servers included. It
answers for you alone: what your client believes is up there, and aircraft only
you can see.

| | |
|---|---|
| `/overflight status` | what the last frame contained, and a guess at why if it was empty |
| `/overflight list` | every aircraft in range with bearing, elevation, level, humidity, and whether it is trailing |
| `/overflight probe [fl]` | temperature, pressure and humidity overhead, and what an airliner would leave there |
| `/overflight spawn <type> [fl] [smoking\|burning]` | put one aircraft over your head |
| `/overflight convoy <count> <type> [line\|vee\|echelon]` | put up a formation |
| `/overflight preset <name>` | switch preset and save |
| `/overflight density <value>` | change traffic density without editing the file |
| `/overflight shell <blocks>` | move the sky shell without a restart, to find what sits in front of what |
| `/overflight diagnostics` | show the three commands below, for when the sky is empty and you cannot tell whether that is correct |
| `/overflight debug` | paint every quad solid magenta: missing geometry, or geometry too faint to see? |
| `/overflight rendertype <name>` | force a different render type |
| `/overflight depth <off\|test\|write>` | change how the sky treats the depth buffer |
| `/overflight reload` | re-read the config |
| `/overflight clear` | remove everything spawned by hand |

An empty sky is the one failure that looks exactly like correct behaviour, since
most aircraft really do leave nothing behind. `status` tells the cases apart: no
aircraft in range, aircraft but air too dry, or trails that produced no geometry
— which would be a bug, and says so.

`/airspace` appears when the mod is on the server too, and puts aircraft over
everybody. Gamemaster permission, the same bar as `/time` or `/weather`.

| | |
|---|---|
| `/airspace spawn <type> [fl] [heading] [smoking\|burning]` | one aircraft, for every player |
| `/airspace convoy <count> <type> [line\|vee\|echelon]` | a formation, for every player |
| `/airspace clear` | remove hand-placed aircraft everywhere |

The request travels, not the aircraft: a type, a place and a heading, from which
each client builds the same flight with the same code. Positions are never sent
and nothing is streamed. Players without the mod are skipped, and the command
says so if that turns out to be everyone.

## Configuration

`config/overflight.json`, written on first launch. With Mod Menu and Cloth
Config installed the same settings are on a screen, in three tabs; both are
optional and their absence only means the screen is not offered.

| Preset | |
|---|---|
| `realistic` | Physics as measured. Default. |
| `fancy` | The same physics at a pace you can sit and watch. |
| `busy` | Traffic density of European airspace. |
| `quiet` | Remote ocean traffic levels. |
| `chemtrail` | Persistent trails forced on, heavy traffic, deliberate crosshatching. The look rather than the physics. |
| `coldwar` | Military-heavy mix on low-bypass engines. |
| `abandoned` | No traffic at all, for post-apocalyptic worlds. |
| `custom` | Leave every value exactly as written. |

A Minecraft day is twenty minutes, so this sky runs seventy-two times faster
than the real one. That is the whole difference between the two main presets: a
persistent contrail really does last about forty minutes, which here is two
Minecraft days.

**`realistic`** — the sky as measured. Trails last forty minutes, so they build
up over a session and change slowly; about one flight in five leaves one.
Aircraft are drawn at their true angular size, which past sixty kilometres is
under half a pixel, so what you see is the trail and not the aircraft. Damp air
comes in patches wider than your view, so the sky is often all trails or none,
for days at a time.

**`fancy`** — the same physics wound to a pace you can sit and watch. A trail
forms, spreads, frays and disperses in about seven minutes. Over half the
flights leave one, and the damp patches are smaller than your view, so a single
glance holds trails of several ages instead of an all-or-nothing sky. Aircraft
are drawn larger than life and their lights carry further, so there is something
to follow across the sky as well as something to look at. Not what an instrument
would record.

| | `realistic` | `fancy` |
|---|---|---|
| Persistent trail lasts | 40 min | 7 min |
| Flights leaving a lasting trail | 22% | 55% |
| Damp patches across | 180 km | 70 km |
| Flights per hour per 1000 km square | 45 | 120 |
| Aircraft drawn at | true size | 2.6x life at 300 km |
| Navigation lights carry to | 14 km | 60 km |

Anything between the two is a matter of editing `trails.persistenceMultiplier`,
where 1.0 is the realistic forty minutes and 0.18 is fancy's seven.

A preset overwrites the sections it covers every time the config loads, so set
`preset` to `custom` before hand-editing anything you want to keep.

Worth knowing about:

- `traffic.mix` — weights per category. Anything omitted keeps its default; zero
  removes a category from the sky.
- `trails.persistenceMultiplier` — how long a surviving trail lasts.
- `trails.spreadVariation` — how unevenly a trail spreads along its length. 0
  gives a ribbon of constant width.
- `trails.shearVariationMs` — how much an ageing trail meanders. 0 keeps it
  straight.
- `trails.nightVisibility` — how visible trails stay after dark, scaled by the
  phase of the moon. 0 hides them at night.
- `trails.fibres` — how many strands an ageing trail frays into. 1 gives a flat
  ribbon, which is cheaper and much less convincing.
- `graphics.shellRadius` — how far out the sky is drawn. Angular sizes are right
  whatever it is, but it is the depth the rest of the pipeline sees, so it
  decides what the sky ends up in front of and behind. Raise it if trails appear
  in front of far terrain drawn by Voxy or Distant Horizons.
- `graphics.keepInsideVanillaFog` — without a shader pack the shell is pulled in
  to stay inside Minecraft's fog, which otherwise paints trails its own grey.
  Turn it off if you run a level-of-detail mod without shaders and would rather
  far terrain hid the sky properly.
- `graphics.trailDetail` — samples along one trail, 8 to 1024. The one setting
  that really costs frames.
- `trails.crowInstability` — the bulging and breaking of an ageing trail. Cheap,
  but the first thing to turn off if you are counting.

### What each preset costs

Geometry built per frame, measured over 970 samples on one thread. The time is
the mod's own share of a frame, before the GPU draws any of it.

| Preset | Aircraft | Trails | Quads | ms/frame |
|---|---|---|---|---|
| `realistic` | 10.5 | 8.8 | 1 280 | 0.39 |
| `busy` | 26.3 | 22.2 | 3 740 | 0.75 |
| `chemtrail` | 49.7 | 43.0 | 14 400 | 2.34 |
| `coldwar` | 8.6 | 4.9 | 690 | 0.15 |
| `quiet` | 1.5 | 1.4 | 160 | 0.04 |

Everything but `chemtrail` is free. `chemtrail` is the one that can be felt: it
is deliberately a sky nobody would call subtle, and 14 000 translucent quads
have to be sorted and blended. If it costs you frames, lower `graphics.trailDetail`
before anything else.

## API

Other mods can read the sky and put aircraft in it. The mod itself only ever
draws — it spawns no entities and drops no loot, so what an aircraft overhead
means is left to whoever asks for one.

```java
import dev.overflight.api.Overflight;
import dev.overflight.core.traffic.Flight;

// What is up there, and where it will be in two minutes
for (Flight flight : Overflight.flightsNear(playerX, playerZ, 60_000)) {
    double x = flight.xAt(nowSeconds + 120);
    double z = flight.zAt(nowSeconds + 120);
}

// Something is wrong with this one
Overflight.spawn("military_transport", playerX, playerZ, 280, 90.0,
        Flight.Condition.BURNING);

// Would a trail even form up there right now?
boolean persists = Overflight.airAt(playerX, playerZ, 350).contrailPersists();
```

A `Flight` is a function of time rather than a snapshot: ask it for a position at
any moment, past or future, and it will answer.

## Building

| Minecraft | Loader | Gradle project |
| --- | --- | --- |
| 26.2 | Fabric | `backends:fabric-26.2` |
| 26.1.x | Fabric | `backends:fabric-26.1` |
| 1.21.11 | Fabric | `backends:fabric-1.21.11` |

Building everything needs both JDK 25, which 26.x mandates, and JDK 21 for
1.21.11; each backend on its own needs only its own.

```bash
git clone https://github.com/LeonovAndreww/Overflight.git
cd overflight
./gradlew build
```

The jars land in `backends/*/build/libs/`.

The physics has tests, and they are the point of keeping the core free of
Minecraft: vapour pressure is checked against published figures, the standard
atmosphere against its table, and the humidity field against the fraction of the
sky it is supposed to leave supersaturated. Two of them guard bugs that shipped —
widths jumping between neighbouring samples, and a pattern hung on a trail's age
rather than on when it was emitted, which makes it crawl along the trail.

```bash
./gradlew :overflight-core:test
```

The repository is split into a version-agnostic core and per-version render
backends. The core is plain Java 8 with no dependencies and holds the
atmosphere, the trail physics and the traffic generation; backends compile those
sources themselves at their own language level.

Versions on the same generation of Minecraft's render pipeline share their
backend sources too, so `fabric-26.1` compiles `fabric-26.2`'s Java rather than
copying it and differs only in its manifest and its dependency versions.

Where two versions genuinely diverge they do it in two files. `Compat` holds the
names that moved -- the day clock, the command builder, the payload registry --
and `SkyRenderHooks` holds the join to the renderer, which is where 1.21.11 and
26.x actually differ: both split extraction from drawing, but one collects
geometry for the renderer to schedule and the other writes to an open buffer.
Everything else, physics and geometry alike, is the same source on every
version. A backend that needs its own copy of those two excludes the shared one
rather than shadowing it, since javac compiles every source root it is given and
has no notion of one overriding another.

Textures and the icon are generated from source in `tools/` rather than
committed as opaque images, so the shape of a falloff can be argued with:

```bash
java tools/GenerateTrailTexture.java
java tools/GenerateAircraftTexture.java
java tools/GenerateIcon.java
```

## Contact

Issues and feature requests are tracked via GitHub Issues:
- [Create new issue](https://github.com/LeonovAndreww/Overflight/issues)
