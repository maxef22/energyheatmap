package com.controlj.addon.heatmap.util;

import java.io.*;
import java.util.Properties;

public class SettingsManager {
    private static final String CONFIG_FILE = "/config.properties";
    private static String dataDir;
    private static Properties props = new Properties();

    public static void init(String webappDataDir) {
        if (webappDataDir == null) {
            System.err.println("[SettingsManager] webapp.data.dir is null!");
            return;
        }
        dataDir = webappDataDir;
        File dir = new File(dataDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("[SettingsManager] Created data directory: " + dataDir);
            }
        }
        load();
    }

    private static void load() {
        if (dataDir == null) {
            System.err.println("[SettingsManager] Cannot load: dataDir is null");
            return;
        }
        File file = new File(dataDir + CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                System.out.println("[SettingsManager] Loaded settings from: " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[SettingsManager] Failed to load: " + e.getMessage());
            }
        } else {
            System.out.println("[SettingsManager] No config file found, using defaults");
        }
    }

    private static void save() {
        if (dataDir == null) {
            System.err.println("[SettingsManager] Cannot save: dataDir is null");
            return;
        }
        File file = new File(dataDir + CONFIG_FILE);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Heatmap Add-on Settings");
            System.out.println("[SettingsManager] Saved settings to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[SettingsManager] Failed to save: " + e.getMessage());
        }
    }

    public static int getMinDaysForMedian() {
        return Integer.parseInt(props.getProperty("minDaysForMedian", "3"));
    }

    public static void setMinDaysForMedian(int days) {
        props.setProperty("minDaysForMedian", String.valueOf(days));
        save();
    }

    public static double getZeroThreshold() {
        return Double.parseDouble(props.getProperty("zeroThreshold", "100.0"));
    }

    public static void setZeroThreshold(double threshold) {
        props.setProperty("zeroThreshold", String.valueOf(threshold));
        save();
    }

    public static boolean isAnomalyCorrectionEnabled() {
        return Boolean.parseBoolean(props.getProperty("anomalyCorrectionEnabled", "true"));
    }

    public static void setAnomalyCorrectionEnabled(boolean enabled) {
        props.setProperty("anomalyCorrectionEnabled", String.valueOf(enabled));
        save();
    }

    public static boolean isDebugLoggingEnabled() {
        return Boolean.parseBoolean(props.getProperty("debugLoggingEnabled", "false"));
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        props.setProperty("debugLoggingEnabled", String.valueOf(enabled));
        save();
    }

    public static double getResetThreshold() {
        return Double.parseDouble(props.getProperty("resetThreshold", "10000.0"));
    }

    public static void setResetThreshold(double threshold) {
        props.setProperty("resetThreshold", String.valueOf(threshold));
        save();
    }

    /**
     * Имя тренда статуса связи: 0 — связи нет, 1 — связь есть.
     * Вынесено в настройку, чтобы исправлять имя на объекте без пересборки аддона.
     */
    public static String getStatusTrendName() {
        String name = props.getProperty("statusTrendName", "com_st_min");
        return (name == null || name.trim().isEmpty()) ? "com_st_min" : name.trim();
    }

    public static void setStatusTrendName(String name) {
        if (name == null || name.trim().isEmpty()) return;
        props.setProperty("statusTrendName", name.trim());
        save();
    }

    /**
     * Что означает значение 0 в тренде статуса связи:
     * true (по умолчанию) — 0 это "связи нет", 1 и больше — связь есть;
     * false — наоборот, 0 это "связь есть", а ошибка кодируется единицей.
     * Вынесено в настройку, потому что на объектах встречались оба варианта.
     */
    public static boolean isStatusErrorOnZero() {
        return Boolean.parseBoolean(props.getProperty("statusErrorOnZero", "true"));
    }

    public static void setStatusErrorOnZero(boolean value) {
        props.setProperty("statusErrorOnZero", String.valueOf(value));
        save();
    }

    /**
     * Минимальная длина блока «замерших» показаний на границе месяца (в часах),
     * после которой считаем, что связи не было. Защищает от коротких ночных нулей.
     */
    public static int getFrozenRunHours() {
        try {
            return Integer.parseInt(props.getProperty("frozenRunHours", "12").trim());
        } catch (NumberFormatException e) {
            return 12;
        }
    }

    public static void setFrozenRunHours(int hours) {
        props.setProperty("frozenRunHours", String.valueOf(hours));
        save();
    }

    public static String getDataDir() {
        return dataDir;
    }
}