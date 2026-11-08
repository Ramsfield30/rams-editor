package com.rams.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emmet-style abbreviations: "ul>li*3" becomes real HTML, "m10-20" becomes "margin: 10px 20px;".
 * Pure text in, text out, so it is easy to test.
 */
public final class Emmet {

    public static final class Result {
        public final String text;
        public final int cursor;

        Result(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    private Emmet() {}

    // ------------------------------------------------------------ extract

    /** Finds the abbreviation that ends at the end of the given line text. */
    public static String extract(String prefix, boolean css) {
        if (prefix == null || prefix.isEmpty()) return null;
        int end = prefix.length();
        int start = end;
        int sq = 0, br = 0, par = 0;
        while (start > 0) {
            char c = prefix.charAt(start - 1);
            if (c == ']') sq++;
            else if (c == '[') { if (sq == 0) break; sq--; }
            else if (c == '}') br++;
            else if (c == '{') { if (br == 0) break; br--; }
            else if (c == ')') par++;
            else if (c == '(') { if (par == 0) break; par--; }
            else if (sq == 0 && br == 0 && par == 0) {
                if (Character.isWhitespace(c) || c == '<' || c == '"' || c == '\''
                        || c == ';' || c == ',' || c == '=' || c == '/' && css) break;
                if (c == '>' && endsTag(prefix, start - 1)) break;
            }
            start--;
        }
        String abbr = prefix.substring(start, end);
        return abbr.isEmpty() ? null : abbr;
    }

    private static boolean endsTag(String s, int gt) {
        int lt = s.lastIndexOf('<', gt);
        if (lt < 0) return false;
        String inner = s.substring(lt + 1, gt);
        return inner.matches("/?[a-zA-Z][^<>]*");
    }

    // ------------------------------------------------------------- expand

    public static Result expand(String abbr, boolean css, String unit, String baseIndent) {
        if (abbr == null || abbr.isEmpty()) return null;
        try {
            return css ? expandCss(abbr) : expandHtml(abbr, unit, baseIndent);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================================================================ HTML

    private static final Set<String> VOID = set("br", "hr", "img", "input", "meta", "link",
            "area", "base", "col", "embed", "param", "source", "track", "wbr");

    private static final Set<String> INLINE = set("a", "abbr", "acronym", "b", "bdo", "big",
            "br", "button", "cite", "code", "dfn", "em", "font", "i", "img", "input", "kbd",
            "label", "map", "object", "q", "samp", "select", "small", "span", "strike",
            "strong", "sub", "sup", "textarea", "tt", "u", "var");

    private static final Set<String> KNOWN = set("a", "abbr", "address", "article", "aside",
            "audio", "b", "blockquote", "body", "br", "button", "canvas", "caption", "cite",
            "code", "col", "dd", "details", "div", "dl", "dt", "em", "fieldset", "figcaption",
            "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
            "hr", "html", "i", "iframe", "img", "input", "label", "legend", "li", "link",
            "main", "mark", "meta", "nav", "ol", "optgroup", "option", "p", "pre", "script",
            "section", "select", "small", "source", "span", "strong", "style", "sub", "summary",
            "sup", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead",
            "time", "title", "tr", "u", "ul", "video");

    private static final Map<String, String> TAG_ALIAS = new HashMap<>();
    private static final Map<String, String[][]> TAG_ATTRS = new HashMap<>();

    static {
        TAG_ALIAS.put("btn", "button");
        TAG_ALIAS.put("bq", "blockquote");
        TAG_ALIAS.put("fig", "figure");
        TAG_ALIAS.put("figc", "figcaption");
        TAG_ALIAS.put("sect", "section");
        TAG_ALIAS.put("art", "article");
        TAG_ALIAS.put("hdr", "header");
        TAG_ALIAS.put("ftr", "footer");
        TAG_ALIAS.put("adr", "address");
        TAG_ALIAS.put("ifr", "iframe");
        TAG_ALIAS.put("a:link", "a");
        TAG_ALIAS.put("script:src", "script");

        TAG_ATTRS.put("a", new String[][]{{"href", ""}});
        TAG_ATTRS.put("a:link", new String[][]{{"href", "http://"}});
        TAG_ATTRS.put("img", new String[][]{{"src", ""}, {"alt", ""}});
        TAG_ATTRS.put("link", new String[][]{{"rel", "stylesheet"}, {"href", ""}});
        TAG_ATTRS.put("script:src", new String[][]{{"src", ""}});
        TAG_ATTRS.put("form", new String[][]{{"action", ""}});
        TAG_ATTRS.put("input", new String[][]{{"type", "text"}});
        TAG_ATTRS.put("ifr", new String[][]{{"src", ""}});
        TAG_ATTRS.put("iframe", new String[][]{{"src", ""}});
        TAG_ATTRS.put("meta:vp", new String[][]{{"name", "viewport"},
                {"content", "width=device-width, initial-scale=1.0"}});
        TAG_ATTRS.put("meta:utf", new String[][]{{"charset", "UTF-8"}});
        TAG_ALIAS.put("meta:vp", "meta");
        TAG_ALIAS.put("meta:utf", "meta");
    }

    private static final String HTML5 =
            "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n{U}<meta charset=\"UTF-8\">\n"
                    + "{U}<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                    + "{U}<title>Document</title>\n</head>\n<body>\n{U}\u0001\n</body>\n</html>";

    private static final class Node {
        String tag;                  // null = no tag yet (implicit) or text-only
        boolean textOnly;
        boolean group;
        String text;
        int repeat = 1;
        Node parent;
        List<String[]> attrs = new ArrayList<>();
        List<Node> children = new ArrayList<>();

        Node deepCopy() {
            Node n = new Node();
            n.tag = tag;
            n.textOnly = textOnly;
            n.group = group;
            n.text = text;
            n.repeat = repeat;
            for (String[] a : attrs) n.attrs.add(new String[]{a[0], a[1]});
            for (Node c : children) {
                Node cc = c.deepCopy();
                cc.parent = n;
                n.children.add(cc);
            }
            return n;
        }

        void setAttr(String name, String value) {
            for (String[] a : attrs) {
                if (a[0].equals(name)) { a[1] = value; return; }
            }
            attrs.add(new String[]{name, value});
        }

        void addClass(String cls) {
            for (String[] a : attrs) {
                if (a[0].equals("class")) { a[1] = a[1].isEmpty() ? cls : a[1] + " " + cls; return; }
            }
            attrs.add(new String[]{"class", cls});
        }
    }

    private static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s; }

        boolean more() { return i < s.length(); }

        char peek() { return s.charAt(i); }
    }

    private static Result expandHtml(String abbr, String unit, String base) {
        if (abbr.equals("!") || abbr.equals("html:5") || abbr.equals("doc")) {
            return finish(HTML5.replace("{U}", unit), unit, base);
        }
        // a lone plain word like "hello" is not an abbreviation worth expanding
        if (abbr.matches("[a-zA-Z][a-zA-Z0-9:-]*") && !KNOWN.contains(abbr)
                && !TAG_ALIAS.containsKey(abbr) && !TAG_ATTRS.containsKey(abbr)
                && !abbr.matches("lorem\\d*")) {
            return null;
        }
        Parser p = new Parser(abbr);
        List<Node> roots = parseSeq(p);
        if (p.more()) return null;               // something we could not read
        if (roots.isEmpty()) return null;
        List<Node> expanded = expandList(roots, null, new int[]{3000});
        List<String[]> lines = new ArrayList<>();   // {level, text}
        for (Node n : expanded) renderNode(n, null, 0, lines, unit);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < lines.size(); k++) {
            int level = Integer.parseInt(lines.get(k)[0]);
            if (k > 0) {
                sb.append('\n').append(base);
                for (int l = 0; l < level; l++) sb.append(unit);
            }
            sb.append(lines.get(k)[1]);
        }
        return finish(sb.toString(), unit, base);
    }

