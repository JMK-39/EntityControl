import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 检查最终（reobf 之后）JAR 中的 KineticCore 方法引用，无第三方依赖，可用 {@code java KineticReobfCheck.java} 直接运行。
 * Checks KineticCore method references in final (post-reobf) JARs; dependency-free, runnable with
 * {@code java KineticReobfCheck.java}.
 *
 * <pre>
 * core  &lt;devClassesDir|devJar&gt; &lt;reobfCoreJar&gt;
 *     每个 dev.xyat.kineticcore.api 类型的公开/受保护方法在 reobf JAR 中必须保持原名（未被重映射为 m_*）。
 *     Every public/protected method of dev.xyat.kineticcore.api types must keep its name in the reobf JAR.
 * addon &lt;reobfCoreJar&gt; &lt;reobfAddonJar&gt;
 *     附属 JAR 中每个指向 dev/xyat/kineticcore 的方法引用都必须能在核心 JAR 中按名称与描述符解析到。
 *     Every method reference from the addon JAR into dev/xyat/kineticcore must resolve by name and descriptor
 *     in the core JAR.
 * </pre>
 */
public final class KineticReobfCheck {
    private static final String CORE_PREFIX = "dev/xyat/kineticcore/";
    private static final String API_PREFIX = "dev/xyat/kineticcore/api/";

