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
