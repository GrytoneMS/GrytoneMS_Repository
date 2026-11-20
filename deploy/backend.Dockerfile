# 백엔드 이미지: 서버에서 직접 빌드 (테스트는 CI에서 돌리므로 여기선 건너뜀)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY backend/ .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v plain | head -n 1)" /app.jar

FROM eclipse-temurin:17-jre
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /app.jar app.jar
# 메모리 2GB 서버라 힙을 작게 잡음
ENV JAVA_OPTS="-Xms256m -Xmx640m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
