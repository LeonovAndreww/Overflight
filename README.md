# Overflight
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client--side-blue?style=flat)](#)
[![License](https://img.shields.io/github/license/LeonovAndreww/overflight)](LICENSE)

Airliners at their real cruising altitudes, leaving condensation trails that
appear, spread or fail to form depending on what the air up there is actually
doing.

Client-side and purely cosmetic. Nothing is an entity, nothing can be flown,
collided with or shot down, and no server ever hears about it.

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
- **Aircraft fly where they really fly.** Cruise levels from FL180 for
  turboprops to FL600+ for high-altitude reconnaissance, eastbound on odd levels
  and westbound on even ones.
- **The traffic mix is yours.** Eleven categories with independent weights, from
  narrowbody airliners to tankers, so the sky over a survival world and the sky
  over a Cold War roleplay server can differ.
- **Navigation lights at night.** Red and green wingtips, white strobes.

## Compatibility

Aircraft and trails are drawn on a fixed-radius shell around the camera rather
than in world coordinates — FL350 is 10 668 m, far above the Y=320 build limit.
Angular sizes stay correct, nothing ever reaches the far clip plane, and there
are no entities to tick.

Geometry is submitted with vanilla render types and no custom GLSL, so shader
packs light and fog it with their own programs. It is drawn after terrain, which
leaves level-of-detail mods free to write their depth first.

## Configuration

`config/overflight.json`, generated on first launch. Presets cover the common
cases and can be overridden field by field:

| Preset | |
|---|---|
| `realistic` | Physics as measured. Default. |
| `busy` | Traffic density of European airspace. |
| `quiet` | Remote ocean traffic levels. |
| `chemtrail` | Persistent trails forced on, heavy traffic, deliberate crosshatching. The look rather than the physics. |
| `coldwar` | Military-heavy mix on low-bypass engines. |
| `abandoned` | No traffic at all, for post-apocalyptic worlds. |
| `custom` | Ignore presets entirely. |

Traffic density also accepts a schedule keyed to the in-game day, so a world can
start silent and gain air traffic later.

## Building

Requires JDK 25, which Minecraft 26.2 mandates.

```bash
git clone https://github.com/LeonovAndreww/overflight.git
cd overflight
./gradlew :backends:fabric-26.2:build
```

The jar lands in `backends/fabric-26.2/build/libs/`.

The repository is split into a version-agnostic core and per-version render
backends. The core is plain Java 8 with no dependencies and holds the
atmosphere, the trail physics and the traffic generation; backends compile those
sources themselves at their own language level. Support for older Minecraft
versions is planned and only requires a new backend, not a second copy of the
simulation.

## Contact

Issues and feature requests are tracked via GitHub Issues:
- [Create new issue](https://github.com/LeonovAndreww/overflight/issues)
