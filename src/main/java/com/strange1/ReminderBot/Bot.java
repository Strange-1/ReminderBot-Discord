package com.strange1.ReminderBot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

public class Bot extends ListenerAdapter {
    static Properties properties;
    static JDA jda;
    protected static String TOKEN;
    private static Connection con;

    public static void main(String[] args) throws LoginException, InterruptedException, SQLException, IOException {
        properties = new Properties();
        FileInputStream is = new FileInputStream("src/config/config.properties");
        properties.load(is);
        TOKEN = properties.getProperty("TOKEN");

        if (args.length > 0 && List.of(new String[]{"-t", "/t", "--test"}).contains(args[0])) {
            doTest();
            if (!con.isClosed()) {
                con.close();
                System.out.println("Connection Closed");
            }
        } else
            Init();
    }

    public static void Init() throws LoginException, InterruptedException {
        JDABuilder builder = JDABuilder.createDefault(TOKEN);
        builder.setAutoReconnect(true)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListeners(new Bot())
                .setActivity(Activity.playing("Type /ping"));
        jda = builder.build();
        jda.updateCommands()
                .addCommands(new CommandData("ping", "Ping!"))
                .addCommands(new CommandData("info", "show bot's information."))
                .queue();
        connectJDBC();
        jda.awaitReady();

        System.out.println("Hey ya!");
    }

    public static int connectJDBC() {
        String ip = properties.getProperty("ip");
        String db = properties.getProperty("db");
        String user = properties.getProperty("user");
        String passwd = properties.getProperty("passwd");
        try {
            String url = "jdbc:mariadb://" + ip + "/" + db;
            System.out.println("Connecting \"" + url + "\" by " + user + "...");
            con = DriverManager.getConnection(url, user, passwd);
            if (con.isValid(0)) {
                System.out.println("JDBC connection established");
                return 0;
            }
        } catch (SQLException e) {
            System.out.println("SQL ERROR: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    protected static ArrayList<SqlBundle> readSQL(String sql) throws SQLException {
        PreparedStatement statement = con.prepareStatement(sql == null ? "select * from log;" : sql);
        ResultSet rs = statement.executeQuery();
        ArrayList<SqlBundle> bundles = new ArrayList<>();
        while (rs.next()) {
            bundles.add(new SqlBundle(
                    rs.getLong("id"),
                    rs.getString("channel"),
                    rs.getString("client"),
                    rs.getLong("time"),
                    rs.getString("message"),
                    rs.getString("object"),
                    rs.getLong("repeattime"),
                    rs.getString("status")
            ));
        }
        return bundles;
    }

    protected static int deleteSQL(long id) throws SQLException {
        PreparedStatement statement = con.prepareStatement("delete from log where id=?");
        statement.setLong(1, id);
        return statement.executeUpdate() == 1 ? 0 : 1;
    }

    protected static int writeSQL(SqlBundle bundle) throws SQLException {
        PreparedStatement statement = con.prepareStatement("insert into log values (?,?,?,?,?,?,?,?);");
        statement.setObject(1, bundle.Id);
        statement.setObject(2, bundle.Channel);
        statement.setObject(3, bundle.Client);
        statement.setObject(4, bundle.Time);
        statement.setObject(5, bundle.Message);
        statement.setObject(6, bundle.Object);
        statement.setObject(7, bundle.RepeatTime);
        statement.setObject(8, bundle.Status);
        int rs = statement.executeUpdate();
        return rs == 1 ? 0 : 1;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        switch (event.getName()) {
            case "ping":
                long time = System.currentTimeMillis();
                event.reply("Pong!").setEphemeral(true)
                        .flatMap(v ->
                                event.getHook().editOriginalFormat("Pong: %d ms", System.currentTimeMillis() - time) // then edit original
                        ).queue();
                break;
            case "info":
                event.reply("Discord Bot \"ReminderBot\"").setEphemeral(true).queue();
                break;
        }
    }

    protected static void doTest() {
        long startTime = Calendar.getInstance().getTimeInMillis();
        int errorCount = 0;
        System.out.println("===== Test mode =====");
        try {
            if (connectJDBC() == 0) {
                System.out.println("===== INSERT test =====");
                errorCount++;
                if (writeSQL(new SqlBundle(1, "text_channel", "test_client", 1, "test_message", "test_object", 1, "Test")) == 0) {
                    System.out.println("INSERT test successful\n");
                    errorCount--;
                }

                System.out.println("===== SELECT test(1) =====");
                errorCount++;
                ArrayList<SqlBundle> bundles = readSQL("select * from log where id=1");
                if (!bundles.isEmpty()) {
                    System.out.println(bundles.get(0).getDataString());
                    System.out.println("SELECT test successful\n");
                    errorCount--;
                }

                System.out.println("===== DELETE test =====");
                errorCount++;
                if (deleteSQL(1) == 0) {
                    System.out.println("DELETE test successful\n");
                    errorCount--;
                }

                System.out.println("===== SELECT test(2) =====");
                errorCount++;
                if (readSQL("select * from log where id=1").isEmpty()) {
                    System.out.println("SELECT test successful\n");
                    errorCount--;
                }
            } else {
                System.out.println("Connection Error");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        long endTime = Calendar.getInstance().getTimeInMillis();
        System.out.println("===== Test ended =====");
        System.out.println("Error count: " + errorCount);
        System.out.println("Test time: " + (endTime-startTime) + "ms");
    }
}

