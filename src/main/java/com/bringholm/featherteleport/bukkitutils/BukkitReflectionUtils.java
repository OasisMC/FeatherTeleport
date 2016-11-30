package com.bringholm.featherteleport.bukkitutils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BukkitReflectionUtils {
    public static void modifyFinalField(Field field, Object target, Object newValue) {
        try {
            field.setAccessible(true);
            Field modifierField = Field.class.getDeclaredField("modifiers");
            modifierField.setAccessible(true);
            modifierField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(target, newValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
