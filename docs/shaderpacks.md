# Шейдерпаки: что они делают со следами (разбор по коду)

Разобрано чтением исходников паков из `external/shaderpacks/` 21.11.2026.
**В игре не проверено** (клиент с Iris не запускался). Программы следов —
`/overflight skyprogram plain|sky|textured` (`ShaderPacks.SkyProgram`).

| Пак | `gbuffers_skytextured` для не-солнца/луны («custom sky») | Альфа вершины | Вывод для `sky` |
|---|---|---|---|
| Complementary Reimagined/Unbound r5.8.1 | `color.rgb *= color.rgb * smoothstep1(sqrt1(max0(VdotU)))` — цвет в квадрате и умножен на высоту над горизонтом; у MC < 1.13 — `discard` | `glColor.a` в цвет не входит (умножается только `color.rgb *= glColor.rgb`; `color.a` берётся из текстуры, затем гасится дождём) | Плохо: след темнеет у горизонта (квадрат и `VdotU`) и теряет альфу. `plain` предпочтителен |
| BSL 10.1.3 | `albedo.rgb = pow(albedo.rgb, 2.2) * albedo.a`, потом при `ALPHA_BLEND == 0` `sqrt` обратно | учтена (premultiply) | Приемлемо: цвет возвращается в исходный, альфа работает. `sky` годится, но `plain` проще |
| Bliss v2.1.2 | `RESOURCEPACK_SKY != 0` и renderStage: рисует в `RENDERTARGETS:10`, `rgb / 5.0`, небольшой шум; при `RESOURCEPACK_SKY == 0` — `discard` | `COLOR.a` сохраняется | Зависит от настройки пака: при 0 следы через `sky` пропадут. `plain` безопаснее |
| Solas 3.7 | Своего `gbuffers_skytextured` нет — небо и объекты неба идут через `gbuffers_textured` / skybasic-пайплайн пака | — | `sky` не имеет смысла (Iris подставит запасную программу); `plain` или `textured` |
| Better Voxels 1.6, Rethinking Voxels r0.1-beta9 | Код Complementary: для не-солнца/луны `color.rgb *= color.rgb * smoothstep1(sqrt1(max0(VdotU)))`; при `MC_VERSION < 11300` `discard` | `glColor.a` в цвет не входит | Как у Complementary: `sky` портит цвет, `plain` предпочтителен |
| Photon v1.4 | Стадия `CUSTOM_SKY`: при выключенном по умолчанию `CUSTOM_SKY` пишет `vec3(0.0)`; вывод `vec3` без альфы, `blend.gbuffers_skytextured = off` | нет (RGB-вывод) | `sky` даст чёрные непрозрачные линии: не использовать. `plain` |
| Super Duper Vanilla | пишет `toLinear(albedo.rgb * albedo.a) * skyBoxIntensitySqrd` в `colortex4`, `blend = ONE ONE ZERO ZERO` | учтена (premultiply, аддитивно) | Цвет линейный и аддитивный: белый след добавит свет, но не затемнит; в `colortex4` (не `colortex0`), поэтому отдельная проверка нужна |
| MakeUp UltraFast | `gbuffers_skytextured` — общий `common/skytextured_fragment.glsl` (солнце/луна), по умолчанию `USE_BASIC_SH` | не разбиралась подробно | не разобрано до конца; `plain` |

## Выводы
- `plain` (по умолчанию) обходит все эти преобразования цвета: след приходит так, как его
  собрал мод. Риск — цветовое пространство `colortex0`: BSL линеаризует (`^2.2`), Bliss делит
  на 5 (HDR-диапазон), так что белый из `plain` может выглядеть иначе, чем небо пака.
  Это проверять только в игре (T1.1).
- Complementary — единственный из разобранных, где `sky` заведомо ломает цвет следа
  (квадрат + игнор альфы + затемнение к горизонту): это источник чёрных следов ночью и
  коричневых на закате.
- Где в пайплайне паков рисуются облака (`gbuffers_clouds`) и закрывают ли они следы,
  по коду не решить без знания порядка отрисовки Iris: пока открытый вопрос T1.3.

## Облака паков и следы (разбор по коду, 23.11.2026)
Следы рисуются на стадии неба в `colortex0`; свои облака паки считают позже, в `deferred1`,
и смешивают поверх уже записанного цвета, поэтому облака пака **закрывают** след
пропорционально своей альфе (прозрачное облако — след виден сквозь него).
- Complementary (`program/deferred1.glsl`, ~стр. 394–400): при `VL_CLOUDS_ACTIVE` для `z0 > 0.56`
  `color = mix(color, clouds.rgb, clouds.a)`. Ванильные облака (`gbuffers_clouds`) делают
  `discard`, если `CLOUD_STYLE_DEFINE != 50`, то есть по умолчанию их нет.
