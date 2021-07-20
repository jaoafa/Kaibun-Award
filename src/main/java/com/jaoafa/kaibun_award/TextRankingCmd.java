package com.jaoafa.kaibun_award;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;

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
                SelectionMenu.Builder menu = SelectionMenu.create("menu:gentext:award")
                    .setPlaceholder("好きな文章を選んでね！");
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
                            texts.add(new Text(
                                res.getInt("rowid"),
                                res.getString("text"),
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
                        String label = ((i + 1) + "位: " + texts.get(i).getText());
                        if (label.length() > 22) {
                            label = label.substring(0, 22) + "...";
                        }
                        menu.addOption(label, String.valueOf(texts.get(i).getRowId()));
                    }

                    Message send_msg = new MessageBuilder()
                        .setEmbeds(embed.build())
                        .setActionRows(ActionRow.of(
                            menu.build()
                        )).build();
                    event.getHook().editOriginal(send_msg).queue();
                } catch (SQLException e) {
                    event.getHook().editOriginal("データベースの操作に失敗しました。").queue();
                    e.printStackTrace();
                }
            });
    }

    static class Text {
        int rowid;
        String text;
        int voteCount;

        public Text(int rowid, String text, int voteCount) {
            this.rowid = rowid;
            this.text = text;
            this.voteCount = voteCount;
        }

        public int getRowId() {
            return rowid;
        }

        public String getText() {
            return text;
        }

        public int getVoteCount() {
            return voteCount;
        }
    }
}
