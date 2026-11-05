package com.rams.editor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/**
 * One-pass highlighter. Gives every character a color number (0 = plain text).
 * It reads the file once from start to end, so even long files take only a few milliseconds.
 */
public final class FastHighlighter {

    // Color numbers (same as SyntaxHighlighter.PALETTE)
    private static final byte COMMENT = 1, BRACKET = 2, BLUE = 3, CYAN = 4, YELLOW = 5,
            PURPLE = 6, VIOLET = 7, CREAM = 8, TEAL = 9, MINT = 10, PALE_MINT = 11;

    private static final HashSet<String> JS_DECL = set(
            "var", "let", "const", "function", "class", "new", "typeof", "instanceof",
            "this", "extends", "static");
    private static final HashSet<String> JS_FLOW = set(
            "if", "else", "for", "while", "do", "break", "continue", "return", "try", "catch",
            "finally", "switch", "case", "default", "throw", "import", "export", "async",
            "await", "yield", "in", "of", "delete");
    private static final HashSet<String> JS_LITERAL = set("true", "false", "null", "undefined");

    private static final HashSet<String> CSS_CYAN_WORDS = set(
            "block", "inline", "inline-block", "flex", "grid", "absolute", "relative", "fixed",
            "sticky", "center", "left", "right", "top", "bottom", "solid", "dashed", "dotted",
            "bold", "italic", "column", "row", "wrap", "nowrap", "pointer", "cover", "contain");
    private static final HashSet<String> CSS_VIOLET_WORDS = set(
            "none", "auto", "inherit", "initial", "hidden", "visible", "transparent", "normal",
            "unset");
    private static final HashSet<String> CSS_UNITS = set(
            "px", "em", "rem", "vh", "vw", "%", "pt", "pc", "cm", "mm", "in", "s", "ms", "deg",
            "fr");
    private static final HashSet<String> CSS_NESTING_AT = set(
            "media", "supports", "keyframes", "-webkit-keyframes", "layer", "container",
            "document");

    private static HashSet<String> set(String... words) {
        return new HashSet<>(Arrays.asList(words));
    }

    private FastHighlighter() {}

