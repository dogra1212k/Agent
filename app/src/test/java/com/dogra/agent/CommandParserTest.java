package com.dogra.agent;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static com.dogra.agent.Command.Kind.*;

public class CommandParserTest {
    private final CommandParser parser = new CommandParser();
    private void one(String text, Command.Kind kind, String value) {
        List<Command> commands = parser.parse(text);
        assertEquals(text, 1, commands.size());
        assertEquals(text, kind, commands.get(0).kind);
        assertEquals(text, value, commands.get(0).value);
    }
    @Test public void opensAppsInThreeLanguages() {
        one("Chrome kholo", OPEN, "chrome");
        one("मेरा क्रोम खोलो", OPEN, "क्रोम");
        one("open WhatsApp", OPEN, "whatsapp");
        one("Agent, YouTube open kro", OPEN, "youtube");
    }
    @Test public void chainsOpenAndSearch() {
        for (String text : new String[]{"Chrome kholo aur AI news search karo", "Chrome open kro or AI news search kro", "क्रोम खोलो और AI news सर्च करो"}) {
            List<Command> commands = parser.parse(text);
            assertEquals(text, 2, commands.size());
            assertEquals(OPEN, commands.get(0).kind);
            assertEquals(SEARCH, commands.get(1).kind);
            assertEquals("AI news", commands.get(1).value);
        }
    }
    @Test public void searchesPreserveCaseAndConjunctions() {
        one("search Cats and Dogs", SEARCH, "Cats and Dogs");
        one("YouTube par Hindi songs search karo", YOUTUBE, "Hindi songs");
        one("गूगल पर भारत का मौसम सर्च करो", SEARCH, "भारत का मौसम");
        one("maps Delhi", MAPS, "Delhi");
    }
    @Test public void typingNeverExecutesTheDictatedPayload() {
        one("type Namaste; home", TYPE, "Namaste; home");
        one("type stop agent aur delete", TYPE, "stop agent aur delete");
        one("लिखो नमस्ते और वापस जाओ", TYPE, "नमस्ते और वापस जाओ");
    }
    @Test public void hindiNumbersAndScreenActions() {
        one("टैप ३", TAP, "3");
        one("tap three", TAP, "3");
        one("टैप तीन", TAP, "3");
        one("नंबर दिखाओ", NUMBERS, "");
        one("नीचे जाओ", SCROLL, "down");
        one("back", BACK, "");
    }
    @Test public void validatesTimersAndDialPayloads() {
        one("५ मिनट का टाइमर लगाओ", TIMER, "5");
        one("dial +91 98765 43210", DIAL, "+919876543210");
        assertEquals(UNKNOWN, parser.parse("timer 0").get(0).kind);
        assertEquals(UNKNOWN, parser.parse("timer 99999999999999999999").get(0).kind);
        assertEquals(UNKNOWN, parser.parse("call Alice and send money").get(0).kind);
    }
    @Test public void incompletePlansHaveNoPartialSideEffects() {
        List<Command> commands = parser.parse("home; do something unsupported");
        assertEquals(1, commands.size());
        assertEquals(UNKNOWN, commands.get(0).kind);
        assertEquals(UNKNOWN, parser.parse(null).get(0).kind);
        assertEquals(UNKNOWN, parser.parse("home;home;home;home;home;home;home").get(0).kind);
    }
    @Test public void explicitStopAndHelp() {
        one("रुको", STOP, "");
        one("stop agent", STOP, "");
        one("madad", HELP, "");
    }
}
