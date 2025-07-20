FROM openjdk:17-jre-slim

WORKDIR /app

# 타임존 설정 (KST)
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY build/libs/discord-study-bot.jar app.jar

# 헬스체크용 포트 (Render 슬립 방지)
EXPOSE 10000

# 간단한 HTTP 서버 추가 (슬립 방지용)
RUN echo '#!/bin/bash\n\
java -jar app.jar &\n\
APP_PID=$!\n\
\n\
# 간단한 HTTP 서버 (헬스체크용)\n\
while true; do\n\
  echo -e "HTTP/1.1 200 OK\n\nBot is running" | nc -l -p 10000\n\
done &\n\
\n\
wait $APP_PID' > start.sh

RUN chmod +x start.sh

CMD ["./start.sh"]