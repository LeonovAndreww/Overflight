# JOURNAL

## 2026-11-20/21 — агент, T0.1
- Рабочее дерево было «грязным» на 63 файла: только CRLF от Windows (`diff --ignore-space-at-eol` пуст),
  из-за CRLF не запускался `gradlew`. Откатил, добавил `.gitattributes` (LF), `external/` в .gitignore.
- Поставил Temurin 21 и 25 в `~/jdks` (apt Adoptium виснет), `~/.gradle/gradle.properties`.
- `./gradlew` не качает Gradle (SSL handshake в Java), скачал zip curl'ом, использую `~/gradle-9.5.1/bin/gradle`.
- `:overflight-core:test` — BUILD SUCCESSFUL (44 мин, почти всё — загрузка зависимостей при нагрузке на сервер ~20).
- Полная `gradle build`: BUILD SUCCESSFUL (35 мин), четыре jar в backends/*/build/libs (fabric 1.21.11, 26.1.2, 26.2, 26.3).
- Дальше: T0.2 (Xvfb + mesa, runClient), потом T1.x. Ничего не запушено до проверки remote.
- Push: большой первый push (49 коммитов) падал с sideband disconnect; помог http.postBuffer=500M и повтор, запушено.

## 2026-11-21 — агент, T0.2
- `xvfb-run glxinfo`: llvmpipe, OpenGL core 4.5 (Mesa 25.2.8) — mesa уже стоял.
- `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1920x1080x24" nice gradle --no-daemon :backends:fabric-26.2:runClient --args='--quickPlaySingleplayer "New World"'` (JAVA_HOME=jdk25): клиент сам вошёл в мир, DevAutoShot сделал 3 скриншота (`Sky hook seen: true`), игра вышла сама, BUILD SUCCESSFUL за 50 мин (нагрузка сервера ~14, сервер игры отставал на 70 с). Скриншоты в run/screenshots (gitignored); на кадре виден ванильный облачный слой, самолётных следов в этом кадре не разглядел — визуально следы не подтверждены.
- Обходные пути не понадобились. Дальше: T1.x; для визуальных задач запускать клиент ночью/при малой нагрузке, он очень медленный.

## 2026-11-21 (ночь) — агент, T1.5 + часть T1.9
- T1.5: `clearSky` в `SkyRenderer` теперь сглаживается экспонентой по реальному времени кадра (nanoTime, τ=0,15 с, dt ≤ 0,5 с) вместо 0,12 за кадр.
- T1.9: `SkyCover` без боксинга (heapsort по ages/covers), добавлен `SkyCoverTest` (под бюджетом без изменений; старейшие гаснут первыми при любом порядке хранения).
- Проверка: `:overflight-core:test` зелёный (offline, 5,5 мин), `compileJava` всех модулей зелёный. В игре не запускал — клиент слишком медленный на загруженном сервере.
- Дальше: T1.9 остаток (TrailSampler, TrafficGenerator, OverflightConfig), T1.8, T1.7.
- T1.6: в `SkyRenderer.extract` blindness/darkness (через `camera.entity()`) обнуляют целевой `clearSky`, следы гаснут так же, как под водой. compileJava всех модулей зелёный; в игре не проверено.
- T1.9 (частично): просмотрен `TrafficGenerator` — кеш CellSlot ограничен (≈25 ячеек × ≤24 слота < CACHE_LIMIT 2048), замечаний нет; `TrailSampler`, `OverflightConfig`, бэкенды 26.1/26.3/1.21.11 не просмотрены.

## 2026-11-21 (утро) — агент, T1.9 (OverflightConfig, TrailSampler)
- Баг: look-пресет `chemtrail` ставил densityPerHour=220, затем `applyTrafficPreset("modern")` перезаписывал на 250. Теперь плотность/maxAircraft chemtrail ставятся после трафик-пресета. Добавлен `OverflightConfigTest` (4 теста).
- `TrailSampler` прочитан целиком, замечаний нет. Открытые мелочи по конфигу записаны в TASKS (T1.9).
- Проверка: `:overflight-core:test` зелёный (offline, JAVA_HOME=~/jdks/jdk21). В игре не запускал.
- Дальше: T1.7 (производительность спрайтов, замер RenderPreview), сброс look-полей при смене пресета, sanitise.

## 2026-11-21 (день) — агент, T1.9 (конфиг, остаток)
- realistic/fancy теперь возвращают поля chemtrail; sanitise зажимает недостающие поля; +2 теста в OverflightConfigTest.
- Проверка: `:overflight-core:test` зелёный (offline, jdk21). В игре не запускал.
- Дальше: очистка mix при смене с coldwar, T1.7 (производительность), ревью бэкендов 26.1/26.3/1.21.11.

## 2026-11-21 (проход 4) — агент, T1.9 хвост + T1.7 часть
- Конфиг: смена с `coldwar` теперь убирает записанные им записи mix (только нетронутые; ручные правки остаются). Тест в OverflightConfigTest.
- T1.7: `TrailPuffBuilder.build` перескакивает молодой участок следа (spread ≤ 0.02, сетка на самом мелком шаге) сразу к его дальнему краю. Проверка: PNG из RenderPreview на трёх сценах (sky, sky seed=7 persist=2.5, loops) до и после совпадают md5; `:overflight-core:test` зелёный. Замер при нагрузке ~8: 18,9 → 17,8 мс (шум), реального выигрыша не подтверждено.
- T3.1: оказалось уже сделано (release 8 в core). Отмечено.
- Дальше: замер T1.7 при малой нагрузке и другие идеи (не пересобирать при неподвижной камере), ревью бэкендов 26.1/26.3/1.21.11, T1.10.

## 2026-11-21 (проход 5) — агент, T1.9 бэкенды
- Прочитаны SkyDraw/SkyPipeline/миксины/Compat бэкендов 26.1, 26.3, 1.21.11 и `SkyRenderer.extract`; багов не найдено. Мелочь: при выключенном рендере/без мира сбрасывается `lastClearNanos`, чтобы затухание не прыгало при возврате.
- Проверка: `compileJava` fabric-26.2 (см. коммит). Миксины в игре не запускались.
- Дальше: просмотр команд/экрана настроек, T1.7 замер при малой нагрузке, T1.10, T1.11 (CHANGELOG/README).

## 2026-11-21 (проход 6) — агент, T1.11
- CHANGELOG: раздел «Unreleased (0.3.0)» (небо-фаза, спрайты, затухание по времени, слепота, правки пресетов). README: `skyphase`/`skyprogram` в таблице команд (id сверены с `ShaderPacks.SkyProgram`).
- Проверка: только чтение кода/диффа; сборка не затрагивалась (правки документации). Сервер под нагрузкой ~16, игру не запускал.
- Версию не поднимал, не тегал. Дальше: T1.7 честный замер при малой нагрузке, ревью AirspaceCommands/OverflightCommands/ConfigScreen, T1.10.

## 2026-11-21 (проход 7) — агент, T1.9 команды и экран настроек
- Баг: `OverflightConfigScreen` при сохранении делал только `sanitise()`, без `applyPreset()`: смена пресета в экране вступала в силу лишь после перезапуска/reload. Теперь `applyPreset().sanitise()`, как при загрузке.
- `OverflightCommands`: убрана неиспользуемая переменная, перепутанные javadoc (debug, skyPhase) разнесены. `AirspaceCommands` — замечаний нет (неизвестная формация = vee, это задокументировано в FlightRequests).
- Проверка: `compileJava` fabric-26.2 зелёный (offline, jdk25, ~20+ мин при нагрузке ~16). В игре экран не открывал (нужен Cloth Config).
- Не сделано: T1.7 честный замер, T1.8, T1.10, T1.1–T1.3 (нужен запуск клиента, сервер перегружен). Версию не трогал.
