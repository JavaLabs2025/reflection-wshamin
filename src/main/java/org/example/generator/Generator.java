package org.example.generator;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Generator {

    private static final int MAX_DEPTH = 3;
    private final Random rnd = new Random();

    public <T> T generateValueOfType(Class<T> type) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return (T) generate(type, 0, new HashMap<>());
    }

    private Object generate(Class<?> type, int depth, Map<Class<?>, Integer> visiting) throws InvocationTargetException, InstantiationException, IllegalAccessException {

        if (depth > MAX_DEPTH) return defaultFor(type);
        visiting.merge(type, 1, Integer::sum);

        if (type.isPrimitive()) return randomPrimitive(type);
        if (isWrapper(type)) return randomWrapper(type);
        if (type == String.class) return randomString();
        if (type.isEnum()) return randomEnum(type);
        if (type.isArray()) return randomArray(type.getComponentType(), depth, visiting);
        if (Collection.class.isAssignableFrom(type)) return new ArrayList<>();

        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            Class<?> impl = pickAnnotatedImplementation(type);

            if (impl == null) {
                throw new IllegalArgumentException("Нет @Generatable реализаций для: " + type.getName());
            }

            return generate(impl, depth + 1, visiting);
        }

        if (!type.isAnnotationPresent(Generatable.class)) {
            throw new IllegalArgumentException("Класс не помечен @Generatable: " + type.getName());
        }

        try {
            Constructor<?>[] constructors = type.getDeclaredConstructors();

            for (Constructor<?> c : constructors) {
                Class<?>[] params = c.getParameterTypes();
                Type[] gParams = c.getGenericParameterTypes();
                Object[] args = new Object[params.length];

                boolean ok = true;

                for (int i = 0; i < params.length; i++) {
                    Class<?> p = params[i];
                    try {
                        if (Collection.class.isAssignableFrom(p)) {
                            args[i] = createAndFillCollection(p, gParams[i], depth, visiting);
                        } else {
                            args[i] = generate(p, depth + 1, visiting);
                        }
                    } catch (Throwable t) {
                        ok = false;
                        break;
                    }
                }

                if (!ok) {
                    continue;
                }

                try {
                    return c.newInstance(args);
                } catch (InvocationTargetException e) {
                    System.out.println("Конструктор не подошел, пробуем следующий");
                }
            }

            return defaultFor(type);
        } finally {
            visiting.merge(type, -1, Integer::sum);

            if (visiting.get(type) != null && visiting.get(type) <= 0) {
                visiting.remove(type);
            }
        }
    }

    private Object randomPrimitive(Class<?> p) {
        if (p == boolean.class) return rnd.nextBoolean();
        if (p == byte.class) return (byte) rnd.nextInt();
        if (p == short.class) return (short) rnd.nextInt();
        if (p == char.class) return (char) ('a' + rnd.nextInt(26));
        if (p == int.class) return rnd.nextInt();
        if (p == long.class) return rnd.nextLong();
        if (p == float.class) return rnd.nextFloat();
        if (p == double.class) return rnd.nextDouble();
        throw new IllegalArgumentException("Неизвестный примитив: " + p);
    }

    private Object randomWrapper(Class<?> w) {
        if (w == Boolean.class) return rnd.nextBoolean();
        if (w == Byte.class) return (byte) rnd.nextInt();
        if (w == Short.class) return (short) rnd.nextInt();
        if (w == Character.class) return (char) ('a' + rnd.nextInt(26));
        if (w == Integer.class) return rnd.nextInt();
        if (w == Long.class) return rnd.nextLong();
        if (w == Float.class) return rnd.nextFloat();
        if (w == Double.class) return rnd.nextDouble();
        throw new IllegalArgumentException("Неизвестная обёртка: " + w);
    }

    private Object randomEnum(Class<?> e) {
        Object[] constants = e.getEnumConstants();

        if (constants == null || constants.length == 0) {
            return null;
        }

        return constants[rnd.nextInt(constants.length)];
    }

    private Object randomArray(Class<?> component, int depth, Map<Class<?>, Integer> visiting) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        int len = rnd.nextInt(3);
        Object array = Array.newInstance(component, len);

        for (int i = 0; i < len; i++) {
            Array.set(array, i, generate(component, depth + 1, visiting));
        }

        return array;
    }

    private Collection<?> createAndFillCollection(Class<?> raw, Type gType, int depth, Map<Class<?>, Integer> visiting)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Collection<Object> coll;

        if (Set.class.isAssignableFrom(raw)) {
            coll = new HashSet<>();
        }
        else if (List.class.isAssignableFrom(raw) || Collection.class.isAssignableFrom(raw)) {
            coll = new ArrayList<>();
        }
        else {
            coll = new ArrayList<>();
        }

        Class<?> elemClass = String.class;

        if (gType instanceof ParameterizedType pt) {
            Type t = pt.getActualTypeArguments()[0];

            if (t instanceof Class<?> c) {
                elemClass = c;
            }
            else if (t instanceof ParameterizedType pt2 && pt2.getRawType() instanceof Class<?> c2) {
                elemClass = c2;
            }
        }

        int size = 1 + rnd.nextInt(3);

        for (int k = 0; k < size; k++) {
            Object val = generate(elemClass, depth + 1, visiting);
            coll.add(val);
        }

        return coll;
    }

    private String randomString() {
        int n = 3 + rnd.nextInt(8);
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            sb.append((char) ('a' + rnd.nextInt(26)));
        }

        return sb.toString();
    }

    private boolean isWrapper(Class<?> c) {
        return c == Boolean.class || c == Byte.class || c == Short.class || c == Character.class ||
                c == Integer.class || c == Long.class || c == Float.class || c == Double.class;
    }

    private Object defaultFor(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == char.class) return (char) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            if (type == double.class) return 0d;
        }
        return null;
    }

    private Class<?> pickAnnotatedImplementation(Class<?> target) {
        String pkg = target.getPackage().getName();
        List<Class<?>> candidates = new ArrayList<>();

        for (Class<?> c : getAllClassesInPackage(pkg)) {
            if (c == target) continue;
            if (Modifier.isAbstract(c.getModifiers()) || c.isInterface()) continue;
            if (!c.isAnnotationPresent(Generatable.class)) continue;
            if (target.isAssignableFrom(c)) candidates.add(c);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private List<Class<?>> getAllClassesInPackage(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');

        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    String url = resource.getFile();
                    String filePath = URLDecoder.decode(url, StandardCharsets.UTF_8);
                    findAndAddClassesInDirectory(packageName, filePath, classes);
                } else if ("jar".equals(protocol)) {
                    JarURLConnection conn = (JarURLConnection) resource.openConnection();
                    JarFile jar = conn.getJarFile();
                    Enumeration<JarEntry> entries = jar.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(path) && name.endsWith(".class") && !entry.isDirectory()) {
                            String className = name.replace('/', '.').substring(0, name.length() - 6);
                            tryLoad(classes, className);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Проблемы IO при чтении классов из пакета");
        }

        return classes;
    }

    private void findAndAddClassesInDirectory(String packageName, String dirPath, List<Class<?>> classes) {
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                findAndAddClassesInDirectory(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                tryLoad(classes, className);
            }
        }
    }

    private void tryLoad(List<Class<?>> classes, String className) {
        try {
            classes.add(Class.forName(className));
        } catch (ClassNotFoundException e) {
            System.out.println("Class не найден");
        }
    }
}
