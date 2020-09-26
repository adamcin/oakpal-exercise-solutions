FROM maven:3.6.3-jdk-8 AS build
ADD . /app
WORKDIR /app
RUN bash -c "[ -f /app/target/oakpal-exercise-solutions-*.opear ] || mvn -B clean install"

FROM adamcin/oakpal:2.2.1

COPY --from=build /app/target/oakpal-exercise-solutions-*.opear /app/oakpal-exercise-solutions.opear

ENV OAKPAL_OPEAR "/app/oakpal-exercise-solutions.opear"