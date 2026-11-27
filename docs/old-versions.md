# Старые версии (1.7.10 и старше): разведка

Разведка 27.11.2026 (проход 66) по документации; каркас и проверка хука в игре — проход 67 (см. «Состояние»).

## Forge 1.7.10 (личная цель автора)
- Стандартная сборка — шаблон GTNH `ExampleMod1.7.10` на RetroFuturaGradle (RFG), Java 8 для кода (тулчейн сам берёт JDK 17+ для Gradle).
- Миксины: UniMixins (актуальная 1.7.10-0.2.1; GTNH Mixins устарел). Шаблон GTNH уже включает интеграцию.
- Точка вставки: `RenderGlobal.renderSky` (миксин в конец метода) или `IRenderHandler` измерения (`WorldProvider.setSkyRenderer`) — второй вариант заменяет всё небо, поэтому годится только миксин.
- Рендер — фиксированный конвейер: `Tessellator` + GL11, без шейдеров и буферов; квады и ленты ядра (`TrailMeshBuilder`) отдаются как есть, нужен лишь свой обход `SkyDraw`.
- Ядро `overflight-core` собирается с `--release 8`, так что подключается напрямую.
- Сеть: `SimpleNetworkWrapper` FML; команды — `CommandBase` на сервере; конфиг — `Configuration` Forge или собственный JSON ядра.
- Риск: шейдеры в 1.7.10 (Angelica/Shaders) подменяют небо — те же вопросы, что в T1.1.

## Legacy Fabric / Ornithe
- Legacy Fabric API выпускается для 1.7.10 (релизы июня 2026), нужен Legacy Fabric loader; Ornithe — отдельная экосистема со своими маппингами, моды между ними не взаимозаменяемы.
- Для 1.7.10 основная масса сборок — Forge, Fabric-вариант вторичен.

## Состояние (проход 67, 27.11.2026)
- `backends/forge-1.7.10` — отдельный Gradle-проект (шаблон GTNH, RFG, Gradle 9.7.1, JDK 25 для сборки, код Java 8 через Jabel); ядро подключено как исходники (`../../overflight-core/src/main/java`), общие ресурсы — из `common/resources` (без Fabric-овского `overflight.mixins.json`). Spotless GTNH отключён (табы ядра).
- Миксин `MixinRenderGlobal` на `RenderGlobal.renderSky` (TAIL) через UniMixins 0.3.1 — **проверено в игре на CI** (job `forge-1-7-10-client`, ручной): `Sky hook seen: true`, на кадре виден красный/розовый маркер-квад на небе, облака его закрывают (как и должны).
- Локально на сервере не собирается: скачивание Gradle 9.7.1 и зависимостей идёт часами — собирать только в CI (`forge-1-7-10`, `forge-1-7-10-client`).
- Подводные камни: LWJGL 2 вызывает консольный `xrandr` (пакет `x11-xserver-utils`), без него падает под Xvfb; окно без фокуса открывает меню паузы (`pauseOnLostFocus=false`); в `renderSky` включены туман и отсечение граней — своё рисуем с `GL_FOG`/`GL_CULL_FACE` выключенными.
- Проход 68: `SkyHook` вызывает `SkyRenderer.extract` (мир, камера, солнце, влажность — через API 1.7.10) и рисует три меши (`SkyDraw`, Tessellator, без тумана/глубины/отсечения, blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA, всё в `glPushAttrib`). Конфиг — `config/overflight.json` (Gson). Проверено в игре на CI: два ручных рейса, след виден. Не сделано: команды, сеть, GUI, шейдеры.

## План
1. ~~Каркас, подключение core~~ — сделано.
2. ~~Миксин на `renderSky`, один квад~~ — сделано.
3. Перенести SkyRenderer.extract/SkyDraw под Tessellator.
4. Проверять в CI (как forge-client): Xvfb + llvmpipe, 1.7.10 работает на GL 2.x.
Тяжёлое на сервере не запускать (см. NOTES: одна JVM, `timeout`).
