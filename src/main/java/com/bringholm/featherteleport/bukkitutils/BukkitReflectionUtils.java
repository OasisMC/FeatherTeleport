package com.bringholm.featherteleport.bukkitutils;

import java.lang.reflect.*;

public class BukkitReflectionUtils {

    public static Object getDeclaredField(Object object, String fieldName) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