- BSL (`program/deferred1.glsl`, ~стр. 551–571): при `CLOUDS == 1/2` (по умолчанию 2)
  `color.rgb = mix(color.rgb, cloud.rgb, cloud.a)`; ванильные облака рисуются только при
  `CLOUDS == 3`. Вывод тот же.
- Solas: свои облака в `gbuffers_clouds` при `VANILLA_CLOUDS` (альфа `VANILLA_CLOUD_OPACITY²`),
  рисуются после неба обычным блендингом — закрывают след; без этой опции не разобрано.
- Bliss (`world0/deferred1.fsh`, стр. ~464–484): при `z0 > 0.56` `color = mix(color, clouds.rgb, clouds.a)` — собственные объёмные облака (`clouds=off` отключает только ванильные) закрывают след пропорционально альфе. Важно: `RESOURCEPACK_SKY` по умолчанию 0, и тогда `gbuffers_skytextured` просто `discard`: программа `sky` под Bliss без этой настройки не рисует след вообще.
- Better/Rethinking Voxels: код Complementary (`gbuffers_clouds` с `CLOUD_STYLE_DEFINE == 50`, иначе `discard`); у Rethinking Voxels тот же разбор.
- Photon: облака считаются в `d1_clouds`/`d2_clouds_upscaling` и сливаются в `c1_blend_layers` — порядок относительно неба не проверялся, по коду не решено.
- Super Duper Vanilla: `blend.gbuffers_clouds = off`, `alphaTest = off` — облака не прозрачны сами по себе; закрывают ли след, не разобрано.
Не проверено в игре (Iris под llvmpipe не тянет, см. JOURNAL, проход 35).

## Не разобрано
Insanity, Mellow, Body Camera (не скачаны), порядок облаков у Photon/SDV/MakeUp; Unbound — совпадает с Reimagined (общая кодовая база).
Автовыбор программы по имени пака не делался.


## Итог по `plain`
Ни один из разобранных паков не подменяет ванильную программу при `plain`, поэтому он остаётся значением по умолчанию. Под `sky` ломают след Complementary, Voxels-паки, Photon (чёрный opaque), Bliss (при `RESOURCEPACK_SKY=0` пропадает). Паки скачаны с Modrinth 25.11.2026 (Photon 1.4, Rethinking Voxels beta9, SDV, MakeUp UltraFast).

## Что Iris отдаёт снаружи (T2.2, по javap iris-fabric 1.11.2+mc26.2)
Публичный `net.irisshaders.iris.api.v0.IrisApi` содержит только: `isShaderPackInUse`, `isRenderingShadowPass`, `getSunPathRotation`, `getConfig`, `assignPipeline`, экран и текстовый sink. Uniform-цветов неба/солнца, времени суток или освещения в нём нет — они живут во внутренних `CelestialUniforms`/`CommonUniforms` и вычисляются из тех же ванильных значений (время, наклон `sunPathRotation`). Цвет неба пак считает сам в шейдере, снаружи его не прочитать.
Вывод: единственное, что стоит брать из пака, — `getSunPathRotation`, и мод это уже делает (`ShaderPacks.sunPathRotationDegrees`). Ванильные `SKY_COLOR`/`SKY_LIGHT_COLOR` остаются лучшим доступным приближением; лезть во внутренности Iris (хрупко между версиями) не стоит.

## Voxy 0.2.19-beta (26.2) — по коду, 27.11.2026

Разобран `external/test-mods/voxy-0.2.19-beta.jar` (javap). Voxy рисует LOD-чанки не в
отдельной стадии, а внутри прохода Sodium: `MixinDefaultChunkRenderer.doRender` (при
`DefaultTerrainRenderPasses.SOLID`) вызывает `VoxyRenderSystem.renderOpaque` в тот же
`RenderTarget`, что и ванильная геометрия, с записью глубины. Небо (а с ним и наши следы,
стадия sky) рисуется раньше твёрдой геометрии, поэтому непрозрачные чанки Voxy перекрывают
следы так же, как обычные. Собственных облаков у Voxy нет (в миксинах нет ни облаков, ни
неба) — облака остаются ванильными или паковыми. С Iris Voxy идёт через
`iris.MixinIrisRenderingPipeline` в пайплайне пака, то есть по правилам пака.
Вывод: следы под LOD-чанками скрываться должны. В игре НЕ проверено (нужен мир с готовыми LOD).
