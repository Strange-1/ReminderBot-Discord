package com.strange1.ReminderBot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class Bot extends ListenerAdapter {
    static Properties properties;
    static JDA jda;
    protected static String TOKEN = "";
    private static boolean isOnService = false;
    private static Timer timer;
    private static String manualPath;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        properties = new Properties();
        FileInputStream is = new FileInputStream("src/config/config.properties");
        properties.load(is);
        if (args.length == 0) { //args 없음, 토큰 수동으로 입력
            System.out.print("TOKEN(NORMAL): ");
            TOKEN = Files.readAllLines(Path.of("../TOKEN")).get(0);
            manualPath = getProperty("manualPathNormal");
            System.out.println(String.format("%s\n[main] NORMAL RUN", TOKEN));
            Init(false);
        } else if (args[0].equals("-t")) { //테스트 설정으로 실행
            TOKEN = args[1];
            manualPath = getProperty("manualPathTest");
            System.out.println("[main] TEST RUN");
            Init(true);
        } else if (args[0].equals("-n")) { //통상 설정으로 실행
            TOKEN = args[1];
            manualPath = getProperty("manualPathNormal");
            System.out.println(String.format("[main] NORMAL RUN by TOKEN %s", args[1]));
            Init(false);
        }
    }

    public static void Init(boolean isTestRun) throws LoginException, InterruptedException {
        if (!connectJDBC(getProperty(isTestRun ? "testip" : "normalip"))) {
            System.out.println("[Init] Aborting boot");
            return;
        }
        BuildJDA();
        if (isTestRun) {
            System.out.println("[Init] Beginning test...");
        } else {
            BuildTimer();
            System.out.println("[Init] Hey ya!");
        }
    }

    private static void BuildJDA() throws LoginException, InterruptedException {
        JDABuilder builder = JDABuilder
                .createDefault(TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOTE)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setAutoReconnect(true)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListeners(new Bot())
                .setActivity(Activity.playing("Closed"));
        jda = builder.build();
        setSlashCommands();
        jda.awaitReady();
    }

    private static void BuildTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isOnService)
                    return;
                jda.getPresence().setActivity(Activity.playing(String.format("Running on %d servers", getServerCount())));
                long currentTime = System.currentTimeMillis();
                long timePrevious = currentTime - currentTime % 60000;
                long timeNext = currentTime - currentTime % 60000 + 60000;
                try {
                    var rs = BotSQL.ReadSchedulesByTime(timePrevious, timeNext, true);
                    while (rs.next()) {
                        DoAlarm(rs.getString(2), rs.getString(3), rs.getString(5), rs.getString(8));
                        BotSQL.CompleteSchedulesByCode(rs.getString(11));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 60000);
    }

    public static boolean connectJDBC(String IP) {
        String ip = IP;
        String db = getProperty("db");
        String user = getProperty("user");
        String passwd = getProperty("passwd");
        try {
            String url = "jdbc:mariadb://" + ip + "/" + db;
            System.out.println("[connectJDBC] Connecting \"" + url + "\" by " + user + "...");
            for (int i = 0; i < 5; i++) {
                Connection con = DriverManager.getConnection(url, user, passwd);
                if (con.isValid(10)) {
                    con.setAutoCommit(true);
                    System.out.println("[connectJDBC] JDBC connection established");
                    BotSQL.setConnection(con);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("[connectJDBC] SQL ERROR: " + e.getMessage());
        }
        System.out.println("[connectJDBC] FATAL: JDBC connection FAILURE");
        return false;
    }

    public static void setSlashCommands() {
        jda.updateCommands().addCommands(new CommandData("ping", "Ping!"))
                .addCommands(new CommandData("addtimer", "Adds an alarm that will ring after the set time.")
                                .addOptions(new OptionData(OptionType.INTEGER, "day", "~day(s) later (>=0)"),
                                        new OptionData(OptionType.INTEGER, "hour", "~hour(s) later (-24~23)"),
                                        new OptionData(OptionType.INTEGER, "minute", "~minute(s) later (-60~59)"),
                                        new OptionData(OptionType.STRING, "message", "Enter a message."),
                                        new OptionData(OptionType.MENTIONABLE, "to", "user(s) who receive this alarm"))
                        , new CommandData("addalarm", "Adds an alarm that will ring scheduled time.")
                                .addOptions(new OptionData(OptionType.INTEGER, "hour", "alarm hour(24h) ex)14", true),
                                        new OptionData(OptionType.INTEGER, "minute", "alarm minute ex)15", true),
                                        new OptionData(OptionType.INTEGER, "year", "alarm year ex)2021"),
                                        new OptionData(OptionType.INTEGER, "month", "alarm month ex)7"),
                                        new OptionData(OptionType.INTEGER, "day", "alarm day ex)31"),
                                        new OptionData(OptionType.STRING, "message", "Enter a message"),
                                        new OptionData(OptionType.MENTIONABLE, "to", "user(s) who receive this alarm"))
                        , new CommandData("list", "Displays your scheduled alarm(s).")
                        , new CommandData("listall", "Displays everyone's scheduled alarms in this server.")
                        , new CommandData("cancelalarm", "Cancels the scheduled alarm. ")
                                .addOption(OptionType.STRING, "code", "a Code of alarm to cancel", true)
                        , new CommandData("debug", "Dev command")
                                .addOption(OptionType.STRING, "command", "Dev command", true)
                        , new CommandData("invite", "Gives an invitation URL to you.")
                        , new CommandData("changetimezone", "Changes your timezone. ex)GMT +- 00:00")
                                .addOptions(new OptionData(OptionType.INTEGER, "hour", "Timezone hour", true),
                                        new OptionData(OptionType.INTEGER, "minute", "Timezone minute", false))
                        , new CommandData("setauthority", "Choose a role who can use this bot.")
                                .addOption(OptionType.ROLE, "role", "Who can use this bot?", true)
                        , new CommandData("whocanusethis", "What role can use this bot?")
                        , new CommandData("help", "Command manual")
                                .addOption(OptionType.STRING, "command", "Enter a command")
                ).queue();
    }

    private static String getProperty(@NotNull String key) {
        if (properties != null) {
            return properties.getProperty(key);
        }
        return null;
    }

    private static Pair<String, String> MilisToDateString(long PosixTime, Pair<Integer, Integer> timezone) {
        SimpleDateFormat dateFormat, timeFormat;
        dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);
        timeFormat = new SimpleDateFormat("h:mm a (z)", Locale.ENGLISH);
        String tz = String.format("GMT%c%d%s", timezone.getLeft() < 0 ? '-' : '+', timezone.getLeft(), timezone.getRight() == 0 ? "" : String.format(":%2d", timezone.getRight()));
        timeFormat.setTimeZone(TimeZone.getTimeZone(tz));
        dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
        return Pair.of(dateFormat.format(new Date(PosixTime)), timeFormat.format(new Date(PosixTime)));
    }

    private static EmbedBuilder MakeSimpleEmbedBuilder(String Title, String Description) {
        var eb = new EmbedBuilder();
        eb.setColor(Color.red)
                .setAuthor(properties.getProperty("title"))
                .setTitle(Title)
                .setDescription(Description)
                .setTimestamp(Instant.now());
        return eb;
    }

    private static EmbedBuilder MakeAnnounceEmbedBuilder(String Title, String Description, String Announcement) {
        var eb = MakeSimpleEmbedBuilder(Title, Description);
        if (Announcement.isEmpty())
            return eb;
        else
            return eb.setDescription(String.format("%s\n\nGlobal announcement:\n%s", Description, Announcement));
    }

    public static int getServerCount() {
        return jda.getGuilds().size();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        var CommandTime = new TimeData(System.currentTimeMillis());
        try {
            String fullCommand = event.getCommandPath();
            for (var option : event.getOptions()) {
                fullCommand += String.format(" %s", option.toString());
            }
            BotSQL.addLog(event.getGuild(), event.getMessageChannel(), event.getUser(), fullCommand, CommandTime.getPosixTime());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //Dev 권한 확인
        String CommandName = event.getName();
        if (!isOnService && !event.getUser().getId().equals(properties.getProperty("devId"))) {
            event.reply("503").setEphemeral(true).queue();
            return;
        }

        //디버그 커맨드. Dev 외에는 사용 불가능해야 함
        if (CommandName.equals("debug")) {
            if (!event.getUser().getId().equals(properties.getProperty("devId"))) {
                event.reply("403").setEphemeral(true).queue();
                return;
            } else if (event.getOption("command") == null) {
                event.reply("Wrong command, sir").setEphemeral(true).queue();
                return;
            }
            switch (event.getOption("command").getAsString()) {
                case "forcesystemshutdown": //서비스 중단. 봇 종료는 반드시 이를 이용할 것.
                    event.reply("Shutting down").setEphemeral(true).complete();
                    if (timer != null)
                        timer.cancel();
                    jda.shutdown();
                    break;
                case "beginservice": //서비스 시작. 이 명령이 없으면 Dev만 서비스 이용 가능
                    jda.getPresence().setStatus(OnlineStatus.ONLINE);
                    jda.getPresence().setActivity(Activity.playing("On Service"));
                    isOnService = true;
                    event.reply("awaiting commands").setEphemeral(true).queue();
                    break;
                default:
                    event.reply("Wrong command, sir").setEphemeral(true).queue();
                    break;
            }
            return;
        } else {
            if (event.getGuild() == null) //DM인 경우
            {
                event.reply("Sorry, I do not support direct messages.").queue();
                return;
            }

            //일반 사용자 커맨드. 서버별 권한 확인 필요
            try {
                if (!hasPermission(event.getGuild(), event.getUser().getId())) {
                    event.reply("Not enough authority. Please ask to Admin.").setEphemeral(true).queue();
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            //개인별 명령 권한 통과

            String message;
            IMentionable user;
            ResultSet resultSet_listcommand = null;
            long day = 0, hour = 0, min = 0;
            switch (CommandName) {
                case "ping": //핑
                    var pinghook = event.reply("Pong!").setEphemeral(true).complete();
                    pinghook.editOriginalFormat("Pong: %d ms", System.currentTimeMillis() - CommandTime.getPosixTime()).queue();
                    break;
                case "invite": //초대 URL 출력
                    event.replyEmbeds(MakeSimpleEmbedBuilder("ReminderBot - Invitation", properties.getProperty("invitationURL")).build())
                            .setEphemeral(true).queue();
                    break;
                case "addtimer": //타이머식 알람 추가
                    message = "";
                    user = event.getUser();
                    for (var i : event.getOptions()) {
                        switch (i.getName()) {
                            case "day":
                                day += i.getAsLong();
                                break;
                            case "hour":
                                if (hour < 0) {
                                    day--;
                                    hour += 24;
                                }
                                hour += i.getAsLong();
                                break;
                            case "minute":
                                if (min < 0) {
                                    hour--;
                                    min += 60;
                                }
                                min += i.getAsLong();
                                break;
                            case "message":
                                message = i.getAsString();
                                break;
                            case "to":
                                user = i.getAsMentionable();
                                break;
                        }
                    }
                    if (day < 0
                            || hour < 0 || hour > 23
                            || min < 0 || min > 59
                            || day + hour + min == 0) {
                        event.reply("Error: Wrong time data").setEphemeral(true).queue();
                        return;
                    }
                    long milis = day * 24 * 60 * 60 * 1000
                            + hour * 60 * 60 * 1000
                            + min * 60 * 1000;
                    long newAlarmTime = CommandTime.getPosixTime() + milis;
                    addNewAlarm(event, message, CommandTime.getPosixTime(), newAlarmTime, user);
                    break;
                case "addalarm": //시각 지정식 알람 추가
                    int year = CommandTime.getField(TimeData.Field.YEAR);
                    int month = CommandTime.getField(TimeData.Field.MONTH);
                    day = CommandTime.getField(TimeData.Field.DAY);
                    message = "";
                    user = event.getUser();
                    for (var i : event.getOptions()) {
                        switch (i.getName()) {
                            case "year":
                                year = (int) i.getAsLong();
                                break;
                            case "month":
                                month = (int) i.getAsLong();
                                break;
                            case "day":
                                day = i.getAsLong();
                                break;
                            case "hour":
                                hour = i.getAsLong();
                                break;
                            case "minute":
                                min = i.getAsLong();
                                break;
                            case "message":
                                message = i.getAsString();
                                break;
                            case "to":
                                user = i.getAsMentionable();
                                break;
                        }
                    }
                    var newAlarmDate = new TimeData();
                    try {
                        Pair<Integer, Integer> timezone = BotSQL.ReadTimezoneById(event.getUser().getId(), null);
                        if (timezone == null) {
                            event.reply("Error: Your timezone is not set. Please use \"/changetimezone\" first.").setEphemeral(true).queue();
                            return;
                        }
                        newAlarmDate.setTimeZone(SimpleTimeZone.getTimeZone(
                                        String.format("GMT%c%d%s", timezone.getLeft() < 0 ? '-' : '+', timezone.getLeft(), timezone.getRight() == 0 ? "" : String.format(":%2d", timezone.getRight()))))
                                .setField(TimeData.Field.YEAR, year)
                                .setField(TimeData.Field.MONTH, month)
                                .setField(TimeData.Field.DAY, (int) day)
                                .setField(TimeData.Field.HOUR, (int) hour)
                                .setField(TimeData.Field.MINUTE, (int) min);
                        addNewAlarm(event, message, CommandTime.getPosixTime(), newAlarmDate.getPosixTime(), user);
                    } catch (Exception e) {
                        event.reply("Error: Wrong time data").setEphemeral(true).queue();
                        return;
                    }
                    break;
                case "list": //알람 목록 출력.
                    try {
                        resultSet_listcommand = BotSQL.ReadSchedulesById(event.getGuild().getId(), event.getMessageChannel().getId(), event.getUser().getId());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    // Do not break;
                case "listall":
                    try {
                        if (resultSet_listcommand == null)
                            resultSet_listcommand = BotSQL.ReadSchedulesById(event.getGuild().getId());
                        StringBuilder query = new StringBuilder();
                        int count = 0;
                        Pair<String, String> datePair;
                        while (resultSet_listcommand.next()) {
                            count++;
                            datePair = MilisToDateString(resultSet_listcommand.getLong(7), BotSQL.ReadTimezoneById(event.getUser().getId(), Pair.of(0, 0)));
                            TextChannel messageChannel = event.getGuild().getTextChannelById(resultSet_listcommand.getString(3));
                            query.append(String.format(
                                    "Alarm code: %s\nDate: %s\nTime: %s\nChannel: %s\nMessage: %s\n" +
                                            "To: %s\nBy: %s\n\n"
                                    , resultSet_listcommand.getString(11)
                                    , datePair.getLeft()
                                    , datePair.getRight()
                                    , messageChannel != null ? messageChannel.getName() : "== Deleted channel =="
                                    , resultSet_listcommand.getString(5) != null ? resultSet_listcommand.getString(5) : "NULL"
                                    , resultSet_listcommand.getString(8)
                                    , jda.getUserById(resultSet_listcommand.getString(4)).getName()
                            ));
                        }
                        if (count == 0) {
                            event.reply("There is no scheduled alarm.").setEphemeral(true).queue();
                            return;
                        }
                        var eb = MakeSimpleEmbedBuilder("Alarm List", String.format("%d found\n\n%s\nAlphabet %s is not used in the alarm code.", count, query, properties.getProperty("randomNotused")));

                        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "cancelalarm": //알람 취소
                    var canceloption = event.getOption("code");
                    if (canceloption == null) {
                        event.reply("Error: Enter the alarm code. ex) ABCDEFGH").queue();
                        return;
                    } else if (canceloption.getAsString().equals(properties.getProperty("devmanalarm"))) {
                        event.reply("Dev team's holiday plan has been Successfully cancelled. (Not really)").setEphemeral(true).queue();
                        return;
                    }
                    ResultSet resultSet_cancelcommand;
                    try {
                        int deletedCount = BotSQL.CancelSchedulesByCode(event.getUser().getId(), canceloption.getAsString().toUpperCase());
                        if (deletedCount > 0)
                            event.reply("Successfully cancelled.").setEphemeral(true).queue();
                        else
                            event.reply("Cannot find any scheduled alarm of that code.").setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "changetimezone": //알람 시간대 설정
                    int timezoneHour = ((int) event.getOption("hour").getAsLong());
                    int timezoneMinute = event.getOption("minute") != null ? ((int) event.getOption("minute").getAsLong()) : 0;
                    if (timezoneHour < -12 || timezoneHour > 14) {
                        event.reply("Enter the hour between -12 and 14.").setEphemeral(true).queue();
                        return;
                    }
                    if (timezoneMinute < 0 || timezoneMinute > 59) {
                        event.reply("Enter the minute between 0 and 59.").setEphemeral(true).queue();
                        return;
                    }
                    try {
                        if (BotSQL.ChangeTimezone(event.getUser().getId(), timezoneHour, timezoneMinute) > 0)
                            event.reply("Timezone has been successfully changed.").setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "setauthority": //이용 가능한 역할을 설정. 서버 관리자 전용 기능
                    try {
                        if (hasPermission(event.getGuild(), event.getUser().getId())) {
                            BotSQL.setPermission(event.getGuild(), event.getOption("role").getAsRole().getId());
                            event.reply("Authority changed.").setEphemeral(true).queue();
                        } else
                            event.reply("Denied: please ask to administrators.").setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "whocanusethis": //이용 가능한 역할을 확인
                    try {
                        var roleId = BotSQL.getPermission(event.getGuild());
                        if (roleId.equals("@everyone"))
                            event.reply("Anyone can use this bot.").setEphemeral(true).queue();
                        else {
                            var role = event.getGuild().getRoleById(roleId);
                            event.reply(String.format("%s can use this bot.", role.getAsMention())).setEphemeral(true).queue();
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "help":
                    String filePos = manualPath;
                    String lines = "";
                    String command = event.getOption("command") != null ? event.getOption("command").getAsString() : "help";
                    if (command.startsWith("/")) command = command.substring(1);
                    try {
                        lines = Files.readString(Path.of(filePos + command));
                    } catch (Exception e) {
                        command = "help";
                        try {
                            lines = Files.readString(Path.of(filePos + command));
                        } catch (Exception ex) {
                            event.reply("Error: Wrong command.").setEphemeral(true).queue();
                        }
                    }
                    event.replyEmbeds(MakeSimpleEmbedBuilder(String.format("Manual - %s", command), lines).build()).setEphemeral(true).queue();
                    break;
            }
        }

    }

    private static boolean addNewAlarm(SlashCommandEvent event, String message, long ListedTime, long AlarmTime, IMentionable user) {
        var v = event.replyEmbeds(MakeSimpleEmbedBuilder("New Alarm", "Processing").build()).setEphemeral(true).complete();
        SqlScheduleBundle bundle = new SqlScheduleBundle();
        try {
            bundle.setId(BotSQL.getNewId());
            bundle.setGuild(event.getGuild().getId());
            bundle.setMessageChannel(event.getMessageChannel().getId());
            bundle.setClient(event.getUser().getId());
            bundle.setMessage(message);
            bundle.setListedTime(ListedTime);
            bundle.setAlarmTime(AlarmTime);
            bundle.setTo(user.getAsMention());
            bundle.setRepeatTime(0);
            bundle.setStatus(SqlScheduleBundle.StatusId.ACTIVE);
            bundle.setCode(BotSQL.MakeNewCode(properties.getProperty("randomcode")));
            if (AlarmTime <= System.currentTimeMillis()) {
                v.editOriginalEmbeds(MakeSimpleEmbedBuilder("New Alarm - Error", "Date and time must be future.").build()).queue();
                return false;
            }
            if (BotSQL.WriteSchedule(bundle)) {
                Pair<Integer, Integer> timezone = BotSQL.ReadTimezoneById(bundle.ClientId, null);
                v.editOriginalEmbeds(
                        MakeSimpleEmbedBuilder(
                                "New Alarm - Success",
                                String.format("A new alarm has been added at %s\nAlarm code: %s\n%s",
                                        MilisToDateString(bundle.AlarmTime, timezone != null ? timezone : Pair.of(0, 0)),
                                        bundle.Code,
                                        timezone != null ? "" : "Your timezone is not set. Please use [/changetimezone]."))
                                .build()).queue();
            } else {
                v.editOriginalEmbeds(MakeSimpleEmbedBuilder("New Alarm - Error", "Error occured. Please try again later.").build()).queue();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean hasPermission(Guild guild, String userId) throws SQLException { //특정 멤버의 봇 사용 권한 확인하기
        var roleId = BotSQL.getPermission(guild);
        if (roleId.equals("@everyone") || guild.getRoleById(roleId).getName().equals("@everyone")) //제한이 없는 경우
            return true;
        else if (guild.getMembersWithRoles(guild.getRoleById(roleId)).stream().filter(member -> member.getId().equals(userId)).findFirst().orElse(null) != null) { //제한이 있으며 사용자가 이에 부합하는 경우
            return true;
        } else if (getPermissions(guild, userId).contains(Permission.ADMINISTRATOR)) { //사용자가 서버의 관리자인 경우
            return true;
        } else
            return false;
    }

    private static EnumSet<Permission> getPermissions(Guild guild, String userId) { //특정 멤버의 권한 목록 가져오기
        final Member member[] = new Member[1];
        guild.loadMembers().onSuccess(new Consumer<List<Member>>() {
            @Override
            public void accept(List<Member> memberList) {
                member[0] = memberList.stream().filter(m -> m.getId().equals(userId)).findFirst().orElse(null);
            }
        }).onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        var user = guild.getMembers().stream().filter(m -> m.getId().equals(userId)).findFirst().orElse(null);
        if (user == null)
            return null;
        else
            return user.getPermissions();
    }


    private static void DoAlarm(String GuildId, String MessageChannelId, String Message, String To) { //시간이 된 알람 울리기
        Guild guild = jda.getGuildById(GuildId);
        TextChannel messageChannel;
        if (guild != null) messageChannel = guild.getTextChannelById(MessageChannelId);
        else return;
        if (messageChannel != null) {
            messageChannel.sendMessage(String.format("%s %s", To, Message)).queue();
        }
    }

}
