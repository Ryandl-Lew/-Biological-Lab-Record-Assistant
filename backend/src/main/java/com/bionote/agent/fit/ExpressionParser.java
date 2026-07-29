package com.bionote.agent.fit;

import com.bionote.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses RHS of equations like {@code y = a + b*x} or {@code a*exp(-k*x)+b}.
 * Supports + - * / ^, parentheses, and functions exp/log/ln/sqrt/sin/cos.
 */
public final class ExpressionParser {
    private ExpressionParser() {}

    public record CompiledEquation(String equation, String rhs, List<String> parameters, Expr root) {
        public double value(double x, double[] params) {
            return root.eval(x, params);
        }
    }

    public static CompiledEquation parseEquation(String raw) {
        if (raw == null || raw.isBlank()) throw invalid("equation is required");
        String text = raw.trim().replace("（", "(").replace("）", ")");
        int eq = text.indexOf('=');
        String rhs;
        if (eq >= 0) {
            String left = text.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!left.equals("y") && !left.isEmpty()) throw invalid("equation must assign to y");
            rhs = text.substring(eq + 1).trim();
        } else {
            rhs = text;
        }
        if (rhs.isBlank() || rhs.length() > 200) throw invalid("equation RHS is invalid");
        if (!rhs.matches("[0-9a-zA-Z_+\\-*/^().,\\s]+")) throw invalid("equation contains unsupported characters");
        Parser parser = new Parser(rhs);
        Expr root = parser.parseExpression();
        parser.expectEnd();
        List<String> parameters = new ArrayList<>(parser.parameters);
        if (parameters.size() > 6) throw invalid("equation allows at most 6 parameters");
        if (parameters.isEmpty()) throw invalid("equation must contain at least one free parameter");
        String normalized = "y = " + rhs.replaceAll("\\s+", "");
        return new CompiledEquation(normalized, rhs, parameters, root);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FIT_INVALID_EQUATION",
                "方程无效：" + message + "。请使用预置方程（如 y=a+b*x），或改用「多元线性回归」并对多列自变量分别指定列名。");
    }

    interface Expr {
        double eval(double x, double[] params);
    }

    private static final class Parser {
        private final String input;
        private int pos;
        private final LinkedHashSet<String> parameters = new LinkedHashSet<>();
        private static final Set<String> FUNCS = Set.of("exp", "log", "ln", "sqrt", "sin", "cos");

        Parser(String input) { this.input = input; }

        Expr parseExpression() {
            Expr left = parseTerm();
            while (true) {
                skipWs();
                if (match('+')) {
                    Expr right = parseTerm();
                    Expr l = left;
                    left = (x, p) -> l.eval(x, p) + right.eval(x, p);
                } else if (match('-')) {
                    Expr right = parseTerm();
                    Expr l = left;
                    left = (x, p) -> l.eval(x, p) - right.eval(x, p);
                } else break;
            }
            return left;
        }

        Expr parseTerm() {
            Expr left = parsePower();
            while (true) {
                skipWs();
                if (match('*')) {
                    Expr right = parsePower();
                    Expr l = left;
                    left = (x, p) -> l.eval(x, p) * right.eval(x, p);
                } else if (match('/')) {
                    Expr right = parsePower();
                    Expr l = left;
                    left = (x, p) -> l.eval(x, p) / right.eval(x, p);
                } else break;
            }
            return left;
        }

        Expr parsePower() {
            Expr left = parseUnary();
            skipWs();
            if (match('^')) {
                Expr right = parseUnary();
                return (x, p) -> Math.pow(left.eval(x, p), right.eval(x, p));
            }
            return left;
        }

        Expr parseUnary() {
            skipWs();
            if (match('+')) return parseUnary();
            if (match('-')) {
                Expr inner = parseUnary();
                return (x, p) -> -inner.eval(x, p);
            }
            return parsePrimary();
        }

        Expr parsePrimary() {
            skipWs();
            if (match('(')) {
                Expr inner = parseExpression();
                if (!match(')')) throw invalid("missing closing parenthesis");
                return inner;
            }
            if (Character.isDigit(peek()) || peek() == '.') return parseNumber();
            if (Character.isLetter(peek()) || peek() == '_') {
                String ident = parseIdent();
                skipWs();
                if (match('(')) {
                    if (!FUNCS.contains(ident)) throw invalid("unsupported function: " + ident);
                    Expr arg = parseExpression();
                    if (!match(')')) throw invalid("missing closing parenthesis");
                    return switch (ident) {
                        case "exp" -> (x, p) -> Math.exp(arg.eval(x, p));
                        case "log", "ln" -> (x, p) -> Math.log(arg.eval(x, p));
                        case "sqrt" -> (x, p) -> Math.sqrt(arg.eval(x, p));
                        case "sin" -> (x, p) -> Math.sin(arg.eval(x, p));
                        case "cos" -> (x, p) -> Math.cos(arg.eval(x, p));
                        default -> throw invalid("unsupported function: " + ident);
                    };
                }
                if ("x".equals(ident)) return (x, p) -> x;
                if ("e".equals(ident)) return (x, p) -> Math.E;
                if ("pi".equals(ident)) return (x, p) -> Math.PI;
                parameters.add(ident);
                int index = indexOf(parameters, ident);
                return (x, p) -> p[index];
            }
            throw invalid("unexpected token near position " + pos);
        }

        Expr parseNumber() {
            int start = pos;
            while (Character.isDigit(peek()) || peek() == '.') pos++;
            try {
                double value = Double.parseDouble(input.substring(start, pos));
                return (x, p) -> value;
            } catch (NumberFormatException e) {
                throw invalid("方程中的数值无法解析（请勿使用 y=a+b1*x1+... 省略式；多元回归请用列名列表）");
            }
        }

        String parseIdent() {
            int start = pos;
            while (Character.isLetterOrDigit(peek()) || peek() == '_') pos++;
            return input.substring(start, pos);
        }

        void expectEnd() {
            skipWs();
            if (pos < input.length()) throw invalid("unexpected trailing input");
        }

        void skipWs() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        boolean match(char c) {
            skipWs();
            if (pos < input.length() && input.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        char peek() {
            return pos < input.length() ? input.charAt(pos) : '\0';
        }

        static int indexOf(LinkedHashSet<String> set, String value) {
            int i = 0;
            for (String item : set) {
                if (item.equals(value)) return i;
                i++;
            }
            throw invalid("parameter not found: " + value);
        }
    }
}
