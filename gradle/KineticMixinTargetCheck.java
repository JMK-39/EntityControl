import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * 检查 Mixin 的目标在当前 Minecraft 版本里是否存在：javac 不检查注解里的方法名与描述符，目标变化只会在游戏加载到该类时才报错。
 * Checks that every Mixin target exists in the Minecraft version a node compiles against. javac does not check
 * the method names and descriptors inside Mixin annotations, so a changed target otherwise only fails once the
 * game loads that class.
 *
 * <pre>
 * java -cp asm.jar:asm-tree.jar KineticMixinTargetCheck.java &lt;modClassesDir&gt; &lt;classpath or --classpath-file=path&gt;
 * </pre>
 *
 * For each injector the target method must exist, and calls, field accesses and allocations named by @At must occur
 * in it (as often as require asks); an @Inject handler's parameters must start with the target's
 * parameters (or be only the CallbackInfo). @Shadow, @Accessor and @Invoker members must exist in the target or a
 * superclass. Injectors with require = 0 are optional and only reported as notes.
 */
public final class KineticMixinTargetCheck {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final List<String> INJECTORS = List.of(
            INJECT,
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"
    );

    private final Map<String, Path> directories = new HashMap<>();
    private final List<ZipFile> jars = new ArrayList<>();
    private final Map<String, Optional<ClassNode>> cache = new HashMap<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final java.util.Set<String> activeMixins = new java.util.HashSet<>();
    private final java.util.Set<String> mixinPackages = new java.util.HashSet<>();
    private final java.util.Set<String> knownIssues = new java.util.HashSet<>();
    private boolean srgRuntime;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: KineticMixinTargetCheck <modClassesDir> <classpath or --classpath-file=path>");
            System.exit(2);
        }
        KineticMixinTargetCheck check = new KineticMixinTargetCheck();
        for (int index = 2; index < args.length; index++) {
            String option = args[index];
            if (option.equals("--srg-runtime")) check.srgRuntime = true;
            else if (option.startsWith("--configs=")) check.readConfigs(Path.of(option.substring("--configs=".length())));
            else if (option.startsWith("--known=")) {
                for (String key : option.substring("--known=".length()).split(",")) if (!key.isBlank()) check.knownIssues.add(key.trim());
            }
        }
        Path modClasses = Path.of(args[0]);
        check.directories.put("mod", modClasses);
        // The classpath may come from a file (--classpath-file=path) to stay below the Windows command-line limit.
        String classpath = args[1].startsWith("--classpath-file=")
                ? Files.readString(Path.of(args[1].substring("--classpath-file=".length()))).trim()
                : args[1];
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry);
            if (Files.isDirectory(path)) check.directories.put(entry, path);
            else if (Files.isRegularFile(path) && entry.endsWith(".jar")) check.jars.add(new ZipFile(path.toFile()));
        }

        int mixins = 0;
        try (Stream<Path> files = Files.walk(modClasses)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode mixin = read(Files.readAllBytes(file));
                check.checkMixinPackageReferences(mixin);
                AnnotationNode annotation = annotation(mixin.invisibleAnnotations, MIXIN);
                if (annotation == null || !check.isActive(mixin.name)) continue;
                mixins++;
                check.checkMixin(mixin, annotation);
            }
        }

        check.notes.forEach(note -> System.out.println("NOTE: " + note));
        check.problems.forEach(problem -> System.out.println("PROBLEM: " + problem));
        System.out.println("KineticMixinTargetCheck: " + mixins + " mixins, " + check.problems.size() + " problem(s), "
                + check.notes.size() + " note(s)");
        if (!check.problems.isEmpty()) System.exit(1);
    }

    // Mixin refuses to load a class from a mixin package unless it is an interface mixin (accessors, invokers), so
    // code must not call into other classes there: helpers belong outside the mixin packages. A mixin naming itself or
    // its own anonymous classes is fine, Mixin rewrites or copies those.
    private void checkMixinPackageReferences(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                List<String> referenced = new ArrayList<>();
                if (instruction instanceof MethodInsnNode call) referenced.add(call.owner);
                else if (instruction instanceof FieldInsnNode field) referenced.add(field.owner);
                else if (instruction instanceof TypeInsnNode type) {
                    Type element = type.desc.startsWith("[") ? Type.getType(type.desc).getElementType() : Type.getObjectType(type.desc);
                    if (element.getSort() == Type.OBJECT) referenced.add(element.getInternalName());
                }
                else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) referenced.add(type.getInternalName());
                else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (Object argument : indy.bsmArgs) if (argument instanceof Handle handle) referenced.add(handle.getOwner());
                }
                for (String name : referenced) {
                    if (!loadForbidden(owner.name, name)) continue;
                    problem(key(owner, method.name), owner.name + "." + method.name + " references " + name
                            + ", which is in a mixin package and cannot be loaded at runtime");
                }
            }
        }
    }

    private boolean loadForbidden(String from, String name) {
        if (name.equals(from)) return false;
        String nested = name.startsWith(from + "$") ? name.substring(from.length() + 1) : "";
        if (!nested.isEmpty() && nested.chars().allMatch(Character::isDigit)) return false;
        if (mixinPackages.stream().noneMatch(name::startsWith)) return false;
        ClassNode target = load(name).orElse(null);
        // Interface mixins are loadable; anything else in a mixin package is not.
        return target == null || (target.access & Opcodes.ACC_INTERFACE) == 0
                || annotation(target.invisibleAnnotations, MIXIN) == null;
    }

    // Only the mixins listed in the packaged configs of this node are applied, so only those are checked.
    private void readConfigs(Path directory) throws IOException {
        java.util.regex.Pattern packagePattern = java.util.regex.Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Pattern listPattern = java.util.regex.Pattern.compile(
                "\"(?:mixins|client|server)\"\\s*:\\s*\\[(.*?)]", java.util.regex.Pattern.DOTALL);
        java.util.regex.Pattern entryPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
        try (Stream<Path> files = Files.list(directory)) {
            for (Path config : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String text = Files.readString(config);
                var packageMatcher = packagePattern.matcher(text);
                if (!packageMatcher.find()) continue;
                String prefix = packageMatcher.group(1).replace('.', '/') + "/";
                mixinPackages.add(prefix);
                var lists = listPattern.matcher(text);
                while (lists.find()) {
                    var entries = entryPattern.matcher(lists.group(1));
                    while (entries.find()) activeMixins.add(prefix + entries.group(1).replace('.', '/'));
                }
            }
        }
    }

    private boolean isActive(String mixinName) {
        return activeMixins.isEmpty() || activeMixins.contains(mixinName);
    }

    // Known issues (simple mixin class name + "." + member) are reported, but do not fail the check.
    private void problem(String key, String message) {
        if (knownIssues.contains(key)) notes.add("known issue: " + message);
        else problems.add(message);
    }

    private static String key(ClassNode mixin, String member) {
        return mixin.name.substring(mixin.name.lastIndexOf('/') + 1) + "." + member;
    }

    private void checkMixin(ClassNode mixin, AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        for (Object type : list(value(annotation, "value"))) targets.add(((Type) type).getInternalName());
        List<String> namedTargets = new ArrayList<>();
        for (Object name : list(value(annotation, "targets"))) namedTargets.add(((String) name).replace('.', '/'));
        targets.addAll(namedTargets);

        for (String targetName : targets) {
            ClassNode target = load(targetName).orElse(null);
            if (target == null) {
                String message = mixin.name + ": target class " + targetName + " does not exist";
                // A target named by string belongs to another mod; Mixin skips it when that mod is absent.
                if (namedTargets.contains(targetName)) notes.add("optional: " + message);
                else problem(key(mixin, "<class>"), message);
                continue;
            }
            for (MethodNode method : mixin.methods) checkMethod(mixin, target, method);
            for (FieldNode field : mixin.fields) {
                if (annotation(field.visibleAnnotations, SHADOW) == null && annotation(field.invisibleAnnotations, SHADOW) == null) continue;
                if (findField(target, field.name) == null) {
                    problem(key(mixin, field.name), mixin.name + ": @Shadow field " + field.name + " not found in " + targetName);
                }
            }
        }
    }

    private void checkMethod(ClassNode mixin, ClassNode target, MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        String where = mixin.name + "." + method.name;
        String key = key(mixin, method.name);

        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(SHADOW) || annotation.desc.equals(OVERWRITE)) {
                if (findMethod(target, method.name, method.desc) == null) {
                    unresolved(key, where + ": " + simple(annotation.desc) + " target " + method.name + method.desc
                            + " not found in " + target.name, method.name, Boolean.FALSE.equals(value(annotation, "remap")), false);
                }
            } else if (annotation.desc.equals(ACCESSOR)) {
                String name = (String) value(annotation, "value");
                if (name == null || name.isEmpty()) name = accessorName(method.name);
                if (findField(target, name) == null) {
                    problem(key, where + ": @Accessor field " + name + " not found in " + target.name);
                }
            } else if (annotation.desc.equals(INVOKER)) {
                String name = (String) value(annotation, "value");
                if (name == null || name.isEmpty()) name = invokerName(method.name);
                if (findMethodByName(target, name).isEmpty()) {
                    problem(key, where + ": @Invoker method " + name + " not found in " + target.name);
                }
            } else if (INJECTORS.contains(annotation.desc)) {
                checkInjector(key, where, target, method, annotation);
            }
        }
    }

    private void checkInjector(String key, String where, ClassNode target, MethodNode handler, AnnotationNode annotation) {
        Object require = value(annotation, "require");
        boolean optional = require instanceof Integer count && count == 0;
        boolean unmapped = Boolean.FALSE.equals(value(annotation, "remap"));
        for (Object selectorObject : list(value(annotation, "method"))) {
            String selector = (String) selectorObject;
            List<MethodNode> candidates = resolve(target, selector);
            if (candidates.isEmpty()) {
                unresolved(key, where + ": " + simple(annotation.desc) + " target " + selector + " not found in " + target.name,
                        selector, unmapped, optional);
                continue;
            }
            if (annotation.desc.equals(INJECT) && candidates.stream().noneMatch(candidate -> injectFits(candidate, handler))) {
                problem(key, where + ": @Inject handler " + handler.desc + " does not fit " + target.name + "."
                        + candidates.get(0).name + candidates.get(0).desc);
            }
            if (!optional) checkAtTargets(key, where, candidates, annotation);
        }
    }

    private void unresolved(String key, String message, String selector, boolean unmapped, boolean optional) {
        if (optional) {
            notes.add("optional: " + message);
        } else if (srgRuntime && unmapped && selector.matches("^[mf]_\\d+_.*")) {
            // Forge runs on SRG names in production; the named classes checked here do not contain them.
            notes.add("SRG name, checked only in game: " + message);
        } else {
            problem(key, message);
        }
    }

    // An injection point that names a call, field access or allocation must occur in the target method, as often as
    // the injector's require asks for (once when require is not set).
    private void checkAtTargets(String key, String where, List<MethodNode> candidates, AnnotationNode injector) {
        Object require = value(injector, "require");
        List<?> ats = list(value(injector, "at"));
        for (Object atObject : ats) {
            if (!(atObject instanceof AnnotationNode at)) continue;
            String kind = (String) value(at, "value");
            String target = (String) value(at, "target");
            if (kind == null || target == null || target.isEmpty()) continue;
            if (!List.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW").contains(kind)) continue;
            int required = ats.size() == 1 && require instanceof Integer count ? Math.max(count, 1) : 1;
            int found = 0;
            for (MethodNode method : candidates) found += countMatches(method, kind, target);
            if (found < required) {
                problem(key, where + ": @At(\"" + kind + "\") " + target + " occurs " + found + " time(s) in "
                        + candidates.get(0).name + candidates.get(0).desc + ", " + required + " required");
            }
        }
    }

    private static int countMatches(MethodNode method, String kind, String target) {
        if (method.instructions == null) return 0;
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (kind.equals("FIELD") && instruction instanceof FieldInsnNode field) {
                if (memberMatches(target, field.owner, field.name, ":" + field.desc)) count++;
            } else if (kind.startsWith("INVOKE") && instruction instanceof MethodInsnNode call) {
                if (memberMatches(target, call.owner, call.name, call.desc)) count++;
            } else if (kind.equals("NEW") && instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                String wanted = target.startsWith("L") && target.endsWith(";") ? target.substring(1, target.length() - 1) : target;
                if (wanted.replace('.', '/').equals(type.desc)) count++;
            }
        }
        return count;
    }

    // Matches "Lowner;name(desc)" and "Lowner;name:desc" targets; the owner and the descriptor may be left out.
    private static boolean memberMatches(String target, String owner, String name, String desc) {
        String member = target;
        int ownerEnd = target.indexOf(';');
        if (target.startsWith("L") && ownerEnd > 0) {
            if (!target.substring(1, ownerEnd).equals(owner)) return false;
            member = target.substring(ownerEnd + 1);
        }
        int paren = member.indexOf('(');
        int colon = member.indexOf(':');
        int split = paren < 0 ? colon : (colon < 0 ? paren : Math.min(paren, colon));
        String memberName = split < 0 ? member : member.substring(0, split);
        String memberDesc = split < 0 ? null : member.substring(split);
        return memberName.equals(name) && (memberDesc == null || memberDesc.equals(desc));
    }

    // A handler takes either only the CallbackInfo or the target's parameters followed by it (and optional locals).
    private static boolean injectFits(MethodNode target, MethodNode handler) {
        Type[] targetArgs = Type.getArgumentTypes(target.desc);
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        if (handlerArgs.length == 1 && isCallbackInfo(handlerArgs[0])) return true;
        if (handlerArgs.length < targetArgs.length + 1) return false;
        for (int index = 0; index < targetArgs.length; index++) {
            if (!targetArgs[index].equals(handlerArgs[index])) return false;
        }
        return isCallbackInfo(handlerArgs[targetArgs.length]);
    }

    private static boolean isCallbackInfo(Type type) {
        return type.getInternalName().startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    }

    private List<MethodNode> resolve(ClassNode target, String selector) {
        String name = selector;
        String desc = null;
        int paren = selector.indexOf('(');
        if (paren >= 0) {
            name = selector.substring(0, paren);
            desc = selector.substring(paren);
        }
        int owner = name.indexOf(';');
        if (name.startsWith("L") && owner >= 0) name = name.substring(owner + 1);
        boolean any = name.endsWith("*");
        if (any) name = name.substring(0, name.length() - 1);
        List<MethodNode> result = new ArrayList<>();
        for (MethodNode method : target.methods) {
            boolean nameMatches = name.isEmpty() || (any ? method.name.startsWith(name) : method.name.equals(name));
            if (nameMatches && (desc == null || method.desc.equals(desc))) result.add(method);
        }
        return result;
    }

    private MethodNode findMethod(ClassNode type, String name, String desc) {
        for (ClassNode current = type; current != null; current = superOf(current)) {
            for (MethodNode method : current.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) return method;
            }
        }
        return null;
    }

    private List<MethodNode> findMethodByName(ClassNode type, String name) {
        List<MethodNode> result = new ArrayList<>();
        for (ClassNode current = type; current != null; current = superOf(current)) {
            for (MethodNode method : current.methods) if (method.name.equals(name)) result.add(method);
        }
        return result;
    }

    private FieldNode findField(ClassNode type, String name) {
        for (ClassNode current = type; current != null; current = superOf(current)) {
            for (FieldNode field : current.fields) if (field.name.equals(name)) return field;
        }
        return null;
    }

    private ClassNode superOf(ClassNode type) {
        return type.superName == null ? null : load(type.superName).orElse(null);
    }

    private Optional<ClassNode> load(String internalName) {
        return cache.computeIfAbsent(internalName, name -> {
            String file = name + ".class";
            try {
                for (Path directory : directories.values()) {
                    Path path = directory.resolve(file);
                    if (Files.isRegularFile(path)) return Optional.of(read(Files.readAllBytes(path)));
                }
                for (ZipFile jar : jars) {
                    var entry = jar.getEntry(file);
                    if (entry == null) continue;
                    try (InputStream stream = jar.getInputStream(entry)) {
                        return Optional.of(read(stream.readAllBytes()));
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("cannot read " + file, exception);
            }
            return Optional.empty();
        });
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals(desc)) return annotation;
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        }
        return null;
    }

    private static List<?> list(Object value) {
        if (value == null) return List.of();
        return value instanceof List<?> list ? list : List.of(value);
    }

    private static String accessorName(String method) {
        for (String prefix : List.of("get", "set", "is")) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                String rest = method.substring(prefix.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }

    private static String invokerName(String method) {
        for (String prefix : List.of("call", "invoke", "new", "create")) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                String rest = method.substring(prefix.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }

    private static String simple(String desc) {
        return "@" + desc.substring(desc.lastIndexOf('/') + 1, desc.length() - 1);
    }
}
