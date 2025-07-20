# 멀티 스테이지 빌드 (빌드 + 실행)
FROM eclipse-temurin:17-jdk-alpine as build

WORKDIR /app

# Gradle wrapper와 소스 코드 복사
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY src ./src

# 실행 권한 부여 및 빌드
RUN chmod +x ./gradlew
RUN ./gradlew shadowJar --no-daemon

# 실행 스테이지
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 타임존 설정 (KST) - Alpine용
RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/discord-study-bot.jar app.jar

# 포트 노출 (Render 요구사항)
EXPOSE 10000

# 봇 실행
CMD ["java", "-jar", "app.jar"]