    private static Result finish(String text, String unit, String base) {
        if (text.contains("\u0001")) {
            // template with an explicit cursor mark; also indent later lines by the base indent
            String[] parts = text.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < parts.length; k++) {
                if (k > 0) sb.append('\n').append(base);
                sb.append(parts[k]);
            }
            String out = sb.toString();
            int at = out.indexOf('\u0001');
            return new Result(out.replace("\u0001", ""), at);
        }
        int at = text.indexOf("=\"\"");
        if (at >= 0) return new Result(text, at + 2);
        at = text.indexOf("></");
        if (at >= 0) return new Result(text, at + 1);
        return new Result(text, text.length());
    }

    // ---- parsing ----

    private static List<Node> parseSeq(Parser p) {
        List<Node> roots = new ArrayList<>();
        Node parent = null;
        Node last = null;
        while (p.more()) {
            char c = p.peek();
            if (c == ')') break;
            if (c == '>') {
                p.i++;
                if (last == null) throw new IllegalStateException();
                parent = last.group && !last.children.isEmpty()
                        ? last.children.get(last.children.size() - 1) : last;
                continue;
            }
            if (c == '+') { p.i++; continue; }
            if (c == '^') {
                p.i++;
                parent = parent == null ? null : parent.parent;
                continue;
            }
            Node n = parseItem(p);
            if (parent == null) roots.add(n);
            else { n.parent = parent; parent.children.add(n); }
            last = n;
        }
        return roots;
    }

