package com.bionote.agent.fit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preset fit equations and natural-language aliases. Custom univariate formulas are also supported
 * via {@link #extractCustomEquation(String)} when the user writes an explicit {@code y=...}
 * expression.
 */
public final class FitMethodCatalog {
    public record Method(String id, String label, String equation) {}

    private static final Pattern CUSTOM_EQUATION =
            Pattern.compile("(?i)y\\s*=\\s*([0-9a-zA-Z_+\\-*/^().,\\s]+)");

    private static final List<Method> METHODS =
            List.of(
                    new Method("linear", "线性", "y=a+b*x"),
                    new Method("quadratic", "二次", "y=a+b*x+c*x^2"),
                    new Method("cubic", "三次", "y=a+b*x+c*x^2+d*x^3"),
                    new Method("exp_decay", "指数衰减", "y=a*exp(-k*x)+b"),
                    new Method("exp_growth", "指数增长", "y=a*exp(k*x)+b"),
                    new Method("log", "对数", "y=a+b*log(x)"),
                    new Method("sqrt", "平方根", "y=a+b*sqrt(x)"),
                    new Method("power", "幂函数", "y=a*x^b"),
                    new Method("reciprocal", "倒数", "y=a+b/x"),
                    new Method("hyperbola", "双曲线/米氏", "y=a*x/(b+x)"),
                    new Method("logistic", "Logistic", "y=a/(1+exp(-b*(x-c)))"));

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        putAliases("linear", "linear", "线性", "一次", "直线", "标曲线性", "标准曲线线性");
        putAliases("quadratic", "quadratic", "二次", "抛物线");
        putAliases("cubic", "cubic", "三次", "三次多项式");
        putAliases("exp_decay", "exp_decay", "指数衰减", "衰减", "半衰期", "dose response", "剂量反应衰减");
        putAliases("exp_growth", "exp_growth", "指数增长", "增长");
        putAliases("log", "log", "对数", "半对数", "log线性");
        putAliases("sqrt", "sqrt", "平方根", "根号");
        putAliases("power", "power", "幂函数", "幂律", "power law");
        putAliases("reciprocal", "reciprocal", "倒数", "反比例");
        putAliases("hyperbola", "hyperbola", "双曲线", "米氏", "michaelis", "饱和曲线", "michaelis-menten");
        putAliases("logistic", "logistic", "sigmoid", "逻辑斯蒂", "S型", "s曲线", "生长曲线logistic");
    }

    private FitMethodCatalog() {}

    public static List<Method> all() {
        return METHODS;
    }

    public static List<String> allEquations() {
        return METHODS.stream().map(Method::equation).toList();
    }

    public static Method byId(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Method method : METHODS) {
            if (method.id().equals(key)) return method;
        }
        return null;
    }

    /** Resolve an explicit user method phrase to a catalog equation, or null if unknown. */
    public static String resolveEquation(String phrase) {
        if (phrase == null || phrase.isBlank()) return null;
        String custom = extractCustomEquation(phrase);
        if (custom != null) return custom;
        String text = phrase.trim().toLowerCase(Locale.ROOT);
        for (Method method : METHODS) {
            if (text.contains(method.equation().toLowerCase(Locale.ROOT))) return method.equation();
            if (text.equals(method.id()) || text.contains(method.label().toLowerCase(Locale.ROOT)))
                return method.equation();
        }
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (text.contains(entry.getKey())) {
                Method method = byId(entry.getValue());
                return method == null ? null : method.equation();
            }
        }
        return null;
    }

    public static String resolveFromMessage(String message) {
        if (message == null || message.isBlank()) return null;
        String custom = extractCustomEquation(message);
        if (custom != null) return custom;
        String lower = message.toLowerCase(Locale.ROOT);
        // Prefer longer / more specific aliases first (map iteration order).
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (lower.contains(entry.getKey()) || message.contains(entry.getKey())) {
                Method method = byId(entry.getValue());
                if (method != null) return method.equation();
            }
        }
        return null;
    }

    /**
     * Extract and validate a user-written univariate formula such as {@code y=a*x/(b+x)}. Returns
     * normalized {@code y=...} or null.
     */
    public static String extractCustomEquation(String message) {
        if (message == null || message.isBlank()) return null;
        Matcher matcher = CUSTOM_EQUATION.matcher(message);
        String lastValid = null;
        while (matcher.find()) {
            String rhs = matcher.group(1).trim();
            String candidate = "y=" + rhs;
            try {
                ExpressionParser.CompiledEquation compiled =
                        ExpressionParser.parseEquation(candidate);
                lastValid = "y=" + compiled.rhs().replaceAll("\\s+", "");
            } catch (RuntimeException ignored) {
                // keep scanning
            }
        }
        return lastValid;
    }

    public static boolean wantsAutoCompare(String message) {
        if (message == null) return false;
        String text = message.toLowerCase(Locale.ROOT);
        return text.contains("不确定")
                || text.contains("不知道")
                || text.contains("随便")
                || text.contains("自动")
                || text.contains("试试看")
                || text.contains("试一下")
                || text.contains("比选")
                || text.contains("比较模型")
                || text.contains("选最优")
                || text.contains("auto")
                || text.contains("不确定方程")
                || text.contains("方程不确定");
    }

    public static String catalogDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Preset univariate equations (also accept custom y=... with params a-z, x, exp/log/ln/sqrt/sin/cos, + - * / ^):\n");
        for (Method method : METHODS) {
            sb.append("- id=")
                    .append(method.id())
                    .append(" label=")
                    .append(method.label())
                    .append(" equation=")
                    .append(method.equation())
                    .append('\n');
        }
        return sb.toString();
    }

    private static void putAliases(String id, String... aliases) {
        for (String alias : aliases) {
            ALIASES.put(alias.toLowerCase(Locale.ROOT), id);
        }
    }
}
