package com.rams.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which suggestions to show while you type: HTML tags and attributes,
 * CSS properties and values, JavaScript keywords and words from your own file.
 * Pure text in, list out, so it is easy to test.
 */
public final class Suggestions {

    public static final class Item {
        public final String label;
        public final String detail;
        public final String commit;
        public final int cursorBack;     // how far back from the end to put the cursor

        Item(String label, String detail, String commit, int cursorBack) {
            this.label = label;
            this.detail = detail;
            this.commit = commit;
            this.cursorBack = cursorBack;
        }
    }

    public static final class Result {
        public final String prefix;
        public final List<Item> items;

        Result(String prefix, List<Item> items) {
            this.prefix = prefix;
            this.items = items;
        }
    }

    private static final int MAX = 40;

    private Suggestions() {}

    /**
     * @param fileName name of the open file (the type decides the rules)
     * @param before   all the text before the cursor
     * @param whole    the whole file (used for words you already wrote)
     */
    public static Result suggest(String fileName, String before, String whole) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);

        String mode;
        switch (ext) {
            case "html": case "htm": case "svg": case "xml": case "php":
                mode = htmlRegion(before);
                break;
            case "css":
                mode = "css";
                break;
            case "js": case "mjs": case "jsx": case "ts": case "tsx":
                mode = "js";
                break;
            default:
                return null;
        }
        switch (mode) {
            case "css": return css(before);
            case "js": return js(before, whole);
            default: return html(before, whole);
        }
    }

    // Is the cursor in plain HTML, or inside a <style> or <script> block?
    private static String htmlRegion(String before) {
        int styleOpen = lastIgnoreCase(before, "<style");
        int styleClose = lastIgnoreCase(before, "</style");
        if (styleOpen > styleClose && before.indexOf('>', styleOpen) >= 0) return "css";
        int scriptOpen = lastIgnoreCase(before, "<script");
        int scriptClose = lastIgnoreCase(before, "</script");
        if (scriptOpen > scriptClose && before.indexOf('>', scriptOpen) >= 0) return "js";
        return "html";
    }

    private static int lastIgnoreCase(String s, String what) {
        for (int i = s.length() - what.length(); i >= 0; i--) {
            if (s.regionMatches(true, i, what, 0, what.length())) return i;
        }
        return -1;
    }

    // ================================================================ HTML

    private static final String[] TAGS = {
            "a", "abbr", "address", "article", "aside", "audio", "b", "blockquote", "body", "br",
            "button", "canvas", "caption", "code", "col", "details", "dialog", "div", "dl", "dt",
            "dd", "em", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
            "h4", "h5", "h6", "head", "header", "hr", "html", "i", "iframe", "img", "input",
            "label", "legend", "li", "link", "main", "mark", "meta", "nav", "ol", "optgroup",
            "option", "p", "pre", "script", "section", "select", "small", "source", "span",
            "strong", "style", "sub", "summary", "sup", "svg", "table", "tbody", "td",
            "template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "u", "ul",
            "video"
    };

    private static final String[] GLOBAL_ATTRS = {
            "class", "id", "style", "title", "lang", "hidden", "tabindex", "data-", "role",
            "aria-label", "draggable", "contenteditable"
    };

    private static final Map<String, String[]> TAG_ATTRS = new HashMap<>();
    private static final Set<String> BOOLEAN_ATTRS = new HashSet<>(Arrays.asList(
            "hidden", "required", "disabled", "checked", "readonly", "selected", "defer",
            "async", "controls", "autoplay", "loop", "muted", "multiple", "autofocus"));

    static {
        TAG_ATTRS.put("a", new String[]{"href", "target", "rel", "download"});
        TAG_ATTRS.put("img", new String[]{"src", "alt", "width", "height", "loading"});
        TAG_ATTRS.put("input", new String[]{"type", "name", "value", "placeholder", "required",
                "disabled", "checked", "readonly", "min", "max", "maxlength", "autofocus"});
        TAG_ATTRS.put("link", new String[]{"rel", "href", "type"});
        TAG_ATTRS.put("meta", new String[]{"name", "content", "charset"});
        TAG_ATTRS.put("script", new String[]{"src", "type", "defer", "async"});
        TAG_ATTRS.put("form", new String[]{"action", "method"});
        TAG_ATTRS.put("button", new String[]{"type", "disabled", "name", "value"});
        TAG_ATTRS.put("label", new String[]{"for"});
        TAG_ATTRS.put("textarea", new String[]{"name", "rows", "cols", "placeholder",
                "required", "disabled"});
        TAG_ATTRS.put("select", new String[]{"name", "required", "disabled", "multiple"});
        TAG_ATTRS.put("option", new String[]{"value", "selected", "disabled"});
        TAG_ATTRS.put("iframe", new String[]{"src", "width", "height", "allow"});
        TAG_ATTRS.put("video", new String[]{"src", "controls", "autoplay", "loop", "muted",
                "poster", "width", "height"});
        TAG_ATTRS.put("audio", new String[]{"src", "controls", "autoplay", "loop", "muted"});
        TAG_ATTRS.put("td", new String[]{"colspan", "rowspan"});
        TAG_ATTRS.put("th", new String[]{"colspan", "rowspan", "scope"});
    }

    private static Result html(String before, String whole) {
        int lt = before.lastIndexOf('<');
        int gt = before.lastIndexOf('>');
        if (lt < 0 || lt < gt) return null;            // not inside a tag
        String inTag = before.substring(lt + 1);

        // 1) the tag name itself: <di  or  </di
        if (inTag.matches("/?[A-Za-z0-9-]*")) {
            boolean closing = inTag.startsWith("/");
            String prefix = closing ? inTag.substring(1) : inTag;
            List<Item> items = new ArrayList<>();
            if (closing) {
                for (String t : openTags(before.substring(0, lt))) {
                    if (startsWith(t, prefix)) items.add(new Item(t, "close", t + ">", 0));
                }
                return new Result(prefix, items);
            }
            for (String t : TAGS) {
                if (startsWith(t, prefix)) items.add(tagItem(t));
            }
            return new Result(prefix, items);
        }

        String tagName = inTag.split("[\\s/>]", 2)[0].toLowerCase(Locale.ROOT);

        // 2) inside an attribute value: class="..."
        int quotes = 0;
        for (int i = 0; i < inTag.length(); i++) {
            char c = inTag.charAt(i);
            if (c == '"') quotes++;
        }
        if (quotes % 2 == 1) {
            int q = inTag.lastIndexOf('"');
            Matcher m = Pattern.compile("([a-zA-Z-]+)\\s*=\\s*\"[^\"]*$").matcher(inTag);
            String attr = m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
            String valuePart = inTag.substring(q + 1);
            int sp = Math.max(valuePart.lastIndexOf(' '), -1);
            String prefix = valuePart.substring(sp + 1);
            if (attr.equals("class")) return new Result(prefix, classNames(whole, prefix));
            if (attr.equals("type") && tagName.equals("input")) {
                return new Result(prefix, words(prefix, "value", "text", "email", "password",
                        "number", "checkbox", "radio", "submit", "button", "date", "file",
                        "search", "tel", "url", "color", "range"));
            }
            if (attr.equals("rel")) {
                return new Result(prefix, words(prefix, "value", "stylesheet", "icon",
                        "preload", "canonical", "noopener", "noreferrer"));
            }
            if (attr.equals("target")) {
                return new Result(prefix, words(prefix, "value", "_blank", "_self", "_parent",
                        "_top"));
            }
            return null;
        }

        // 3) an attribute name
        String prefix = "";
        Matcher pm = Pattern.compile("([A-Za-z0-9:_-]*)$").matcher(inTag);
        if (pm.find()) prefix = pm.group(1);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String[] specific = TAG_ATTRS.get(tagName);
        if (specific != null) names.addAll(Arrays.asList(specific));
        names.addAll(Arrays.asList(GLOBAL_ATTRS));
        List<Item> items = new ArrayList<>();
        for (String n : names) {
            if (!startsWith(n, prefix)) continue;
            if (n.equals("data-")) items.add(new Item("data-", "attribute", "data-", 0));
            else if (BOOLEAN_ATTRS.contains(n)) items.add(new Item(n, "attribute", n, 0));
            else items.add(mk(n, "attribute", n + "=\"|\""));
        }
        return new Result(prefix, items);
    }

    private static final Set<String> VOID_TAGS = new HashSet<>(Arrays.asList(
            "br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed",
            "param", "source", "track", "wbr"));

    // The tags that are open at this point, nearest first (each name once)
    private static List<String> openTags(String text) {
        List<String> stack = new ArrayList<>();
        Matcher m = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9-]*)[^<>]*?(/?)>").matcher(text);
        while (m.find()) {
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (VOID_TAGS.contains(name) || !m.group(3).isEmpty()) continue;
            if (!m.group(1).isEmpty()) {
                for (int i = stack.size() - 1; i >= 0; i--) {
                    if (stack.get(i).equals(name)) {
                        while (stack.size() > i) stack.remove(stack.size() - 1);
                        break;
                    }
                }
            } else {
                stack.add(name);
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = stack.size() - 1; i >= 0; i--) out.add(stack.get(i));
        return new ArrayList<>(out);
    }

    // "|" in the template marks where the cursor should end up
    private static Item mk(String label, String detail, String template) {
        int at = template.indexOf('|');
        if (at < 0) return new Item(label, detail, template, 0);
        String commit = template.substring(0, at) + template.substring(at + 1);
        return new Item(label, detail, commit, commit.length() - at);
    }

    // A tag completes to the whole element, with the cursor inside it
    private static Item tagItem(String t) {
        switch (t) {
            case "img": return mk(t, "tag", "img src=\"|\" alt=\"\">");
            case "input": return mk(t, "tag", "input type=\"text\">");
            case "br": case "hr": case "col": case "source": return mk(t, "tag", t + ">");
            case "link": return mk(t, "tag", "link rel=\"stylesheet\" href=\"|\">");
            case "meta": return mk(t, "tag", "meta name=\"|\" content=\"\">");
            case "a": return mk(t, "tag", "a href=\"|\"></a>");
            case "script": return mk(t, "tag", "script src=\"|\"></script>");
            default: return mk(t, "tag", t + ">|</" + t + ">");
        }
    }

    private static final Set<String> MEMBER_FUNCS = new HashSet<>(Arrays.asList(
            "log", "error", "warn", "info", "table", "querySelector", "querySelectorAll",
            "getElementById", "getElementsByClassName", "getElementsByTagName", "createElement",
            "addEventListener", "removeEventListener", "add", "remove", "toggle", "contains",
            "getAttribute", "setAttribute", "removeAttribute", "appendChild", "append",
            "prepend", "push", "pop", "shift", "unshift", "slice", "splice", "map", "filter",
            "reduce", "forEach", "find", "findIndex", "includes", "indexOf", "join", "sort",
            "reverse", "then", "catch", "finally", "json", "text", "toString", "toFixed",
            "trim", "split", "replace", "startsWith", "endsWith", "toLowerCase", "toUpperCase",
            "getItem", "setItem", "removeItem", "stringify", "parse", "floor", "ceil", "round",
            "random", "max", "min", "preventDefault", "stopPropagation", "focus", "click",
            "submit"));

    private static final Set<String> GLOBAL_FUNCS = new HashSet<>(Arrays.asList(
            "fetch", "setTimeout", "setInterval", "clearTimeout", "clearInterval", "parseInt",
            "parseFloat", "isNaN", "alert", "confirm", "prompt", "requestAnimationFrame"));

    private static final Set<String> CONTROL_WORDS = new HashSet<>(Arrays.asList(
            "if", "while", "switch", "catch"));

    private static List<Item> classNames(String whole, String prefix) {
        TreeSet<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("class\\s*=\\s*\"([^\"]*)\"").matcher(whole);
        while (m.find()) {
            for (String c : m.group(1).trim().split("\\s+")) {
                if (!c.isEmpty() && !c.equals(prefix)) found.add(c);
            }
        }
        List<Item> items = new ArrayList<>();
        for (String c : found) if (startsWith(c, prefix)) items.add(new Item(c, "class", c, 0));
        return items;
    }

    private static List<Item> words(String prefix, String detail, String... list) {
        List<Item> items = new ArrayList<>();
        for (String w : list) if (startsWith(w, prefix)) items.add(new Item(w, detail, w, 0));
        return items;
    }

    // ================================================================= CSS

    private static final String[] CSS_PROPS = {
            "align-content", "align-items", "align-self", "animation", "aspect-ratio",
            "backdrop-filter", "background", "background-color", "background-image",
            "background-position", "background-repeat", "background-size", "border",
            "border-bottom", "border-collapse", "border-color", "border-left",
            "border-radius", "border-right", "border-style", "border-top", "border-width",
            "bottom", "box-shadow", "box-sizing", "color", "column-gap", "content", "cursor",
            "display", "filter", "flex", "flex-basis", "flex-direction", "flex-flow",
            "flex-grow", "flex-shrink", "flex-wrap", "float", "font", "font-family",
            "font-size", "font-style", "font-weight", "gap", "grid", "grid-column",
            "grid-gap", "grid-row", "grid-template-areas", "grid-template-columns",
            "grid-template-rows", "height", "inset", "justify-content", "justify-items",
            "justify-self", "left", "letter-spacing", "line-height", "list-style", "margin",
            "margin-bottom", "margin-left", "margin-right", "margin-top", "max-height",
            "max-width", "min-height", "min-width", "object-fit", "opacity", "order",
            "outline", "overflow", "overflow-x", "overflow-y", "padding", "padding-bottom",
            "padding-left", "padding-right", "padding-top", "pointer-events", "position",
            "resize", "right", "row-gap", "scroll-behavior", "text-align", "text-decoration",
            "text-overflow", "text-shadow", "text-transform", "top", "transform",
            "transition", "user-select", "vertical-align", "visibility", "white-space",
            "width", "word-break", "z-index"
    };

    private static final Map<String, String[]> CSS_VALUES = new HashMap<>();

    static {
        CSS_VALUES.put("display", new String[]{"flex", "grid", "block", "inline", "inline-block",
                "inline-flex", "none", "contents"});
        CSS_VALUES.put("position", new String[]{"relative", "absolute", "fixed", "sticky",
                "static"});
        CSS_VALUES.put("flex-direction", new String[]{"row", "column", "row-reverse",
                "column-reverse"});
        CSS_VALUES.put("flex-wrap", new String[]{"wrap", "nowrap", "wrap-reverse"});
        CSS_VALUES.put("justify-content", new String[]{"center", "space-between",
                "space-around", "space-evenly", "flex-start", "flex-end"});
        CSS_VALUES.put("align-items", new String[]{"center", "flex-start", "flex-end",
                "stretch", "baseline"});
        CSS_VALUES.put("text-align", new String[]{"center", "left", "right", "justify"});
        CSS_VALUES.put("font-weight", new String[]{"bold", "normal", "bolder", "lighter",
                "400", "500", "600", "700"});
        CSS_VALUES.put("font-style", new String[]{"italic", "normal", "oblique"});
        CSS_VALUES.put("text-decoration", new String[]{"none", "underline", "line-through"});
        CSS_VALUES.put("text-transform", new String[]{"uppercase", "lowercase", "capitalize",
                "none"});
        CSS_VALUES.put("overflow", new String[]{"hidden", "auto", "scroll", "visible"});
        CSS_VALUES.put("cursor", new String[]{"pointer", "default", "text", "not-allowed",
                "grab", "move"});
        CSS_VALUES.put("box-sizing", new String[]{"border-box", "content-box"});
        CSS_VALUES.put("visibility", new String[]{"visible", "hidden"});
        CSS_VALUES.put("white-space", new String[]{"nowrap", "normal", "pre", "pre-wrap"});
        CSS_VALUES.put("object-fit", new String[]{"cover", "contain", "fill", "none"});
        CSS_VALUES.put("border-style", new String[]{"solid", "dashed", "dotted", "none"});
        CSS_VALUES.put("background-repeat", new String[]{"no-repeat", "repeat", "repeat-x",
                "repeat-y"});
        CSS_VALUES.put("background-size", new String[]{"cover", "contain", "auto"});
        CSS_VALUES.put("pointer-events", new String[]{"none", "auto"});
        CSS_VALUES.put("user-select", new String[]{"none", "auto", "text", "all"});
    }

    private static final String[] CSS_AT = {"media", "keyframes", "import", "font-face",
            "supports", "charset"};

    private static final String[] CSS_PSEUDO = {"hover", "focus", "active", "visited",
            "first-child", "last-child", "nth-child()", "not()", "before", "after",
            "focus-visible", "disabled", "checked", "root", "placeholder"};

    private static Result css(String before) {
        String clean = before.replaceAll("(?s)/\\*.*?\\*/", "");
        int depth = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && depth > 0) depth--;
        }
        int cut = Math.max(Math.max(clean.lastIndexOf(';'), clean.lastIndexOf('{')),
                clean.lastIndexOf('}'));
        String stmt = clean.substring(cut + 1);

        if (depth == 0) {
            // selectors: at-rules and pseudo classes
            Matcher at = Pattern.compile("@([A-Za-z-]*)$").matcher(stmt);
            if (at.find()) {
                List<Item> items = new ArrayList<>();
                for (String a : CSS_AT) {
                    if (startsWith(a, at.group(1))) items.add(new Item(a, "at-rule", a, 0));
                }
                return new Result(at.group(1), items);
            }
            Matcher ps = Pattern.compile(":([A-Za-z-]*)$").matcher(stmt);
            if (ps.find()) {
                List<Item> items = new ArrayList<>();
                for (String p : CSS_PSEUDO) {
                    if (!startsWith(p, ps.group(1))) continue;
                    items.add(mk(p, "pseudo", p.replace("()", "(|)")));
                }
                return new Result(ps.group(1), items);
            }
            return null;
        }

        int colon = stmt.indexOf(':');
        if (colon < 0) {
            // a property name
            Matcher pm = Pattern.compile("([A-Za-z-]*)$").matcher(stmt);
            String prefix = pm.find() ? pm.group(1) : "";
            if (!stmt.trim().equals(prefix)) return null;
            List<Item> items = new ArrayList<>();
            for (String p : CSS_PROPS) {
                if (startsWith(p, prefix)) {
                    items.add(mk(p, "property", p + ": |;"));
                }
            }
            return new Result(prefix, items);
        }

        // a value for a known property
        String prop = stmt.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String valuePart = stmt.substring(colon + 1);
        Matcher vm = Pattern.compile("([A-Za-z0-9-]*)$").matcher(valuePart);
        String prefix = vm.find() ? vm.group(1) : "";
        String[] values = CSS_VALUES.get(prop);
        List<Item> items = new ArrayList<>();
        if (values != null) {
            for (String v : values) if (startsWith(v, prefix)) items.add(new Item(v, "value", v, 0));
        }
        for (String v : new String[]{"inherit", "initial", "unset", "auto", "none"}) {
            if (values != null && Arrays.asList(values).contains(v)) continue;
            if (startsWith(v, prefix) && prefix.length() >= 2) items.add(new Item(v, "value", v, 0));
        }
        return new Result(prefix, items);
    }

    // ============================================================ JavaScript

    private static final String[] JS_WORDS = {
            "async", "await", "break", "case", "catch", "class", "const", "continue", "default",
            "delete", "do", "else", "export", "extends", "false", "finally", "for", "function",
            "if", "import", "in", "instanceof", "let", "new", "null", "of", "return", "static",
            "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var",
            "while", "yield",
            "document", "window", "console", "localStorage", "sessionStorage", "fetch",
            "setTimeout", "setInterval", "clearTimeout", "clearInterval", "JSON", "Math", "Date",
            "Promise", "Array", "Object", "String", "Number", "Boolean", "parseInt",
            "parseFloat", "isNaN", "alert", "confirm", "prompt", "requestAnimationFrame",
            "navigator", "location", "history", "Map", "Set", "Error", "RegExp"
    };

    private static final String[] JS_MEMBERS = {
            "log", "error", "warn", "info", "table", "querySelector", "querySelectorAll",
            "getElementById", "getElementsByClassName", "getElementsByTagName", "createElement",
            "addEventListener", "removeEventListener", "classList", "add", "remove", "toggle",
            "contains", "textContent", "innerHTML", "innerText", "value", "style",
            "getAttribute", "setAttribute", "removeAttribute", "appendChild", "append",
            "prepend", "remove", "children", "parentElement", "nextElementSibling", "dataset",
            "push", "pop", "shift", "unshift", "slice", "splice", "map", "filter", "reduce",
            "forEach", "find", "findIndex", "includes", "indexOf", "join", "sort", "reverse",
            "length", "keys", "values", "entries", "then", "catch", "finally", "json", "text",
            "toString", "toFixed", "trim", "split", "replace", "startsWith", "endsWith",
            "toLowerCase", "toUpperCase", "getItem", "setItem", "removeItem", "stringify",
            "parse", "floor", "ceil", "round", "random", "max", "min", "preventDefault",
            "stopPropagation", "target", "currentTarget", "focus", "click", "submit"
    };

    private static Result js(String before, String whole) {
        // no suggestions inside comments or strings
        int lineStart = before.lastIndexOf('\n') + 1;
        String line = before.substring(lineStart);
        if (line.contains("//")) {
            int c = line.indexOf("//");
            if (!(oddQuotes(line.substring(0, c)))) return null;
        }
        if (oddQuotes(line)) return null;
        int open = before.lastIndexOf("/*");
        if (open >= 0 && open > before.lastIndexOf("*/")) return null;

        Matcher pm = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(before);
        String prefix = pm.find() ? pm.group(1) : "";
        if (prefix.isEmpty()) return null;
        boolean afterDot = before.length() > prefix.length()
                && before.charAt(before.length() - prefix.length() - 1) == '.';

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Item> items = new ArrayList<>();
        String[] list = afterDot ? JS_MEMBERS : JS_WORDS;
        for (String w : list) {
            if (startsWith(w, prefix) && !w.equals(prefix) && seen.add(w)) {
                String template = w;
                if (afterDot && MEMBER_FUNCS.contains(w)) template = w + "(|)";
                else if (!afterDot && GLOBAL_FUNCS.contains(w)) template = w + "(|)";
                else if (!afterDot && CONTROL_WORDS.contains(w)) template = w + " (|) {}";
                items.add(mk(w, afterDot ? "member" : "keyword", template));
            }
        }
        // words already used in this file (not after a dot)
        if (!afterDot && prefix.length() >= 2) {
            TreeSet<String> docWords = new TreeSet<>();
            Matcher m = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{2,}").matcher(whole);
            int count = 0;
            while (m.find() && count < 20000) {
                count++;
                String w = m.group();
                if (startsWith(w, prefix) && !w.equals(prefix)) docWords.add(w);
            }
            for (String w : docWords) {
                if (seen.add(w)) items.add(new Item(w, "word", w, 0));
            }
        }
        return new Result(prefix, items);
    }

    private static boolean oddQuotes(String s) {
        int single = 0, dbl = 0, tick = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '\'') single++;
            else if (c == '"') dbl++;
            else if (c == '`') tick++;
        }
        return single % 2 == 1 || dbl % 2 == 1 || tick % 2 == 1;
    }

    // --------------------------------------------------------------- utils

    private static boolean startsWith(String word, String prefix) {
        return prefix.isEmpty() || word.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /** Trims a result to the most useful few. */
    public static List<Item> top(Result r) {
        List<Item> out = new ArrayList<>();
        if (r == null) return out;
        for (Item i : r.items) {
            if (out.size() >= MAX) break;
            out.add(i);
        }
        return out;
    }
}