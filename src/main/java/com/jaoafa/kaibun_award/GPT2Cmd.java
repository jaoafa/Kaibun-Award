package com.jaoafa.kaibun_award;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GPT2Cmd extends ListenerAdapter {
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!event.getName().equals("gpt2")) return;
        OptionMapping text = event.getOption("text");
        if (text == null) return;
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("external_scripts/gpt2/venv/bin/python",
            "external_scripts/gpt2/main.py",
            "--text",
            text.getAsString());

        event.reply("生成中です…しばらくお待ちください。※めちゃめちゃ時間かかります").queue(
            message -> {
                try {
                    System.out.println("starting process");
                    Process p = builder.start();
                    System.out.println("started process");
                    try (InputStream is = p.getInputStream()) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                            System.out.println("bufferreader");
                            while (true) {
                                String line = br.readLine();
                                System.out.println(line);
                                if (line == null) {
                                    break;
                                }

                                parseGPT2JSON(event.getHook(), line);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );
    }

    void parseGPT2JSON(InteractionHook hook, String str) {
        JSONObject object;
        try {
            object = new JSONObject(str);
        } catch (JSONException e) {
            hook.editOriginal("[ERROR] `" + str + "`").queue();
            return;
        }
        if (!object.has("generated")) {
            hook.editOriginal("[ERROR] `" + object.getString("message") + "`").queue();
            return;
        }
        if (!object.getBoolean("generated")) {
            hook.editOriginal("[PROCESSING] `" + object.getString("message") + "` (Phase: " + object.getInt("phase") + " / 5)").queue();
            return;
        }
        String result = object.getString("text");
        if (result.length() >= 4096) {
            result = result.substring(0, 4090) + "...";
        }
        Message message = new MessageBuilder()
            .setEmbeds(new EmbedBuilder()
                .setDescription(result)
                .build())
            .build();
        hook.editOriginal(message).queue(
            null,
            e -> hook.editOriginal(":warning: 結果の表示に失敗しました: " + e.getMessage() + " (" + e.getClass().getName() + ")").queue()
        );
    }
}