    record Method(String name, String desc, int access) {}
    record Ref(String owner, String name, String desc) {}
    record ClassInfo(String name, String superName, List<String> interfaces, List<Method> methods, List<Ref> refs) {}

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !(args[0].equals("core") || args[0].equals("addon"))) {
            System.err.println("usage: core <devClasses|devJar> <reobfCoreJar> | addon <reobfCoreJar> <reobfAddonJar>");
            System.exit(2);
        }
        List<String> problems = args[0].equals("core") ? checkCore(load(args[1]), load(args[2]))
                : checkAddon(load(args[1]), load(args[2]));
        problems.forEach(System.out::println);
        System.out.println("KineticReobfCheck " + args[0] + ": " + problems.size() + " problem(s)");
        if (!problems.isEmpty()) System.exit(1);
    }

    static List<String> checkCore(Map<String, ClassInfo> dev, Map<String, ClassInfo> reobf) {
        List<String> problems = new ArrayList<>();
        for (ClassInfo info : dev.values()) {
            if (!info.name().startsWith(API_PREFIX)) continue;
            ClassInfo out = reobf.get(info.name());
            if (out == null) {
                problems.add("MISSING CLASS " + info.name());
                continue;
            }
            Set<String> outSigs = new TreeSet<>();
            for (Method m : out.methods()) outSigs.add(m.name() + m.desc());
            for (Method m : info.methods()) {
                boolean visible = (m.access() & 0x0001) != 0 || (m.access() & 0x0004) != 0;
                if (!visible || m.name().startsWith("<") || m.name().startsWith("lambda$") || (m.access() & 0x1000) != 0) continue;
                // 描述符里的原版类型名在 reobf 后不变（官方名 → SRG 只改成员名），因此按“名称 + 描述符”比较。
                // Minecraft class names are unchanged by reobf (only member names change), so compare name+descriptor.
                if (!outSigs.contains(m.name() + m.desc())) {
                    problems.add("RENAMED API " + info.name() + "." + m.name() + m.desc());
                }
            }
            for (Method m : out.methods()) {
                if (m.name().startsWith("m_") && ((m.access() & 0x0001) != 0 || (m.access() & 0x0004) != 0)) {
                    problems.add("SRG NAME IN API " + info.name() + "." + m.name() + m.desc());
                }
            }
        }
        return problems;
    }

    static List<String> checkAddon(Map<String, ClassInfo> core, Map<String, ClassInfo> addon) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (ClassInfo info : addon.values()) {
            for (Ref ref : info.refs()) {
                if (!ref.owner().startsWith(CORE_PREFIX) || core.containsKey(info.name()) && info.name().startsWith(CORE_PREFIX)) continue;
                String key = info.name() + " -> " + ref.owner() + "." + ref.name() + ref.desc();
                if (!seen.add(key)) continue;
                String result = resolve(core, ref.owner(), ref.name(), ref.desc());
                if (result != null) problems.add(result + " " + key);
                if (ref.owner().contains("/internal/")) problems.add("INTERNAL " + key);
            }
        }
        return problems;
    }

    /** null = resolved; otherwise the failure kind. */
    static String resolve(Map<String, ClassInfo> core, String owner, String name, String desc) {
        ClassInfo start = core.get(owner);
        if (start == null) return "MISSING OWNER";
        List<String> queue = new ArrayList<>();
        queue.add(owner);
        Set<String> visited = new TreeSet<>();
        boolean leavesCore = false;
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            if (!visited.add(current)) continue;
            ClassInfo info = core.get(current);
            if (info == null) {
                // Enum.ordinal() is inherited from the JDK and is absent from the core JAR.
                if (current.equals("java/lang/Enum") && name.equals("ordinal") && desc.equals("()I")) return null;
                if (!current.startsWith("java/")) leavesCore = true;
                continue;
            }
            for (Method m : info.methods()) if (m.name().equals(name) && m.desc().equals(desc)) return null;
            if (info.superName() != null) queue.add(info.superName());
            queue.addAll(info.interfaces());
        }
        if (isObjectMethod(name, desc)) return null;
        // 解析离开核心进入原版类：生产环境原版成员是 m_* 名，非 m_* 的名字必然找不到。
        // Resolution left the core into vanilla classes: production vanilla members are m_*; other names cannot exist.
        if (leavesCore && name.startsWith("m_")) return null;
        return leavesCore ? "UNRESOLVED (vanilla name, NoSuchMethodError at runtime)" : "UNRESOLVED";
    }

    static boolean isObjectMethod(String name, String desc) {
        return (name.equals("toString") && desc.equals("()Ljava/lang/String;"))
                || (name.equals("hashCode") && desc.equals("()I"))
                || (name.equals("equals") && desc.equals("(Ljava/lang/Object;)Z"))
                || (name.equals("getClass") && desc.equals("()Ljava/lang/Class;"));
    }

    // ------------------------------------------------------------------ minimal class-file reader

    static Map<String, ClassInfo> load(String path) throws IOException {
        Map<String, ClassInfo> out = new HashMap<>();
        Path p = Paths.get(path);
        if (Files.isDirectory(p)) {
            try (var stream = Files.walk(p)) {
                for (Path f : (Iterable<Path>) stream::iterator) {
                    if (f.toString().endsWith(".class")) add(out, Files.readAllBytes(f));
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(p.toFile())) {
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
                        add(out, zip.getInputStream(entry).readAllBytes());
                    }
                }
            }
        }
        return out;
    }

    static void add(Map<String, ClassInfo> out, byte[] bytes) throws IOException {
        ClassInfo info = parse(bytes);
        out.put(info.name(), info);
    }

    static ClassInfo parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        Object[] pool = new Object[count];
        int[][] refs = new int[count][];
        int[] tags = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            tags[i] = tag;
            switch (tag) {
                case 1 -> pool[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7, 8, 16, 19, 20 -> pool[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> refs[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException("bad constant tag " + tag);
            }
        }
        in.readUnsignedShort();
        String name = className(pool, in.readUnsignedShort());
        int superIndex = in.readUnsignedShort();
        String superName = superIndex == 0 ? null : className(pool, superIndex);
        List<String> interfaces = new ArrayList<>();
        int ic = in.readUnsignedShort();
        for (int i = 0; i < ic; i++) interfaces.add(className(pool, in.readUnsignedShort()));
        int fc = in.readUnsignedShort();
        for (int i = 0; i < fc; i++) { in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort(); skipAttributes(in); }
        List<Method> methods = new ArrayList<>();
        int mc = in.readUnsignedShort();
        for (int i = 0; i < mc; i++) {
            int access = in.readUnsignedShort();
            String mName = (String) pool[in.readUnsignedShort()];
            String mDesc = (String) pool[in.readUnsignedShort()];
            skipAttributes(in);
            methods.add(new Method(mName, mDesc, access));
        }
        List<Ref> methodRefs = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            if (tags[i] == 10 || tags[i] == 11) {
                int[] nat = refs[refs[i][1]];
                methodRefs.add(new Ref(className(pool, refs[i][0]), (String) pool[nat[0]], (String) pool[nat[1]]));
            }
        }
        return new ClassInfo(name, superName, interfaces, methods, methodRefs);
    }

    static String className(Object[] pool, int classIndex) {
        return (String) pool[(Integer) pool[classIndex]];
    }

    static void skipAttributes(DataInputStream in) throws IOException {
        int ac = in.readUnsignedShort();
        for (int i = 0; i < ac; i++) {
            in.readUnsignedShort();
            int len = in.readInt();
            in.skipNBytes(len);
        }
    }
}
