package com.dogra.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.dogra.agent.Command.Kind.*;

/** Local command grammar. Screen contents are never supplied to this parser. */
public final class CommandParser {
    private static final String OPEN_WORD = "(?:open(?:\\s+k(?:a)?ro)?|kholo|khol(?:o|\\s+do|\\s+k(?:a)?ro)?|chalao|खोलो|खोल(?:\\s+दो|कर)?|ओपन(?:\\s+करो)?|चलाओ)";
    private static final String SEARCH_WORD = "(?:search(?:\\s+k(?:a)?ro)?|sarch(?:\\s+k(?:a)?ro)?|dhundo|सर्च(?:\\s+करो)?|खोजो|ढूंढो|ढूँढो)";
    public static String normalized(String s) {
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) digits.append(c >= '०' && c <= '९' ? (char) ('0' + c - '०') : c);
        return digits.toString().toLowerCase(Locale.ROOT).replaceAll("[।!?]+$", "").replaceAll("\\s+", " ").trim();
    }
    private static String group(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        return m.matches() ? m.group(1).trim() : null;
    }
    private static boolean matches(String text, String regex) { return Pattern.matches(regex, text); }
    public List<Command> parse(String original) {
        String text = original == null ? "" : original.trim();
        text = text.replaceFirst("(?iu)^(?:(?:hey\\s+)?agent|jarvis|एजेंट|जार्विस)[, ]+", "")
            .replaceFirst("(?iu)^(?:please|कृपया)\\s+", "");
        if (text.length() > 800 || text.isEmpty()) return Collections.singletonList(new Command(UNKNOWN, "Chhota, saaf command boliye."));
        Command literal = single(text);
        if (literal.kind == TYPE) return Collections.singletonList(literal);
        List<Command> result = new ArrayList<>();
        // Semicolons explicitly sequence commands. Never split dictated text or a search query on 'and'.
        for (String clause : text.split("\\s*;\\s*")) split(clause, result);
        if (result.isEmpty() || result.size() > 6) return Collections.singletonList(new Command(UNKNOWN, "Ek baar mein zyada se zyada 6 commands boliye."));
        for (Command c : result) if (c.kind == UNKNOWN) return Collections.singletonList(c);
        return result;
    }
    private void split(String text, List<Command> result) {
        Command whole = single(text);
        if (whole.kind == TYPE) { result.add(whole); return; }
        Matcher join = Pattern.compile("(?iu)\\s+(?:and then|then|phir|फिर|aur|और|and|or)\\s+").matcher(text);
        while (join.find()) {
            Command first = single(text.substring(0, join.start()));
            Command second = single(text.substring(join.end()));
            if ((first.kind == OPEN || first.kind == HOME || first.kind == BACK) && second.kind != UNKNOWN) {
                result.add(first); split(text.substring(join.end()), result); return;
            }
        }
        result.add(whole);
    }
    private Command single(String raw) {
        String s = normalized(raw);
        String v;
        if (matches(s, "(?:stop|stop agent|band ho jao|bas|रुको|बंद हो जाओ|बस|स्टॉप)")) return new Command(STOP);
        if (matches(s, "(?:help|madad|commands|मदद|क्या कर सकते हो)")) return new Command(HELP);
        if (matches(s, "(?:back|go back|back jao|wapas|peeche|पीछे|वापस)(?: जाओ| jao)?")) return new Command(BACK);
        if (matches(s, "(?:home|go home|home screen|होम|होम स्क्रीन)(?: jao| kholo| पर जाओ| खोलो)?")) return new Command(HOME);
        if (matches(s, "(?:recent apps|recents|हाल के ऐप|रीसेंट ऐप्स)(?: kholo| खोलो)?")) return new Command(RECENTS);
        if (matches(s, "(?:notifications?|notification kholo|नोटिफिकेशन|नोटिफिकेशन खोलो)")) return new Command(NOTIFICATIONS);
        if (matches(s, "(?:quick settings|क्विक सेटिंग्स)(?: kholo| खोलो)?")) return new Command(QUICK_SETTINGS);
        if (matches(s, "(?:show numbers|numbers dikhao|number dikhao|नंबर दिखाओ|नम्बर दिखाओ)")) return new Command(NUMBERS);
        if (matches(s, "(?:hide numbers|numbers hatao|नंबर हटाओ|नम्बर हटाओ)")) return new Command(HIDE_NUMBERS);
        if (matches(s, "(?:read screen|screen padho|screen padh do|स्क्रीन पढ़ो|स्क्रीन पढ़ दो)")) return new Command(READ);
        if (matches(s, "(?:time|time batao|what time is it|समय बताओ|टाइम बताओ|कितने बजे हैं)")) return new Command(TIME);
        if (matches(s, "(?:scroll down|neeche(?: jao| karo)?|नीचे(?: जाओ| करो)?|नीचे स्क्रॉल करो)")) return new Command(SCROLL, "down");
        if (matches(s, "(?:scroll up|upar(?: jao| karo)?|ऊपर(?: जाओ| करो)?|ऊपर स्क्रॉल करो)")) return new Command(SCROLL, "up");
        if (matches(s, "(?:swipe left|left swipe|बाएं स्वाइप|बाएँ स्वाइप)(?: karo| करो)?")) return new Command(SCROLL, "left");
        if (matches(s, "(?:swipe right|right swipe|दाएं स्वाइप|दाएँ स्वाइप)(?: karo| करो)?")) return new Command(SCROLL, "right");
        if (matches(s, "(?:volume up|volume badhao|आवाज बढ़ाओ|आवाज़ बढ़ाओ|वॉल्यूम बढ़ाओ)")) return new Command(VOLUME, "up");
        if (matches(s, "(?:volume down|volume kam karo|आवाज कम करो|आवाज़ कम करो|वॉल्यूम कम करो)")) return new Command(VOLUME, "down");
        if (matches(s, "(?:mute|म्यूट)")) return new Command(VOLUME, "mute");
        if (matches(s, "(?:unmute|अनम्यूट)")) return new Command(VOLUME, "unmute");
        // Preserve payload spelling/case for text entry and web searches.
        v = group(raw.trim(), "(?:type|likho|लिखो|टाइप(?: करो)?)\\s+(.+)");
        if (v != null) return new Command(TYPE, v);
        v = group(raw.trim(), "(.+?)\\s+(?:likh do|लिख दो)");
        if (v != null) return new Command(TYPE, v);
        v = group(s, "(?:tap|click|press|टैप|क्लिक|दबाओ)\\s+(?:number |नंबर |नम्बर )?(.+?)(?: karo| करो)?");
        if (v == null) v = group(s, "(.+?)\\s+(?:par |पर )?(?:tap|click|टैप|क्लिक)(?: karo| करो)?");
        if (v == null) v = group(s, "(.+?)\\s+दबाओ");
        if (v != null) return new Command(TAP, numberWord(v));
        v = group(s, "(?:timer|टाइमर)\\s+(\\d+)\\s*(?:minutes?|minute|min|मिनट)?");
        if (v == null) v = group(s, "(\\d+)\\s*(?:minutes?|minute|min|मिनट)(?: ka| का)?\\s+(?:timer|टाइमर)(?: lagao| लगाओ)?");
        if (v != null) {
            try { int minutes = Integer.parseInt(v); if (minutes > 0 && minutes <= 1440) return new Command(TIMER, v); }
            catch (NumberFormatException ignored) { }
            return new Command(UNKNOWN, "Timer 1 se 1440 minutes ke beech boliye.");
        }
        v = group(s, "(?:dial|call|डायल|कॉल)\\s+([+0-9][0-9 +()-]{2,22})");
        if (v != null) return new Command(DIAL, v.replaceAll("[^+0-9]", ""));
        v = group(raw.trim(), "(?:maps?|मैप्स|नक्शा)(?: mein| me| pe| par| में| पर)?\\s+(.+?)(?: search karo| खोजो| सर्च करो)?");
        if (v != null) return new Command(MAPS, v);
        v = group(raw.trim(), "(?:youtube|यूट्यूब)(?: pe| par| mein| में| पर)\\s+(.+?)\\s+" + SEARCH_WORD);
        if (v != null) return new Command(YOUTUBE, v);
        v = group(raw.trim(), "(?:chrome|google|क्रोम|गूगल)(?: pe| par| mein| में| पर)\\s+(.+?)\\s+" + SEARCH_WORD);
        if (v != null) return new Command(SEARCH, v);
        v = group(raw.trim(), SEARCH_WORD + "\\s+(.+)");
        if (v == null) v = group(raw.trim(), "(.+?)\\s+" + SEARCH_WORD);
        if (v != null) return new Command(SEARCH, v);
        v = group(s, "(?:mera |mere |my |मेरा |मेरे )?" + OPEN_WORD + "\\s+(.+)");
        if (v == null) v = group(s, "(?:mera |mere |my |मेरा |मेरे )?(.+?)\\s+" + OPEN_WORD);
        if (v != null && !v.matches(".*(?: aur | और | and | phir | फिर ).*")) return new Command(OPEN, v.replaceFirst("\\s+(?:app|ऐप)$", ""));
        return new Command(UNKNOWN, "Samajh nahi aaya. Boliye: Chrome kholo, search AI news, ya madad.");
    }
    static String numberWord(String text) {
        String[] en = {"zero","one","two","three","four","five","six","seven","eight","nine","ten"};
        String[] hi = {"शून्य","एक","दो","तीन","चार","पांच","छह","सात","आठ","नौ","दस"};
        String[] roman = {"zero","ek","do","teen","char","panch","chhe","saat","aath","nau","das"};
        for (int i=0; i<en.length; i++) if (text.equals(en[i]) || text.equals(hi[i]) || text.equals(roman[i])) return ""+i;
        return text;
    }
}
