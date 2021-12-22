package com.demo.camera.CameraDemo.utils;

import java.lang.reflect.Method;

public class PropertyUtils {

    private static Method getLongMethod = null;
    private static Method getStringMethod = null;
    private static Method getIntMethod = null;
    private static Method getBooleanMethod = null;


    public static long getLong(final String key, final long def) {
        try {
            if (getLongMethod == null) {
                getLongMethod = Class.forName("android.os.SystemProperties").getMethod("getLong", String.class, long.class);
            }
            return ((Long) getLongMethod.invoke(null, key, def)).longValue();
        } catch (Exception e) {
            return def;
        }
    }

    public static String getStringMethod(final String key, final String def) {
        try {
            if (getStringMethod == null) {
                getStringMethod = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
            }
            return ((String) getStringMethod.invoke(null, key, def)).toString();
        } catch (Exception e) {
            return def;
        }
    }

    public static int getIntMethod(final String key, final int def) {
        try {
            if (getIntMethod == null) {
                getIntMethod = Class.forName("android.os.SystemProperties")
                        .getMethod("getInt", String.class, int.class);
            }
            return ((Integer) getIntMethod.invoke(null, key, def)).intValue();
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean getBooleanMethod(final String key, final boolean def) {
        try {
            if (getBooleanMethod == null) {
                getBooleanMethod = Class.forName("android.os.SystemProperties").getMethod("getBoolean", String.class, boolean.class);
            }
            return ((Boolean) getBooleanMethod.invoke(null, key, def)).booleanValue();
        } catch (Exception e) {
            return def;
        }
    }

    public static void setProperty(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
