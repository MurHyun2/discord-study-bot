package com.studybot;

import io.javalin.Javalin;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StudyBot {
    private static final String BOT_TOKEN = System.getenv("DISCORD_BOT_TOKEN");
    private static final String CHANNEL_ID = System.getenv("DISCORD_CHANNEL_ID");
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");

    // 메시지 검색 한도를 상수로 정의
    public static final int MESSAGE_HISTORY_LIMIT = 100;
    // 참여도 계산 시 조회할 총 메시지 수 (100개씩 나누어 조회됨)
    public static final int PARTICIPATION_HISTORY_LIMIT = 10000;


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

        // ⭐️ UptimeRobot 연동을 위한 웹 서버 시작
        Javalin app = Javalin.create().start(3000); // 3000번 포트로 서버 시작
        app.get("/", ctx -> ctx.result("Study bot is alive!")); // 루트 URL에 접속하면 응답
        System.out.println("🌐 웹 서버가 3000번 포트에서 시작되었습니다.");


        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new SlashCommandListener())
                .build();

        jda.awaitReady();
        System.out.println("🤖 스터디 봇이 시작되었습니다!");

        registerSlashCommands();
        lastDate = LocalDate.now(KST);
        startDateChecker();
        sendStartupMessage();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void registerSlashCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("기록", "오늘의 스터디 참여를 기록하는 팝업창을 엽니다."),
                Commands.slash("참여도", "멤버별 누적 스터디 참여율을 확인합니다."),
                Commands.slash("확인", "특정 날짜의 참여 현황과 미참여자를 확인합니다.")
                        .addOption(OptionType.STRING, "날짜", "확인할 날짜 (YYYY-MM-DD 형식, 비워두면 오늘)", false),
                Commands.slash("도움말", "봇의 모든 명령어를 확인합니다.")
        ).queue(
                success -> System.out.println("✅ 슬래시 명령어가 성공적으로 등록되었습니다."),
                error -> System.err.println("❌ 슬래시 명령어 등록에 실패했습니다: " + error)
        );
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
        TextChannel studyChannel = jda.getTextChannelById(CHANNEL_ID);
        if (studyChannel == null) {
            System.err.println("❌ 스터디 채널을 찾을 수 없습니다: " + CHANNEL_ID);
            return;
        }

        // 비동기로 처리하여 블로킹 방지
        CompletableFuture.runAsync(() -> {
            try {
                List<Member> allMembers = studyChannel.getGuild().loadMembers().get().stream()
                        .filter(member -> !member.getUser().isBot())
                        .collect(Collectors.toList());

                Set<String> participantIds = getParticipantIds(studyChannel, dateToCheck);

                List<Member> absentMembers = allMembers.stream()
                        .filter(member -> !participantIds.contains(member.getUser().getId()))
                        .collect(Collectors.toList());

                if (!absentMembers.isEmpty()) {
                    sendAbsenceNotification(studyChannel, absentMembers);
                }
            } catch (Exception e) {
                System.err.println("❌ 미참여자 체크 중 오류: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private Set<String> getParticipantIds(TextChannel channel, LocalDate date) {
        OffsetDateTime startOfDay = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime endOfDay = date.atTime(LocalTime.MAX).atZone(KST).toOffsetDateTime();

        return channel.getHistory().retrievePast(MESSAGE_HISTORY_LIMIT).complete().stream()
                .filter(msg -> msg.getAuthor().equals(jda.getSelfUser()) && !msg.getEmbeds().isEmpty())
                .filter(msg -> {
                    OffsetDateTime msgTime = msg.getTimeCreated();
                    return !msgTime.isBefore(startOfDay) && !msgTime.isAfter(endOfDay);
                })
                .map(msg -> msg.getEmbeds().get(0))
                .filter(embed -> embed.getFooter() != null &&
                        embed.getFooter().getText() != null &&
                        embed.getFooter().getText().startsWith("참여자 ID:"))
                .map(embed -> embed.getFooter().getText().substring("참여자 ID: ".length()))
                .collect(Collectors.toSet());
    }

    private void sendAbsenceNotification(TextChannel channel, List<Member> absentMembers) {
        StringBuilder message = new StringBuilder("🔔 **어제 스터디 기록이 없는 멤버입니다. 오늘 꼭 기록해주세요!**\n");
        absentMembers.forEach(member -> message.append(member.getAsMention()).append(" "));

        channel.sendMessage(message.toString())
                .setAllowedMentions(List.of(Message.MentionType.USER))
                .queue();
    }

    private void sendStartupMessage() {
        TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
        if (channel != null) {
            channel.sendMessage("```📚 스터디 관리 봇이 시작되었습니다. (/도움말)```").queue();
        }
    }

    public void shutdown() {
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

class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // 채널 검증을 먼저 수행
        if (!isValidChannel(event)) {
            event.reply("이 채널에서는 스터디 봇 명령어를 사용할 수 없습니다.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "기록" -> showRecordModal(event);
            case "참여도" -> calculateAndSendParticipationRate(event);
            case "확인" -> checkRecordsByDate(event);
            case "도움말" -> sendHelpMessage(event);
            default -> event.reply("알 수 없는 명령어입니다.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!"record-modal".equals(event.getModalId())) {
            return;
        }

        Optional<String> contentOpt = Optional.ofNullable(event.getValue("content"))
                .map(mapping -> mapping.getAsString());

        if (contentOpt.isEmpty()) {
            event.reply("⚠️ 입력 내용이 비어있습니다.").setEphemeral(true).queue();
            return;
        }

        String content = contentOpt.get();
        if (content.trim().isEmpty()) {
            event.reply("⚠️ 공부 내용을 입력해주세요.").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(user.getName(), null, user.getAvatarUrl())
                .setColor(new Color(0x3BA55D))
                .setDescription(content.trim())
                .setFooter("참여자 ID: " + user.getId())
                .setTimestamp(event.getTimeCreated());

        event.reply("✅ 기록이 성공적으로 등록되었습니다!").setEphemeral(true).queue();

        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private boolean isValidChannel(SlashCommandInteractionEvent event) {
        String channelId = System.getenv("DISCORD_CHANNEL_ID");
        return channelId != null && channelId.equals(event.getChannel().getId());
    }

    private void showRecordModal(SlashCommandInteractionEvent event) {
        TextInput contentInput = TextInput.create("content", "공부 내용", TextInputStyle.PARAGRAPH)
                .setPlaceholder("오늘 공부한 내용을 자유롭게 기록해주세요.\n여러 줄 입력이 가능합니다.")
                .setRequired(true)
                .setMaxLength(1000) // 최대 길이 제한 추가
                .build();

        Modal modal = Modal.create("record-modal", "스터디 기록 작성")
                .addComponents(ActionRow.of(contentInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void sendHelpMessage(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🤖 스터디 관리 봇 명령어 목록")
                .setColor(new Color(88, 101, 242))
                .setDescription("봇의 모든 기능을 확인하세요!")
                .addField("`/기록`", "오늘의 스터디 참여를 기록하는 팝업창을 엽니다.", false)
                .addField("`/참여도`", "모든 멤버의 누적 참여율을 확인합니다.", false)
                .addField("`/확인 [날짜: YYYY-MM-DD]`", "특정 날짜의 참여/미참여 현황을 확인합니다.", false)
                .addField("`/도움말`", "지금 보고 있는 이 도움말을 표시합니다.", false)
                .setFooter("매일 자정, 어제 스터디를 기록하지 않은 멤버를 자동으로 멘션합니다.");

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void checkRecordsByDate(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        LocalDate dateToCheck = parseDateOption(event.getOption("날짜"));
        if (dateToCheck == null) {
            event.getHook().sendMessage("⚠️ 날짜 형식이 올바르지 않습니다. `YYYY-MM-DD` 형식으로 입력해주세요.").queue();
            return;
        }

        // 비동기로 처리하여 응답 속도 개선
        CompletableFuture.runAsync(() -> {
            try {
                processDateCheck(event, dateToCheck);
            } catch (Exception e) {
                System.err.println("❌ 날짜별 확인 중 오류: " + e.getMessage());
                e.printStackTrace();
                event.getHook().sendMessage("⚠️ 현황을 불러오는 중 오류가 발생했습니다.").queue();
            }
        });
    }

    private LocalDate parseDateOption(OptionMapping dateOption) {
        try {
            if (dateOption == null) {
                return LocalDate.now(StudyBot.KST);
            }
            return LocalDate.parse(dateOption.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void processDateCheck(SlashCommandInteractionEvent event, LocalDate dateToCheck) {
        TextChannel channel = event.getChannel().asTextChannel();
        List<Member> allMembers = channel.getGuild().loadMembers().get().stream()
                .filter(m -> !m.getUser().isBot())
                .collect(Collectors.toList());

        OffsetDateTime startOfDay = dateToCheck.atStartOfDay(StudyBot.KST).toOffsetDateTime();
        OffsetDateTime endOfDay = dateToCheck.atTime(LocalTime.MAX).atZone(StudyBot.KST).toOffsetDateTime();

        Map<User, String> participants = channel.getHistory().retrievePast(StudyBot.MESSAGE_HISTORY_LIMIT).complete().stream()
                .filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser()) && !m.getEmbeds().isEmpty())
                .filter(m -> {
                    OffsetDateTime msgTime = m.getTimeCreated();
                    return !msgTime.isBefore(startOfDay) && !msgTime.isAfter(endOfDay);
                })
                .map(m -> m.getEmbeds().get(0))
                .filter(embed -> embed.getFooter() != null &&
                        embed.getFooter().getText() != null &&
                        embed.getFooter().getText().startsWith("참여자 ID:"))
                .collect(Collectors.toMap(
                        embed -> {
                            String userId = embed.getFooter().getText().substring("참여자 ID: ".length());
                            Member member = event.getGuild().getMemberById(userId);
                            return member != null ? member.getUser() : null;
                        },
                        embed -> Optional.ofNullable(embed.getDescription()).orElse("내용 없음"),
                        (existing, replacement) -> existing
                ));

        participants.remove(null);

        List<Member> absentMembers = allMembers.stream()
                .filter(member -> !participants.containsKey(member.getUser()))
                .collect(Collectors.toList());

        EmbedBuilder eb = createDateCheckEmbed(dateToCheck, participants, absentMembers);
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private EmbedBuilder createDateCheckEmbed(LocalDate date, Map<User, String> participants, List<Member> absentMembers) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🗓️ " + date.format(StudyBot.DATE_FORMATTER) + " 스터디 현황")
                .setColor(new Color(0x5865F2));

        if (participants.isEmpty()) {
            eb.addField("✅ 참여한 멤버", "참여한 멤버가 없습니다.", false);
        } else {
            StringBuilder participantBuilder = new StringBuilder();
            participants.forEach((user, content) -> {
                String truncatedContent = content.length() > 50 ?
                        content.substring(0, 50) + "..." : content;
                participantBuilder.append(String.format("**%s**: %s\n", user.getName(), truncatedContent));
            });
            eb.addField("✅ 참여한 멤버 (" + participants.size() + "명)",
                    participantBuilder.toString(), false);
        }

        if (absentMembers.isEmpty()) {
            eb.addField("❌ 미참여 멤버", "모든 멤버가 참여했습니다! 🎉", false);
        } else {
            String absentString = absentMembers.stream()
                    .map(Member::getEffectiveName)
                    .collect(Collectors.joining(", "));
            eb.addField("❌ 미참여 멤버 (" + absentMembers.size() + "명)", absentString, false);
        }

        return eb;
    }

    private void calculateAndSendParticipationRate(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        // 비동기로 처리하여 응답 속도 개선
        CompletableFuture.runAsync(() -> {
            try {
                processParticipationRate(event);
            } catch (Exception e) {
                System.err.println("❌ 참여도 계산 중 오류: " + e.getMessage());
                e.printStackTrace();
                event.getHook().sendMessage("⚠️ 현황을 불러오는 중 오류가 발생했습니다.").queue();
            }
        });
    }

    // ⭐️ 참여도 계산 메소드 수정
    private void processParticipationRate(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        List<Member> members = channel.getGuild().loadMembers().get().stream()
                .filter(m -> !m.getUser().isBot())
                .sorted(Comparator.comparing(Member::getEffectiveName))
                .collect(Collectors.toList());

        // 메시지를 100개씩 나눠서 가져올 리스트
        List<Message> historyMessages = new ArrayList<>();
        MessageHistory history = channel.getHistory();
        int pages = StudyBot.PARTICIPATION_HISTORY_LIMIT / 100;

        for (int i = 0; i < pages; i++) {
            List<Message> retrieved = history.retrievePast(100).complete();
            historyMessages.addAll(retrieved);
            if (retrieved.size() < 100) {
                break; // 더 이상 가져올 메시지가 없으면 중단
            }
        }

        Map<String, Set<LocalDate>> participationDays = historyMessages.stream()
                .filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser()) && !m.getEmbeds().isEmpty())
                .map(m -> m.getEmbeds().get(0))
                .filter(embed -> embed.getFooter() != null &&
                        embed.getFooter().getText() != null &&
                        embed.getFooter().getText().startsWith("참여자 ID:"))
                .collect(Collectors.groupingBy(
                        embed -> embed.getFooter().getText().substring("참여자 ID: ".length()),
                        Collectors.mapping(embed -> embed.getTimestamp().atZoneSameInstant(StudyBot.KST).toLocalDate(),
                                Collectors.toSet())
                ));

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 멤버별 스터디 참여율")
                .setColor(new Color(70, 130, 180))
                .setDescription("각 멤버가 서버에 참여한 날로부터의 참여율입니다.");

        for (Member member : members) {
            User user = member.getUser();
            LocalDate joinDate = member.getTimeJoined().atZoneSameInstant(StudyBot.KST).toLocalDate();
            long daysSinceJoined = Duration.between(joinDate.atStartOfDay(),
                    LocalDate.now(StudyBot.KST).atStartOfDay()).toDays() + 1;

            Set<LocalDate> participatedSet = participationDays.getOrDefault(user.getId(), Set.of());
            int participationCount = participatedSet.size();

            double rate = (daysSinceJoined > 0) ? ((double) participationCount / daysSinceJoined) * 100 : 0;

            String fieldName = String.format("%s (%.1f%%)", member.getEffectiveName(), rate);
            String fieldValue = String.format("참여: %d일 / 전체: %d일", participationCount, daysSinceJoined);
            eb.addField(fieldName, fieldValue, true);
        }

        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }
}