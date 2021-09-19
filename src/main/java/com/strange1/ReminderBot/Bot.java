package com.strange1.ReminderBot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Bot extends ListenerAdapter {
    static JDA jda;
    protected static String TOKEN = "ODg2MTA4MDc1MTcyNDMzOTYx.YTwydA.40AGXCBDufCo4yeqHKETjl4v0ko";
    private static Connection con;


    public static void main(String[] args) throws LoginException, InterruptedException, SQLException {

        if (args.length > 0 && List.of(new String[]{"-t", "/t", "--test"}).contains(args[0])) {
            System.out.println("Test Mode");
            doTest();
            if (!con.isClosed()) {
                con.close();
                System.out.println("Connection Closed");
            }
            System.out.println("Test ended");
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
        jda.awaitReady();

        System.out.println("Hey ya!");
    }

    public static int connectJDBC() {
        String ip = "192.168.1.16";
        String port = "3306";
        String db = "ReminderBot";
        String user = "sqladmin";
        String passwd = "268bcb7214";
        try {
            String url = "jdbc:mariadb://" + ip + ":" + port + "/" + db;
            System.out.println("Connecting \"" + url + "\" by " + user + "...");
            con = DriverManager.getConnection(url, user, passwd);
            if (con.isValid(0)) {
                System.out.println("===== Connection Established =====");
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
    public void onReady(@NotNull ReadyEvent event) {
        //jda.getTextChannels().get(0).sendMessage("Hey ya!").queue();
        super.onReady(event);
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
        try {
            if (connectJDBC() == 0) {
                System.out.println("===== INSERT test =====");
                if (writeSQL(new SqlBundle(1, "text_channel", "test_client", 1, "test_message", "test_object", 1, "Test")) == 0)
                    System.out.println("INSERT test successful\n");

                System.out.println("===== SELECT test(1) =====");
                System.out.println(readSQL("select * from log where id=1").get(0).getDataString());
                System.out.println("SELECT test successful\n");

                System.out.println("===== DELETE test =====");
                if (deleteSQL(1) == 0)
                    System.out.println("DELETE test successful\n");

                System.out.println("===== SELECT test(2) =====");
                if (readSQL("select * from log where id=1").isEmpty())
                    System.out.println("SELECT test successful\n");
            } else {
                System.out.println("Connection Error");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

