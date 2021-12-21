package com.jaoafa.kaibun_award;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Main {
    static Logger logger;
    static MySQLDBManager manager;

    public static void main(String[] args) throws IOException {
        logger = LoggerFactory.getLogger("Javajaotan2");
        JSONObject config = new JSONObject(Files.readString(Path.of("config.json")));

        copyExternalScripts();

        try {
            manager = new MySQLDBManager(
                config.getString("mysql_hostname"),
                config.getInt("mysql_port"),
                config.getString("mysql_username"),
                config.getString("mysql_password"),
                config.getString("mysql_database")
            );
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            JDA jda = JDABuilder.createDefault(config.getString("token"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES,
                    GatewayIntent.GUILD_MESSAGE_TYPING, GatewayIntent.DIRECT_MESSAGE_TYPING)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setContextEnabled(false)
                .addEventListeners(new GenTextCmd(), new TextRankingCmd())
                .build()
                .awaitReady();

            List<Command.Choice> choice = Files
                .list(Path.of("external_scripts", "gentext", "sources"))
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(file -> file.endsWith(".json"))
                .map(s -> s.substring(0, s.length() - 5))
                .map(x -> new Command.Choice(x, x))
                .collect(Collectors.toList());

            //noinspection ResultOfMethodCallIgnored
            jda.retrieveCommands().queue(
                s -> s
                    .stream()
                    .filter(c -> c.getName().equals("gentext") || c.getName().equals("textranking"))
                    .forEach(Command::delete)
            );

            Objects.requireNonNull(jda
                    .getGuildById(597378876556967936L))
                .upsertCommand("gentext", "文章を生成します。")
                .addOptions(new OptionData(OptionType.STRING, "source", "データソース", true)
                    .addChoices(choice))
                .addOptions(new OptionData(OptionType.INTEGER, "count", "いくつ生成するか (25個以内推奨)", true))
                .queue();

            Objects.requireNonNull(jda
                .getGuildById(597378876556967936L))
                .upsertCommand("textranking", "gentextで生成した文章のランキングを表示します。")
                .addOptions(new OptionData(OptionType.STRING, "source", "データソース", false)
                    .addChoices(choice))
                .queue();
            jda.updateCommands().queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void copyExternalScripts() {
        String srcDirName = "external_scripts";
        File destDir = new File("external_scripts/");

        final File jarFile = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        if (!jarFile.isFile()) {
            logger.warn("仕様によりexternal_scriptsディレクトリをコピーできません。ビルドしてから実行すると、external_scriptsを使用する機能を利用できます。");
            return;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(srcDirName + "/") && !entry.isDirectory()) {
                    File dest = new File(destDir, entry.getName().substring(srcDirName.length() + 1));
                    File parent = dest.getParentFile();
                    if (parent != null) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }
                    logger.info("[external_scripts] Copy " + entry.getName().substring(srcDirName.length() + 1));
                    try (FileOutputStream out = new FileOutputStream(dest); InputStream in = jar.getInputStream(entry)) {
                        byte[] buffer = new byte[8 * 1024];
                        int s;
                        while ((s = in.read(buffer)) > 0) {
                            out.write(buffer, 0, s);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MySQLDBManager getMySQLDBManager() {
        return manager;
    }
}
