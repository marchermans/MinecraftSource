package buildSrc.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.UnaryOperator;

public final class ReflectionUtils {

    private ReflectionUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: ReflectionUtils. This is a utility class");
    }

    private static Object drillField(Object obj, String path) {
        for (String name : path.split("\\.")) {
            if (obj == null) return null;
            Field f = findField(obj.getClass(), name);
            if (f == null) return null;
            try {
                obj = f.get(obj);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return obj;
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    f.setAccessible(true);
                    return f;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String name) {
        try {
            int idx = name.lastIndexOf('.');
            if (idx != -1) {
                target = drillField(target, name.substring(0, idx));
                if (target == null) throw new IllegalStateException("Could not find field '" + name + "'");
                name = name.substring(idx + 1);
            }
            Field f = findField(target.getClass(), name);
            if (f == null) throw new IllegalStateException("Could not find '" + name + "'");
            return (T)f.get(target);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
