package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.bootstrap.ImageViewerModule;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(ImageViewerModule.class, ImageViewerModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(ImageViewerModule.class, ImageViewerModule.MESSAGES));
    }

    /**
     * No two entries share a mnemonic letter in the menu bar or in a menu; the sidebar buttons share the window
     * with the menu bar, so they count with it.
     */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = new HashMap<>();
        List<String> bar = new ArrayList<>(List.of("menu.image", "menu.edit", "menu.view", "menu.go", "menu.options",
                "menu.help"));
        bar.addAll(List.of("info.map.show", "info.map.always", "info.map.browser", "info.map.copy"));
        groups.put("bar", bar);
        groups.put("image", List.of("menu.image.open", "menu.image.save", "menu.image.print", "menu.image.trash",
                "menu.image.exit"));
        groups.put("edit", List.of("menu.edit.copy", "menu.edit.copy-path"));
        groups.put("view", List.of("menu.view.zoom-in", "menu.view.zoom-out", "menu.view.fit", "menu.view.actual",
                "menu.view.rotate-left", "menu.view.rotate-right", "menu.view.flip-horizontal",
                "menu.view.flip-vertical", "menu.view.sidebar", "menu.view.film-strip", "menu.view.full-screen",
                "menu.view.slideshow"));
        groups.put("go", List.of("menu.go.previous", "menu.go.next", "menu.go.first", "menu.go.last"));
        groups.put("options", List.of("menu.options.title", "menu.options.maps", "menu.options.slideshow",
                "menu.options.language"));
        groups.put("slideshow", List.of("menu.options.shuffle", "menu.options.loop"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertEquals(null, before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void windowTitles() {
        assertEquals("Bildbetrachter", texts(Language.GERMAN).get("window.title"));
        assertEquals("b.jpg - Image Viewer", Fixture.MSG.format("window.title.picture", "b.jpg"));
    }
}
