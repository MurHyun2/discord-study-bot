# 멀티 스테이지 빌드 (빌드 + 실행)
FROM openjdk:17-jdk-slim as build

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
FROM openjdk:17-jre-slim

WORKDIR /app

# 타임존 설정 (KST)
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/discord-study-bot.jar app.jar

# 포트 노출 (Render 요구사항)
EXPOSE 10000

# 봇 실행
CMD ["java", "-jar", "app.jar"]