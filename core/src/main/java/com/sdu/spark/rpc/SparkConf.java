package com.sdu.spark.rpc;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.sdu.spark.network.utils.JavaUtils;
import com.sdu.spark.utils.Utils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sdu.spark.SecurityManager.SPARK_AUTH_SECRET_CONF;
import static com.sdu.spark.utils.Utils.byteStringAsKb;
import static com.sdu.spark.utils.Utils.byteStringAsMb;
import static com.sdu.spark.utils.Utils.timeStringAs;

/**
 *
 * @author hanhan.zhang
 * */
public class SparkConf implements Serializable {

    private Map<String, String> settings = Maps.newConcurrentMap();

    public SparkConf setMaster(String master) {
        set("spark.master", master);
        return this;
    }

    public SparkConf setAppName(String name) {
        set("spark.app.name", name);
        return this;
    }

    public void set(String key, String value) {
        settings.put(key, value);
    }

    public SparkConf setIfMissing(String key, String value) {
        settings.putIfAbsent(key, value);
        return this;
    }

    public String get(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public String get(String key) {
        return settings.get(key);
    }

    public boolean contains(String key) {
        return settings.containsKey(key);
    }

    public long getLong(String key, long defaultValue) {
        String value = settings.get(key);
        return NumberUtils.toLong(value, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = settings.get(key);
        return NumberUtils.toInt(value, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        String value = settings.get(key);
        return NumberUtils.toDouble(value, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = settings.get(key);
        if (Strings.isNullOrEmpty(value)) {
            return defaultValue;
        }
        return Boolean.valueOf(value);
    }

    public long getTimeAsMs(String key, String defaultValue) {
        return timeStringAs(get(key, defaultValue), TimeUnit.MILLISECONDS);
    }

    public String getAppId() {
        return get("spark.app.id");
    }

    public static boolean isExecutorStartupConf(String name) {
        return (name.startsWith("spark.auth") && !name.equals(SPARK_AUTH_SECRET_CONF)) ||
                name.startsWith("spark.ssl") ||
                name.startsWith("spark.rpc") ||
                name.startsWith("spark.network") ||
                isSparkPortConf(name);
    }

    private static boolean isSparkPortConf(String name) {
        return (name.startsWith("spark.") && name.endsWith(".port")) || name.startsWith("spark.port.");
    }

    public Map<String, String> getAll() {
        return Maps.newLinkedHashMap(settings);
    }

    public String getenv(String name) {
        return System.getenv(name);
    }

    public long getSizeAsBytes(String key, String defaultValue) {
        return JavaUtils.byteStringAsBytes(get(key, defaultValue));
    }

    public long getSizeAsKb(String key, String defaultValue) {
        return byteStringAsKb(get(key, defaultValue));
    }

    public long getSizeAsMb(String key, String defaultValue) {
        return byteStringAsMb(get(key, defaultValue));
    }
}
