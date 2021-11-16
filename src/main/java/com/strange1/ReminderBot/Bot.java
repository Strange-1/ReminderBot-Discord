package com.strange1.ReminderBot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bot extends ListenerAdapter {
    static Properties properties;
    static JDA jda;
    protected static String TOKEN;
    private static Connection con;
    private static Timer scheduleTimer;
    private static EmbedBuilder embedBuilder;
    private static boolean isOnService = false;

    String[] messages;
    long sqlTime;
    String sqlMessage;

    public static void main(String[] args) throws LoginException, InterruptedException, SQLException, IOException {
        properties = new Properties();
        FileInputStream is = new FileInputStream("src/config/config.properties");
        properties.load(is);
        TOKEN = getProperty("TOKEN");
        Init();
    }

    public static void Init() throws LoginException, InterruptedException {
        BuildJDA();

        System.out.println("Hey ya!");
    }

    private static void BuildJDA() throws LoginException, InterruptedException {
        JDABuilder builder = JDABuilder.createDefault(TOKEN);
        builder.setAutoReconnect(true)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListeners(new Bot())
                .setActivity(Activity.playing("closed"));
        jda = builder.build();
        setSlashCommands();
        connectJDBC();
        jda.awaitReady();
    }

    public static void connectJDBC() {
        String ip = getProperty("ip");
        String db = getProperty("db");
        String user = getProperty("user");
        String passwd = getProperty("passwd");
        try {
            String url = "jdbc:mariadb://" + ip + "/" + db;
            System.out.println("Connecting \"" + url + "\" by " + user + "...");
            con = DriverManager.getConnection(url, user, passwd);
            if (con.isValid(0)) {
                con.setAutoCommit(true);
                System.out.println("JDBC connection established");
            }
        } catch (SQLException e) {
            System.out.println("SQL ERROR: " + e.getMessage());
        }
        BotSQL.setConnection(con);
    }

    public static void setSlashCommands() {
        jda.updateCommands().addCommands(new CommandData("ping", "Ping!"))
                .addCommands(new CommandData("addtime", "adds a new alarm at specified time.")
                        .addOptions(new OptionData(OptionType.INTEGER, "day", "~day(s) later"),
                                new OptionData(OptionType.INTEGER, "hour", "~hour(s) later"),
                                new OptionData(OptionType.INTEGER, "minute", "~minute(s) later"),
                                //new OptionData(OptionType.INTEGER, "second", "~second(s) later"),
                                new OptionData(OptionType.STRING, "message", "Insert alarm message"),
                                new OptionData(OptionType.MENTIONABLE, "to", "user who receive this alarm")))
                .addCommands(new CommandData("list", "shows your active alarm(s)."))
                .addCommands(new CommandData("debug", "Dev command")
                        .addOptions(new OptionData(OptionType.STRING, "command", "Dev command"))).queue();
    }

    private static String getProperty(@NotNull String key) {
        if (properties != null) {
            return properties.getProperty(key);
        }
        return null;
    }

    private static String MilisToDateString(long PosixTime, String Locale) {
        String timeFormat;
        switch (Locale) {
            case "KR":
                timeFormat = "yyyy년 M월 d일 HH시 mm분";
                break;
            default:
                timeFormat = "HH:mm:ss d MMM yyyy";
                break;
        }
        return new SimpleDateFormat(timeFormat).format(new Date(PosixTime));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (!isOnService) {
            event.reply("503").setEphemeral(true).queue();
            return;
        }
        String CommandName = event.getName();
        long CommandTime = System.currentTimeMillis();
        switch (CommandName) {
            case "debug":
                if (!event.getUser().getId().equals(properties.getProperty("devId"))) {
                    event.reply("403").setEphemeral(true).queue();
                    return;
                }
                switch (event.getOption("command").getAsString()) {
                    case "forcesystemshutdown":
                        event.reply("Shutting down").setEphemeral(true).complete();
                        jda.shutdownNow();
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
                break;
            case "ping":
                long time = System.currentTimeMillis();
                event.reply("Pong!").setEphemeral(true).flatMap(v -> event.getHook().editOriginalFormat(
                        "Pong: %d ms", System.currentTimeMillis() - time)
                ).queue();
                break;
            case "addtime":
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
                var v = event.reply("Processing...").setEphemeral(true).complete();
                SqlScheduleBundle bundle = new SqlScheduleBundle();
                try {
                    bundle.setId(BotSQL.getNewId());
                    bundle.setGuild(event.getGuildChannel().getId());
                    bundle.setMessageChannel(event.getMessageChannel().getId());
                    bundle.setClient(event.getUser().getId());
                    bundle.setMessage(message);
                    bundle.setListedTime(CommandTime);
                    bundle.setAlarmTime(CommandTime + milis);
                    bundle.setTo(user.getAsMention());
                    bundle.setRepeatTime(0);
                    bundle.setStatus(SqlScheduleBundle.StatusId.ACTIVE);
                    if (BotSQL.WriteSchedule(bundle)) {
                        v.editOriginalFormat("A new alarm is added at\n%s", MilisToDateString(bundle.AlarmTime, BotSQL.ReadLocaleById(bundle.ClientId))).queue();
                    } else {
                        v.editOriginalFormat("Error occured. Please try again later.").queue();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                break;
            case "list":
                ResultSet resultSet = null;
                SqlScheduleBundle line;
                try {
                    resultSet = BotSQL.ReadSchedulesById(event.getGuildChannel().getId(), event.getMessageChannel().getId(), event.getUser().getId());
                    String query = "";
                    int count = 0;
                    while (resultSet.next()) {
                        count++;
                        query += String.format("%s: %s - %s\n", count, MilisToDateString(resultSet.getLong(7), "KR"), resultSet.getString(5));
                    }
                    if (count == 0) {
                        event.reply("Nothing to show").setEphemeral(true).queue();
                        return;
                    }
                    var eb = new EmbedBuilder();
                    eb.setColor(Color.red);
                    eb.setAuthor(properties.getProperty("title"));
                    eb.setTitle("Alarm List");
                    eb.setDescription(String.format("%d found\n\n%s", count, query));
                    eb.setTimestamp(Instant.now());

                    event.replyEmbeds(eb.build()).setEphemeral(true).queue();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
        }
    }
}

