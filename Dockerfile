# ───────────────────────────────────────────────────────────────────
# Образ приложения. Сборки внутри нет намеренно: §10.2 описывает
# порядок «mvn clean package → docker compose build», то есть jar
# приезжает готовым, а образ его только упаковывает. Так сборка в CI
# и сборка образа не расходятся в версиях JDK и Node.
#
# Контекст сборки — корень репозитория: jar лежит в archi-bootstrap/target/.
# ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

# Приложение не пишет в файловую систему и root'ом быть не обязано.
RUN groupadd --system archi && useradd --system --gid archi --home /app archi

WORKDIR /app
COPY --chown=archi:archi archi-bootstrap/target/archi-creator-*.jar /app/archi-creator.jar

USER archi
EXPOSE 8080

# MaxRAMPercentage вместо -Xmx: предел задаёт лимит контейнера,
# а не число, зашитое в образ.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/archi-creator.jar"]
