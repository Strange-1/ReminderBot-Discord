package com.strange1.ReminderBot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {
    static Properties properties;
    static JDA jda;
    protected static String TOKEN;
    private static Connection con;
    private static boolean isOnService = false;
    private static Timer timer;

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        properties = new Properties();
        FileInputStream is = new FileInputStream("src/config/config.properties");
        properties.load(is);
        if (args.length == 0) {
            TOKEN = getProperty("TOKEN");
            System.out.println("[main] NORMAL RUN");
            Init(false);
        } else if (args[0].equals("-t")) {
            TOKEN = getProperty("TOKENTEST");
            System.out.println("[main] TEST RUN");
            Init(true);
        }
    }

    public static void Init(boolean isTestRun) throws LoginException, InterruptedException {
        if (!connectJDBC()) {
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
        JDABuilder builder = JDABuilder.createDefault(TOKEN);
        builder.setAutoReconnect(true)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListeners(new Bot())
                .setActivity(Activity.playing("closed"));
        jda = builder.build();
        setSlashCommands();

        jda.awaitReady();
    }

    private static void BuildTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
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

    public static boolean connectJDBC() {
        String ip = getProperty("ip");
        String db = getProperty("db");
        String user = getProperty("user");
        String passwd = getProperty("passwd");
        try {
            String url = "jdbc:mariadb://" + ip + "/" + db;
            System.out.println("[connectJDBC] Connecting \"" + url + "\" by " + user + "...");
            for (int i = 0; i < 5; i++) {
                con = DriverManager.getConnection(url, user, passwd);
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
                .addCommands(new CommandData("addalarm", "adds a new alarm at specified time.")
                        .addOptions(new OptionData(OptionType.INTEGER, "day", "~day(s) later"),
                                new OptionData(OptionType.INTEGER, "hour", "~hour(s) later"),
                                new OptionData(OptionType.INTEGER, "minute", "~minute(s) later"),
                                //new OptionData(OptionType.INTEGER, "second", "~second(s) later"),
                                new OptionData(OptionType.STRING, "message", "Insert alarm message"),
                                new OptionData(OptionType.MENTIONABLE, "to", "user who receive this alarm")))
                .addCommands(new CommandData("list", "shows your active alarm(s)."))
                .addCommands(new CommandData("cancelalarm", "cancels an existing alarm by alarm code.")
                        .addOption(OptionType.STRING, "code", "Alarm to cancel", true))
                .addCommands(new CommandData("debug", "Dev command")
                        .addOptions(new OptionData(OptionType.STRING, "command", "Dev command")))
                .addCommands(new CommandData("invite", "Give me an invitation URL"))
                .addCommands(new CommandData("changetimezone", "changes your timezone. ex)GMT +- 00:00")
                        .addOptions(new OptionData(OptionType.INTEGER, "hour", "timezone hour", true),
                                new OptionData(OptionType.INTEGER, "minute", "timezone minute", false)))
                .addCommands(new CommandData("setpermission", "restricts role who can use this bot.")
                        .addOptions(new OptionData(OptionType.ROLE, "role", "Who can use this bot?", true)))
                .queue();
    }

    private static String getProperty(@NotNull String key) {
        if (properties != null) {
            return properties.getProperty(key);
        }
        return null;
    }

    private static String MilisToDateString(long PosixTime, Pair<Integer, Integer> timezone) {
        SimpleDateFormat dateFormat;
        dateFormat = new SimpleDateFormat("h:mm a, MMM dd, yyyy z");
        String tz = String.format("GMT%c%d%s", timezone.getLeft() < 0 ? '-' : '+', timezone.getLeft(), timezone.getRight() == 0 ? "" : String.format(":%2d", timezone.getRight()));
        dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
        return dateFormat.format(new Date(PosixTime));
    }

    private static EmbedBuilder MakeSimpleEmbedBuilder(String Title, String Description) {
        var eb = new EmbedBuilder();
        eb.setColor(Color.red);
        eb.setAuthor(properties.getProperty("title"));
        eb.setTitle(Title);
        eb.setDescription(Description);
        eb.setTimestamp(Instant.now());
        return eb;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String CommandName = event.getName();
        if (!isOnService && !event.getUser().getId().equals(properties.getProperty("devId"))) {
            event.reply("503").setEphemeral(true).queue();
            return;
        }

        if (CommandName.equals("debug")) {
            if (!event.getUser().getId().equals(properties.getProperty("devId"))) {
                event.reply("403").setEphemeral(true).queue();
                return;
            } else if (event.getOption("command") == null) {
                event.reply("Wrong command, sir").setEphemeral(true).queue();
                return;
            }
            switch (event.getOption("command").getAsString()) {
                case "forcesystemshutdown":
                    event.reply("Shutting down").setEphemeral(true).complete();
                    if (timer != null)
                        timer.cancel();
                    jda.shutdown();
                    break;
                case "beginservice":
                    jda.getPresence().setStatus(OnlineStatus.ONLINE);
                    jda.getPresence().setActivity(Activity.playing("On service"));
                    isOnService = true;
                    event.reply("awaiting commands").setEphemeral(true).queue();
                    break;
                default:
                    event.reply("Wrong command, sir").setEphemeral(true).queue();
                    break;
            }
            return;
        } else {/*
            try {
                if (!hasPermission(event)) {
                    event.reply("Not enough permission. Please ask to Admin.").setEphemeral(true).queue();
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }*/

            long CommandTime = System.currentTimeMillis();
            switch (CommandName) {
                case "ping":
                    long time = System.currentTimeMillis();
                    event.reply("Pong!").setEphemeral(true).flatMap(v -> event.getHook().editOriginalFormat(
                            "Pong: %d ms", System.currentTimeMillis() - time)
                    ).queue();
                    break;
                case "invite":
                    event.replyEmbeds(MakeSimpleEmbedBuilder("ReminderBot - Invitation", properties.getProperty("invitationURL")).build()).setEphemeral(true).queue();
                    break;
                case "addalarm":
                    long day = 0, hour = 0, min = 0, sec = 0;
                    String message = "";
                    IMentionable user = event.getUser();
                    for (var i : event.getOptions()) {
                        switch (i.getName()) {
                            case "day":
                                day = i.getAsLong();
                                break;
                            case "hour":
                                hour = i.getAsLong();
                                break;
                            case "minute":
                                min = i.getAsLong();
                                break;
                            case "second":
                                sec = i.getAsLong();
                                break;
                            case "message":
                                message = i.getAsString();
                                break;
                            case "to":
                                user = i.getAsMentionable();
                                break;
                        }
                    }

                    if (day < 0 || day > 30
                            || hour < 0 || hour > 23
                            || min < 0 || min > 59
                            || sec < 0 || sec > 59
                            || day + hour + min + sec == 0) {
                        event.reply("Error: Wrong time data").setEphemeral(true).queue();
                        return;
                    }
                    long milis = day * 24 * 60 * 60 * 1000
                            + hour * 60 * 60 * 1000
                            + min * 60 * 1000
                            + sec * 1000;
                    var v = event.replyEmbeds(MakeSimpleEmbedBuilder("New Alarm", "Processing").build()).setEphemeral(true).complete();
                    SqlScheduleBundle bundle = new SqlScheduleBundle();
                    try {
                        bundle.setId(BotSQL.getNewId());
                        bundle.setGuild(event.getGuild().getId());
                        bundle.setMessageChannel(event.getMessageChannel().getId());
                        bundle.setClient(event.getUser().getId());
                        bundle.setMessage(message);
                        bundle.setListedTime(CommandTime);
                        bundle.setAlarmTime(CommandTime + milis);
                        bundle.setTo(user.getAsMention());
                        bundle.setRepeatTime(0);
                        bundle.setStatus(SqlScheduleBundle.StatusId.ACTIVE);
                        bundle.setCode(BotSQL.MakeNewCode(properties.getProperty("randomcode")));
                        if (BotSQL.WriteSchedule(bundle)) {
                            v.editOriginalEmbeds(
                                    MakeSimpleEmbedBuilder(
                                            "New Alarm - Success",
                                            String.format("A new alarm is added at %s\nAlarm code: %s",
                                                    MilisToDateString(bundle.AlarmTime, BotSQL.ReadTimezoneById(bundle.ClientId)),
                                                    bundle.Code))
                                            .build()).queue();
                        } else {
                            v.editOriginalEmbeds(MakeSimpleEmbedBuilder("New Alarm - Error", "Error occured. Please try again later.").build()).queue();
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "list":
                    ResultSet resultSet_listcommand;
                    try {
                        resultSet_listcommand = BotSQL.ReadSchedulesById(event.getGuild().getId(), event.getMessageChannel().getId(), event.getUser().getId());
                        StringBuilder query = new StringBuilder();
                        int count = 0;
                        while (resultSet_listcommand.next()) {
                            count++;
                            query.append(String.format("%s: %s - %s\n", resultSet_listcommand.getString(11), MilisToDateString(resultSet_listcommand.getLong(7), BotSQL.ReadTimezoneById(event.getUser().getId())), resultSet_listcommand.getString(5) != null ? resultSet_listcommand.getString(5) : "NULL"));
                        }
                        if (count == 0) {
                            event.reply("There is no active alarm.").setEphemeral(true).queue();
                            return;
                        }
                        var eb = MakeSimpleEmbedBuilder("Alarm List", String.format("%d found\n\n%s\nAlphabet %s is not used in the alarm code.", count, query, properties.getProperty("randomNotused")));

                        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "cancelalarm":
                    var canceloption = event.getOption("code");
                    if (canceloption == null) {
                        event.reply("Error: insert alarm code. ex) ABCDEFGH").queue();
                        return;
                    }
                    ResultSet resultSet_cancelcommand;
                    try {
                        int deletedCount = BotSQL.CancelSchedulesByCode(event.getUser().getId(), canceloption.getAsString().toUpperCase());
                        if (deletedCount > 0)
                            event.reply("Successfully cancelled.").setEphemeral(true).queue();
                        else
                            event.reply("Cannot find active alarm of that code.").setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "changetimezone":
                    int timezoneHour = ((int) event.getOption("hour").getAsLong());
                    int timezoneMinute = event.getOption("minute") != null ? ((int) event.getOption("minute").getAsLong()) : 0;
                    if (timezoneHour < 0 || timezoneHour > 12) {
                        event.reply("Timezone hour must be in range of 0 ~ 12.").setEphemeral(true).queue();
                        return;
                    }
                    if (timezoneMinute < 0 || timezoneMinute > 59) {
                        event.reply("Timezone minute must be in range of 0 ~ 59.").setEphemeral(true).queue();
                        return;
                    }
                    try {
                        if (BotSQL.ChangeTimezone(event.getUser().getId(), timezoneHour, timezoneMinute) > 0)
                            event.reply("Timezone is successfully changed.").setEphemeral(true).queue();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
                case "setpermission":
                    break;
            }
        }
    }
/*
    private static boolean hasPermission(SlashCommandEvent event) throws SQLException {
        if (isAdmin(event))
            return true;
        else
            return false;
    }

    private static boolean isAdmin(SlashCommandEvent event) {
        return event.getGuild().getMember(event.getUser()).hasPermission(Permission.ADMINISTRATOR);
    }
*/
    private static void DoAlarm(String GuildChannelId, String MessageChannelId, String Message, String To) {
        jda.getTextChannelById(MessageChannelId).sendMessage(MakeSimpleEmbedBuilder("Alarm!", String.format("%s\n%s", To, Message)).build()).queue();
    }
}