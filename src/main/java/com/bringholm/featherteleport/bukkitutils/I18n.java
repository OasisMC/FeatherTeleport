package com.bringholm.featherteleport.bukkitutils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class I18n {
    private Properties properties;
    private File file;
    private Plugin plugin;
    private String language;
    private String defaultLanguage;

    public I18n(Plugin plugin, String language) {
        this(plugin, language, language);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public I18n(Plugin plugin, String language, String defaultLanguage) {
        this.plugin = plugin;
        this.language = language;
        this.defaultLanguage = defaultLanguage;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        properties = new Properties();
        file = new File(plugin.getDataFolder(), this.language + ".lang");
        reload();
    }

    public void setLanguage(String language) {
        this.language = language;
        this.file = new File(plugin.getDataFolder(), this.language + ".lang");
    }

    public void reload() {
        if (!file.exists()) {
            if (plugin.getResource(language + ".lang") != null) {
                try (InputStream in = plugin.getResource(language + ".lang"); OutputStream out = new FileOutputStream(file)) {
                    IOUtils.copy(in, out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                file = new File(plugin.getDataFolder(), defaultLanguage + ".lang");
                if (!file.exists()) {
                    if (plugin.getResource(defaultLanguage + ".lang") != null) {
                        try (InputStream in = plugin.getResource(defaultLanguage + ".lang"); OutputStream out = new FileOutputStream(file)) {
                            IOUtils.copy(in, out);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        return;
                    }
                }
            }
        }
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String tr(String key) {
        String translation = this.properties.getProperty(key, key);
        if (!translation.contains("&")) {
            return translation;
        }
        List<Character> characterList = new ArrayList<>();
        char[] chars = translation.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&') {
                if (i > 0 && chars[i - 1] == '\\') {
                    characterList.remove(characterList.size() - 1);
                    characterList.add('&');
                    continue;
                }
                if (i != chars.length - 1 && ChatColor.getByChar(chars[i + 1]) != null) {
                    characterList.add(ChatColor.COLOR_CHAR);
                }
            } else {
                characterList.add(chars[i]);
            }
        }
        return new String(ArrayUtils.toPrimitive(characterList.toArray(new Character[characterList.size()])));
    }
}
