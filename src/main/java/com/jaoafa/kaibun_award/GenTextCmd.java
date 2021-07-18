package com.jaoafa.kaibun_award;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GenTextCmd extends ListenerAdapter {
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!event.getName().equals("gentext")) return;
        OptionMapping source_option = event.getOption("source");
        OptionMapping count_option = event.getOption("count");
        String source = source_option != null ? source_option.getAsString() : "default";
        int count = count_option != null ? Math.toIntExact(count_option.getAsLong()) : 1;
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("python3",
            "external_scripts/gentext/main.py",
            "--source",
            source.toLowerCase(Locale.ROOT),
            "--count",
            String.valueOf(count));
        builder.redirectErrorStream(true);
        MySQLDBManager manager = Main.getMySQLDBManager();
        Connection conn;
        try {
            conn = manager.getConnection();
        } catch (SQLException e) {
            event.reply("データベースの接続に失敗しました。").queue();
            e.printStackTrace();
            return;
        }

        event.reply("生成中です…しばらくお待ちください。").queue(
            message -> {
                try {
                    Process p = builder.start();
                    try (InputStream is = p.getInputStream()) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                            while (true) {
                                String line = br.readLine();
                                if (line == null) {
                                    break;
                                }

                                parseGenTextJSON(event.getHook(), source, conn, line);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );
    }

    void parseGenTextJSON(InteractionHook hook, String source, Connection conn, String str) {
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
            hook.editOriginal("[PROCESSING] `" + object.getString("message") + "` (Phase: " + object.getInt("phase") + " / 4)").queue();
            return;
        }

        SelectionMenu.Builder menu = SelectionMenu.create("menu:gentext:award")
            .setPlaceholder("好きな文章を選んでね！");
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < object.getJSONArray("texts").length(); i++) {
            String text = object.getJSONArray("texts").getString(i);
            int text_id = insertGenText(conn, source, text);
            String label = ((i + 1) + "個目: " + text);
            if (label.length() > 22) {
                label = label.substring(0, 22) + "...";
            }
            menu.addOption(label, String.valueOf(text_id));
            texts.add((i + 1) + "個目: " + object.getJSONArray("texts").getString(i));
        }
        String result = String.join("\n", texts);
        if (result.length() >= 4096) {
            result = result.substring(0, 4090) + "...";
        }
        Message message = new MessageBuilder()
            .setEmbeds(new EmbedBuilder()
                .setDescription(result)
                .setFooter("Source: " + source)
                .build())
            .setActionRows(ActionRow.of(
                menu.build()
            )).build();
        hook.editOriginal(message).queue();
    }

    int insertGenText(Connection conn, String source, String text) {
        try {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT rowid FROM gentext WHERE text = ? AND source = ?")) {
                stmt.setString(1, text);
                stmt.setString(2, source);
                ResultSet res = stmt.executeQuery();
                if (res.next()) {
                    return res.getInt("rowid");
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO gentext (text, source, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, text);
                stmt.setString(2, source);
                stmt.executeUpdate();
                try (ResultSet res = stmt.getGeneratedKeys()) {
                    if (res != null && res.next()) {
                        return res.getInt(1);
                    } else {
                        return -1;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event) {
        User user = event.getUser();
        if (!event.getComponentId().equals("menu:gentext:award")) return;
        List<SelectOption> menu = event.getInteraction().getSelectedOptions();
        if (menu == null || menu.isEmpty()) return;
        int text_id = Integer.parseInt(menu.get(0).getValue());
        System.out.println(user.getAsTag() + " -> " + text_id);
        try {
            MySQLDBManager manager = Main.getMySQLDBManager();
            Connection conn;
            try {
                conn = manager.getConnection();
            } catch (SQLException e) {
                event.reply("データベースの接続に失敗したため、投票を反映できませんでした。").setEphemeral(true).queue();
                e.printStackTrace();
                return;
            }

            String votes;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM gentext WHERE rowid = ?")) {
                stmt.setInt(1, text_id);
                ResultSet res = stmt.executeQuery();
                if (!res.next()) {
                    event.reply("指定された文章がデータベースに登録されていませんでした。").setEphemeral(true).queue();
                    return;
                }
                votes = res.getString("votes");
            }
            if (votes != null && votes.contains(user.getId())) {
                event.reply("あなたはすでにこの文章に投票しています。").setEphemeral(true).queue();
                return;
            }
            votes = votes == null || votes.equals("") ? user.getId() : votes + "," + user.getId();
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE gentext SET votes = ? WHERE rowid = ?")) {
                stmt.setString(1, votes);
                stmt.setInt(2, text_id);
                stmt.executeUpdate();
            }
            event.reply("投票に成功しました。ありがとうございます！").setEphemeral(true).queue();
        } catch (SQLException e) {
            e.printStackTrace();
            event.reply("処理に失敗しました。").setEphemeral(true).queue();
        }
    }
}
