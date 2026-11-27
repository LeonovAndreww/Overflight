# Старые версии (1.7.10 и старше): разведка

Исследование по документации, **код не писался и в игре не проверялось** (27.11.2026, проход 66).

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

## План (не начат)
1. Клонировать ExampleMod1.7.10 в `backends/forge-1.7.10`, подключить core через composite/`includeBuild`.
2. Миксин на `renderSky`, отрисовка одного квада — проверить хук.
3. Перенести SkyRenderer.extract/SkyDraw под Tessellator.
4. Проверять в CI (как forge-client): Xvfb + llvmpipe, 1.7.10 работает на GL 2.x.
Тяжёлое на сервере не запускать (см. NOTES: одна JVM, `timeout`).