    public static byte[] analyze(String code, String name) {
        byte[] out = new byte[code.length()];
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "html": case "htm": case "xml": case "svg":
                html(code, out);
                break;
            case "css":
                css(code, out, 0, code.length());
                break;
            case "js": case "mjs": case "cjs": case "jsx": case "ts": case "tsx": case "json":
                js(code, out, 0, code.length());
                break;
            default:
                break;
        }
        return out;
    }

    // ---------------------------------------------------------------- HTML

    private static void html(String s, byte[] out) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            if (s.charAt(i) != '<') { i++; continue; }

            if (s.startsWith("<!--", i)) {
                int e = s.indexOf("-->", i + 4);
                e = e < 0 ? n : e + 3;
                fill(out, i, e, COMMENT);
                i = e;
                continue;
            }
            if (s.startsWith("<!", i)) {
                int e = s.indexOf('>', i);
                e = e < 0 ? n : e + 1;
                fill(out, i, e, BRACKET);
                i = e;
                continue;
            }

            int j = i + 1;
            boolean closing = false;
            if (j < n && s.charAt(j) == '/') { closing = true; j++; }
            if (j >= n || !isAlpha(s.charAt(j))) { i++; continue; }

            fill(out, i, j, BRACKET);
            int nameStart = j;
            while (j < n && (isAlnum(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == ':')) j++;
            fill(out, nameStart, j, BLUE);
            String tag = s.substring(nameStart, j).toLowerCase(Locale.ROOT);

            // attributes
            while (j < n) {
                char d = s.charAt(j);
                if (d == '>') { out[j] = BRACKET; j++; break; }
                if (d == '/' && j + 1 < n && s.charAt(j + 1) == '>') {
                    fill(out, j, j + 2, BRACKET);
                    j += 2;
                    break;
                }
                if (isAlpha(d) || d == '@' || d == ':' || d == '_') {
                    int as = j;
                    while (j < n && (isAlnum(s.charAt(j)) || s.charAt(j) == '-'
                            || s.charAt(j) == ':' || s.charAt(j) == '@' || s.charAt(j) == '_'
                            || s.charAt(j) == '.')) j++;
                    fill(out, as, j, CYAN);
                    int k = j;
                    while (k < n && isSpace(s.charAt(k))) k++;
                    if (k < n && s.charAt(k) == '=') {
                        k++;
                        while (k < n && isSpace(s.charAt(k))) k++;
                        if (k < n && (s.charAt(k) == '"' || s.charAt(k) == '\'')) {
                            char q = s.charAt(k);
                            int e = s.indexOf(q, k + 1);
                            e = e < 0 ? n : e + 1;
                            fill(out, k, e, YELLOW);
                            j = e;
                        } else {
                            int vs = k;
                            while (k < n && !isSpace(s.charAt(k)) && s.charAt(k) != '>') k++;
                            fill(out, vs, k, YELLOW);
                            j = k;
                        }
                    }
                    continue;
                }
                j++;
            }
            i = j;

            // <style> and <script> contents use the CSS / JS rules
            if (!closing && (tag.equals("style") || tag.equals("script"))) {
                int e = indexOfIgnoreCase(s, "</" + tag, i);
                if (e < 0) e = n;
                if (tag.equals("style")) css(s, out, i, e);
                else js(s, out, i, e);
                i = e;
            }
        }
    }

    // ----------------------------------------------------------------- CSS

    private static void css(String s, byte[] out, int start, int end) {
        // stack of contexts: 0 = rules (top level, @media...), 1 = declarations
        int[] stack = new int[64];
        int sp = 0;
        int ctx = 0;
        boolean inValue = false;
        boolean preludeNesting = false;
        int paren = 0;
        int i = start;

        while (i < end) {
            char c = s.charAt(i);

            if (c == '/' && i + 1 < end && s.charAt(i + 1) == '*') {
                int ce = s.indexOf("*/", i + 2);
                ce = (ce < 0 || ce + 2 > end) ? end : ce + 2;
                fill(out, i, ce, COMMENT);
                i = ce;
                continue;
            }
            if (c == '"' || c == '\'') {
                int e = endOfString(s, i, end);
                fill(out, i, e, YELLOW);
                i = e;
                continue;
            }
            if (c == '{') {
                if (sp < stack.length) stack[sp++] = ctx;
                ctx = preludeNesting ? 0 : 1;
                preludeNesting = false;
                inValue = false;
                paren = 0;
                i++;
                continue;
            }
            if (c == '}') {
                if (sp > 0) ctx = stack[--sp];
                inValue = false;
                paren = 0;
                i++;
                continue;
            }
            if (c == ';') { inValue = false; i++; continue; }
            if (c == '(') { paren++; i++; continue; }
            if (c == ')') { if (paren > 0) paren--; i++; continue; }

            boolean valueLike = (ctx == 1 && inValue) || paren > 0;

            if (!valueLike) {
                if (ctx == 0) {
                    // selector text, @rules
                    if (c == '@') {
                        int j = i + 1;
                        while (j < end && isIdentChar(s.charAt(j))) j++;
                        fill(out, i, j, PURPLE);
                        String at = s.substring(i + 1, j).toLowerCase(Locale.ROOT);
                        preludeNesting = CSS_NESTING_AT.contains(at);
                        i = j;
                        continue;
                    }
                    if (c == '.' || c == '#' || c == ':' || isIdentStart(c)) {
                        int j = i;
                        while (j < end && (s.charAt(j) == '.' || s.charAt(j) == '#'
                                || s.charAt(j) == ':')) j++;
                        if (j < end && s.charAt(j) == '-') j++;
                        int idStart = j;
                        while (j < end && isIdentChar(s.charAt(j))) j++;
                        if (j > idStart) fill(out, i, j, MINT);
                        i = Math.max(j, i + 1);
                        continue;
                    }
                    i++;
                    continue;
                } else {
                    // inside { } : a property name before the colon
                    if (isIdentStart(c) || c == '-') {
                        int j = i;
                        while (j < end && isIdentChar(s.charAt(j))) j++;
                        int k = j;
                        while (k < end && isSpace(s.charAt(k))) k++;
                        if (k < end && s.charAt(k) == ':') {
                            fill(out, i, j, CYAN);
                            inValue = true;
                            i = k + 1;
                            continue;
                        }
                        i = Math.max(j, i + 1);
                        continue;
                    }
                    i++;
                    continue;
                }
            }

            // ---- value-like text (after a colon, or inside parentheses) ----
            if (isDigit(c) || (c == '.' && i + 1 < end && isDigit(s.charAt(i + 1)))
                    || (c == '-' && i + 1 < end && (isDigit(s.charAt(i + 1))
                    || (s.charAt(i + 1) == '.' && i + 2 < end && isDigit(s.charAt(i + 2)))))) {
                int j = i;
                if (s.charAt(j) == '-') j++;
                while (j < end && isDigit(s.charAt(j))) j++;
                if (j < end && s.charAt(j) == '.' && j + 1 < end && isDigit(s.charAt(j + 1))) {
                    j++;
                    while (j < end && isDigit(s.charAt(j))) j++;
                }
                int us = j;
                while (j < end && (isAlpha(s.charAt(j)) || s.charAt(j) == '%')) j++;
                String unit = s.substring(us, j).toLowerCase(Locale.ROOT);
                if (j > us && CSS_UNITS.contains(unit)) fill(out, i, j, PALE_MINT);
                else if (j == us) fill(out, i, j, CYAN);
                i = Math.max(j, i + 1);
                continue;
            }
            if (c == '#' && i + 1 < end && isHex(s.charAt(i + 1))) {
                int j = i + 1;
                while (j < end && isHex(s.charAt(j))) j++;
                int len = j - i - 1;
                if (len >= 3 && len <= 8 && !(j < end && isIdentChar(s.charAt(j)))) {
                    fill(out, i, j, VIOLET);
                }
                i = Math.max(j, i + 1);
                continue;
            }
            if (isIdentStart(c) || c == '-') {
                int j = i;
                while (j < end && isIdentChar(s.charAt(j))) j++;
                String word = s.substring(i, j).toLowerCase(Locale.ROOT);
                if (CSS_VIOLET_WORDS.contains(word)) fill(out, i, j, VIOLET);
                else if (CSS_CYAN_WORDS.contains(word)) fill(out, i, j, CYAN);
                else if (paren > 0) {
                    // a property inside parentheses, like (min-width: 768px)
                    int k = j;
                    while (k < end && isSpace(s.charAt(k))) k++;
                    if (k < end && s.charAt(k) == ':') fill(out, i, j, CYAN);
                }
                i = Math.max(j, i + 1);
                continue;
            }
            i++;
        }
    }

    // ----------------------------------------------------------- JavaScript

    private static void js(String s, byte[] out, int start, int end) {
        int i = start;
        while (i < end) {
            char c = s.charAt(i);

            if (c == '/' && i + 1 < end) {
                char d = s.charAt(i + 1);
                if (d == '/') {
                    int e = s.indexOf('\n', i);
                    if (e < 0 || e > end) e = end;
                    fill(out, i, e, COMMENT);
                    i = e;
                    continue;
                }
                if (d == '*') {
                    int e = s.indexOf("*/", i + 2);
                    e = (e < 0 || e + 2 > end) ? end : e + 2;
                    fill(out, i, e, COMMENT);
                    i = e;
                    continue;
                }
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                int e = endOfString(s, i, end);
                fill(out, i, e, YELLOW);
                i = e;
                continue;
            }
            if (c == '`') {
                int j = i + 1;
                while (j < end && s.charAt(j) != '`') {
                    if (s.charAt(j) == '\\') j++;
                    j++;
                }
                int e = Math.min(j + 1, end);
                fill(out, i, e, YELLOW);
                i = e;
                continue;
            }
            if (isDigit(c) || (c == '.' && i + 1 < end && isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < end && (isAlnum(s.charAt(j)) || s.charAt(j) == '.'
                        || s.charAt(j) == '_')) {
                    // stop at a method call like 1.toFixed
                    if (s.charAt(j) == '.' && !(j + 1 < end && isDigit(s.charAt(j + 1)))) break;
                    j++;
                }
                fill(out, i, j, PALE_MINT);
                i = Math.max(j, i + 1);
                continue;
            }
            if (isAlpha(c) || c == '_' || c == '$') {
                int j = i + 1;
                while (j < end && (isAlnum(s.charAt(j)) || s.charAt(j) == '_'
                        || s.charAt(j) == '$')) j++;
                String word = s.substring(i, j);
                byte color;
                if (JS_DECL.contains(word)) color = BLUE;
                else if (JS_FLOW.contains(word)) color = PURPLE;
                else if (JS_LITERAL.contains(word)) color = VIOLET;
                else {
                    int k = j;
                    while (k < end && isSpace(s.charAt(k))) k++;
                    if (k < end && s.charAt(k) == '(') color = CREAM;
                    else if (Character.isUpperCase(c)) color = TEAL;
                    else color = CYAN;
                }
                fill(out, i, j, color);
                i = j;
                continue;
            }
            i++;
        }
    }

    // -------------------------------------------------------------- helpers

    private static int endOfString(String s, int start, int end) {
        char q = s.charAt(start);
        int j = start + 1;
        while (j < end) {
            char c = s.charAt(j);
            if (c == '\\') { j += 2; continue; }
            if (c == q) return j + 1;
            if (c == '\n') return j;
            j++;
        }
        return end;
    }

    private static int indexOfIgnoreCase(String s, String what, int from) {
        int n = s.length() - what.length();
        for (int i = from; i <= n; i++) {
            if (s.regionMatches(true, i, what, 0, what.length())) return i;
        }
        return -1;
    }

    private static void fill(byte[] out, int from, int to, byte color) {
        if (from < to) Arrays.fill(out, from, Math.min(to, out.length), color);
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlnum(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private static boolean isHex(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isIdentStart(char c) {
        return isAlpha(c) || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return isAlnum(c) || c == '_' || c == '-';
    }
}