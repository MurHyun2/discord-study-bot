package com.studybot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty() || CHANNEL_ID == null || CHANNEL_ID.isEmpty()) {
            System.err.println("필수 환경변수(BOT_TOKEN, CHANNEL_ID)가 설정되지 않았습니다!");
            return;
        }

        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS
                )
                .addEventListeners(new CommandListener())
                .build();

        jda.awaitReady();
        System.out.println("🤖 스터디 봇이 시작되었습니다!");

        lastDate = LocalDate.now(KST);
        startDateChecker();
        sendStartupMessage();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void startDateChecker() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LocalDate currentDate = LocalDate.now(KST);
                if (!currentDate.equals(lastDate)) {
                    System.out.println("🗓️ 날짜가 변경되었습니다: " + lastDate + " → " + currentDate);
                    runDailyAbsenceCheck(lastDate);
                    lastDate = currentDate;
                }
            } catch (Exception e) {
                System.err.println("날짜 체크 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);
        System.out.println("📅 날짜 체크 스케줄러가 시작되었습니다.");
    }

    private void runDailyAbsenceCheck(LocalDate dateToCheck) {
        try {
            TextChannel studyChannel = jda.getTextChannelById(CHANNEL_ID);
            if (studyChannel == null) {
                System.err.println("❌ 미참여자 체크: 채널을 찾을 수 없습니다.");
                return;
            }

            List<Member> allMembers = studyChannel.getGuild().loadMembers().get().stream()
                    .filter(member -> !member.getUser().isBot())
                    .collect(Collectors.toList());

            OffsetDateTime startOfDay = dateToCheck.atStartOfDay(KST).toOffsetDateTime();
            OffsetDateTime endOfDay = dateToCheck.atTime(LocalTime.MAX).atZone(KST).toOffsetDateTime();

            Set<User> participants = studyChannel.getHistory().retrievePast(100).complete().stream()
                    .filter(msg -> !msg.getTimeCreated().isBefore(startOfDay) && !msg.getTimeCreated().isAfter(endOfDay))
                    .filter(msg -> msg.getContentRaw().startsWith("!기록"))
                    .map(Message::getAuthor)
                    .collect(Collectors.toSet());

            List<Member> absentMembers = allMembers.stream()
                    .filter(member -> !participants.contains(member.getUser()))
                    .collect(Collectors.toList());

            if (absentMembers.isEmpty()) {
                return;
            }

            StringBuilder message = new StringBuilder("🔔 **어제 스터디 기록이 없는 멤버입니다. 오늘 꼭 기록해주세요!**\n");
            absentMembers.forEach(member -> message.append(member.getAsMention()).append(" "));

            MessageCreateData messageData = MessageCreateData.fromContent(message.toString());
            // FIX: setAllowedMentions is called on the MessageCreateAction, not MessageCreateData
            studyChannel.sendMessage(messageData)
                    .setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue();

        } catch (Exception e) {
            System.err.println("❌ 미참여자 체크 중 오류: " + e.getMessage());
        }
    }

    private void sendStartupMessage() {
        TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
        if (channel != null) {
            // V4: 시작 메시지에 도움말 명령어 안내 추가
            channel.sendMessage("```📚 스터디 관리 봇이 시작되었습니다. (!도움말)```").queue();
        }
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        if (jda != null) {
            jda.shutdown();
        }
        System.out.println("👋 스터디 봇이 안전하게 종료되었습니다.");
    }
}

class CommandListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();

        // V4: 도움말 명령어 처리 추가
        if (message.equals("!참여도")) {
            calculateAndSendParticipationRate(event.getChannel().asTextChannel());
        } else if (message.equals("!명령어") || message.equals("!help") || message.equals("!도움말") || message.equals("!도움")) {
            sendHelpMessage(event.getChannel().asTextChannel());
        }
    }

    /**
     * V4: 명령어 도움말을 Embed 형태로 만들어 전송합니다.
     * @param channel 명령어가 입력된 채널
     */
    private void sendHelpMessage(TextChannel channel) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🤖 스터디 관리 봇 명령어 목록");
        eb.setColor(new Color(114, 137, 218)); // Discord Blurple 색상
        eb.setDescription("봇의 모든 기능을 확인하세요!");

        eb.addField("`!기록`", "오늘의 스터디 참여를 기록합니다. 하루에 여러 번 사용해도 한 번으로 집계됩니다.", false);
        eb.addField("`!참여도`", "모든 멤버의 서버 참여일부터 현재까지의 누적 참여율(%)을 확인합니다.", false);
        eb.addField("`!도움말` (또는 !help, !명령어)", "지금 보고 있는 이 도움말을 표시합니다.", false);

        eb.setFooter("매일 자정, 어제 '!기록'을 하지 않은 멤버를 자동으로 멘션합니다.");

        channel.sendMessageEmbeds(eb.build()).queue();
    }

    private void calculateAndSendParticipationRate(TextChannel channel) {
        Message calculatingMessage = channel.sendMessage("📊 참여도를 계산하고 있습니다. 잠시만 기다려주세요...").complete();

        try {
            List<Member> members = channel.getGuild().loadMembers().get().stream()
                    .filter(m -> !m.getUser().isBot())
                    .sorted(Comparator.comparing(Member::getEffectiveName))
                    .collect(Collectors.toList());

            List<Message> history = channel.getHistory().retrievePast(10000).complete();

            Map<User, Set<LocalDate>> participationDays = history.stream()
                    .filter(m -> m.getContentRaw().startsWith("!기록"))
                    .collect(Collectors.groupingBy(
                            Message::getAuthor,
                            Collectors.mapping(m -> m.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate(), Collectors.toSet())
                    ));

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("🏆 멤버별 스터디 참여율");
            eb.setColor(new Color(70, 130, 180)); // SteelBlue 색상
            eb.setDescription("각 멤버가 서버에 참여한 날로부터의 참여율입니다.");

            for (Member member : members) {
                User user = member.getUser();
                LocalDate joinDate = member.getTimeJoined().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
                long daysSinceJoined = Duration.between(joinDate.atStartOfDay(), LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay()).toDays() + 1;

                Set<LocalDate> participatedSet = participationDays.getOrDefault(user, Set.of());
                int participationCount = participatedSet.size();

                double rate = (daysSinceJoined == 0) ? 0 : ((double) participationCount / daysSinceJoined) * 100;

                String fieldName = String.format("%s (%.1f%%)", member.getEffectiveName(), rate);
                String fieldValue = String.format("참여: %d일 / 전체: %d일", participationCount, daysSinceJoined);
                eb.addField(fieldName, fieldValue, true);
            }

            // FIX: Use editMessageEmbeds to edit a message with an embed
            calculatingMessage.editMessageEmbeds(eb.build()).queue();

        } catch (Exception e) {
            System.err.println("❌ 참여도 계산 중 오류: " + e.getMessage());
            e.printStackTrace();
            calculatingMessage.editMessage("⚠️ 참여도 계산 중 오류가 발생했습니다.").queue();
        }
    }
}
