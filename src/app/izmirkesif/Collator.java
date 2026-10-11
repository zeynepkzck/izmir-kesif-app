package app.izmirkesif;

import java.util.Locale;

final class Collator {
    private final java.text.Collator c;
    private Collator() { c = java.text.Collator.getInstance(new Locale("tr", "TR")); }
    static Collator tr() { return new Collator(); }
    int compare(String a, String b) { return c.compare(a == null ? "" : a, b == null ? "" : b); }
}
