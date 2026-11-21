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
