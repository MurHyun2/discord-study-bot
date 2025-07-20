package com.studybot.discordstudybot.StudyBot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StudyBot {
    private static final String BOT_TOKEN = System.getenv("DISCORD_BOT_TOKEN");
    private static final String CHANNEL_ID = System.getenv("DISCORD_CHANNEL_ID");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private JDA jda;
    private ScheduledExecutorService scheduler;
    private LocalDate lastDate;

    public static void main(String[] args) throws Exception {
        new StudyBot().start();
    }

    public void start() throws Exception {
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty()) {
            System.err.println("DISCORD_BOT_TOKEN 환경변수가 설정되지 않았습니다!");
            return;
        }

        if (CHANNEL_ID == null || CHANNEL_ID.isEmpty()) {
            System.err.println("DISCORD_CHANNEL_ID 환경변수가 설정되지 않았습니다!");
            return;
        }

        // Discord JDA 초기화
        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();

        jda.awaitReady();
        System.out.println("🤖 스터디 봇이 시작되었습니다!");

        // 현재 날짜 초기화
        lastDate = LocalDate.now(KST);

        // 스케줄러 시작
        startDateChecker();

        // 봇 시작 메시지
        sendDateSeparator(true);

        // 셧다운 훅 등록
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void startDateChecker() {
        scheduler = Executors.newScheduledThreadPool(1);

        // 1분마다 날짜 변경 체크
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkDateChange();
            } catch (Exception e) {
                System.err.println("날짜 체크 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);

        System.out.println("📅 날짜 체크 스케줄러가 시작되었습니다. (1분 간격)");
    }

    private void checkDateChange() {
        LocalDate currentDate = LocalDate.now(KST);

        if (!currentDate.equals(lastDate)) {
            System.out.println("🗓️ 날짜가 변경되었습니다: " + lastDate + " → " + currentDate);
            lastDate = currentDate;
            sendDateSeparator(false);
        }
    }

    private void sendDateSeparator(boolean isStartup) {
        try {
            TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
            if (channel == null) {
                System.err.println("❌ 채널을 찾을 수 없습니다: " + CHANNEL_ID);
                return;
            }

            LocalDateTime now = LocalDateTime.now(KST);
            String dateString = now.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)"));
            String timeString = now.format(DateTimeFormatter.ofPattern("HH:mm"));

            var message = new StringBuilder();
            message.append("```");
            message.append("═".repeat(50)).append("\n");

            if (isStartup) {
                message.append("📚 스터디 봇이 시작되었습니다! (KST ").append(timeString).append(")\n");
            } else {
                message.append("🌅 새로운 하루가 시작되었습니다!\n");
            }

            message.append("📅 ").append(dateString).append("\n");
            message.append("═".repeat(50));
            message.append("```");

            channel.sendMessage(message.toString()).queue(
                    success -> System.out.println("✅ 메시지 전송 성공: " + dateString),
                    error -> System.err.println("❌ 메시지 전송 실패: " + error.getMessage())
            );

        } catch (Exception e) {
            System.err.println("❌ 메시지 전송 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        System.out.println("🛑 스터디 봇을 종료합니다...");

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (jda != null) {
            jda.shutdown();
        }

        System.out.println("👋 스터디 봇이 안전하게 종료되었습니다.");
    }
}