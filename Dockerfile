FROM gradle:9.2.1-jdk17-ubi

ARG VERSION=dev
ENV KONAN_DATA_DIR=/cache/.konan
ENV GRADLE_USER_HOME=/cache/.gradle
COPY --from=multiproto-libs:latest /root/.m2 /root/.m2

WORKDIR /app
COPY . .

RUN --mount=type=cache,target=/cache/.gradle \
    --mount=type=cache,target=/cache/.konan \
    gradle --info -PprojectVersion=$VERSION shadowJar && \
    mv grasscutter-$VERSION-all.jar grasscutter.jar

FROM gcr.io/distroless/java17
LABEL authors="osddeitf"
COPY --from=0 /app/grasscutter.jar /
WORKDIR /data
USER 1000
ENTRYPOINT ["java", "-jar", "/grasscutter.jar", "-debug"]