    private static Node parseItem(Parser p) {
        char c = p.peek();
        if (c == '(') {
            p.i++;
            Node g = new Node();
            g.group = true;
            g.children = parseSeq(p);
            if (!p.more() || p.peek() != ')') throw new IllegalStateException();
            p.i++;
            if (p.more() && p.peek() == '*') g.repeat = readNumber(p);
            return g;
        }
        Node n = new Node();
        String name = readName(p);
        // lorem / lorem20
        Matcher lm = Pattern.compile("lorem(\\d*)").matcher(name);
        if (lm.matches()) {
            n.textOnly = true;
            int count = lm.group(1).isEmpty() ? 30 : Integer.parseInt(lm.group(1));
            n.text = lorem(count);
            if (p.more() && p.peek() == '*') n.repeat = readNumber(p);
            return n;
        }
        String key = name;
        if (!name.isEmpty()) {
            String[][] defaults = TAG_ATTRS.get(key);
            n.tag = TAG_ALIAS.containsKey(key) ? TAG_ALIAS.get(key) : key;
            if (defaults != null) for (String[] d : defaults) n.attrs.add(new String[]{d[0], d[1]});
        }
        boolean any = !name.isEmpty();
        while (p.more()) {
            char m = p.peek();
            if (m == '.') {
                p.i++;
                String cls = readWord(p);
                if (cls.isEmpty()) throw new IllegalStateException();
                n.addClass(cls);
                any = true;
            } else if (m == '#') {
                p.i++;
                String id = readWord(p);
                if (id.isEmpty()) throw new IllegalStateException();
                n.setAttr("id", id);
                any = true;
            } else if (m == '[') {
                readAttrs(p, n);
                any = true;
            } else if (m == '{') {
                p.i++;
                int depth = 1;
                int st = p.i;
                while (p.more() && depth > 0) {
                    char ch = p.peek();
                    if (ch == '{') depth++;
                    else if (ch == '}') depth--;
                    p.i++;
                }
                if (depth != 0) throw new IllegalStateException();
                n.text = p.s.substring(st, p.i - 1);
                any = true;
            } else if (m == '*') {
                n.repeat = readNumber(p);
            } else {
                break;
            }
        }
        if (!any) throw new IllegalStateException();
        return n;
    }

    private static int readNumber(Parser p) {
        p.i++;                                   // the *
        int st = p.i;
        while (p.more() && Character.isDigit(p.peek())) p.i++;
        if (st == p.i) throw new IllegalStateException();
        int n = Integer.parseInt(p.s.substring(st, p.i));
        if (n < 1 || n > 200) throw new IllegalStateException();
        return n;
    }

    private static String readName(Parser p) {
        int st = p.i;
        if (p.more() && Character.isLetter(p.peek())) {
            p.i++;
            while (p.more()) {
                char c = p.peek();
                if (Character.isLetterOrDigit(c) || c == '-' || c == ':') p.i++;
                else break;
            }
        }
        // a trailing ':' or '-' does not belong to a name
        while (p.i > st && (p.s.charAt(p.i - 1) == ':' || p.s.charAt(p.i - 1) == '-')) p.i--;
        return p.s.substring(st, p.i);
    }

