package com.jaoafa.kaibun_award;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TextRankingCmd extends ListenerAdapter {
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!event.getName().equals("textranking")) return;
        OptionMapping source_option = event.getOption("source");
        String source = source_option != null ? source_option.getAsString() : null;

        event.reply("ランキングを集計しています…").queue(
            message -> {
                MySQLDBManager manager = Main.getMySQLDBManager();
                try {
                    Connection conn = manager.getConnection();

                    List<Text> texts = new ArrayList<>();
                    try (PreparedStatement stmt = source == null ?
                        conn.prepareStatement("SELECT * FROM gentext WHERE votes IS NOT NULL") :
                        conn.prepareStatement("SELECT * FROM gentext WHERE source = ? AND votes IS NOT NULL")) {
                        if (source != null) {
                            stmt.setString(1, source);
                        }
                        ResultSet res = stmt.executeQuery();
                        while (res.next()) {
                            texts.add(new Text(res.getString("text"),
                                res.getString("votes").split(",").length));
                        }
                    }

                    texts = texts
                        .stream()
                        .sorted(Comparator.comparing(Text::getVoteCount).reversed())
                        .collect(Collectors.toList());
                    EmbedBuilder embed = new EmbedBuilder();
                    for (int i = 0; i < texts.size(); i++) {
                        if (texts.size() >= 25) {
                            break;
                        }
                        embed.addField(new MessageEmbed.Field((i + 1) + "位 (" + texts.get(i).getVoteCount() + "人投票)",
                            texts.get(i).getText(),
                            false));
                    }
                    event.getHook().editOriginalEmbeds(embed.build()).queue();
                } catch (SQLException e) {
                    event.getHook().editOriginal("データベースの操作に失敗しました。").queue();
                    e.printStackTrace();
                }
            });
    }

    static class Text {
        String text;
        int voteCount;

        public Text(String text, int voteCount) {
            this.text = text;
            this.voteCount = voteCount;
        }

        public String getText() {
            return text;
        }

        public int getVoteCount() {
            return voteCount;
        }
    }
}
