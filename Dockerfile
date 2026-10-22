# ───────────────────────────────────────────────────────────────────
# Образ приложения. Сборки внутри нет намеренно: принят
# порядок «mvn clean package → docker compose build», то есть jar
# приезжает готовым, а образ его только упаковывает. Так сборка в CI
# и сборка образа не расходятся в версиях JDK и Node.
#
# Контекст сборки — корень репозитория: jar лежит в archi-bootstrap/target/.
# ───────────────────────────────────────────────────────────────────

# Первый этап раскладывает jar по слоям: библиотеки меняются редко, код — с каждой
# сборкой, и новый образ пересылает только изменившийся слой, а не все 125 МБ.
FROM eclipse-temurin:25-jre AS layers
WORKDIR /build
COPY archi-bootstrap/target/archi-creator-*.jar archi-creator.jar
RUN java -Djarmode=tools -jar archi-creator.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre

# Приложение не пишет в файловую систему и root'ом быть не обязано.
# pebble — менеджер служб Canonical из базового образа, вне пакетов dpkg: приложению
# он не нужен, а его Go-библиотеки отстают от исправлений уязвимостей.
RUN groupadd --system archi && useradd --system --gid archi --home /app archi \
    && rm -rf /usr/bin/pebble /var/lib/pebble

# Файлы приложения принадлежат root: пользователю приложения их менять незачем.
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./

USER archi
EXPOSE 8080

# MaxRAMPercentage вместо -Xmx: предел задаёт лимит контейнера,
# а не число, зашитое в образ. JAVA_TOOL_OPTIONS JVM читает сама — запуску
# не нужна оболочка, и сигнал остановки приходит прямо в JVM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/archi-creator.jar"]