    private static String readWord(Parser p) {
        int st = p.i;
        while (p.more()) {
            char c = p.peek();
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '$') p.i++;
            else break;
        }
        return p.s.substring(st, p.i);
    }

    private static void readAttrs(Parser p, Node n) {
        p.i++;                                   // the [
        while (p.more() && p.peek() != ']') {
            while (p.more() && p.peek() == ' ') p.i++;
            int st = p.i;
            while (p.more() && p.peek() != '=' && p.peek() != ' ' && p.peek() != ']') p.i++;
            String name = p.s.substring(st, p.i);
            if (name.isEmpty()) throw new IllegalStateException();
            String value = "";
            if (p.more() && p.peek() == '=') {
                p.i++;
                if (p.more() && (p.peek() == '"' || p.peek() == '\'')) {
                    char q = p.peek();
                    p.i++;
                    int vs = p.i;
                    while (p.more() && p.peek() != q) p.i++;
                    if (!p.more()) throw new IllegalStateException();
                    value = p.s.substring(vs, p.i);
                    p.i++;
                } else {
                    int vs = p.i;
                    while (p.more() && p.peek() != ' ' && p.peek() != ']') p.i++;
                    value = p.s.substring(vs, p.i);
                }
            }
            n.setAttr(name, value);
        }
        if (!p.more()) throw new IllegalStateException();
        p.i++;                                   // the ]
    }

    // ---- repeating and numbering ----

    private static List<Node> expandList(List<Node> src, Node newParent, int[] budget) {
        List<Node> out = new ArrayList<>();
        for (Node n : src) {
            int times = Math.max(1, n.repeat);
            for (int idx = 1; idx <= times; idx++) {
                if (--budget[0] < 0) throw new IllegalStateException("too big");
                Node c = n.deepCopy();
                c.repeat = 1;
                if (times > 1) substitute(c, idx, true);
                if (c.group) {
                    out.addAll(expandList(c.children, newParent, budget));
                } else {
                    c.parent = newParent;
                    c.children = expandList(c.children, c, budget);
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static void substitute(Node n, int idx, boolean root) {
        if (!root && n.repeat > 1) return;       // it numbers itself
        if (n.text != null) n.text = number(n.text, idx);
        for (String[] a : n.attrs) a[1] = number(a[1], idx);
        for (Node c : n.children) substitute(c, idx, false);
    }

    private static String number(String s, int idx) {
        Matcher m = Pattern.compile("\\$+").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String f = String.format("%0" + m.group().length() + "d", idx);
            m.appendReplacement(sb, Matcher.quoteReplacement(f));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---- writing it out ----

    private static String resolveTag(Node n, Node parent) {
        if (n.tag != null) return n.tag;
        String pt = parent == null ? "" : parent.tag == null ? "" : parent.tag;
        switch (pt) {
            case "ul": case "ol": return "li";
            case "table": case "tbody": case "thead": case "tfoot": return "tr";
            case "tr": return "td";
            case "select": case "optgroup": return "option";
            case "p": case "span": case "a": case "b": case "i": case "em": case "strong":
            case "label": case "button": return "span";
            default: return "div";
        }
    }

    private static String openTag(Node n, String tag) {
        StringBuilder sb = new StringBuilder("<").append(tag);
        for (String[] a : n.attrs) {
            sb.append(' ').append(a[0]).append("=\"").append(a[1]).append('"');
        }
        return sb.append('>').toString();
    }

    private static boolean isInline(Node n, Node parent) {
        if (n.textOnly) return true;
        return INLINE.contains(resolveTag(n, parent));
    }

    private static boolean canInline(Node n, Node parent) {
        if (n.textOnly) return true;
        String tag = resolveTag(n, parent);
        if (VOID.contains(tag)) return true;
        int elements = 0;
        for (Node c : n.children) {
            if (c.textOnly) continue;
            elements++;
            if (!(isInline(c, n) && canInline(c, n))) return false;
        }
        return elements <= 1;
    }

    private static String inlineText(Node n, Node parent) {
        if (n.textOnly) return n.text;
        String tag = resolveTag(n, parent);
        StringBuilder sb = new StringBuilder(openTag(n, tag));
        if (VOID.contains(tag)) return sb.toString();
        if (n.text != null) sb.append(n.text);
        for (Node c : n.children) sb.append(inlineText(c, n));
        return sb.append("</").append(tag).append('>').toString();
    }

    private static void renderNode(Node n, Node parent, int level, List<String[]> lines, String unit) {
        if (canInline(n, parent)) {
            lines.add(new String[]{String.valueOf(level), inlineText(n, parent)});
            return;
        }
        String tag = resolveTag(n, parent);
        lines.add(new String[]{String.valueOf(level), openTag(n, tag) + (n.text == null ? "" : n.text)});
        for (Node c : n.children) renderNode(c, n, level + 1, lines, unit);
        lines.add(new String[]{String.valueOf(level), "</" + tag + ">"});
    }

    // ---- lorem ipsum ----

    private static final String[] LOREM = (
            "lorem ipsum dolor sit amet consectetur adipisicing elit quibusdam voluptatibus "
                    + "dicta rerum nostrum aliquid dolores nam ratione incidunt tempora cupiditate "
                    + "fuga vero cumque reprehenderit saepe laboriosam inventore perferendis "
                    + "officiis temporibus magni sapiente est eius iure quae minima porro odio "
                    + "voluptas natus placeat libero deserunt assumenda eveniet repellat sunt "
                    + "provident explicabo possimus recusandae").split(" ");

    private static String lorem(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String w = LOREM[i % LOREM.length];
            if (i == 0) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            if (i > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.append('.').toString();
    }

    // ================================================================= CSS

    private static final Map<String, String> CSS_FULL = new LinkedHashMap<>();
    private static final Map<String, String> CSS_PROP = new LinkedHashMap<>();
    private static final Set<String> UNITLESS = set("fw", "lh", "op", "zi", "ord", "fx", "fxg",
            "fxs", "flex", "order");

    static {
        String[][] full = {
                {"df", "display: flex;"}, {"dg", "display: grid;"}, {"dn", "display: none;"},
                {"db", "display: block;"}, {"dib", "display: inline-block;"},
                {"di", "display: inline;"}, {"dif", "display: inline-flex;"},
                {"poa", "position: absolute;"}, {"por", "position: relative;"},
                {"pof", "position: fixed;"}, {"poss", "position: sticky;"},
                {"jcc", "justify-content: center;"}, {"jcsb", "justify-content: space-between;"},
                {"jcsa", "justify-content: space-around;"}, {"jcfs", "justify-content: flex-start;"},
                {"jcfe", "justify-content: flex-end;"}, {"aic", "align-items: center;"},
                {"aifs", "align-items: flex-start;"}, {"aife", "align-items: flex-end;"},
                {"ais", "align-items: stretch;"}, {"fdc", "flex-direction: column;"},
                {"fdr", "flex-direction: row;"}, {"fww", "flex-wrap: wrap;"},
                {"tac", "text-align: center;"}, {"tal", "text-align: left;"},
                {"tar", "text-align: right;"}, {"fwb", "font-weight: bold;"},
                {"tdn", "text-decoration: none;"}, {"tdu", "text-decoration: underline;"},
                {"cp", "cursor: pointer;"}, {"ovh", "overflow: hidden;"},
                {"ova", "overflow: auto;"}, {"bxz", "box-sizing: border-box;"},
                {"m0a", "margin: 0 auto;"}, {"wfc", "width: fit-content;"}
        };
        for (String[] f : full) CSS_FULL.put(f[0], f[1]);

        String[][] props = {
                {"m", "margin"}, {"mt", "margin-top"}, {"mr", "margin-right"},
                {"mb", "margin-bottom"}, {"ml", "margin-left"}, {"p", "padding"},
                {"pt", "padding-top"}, {"pr", "padding-right"}, {"pb", "padding-bottom"},
                {"pl", "padding-left"}, {"w", "width"}, {"h", "height"}, {"maw", "max-width"},
                {"mah", "max-height"}, {"miw", "min-width"}, {"mih", "min-height"},
                {"t", "top"}, {"r", "right"}, {"b", "bottom"}, {"l", "left"},
                {"fz", "font-size"}, {"fw", "font-weight"}, {"ff", "font-family"},
                {"lh", "line-height"}, {"c", "color"}, {"bg", "background"},
                {"bgc", "background-color"}, {"bgi", "background-image"}, {"bd", "border"},
                {"bdr", "border-radius"}, {"bdrs", "border-radius"}, {"bxsh", "box-shadow"},
                {"op", "opacity"}, {"zi", "z-index"}, {"gap", "gap"}, {"ls", "letter-spacing"},
                {"fx", "flex"}, {"fxg", "flex-grow"}, {"fxs", "flex-shrink"}, {"ord", "order"},
                {"trs", "transition"}, {"trf", "transform"}, {"anim", "animation"},
                {"ov", "overflow"}, {"ovx", "overflow-x"}, {"ovy", "overflow-y"},
                {"cur", "cursor"}, {"va", "vertical-align"}, {"d", "display"},
                {"pos", "position"}, {"ta", "text-align"}, {"td", "text-decoration"},
                {"tt", "text-transform"}, {"fl", "float"}, {"bdw", "border-width"},
                {"o", "outline"}
        };
        for (String[] pr : props) CSS_PROP.put(pr[0], pr[1]);
    }

    private static final Pattern NUM = Pattern.compile("^(-?\\d*\\.?\\d+)(px|em|rem|vh|vw|vmin|vmax|pt|%|p|e|r)?$");

    private static Result expandCss(String abbr) {
        String a = abbr;
        boolean important = false;
        if (a.endsWith("!")) {
            important = true;
            a = a.substring(0, a.length() - 1);
        }
        if (a.isEmpty()) return null;

        String full = CSS_FULL.get(a);
        if (full != null) {
            String t = important ? full.replace(";", " !important;") : full;
            return new Result(t, t.length());
        }

        // the longest property prefix whose rest looks like a value (or is empty)
        String prop = null;
        String propKey = null;
        String rest = null;
        for (int len = Math.min(4, a.length()); len >= 1; len--) {
            String key = a.substring(0, len);
            String name = CSS_PROP.get(key);
            if (name == null) continue;
            String r = a.substring(len);
            if (r.isEmpty() || r.matches("^(-?\\d.*|-?\\.\\d.*|#.*)")) {
                prop = name;
                propKey = key;
                rest = r;
                break;
            }
        }
        if (prop == null) return null;

        if (rest.isEmpty()) {
            String t = prop + ": ;";
            return new Result(t, prop.length() + 2);
        }

        List<String> values = new ArrayList<>();
        for (String tok : splitValues(rest)) {
            String v = cssValue(tok, propKey);
            if (v == null) return null;
            values.add(v);
        }
        String t = prop + ": " + String.join(" ", values) + (important ? " !important" : "") + ";";
        return new Result(t, t.length());
    }

    // "10-20" -> [10, 20]; "-10" -> [-10]; "10--5" -> [10, -5]
    private static List<String> splitValues(String rest) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '-' && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                if (i + 1 < rest.length() && rest.charAt(i + 1) == '-') continue;
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String cssValue(String tok, String propKey) {
        if (tok.startsWith("#")) {
            if (!tok.matches("#[0-9a-fA-F]{3,8}")) return null;
            return tok;
        }
        if (tok.equals("a")) return "auto";
        Matcher m = NUM.matcher(tok);
        if (!m.matches()) return null;
        String num = m.group(1);
        String unit = m.group(2);
        if (num.startsWith(".")) num = "0" + num;
        if (num.startsWith("-.")) num = "-0" + num.substring(1);
        if (unit == null) {
            if (UNITLESS.contains(propKey) || num.equals("0")) return num;
            return num + "px";
        }
        switch (unit) {
            case "p": return num + "%";
            case "e": return num + "em";
            case "r": return num + "rem";
            default: return num + unit;
        }
    }

    // --------------------------------------------------------------- utils

    private static Set<String> set(String... words) {
        return new HashSet<>(Arrays.asList(words));
    }
}