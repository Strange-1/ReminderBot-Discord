import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;

public class main extends ListenerAdapter {
    static JDA jda;
    protected static String TOKEN = "ODg2MTA4MDc1MTcyNDMzOTYx.YTwydA.40AGXCBDufCo4yeqHKETjl4v0ko";

    public static void main(String[] args) throws LoginException, InterruptedException {

        JDABuilder builder = JDABuilder.createDefault(TOKEN);
        builder.setAutoReconnect(true)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListeners(new main())
                .setActivity(Activity.playing("Type /ping"));
        jda = builder.build();
        jda.upsertCommand("ping", "Calculate ping of the bot").queue();
        jda.upsertCommand("info", "Show Bot's information").queue();
        jda.upsertCommand("shutdown", "SHUTDOWN").queue();

        jda.awaitReady();
        System.out.println("Hey ya!");
    }

    @Override
    public void onReady(@NotNull ReadyEvent event)
    {
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
            case "shutdown":
		event.reply("Are you sure?").setEphemeral(true).queue();
		break;
        }
    }
}

