package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.apps.imageviewer.domain.Orientation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static eu.amadeos.apps.imageviewer.ui.main.Fixture.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private FakeView view;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(dir).withPictures();
        view = new FakeView();
        presenter = fixture.presenter(view);
    }

    private void start(String file) throws Exception {
        edt(() -> presenter.start(List.of(fixture.file(file))));
    }

    @Test
    void withoutArgumentsTheWindowExplainsHowToOpen() throws Exception {
        edt(() -> presenter.start(List.of()));
        assertEquals(Fixture.MSG.get("empty.hint"), view.noPicture);
        assertNotNull(view.settings);
    }

    @Test
    void openingAFileShowsItsFolder() throws Exception {
        start("b.jpg");
        assertEquals(List.of(fixture.file("a.png"), fixture.file("b.jpg"), fixture.file("c.png")), view.album);
        assertEquals(1, view.albumIndex);
        assertEquals("b.jpg", view.fileName());
        assertEquals(3, view.shown.count());
        // EXIF 6: shown turned right, 32 × 64.
        assertEquals(Orientation.ROTATE_90, view.shown.orientation());
        assertEquals(32, view.shown.width());
        assertEquals(64, view.shown.height());
    }

    @Test
    void pagingAndBeepsAtTheEnds() throws Exception {
        start("a.png");
        edt(presenter::previous);
        assertEquals(1, view.beeps);
        edt(presenter::next);
        assertEquals("b.jpg", view.fileName());
        edt(presenter::last);
        assertEquals("c.png", view.fileName());
        edt(presenter::space);
        assertEquals(2, view.beeps);
        edt(presenter::first);
        assertEquals("a.png", view.fileName());
        edt(() -> presenter.select(2));
        assertEquals("c.png", view.fileName());
    }

    @Test
    void titleFromMetadataWhenSwitchedOn() throws Exception {
        start("b.jpg");
        assertEquals("b.jpg", view.shown.title());
        edt(() -> presenter.setTitleFromMetadata(true));
        assertEquals("Evening at the Lake", view.shown.title());
        edt(presenter::first);
        assertEquals("a.png", view.shown.title());
        assertTrue(fixture.preferences.settings().titleFromMetadata());
    }

    @Test
    void turnsCombineWithTheExifOrientation() throws Exception {
        start("b.jpg");
        edt(presenter::rotateLeft);
        assertEquals(Orientation.NORMAL, view.shown.orientation());
        assertTrue(view.shown.canSave());
        edt(presenter::rotateRight);
        assertEquals(Orientation.ROTATE_90, view.shown.orientation());
        assertFalse(view.shown.canSave());
        edt(presenter::flipHorizontally);
        assertEquals(Orientation.ROTATE_90.flipHorizontally(), view.shown.orientation());
    }

    @Test
    void turnsAreKeptPerFileWhilePaging() throws Exception {
        start("a.png");
        edt(presenter::rotateRight);
        edt(presenter::next);
        edt(presenter::previous);
        assertEquals(Orientation.ROTATE_90, view.shown.orientation());
    }

    @Test
    void savingWritesTheTurnAndForgetsIt() throws Exception {
        start("a.png");
        edt(presenter::rotateRight);
        edt(presenter::saveRotation);
        assertEquals(List.of(), view.errors);
        assertEquals(Orientation.NORMAL, presenter.turnOf(fixture.file("a.png")));
        // Read again from the file: now 20 × 40 and shown as stored.
        assertEquals(Orientation.NORMAL, view.shown.orientation());
        assertEquals(20, view.shown.width());
        assertEquals(40, view.shown.height());
    }

    @Test
    void savingWithoutATurnBeeps() throws Exception {
        start("a.png");
        edt(presenter::saveRotation);
        assertEquals(1, view.beeps);
    }

    @Test
    void trashMovesTheFileAndShowsTheNext() throws Exception {
        start("a.png");
        edt(presenter::moveToTrash);
        assertFalse(Files.exists(fixture.file("a.png")));
        assertTrue(Files.exists(dir.resolve("Trash/files/a.png")));
        assertEquals(2, view.album.size());
        assertEquals("b.jpg", view.fileName());
    }

    @Test
    void trashAsksFirst() throws Exception {
        start("a.png");
        view.confirm = false;
        edt(presenter::moveToTrash);
        assertTrue(Files.exists(fixture.file("a.png")));
    }

    @Test
    void theLastPictureInTheTrashLeavesAnEmptyWindow() throws Exception {
        Path single = Files.createDirectories(dir.resolve("single"));
        Files.move(fixture.file("c.png"), single.resolve("c.png"));
        edt(() -> presenter.start(List.of(single.resolve("c.png"))));
        edt(presenter::moveToTrash);
        assertNull(view.shown);
        assertEquals(Fixture.MSG.get("empty.hint"), view.noPicture);
    }

    @Test
    void anUnreadableFileSaysWhy() throws Exception {
        Files.writeString(fixture.file("broken.jpg"), "not a picture");
        start("broken.jpg");
        assertNull(view.shown);
        assertTrue(view.noPicture.startsWith("Cannot show broken.jpg"), view.noPicture);
    }

    @Test
    void openingAFolderWithoutPicturesIsAnError() throws Exception {
        Path empty = Files.createDirectories(dir.resolve("empty"));
        edt(() -> presenter.start(List.of(empty)));
        assertEquals(List.of(Fixture.MSG.get("error.no-pictures")), view.errors);
    }

    @Test
    void openDialogStartsInTheShownPicturesFolder() throws Exception {
        edt(() -> presenter.start(List.of()));
        edt(presenter::open);
        assertEquals(dir.resolve("Pictures"), view.chooserFolder);
        view.chosen = Optional.of(List.of(fixture.file("c.png")));
        edt(presenter::open);
        assertEquals("c.png", view.fileName());
        edt(presenter::open);
        assertEquals(fixture.folder, view.chooserFolder);
    }

    // --8<-- [start:map-test]
    @Test
    void theMapLoadsOnlyWhenAllowed() throws Exception {
        start("b.jpg");
        assertFalse(view.shown.mapAllowed());
        edt(presenter::allowMap);
        assertTrue(view.shown.mapAllowed());
        // Only for this picture.
        edt(presenter::first);
        edt(presenter::next);
        assertTrue(view.shown.mapAllowed());
        edt(() -> presenter.select(2));
        assertFalse(view.shown.mapAllowed());
        edt(() -> presenter.setAlwaysShowMaps(true));
        assertTrue(view.shown.mapAllowed());
    }
    // --8<-- [end:map-test]

    @Test
    void positionLinksAndCopies() throws Exception {
        start("b.jpg");
        edt(presenter::openMapInBrowser);
        edt(presenter::openMapCopyright);
        assertEquals(List.of("https://www.openstreetmap.org/?mlat=47.660300&mlon=9.175400#map=17/47.660300/9.175400",
                "https://www.openstreetmap.org/copyright"), fixture.browsed);
        edt(presenter::copyPosition);
        edt(presenter::copyPath);
        assertEquals(List.of("47.660300, 9.175400", fixture.file("b.jpg").toString()), view.copiedText);
    }

    @Test
    void copyAndPrintTheTurnedPicture() throws Exception {
        start("a.png");
        edt(presenter::rotateRight);
        edt(presenter::copyImage);
        edt(presenter::print);
        assertEquals(20, view.copiedImage.getWidth());
        assertEquals(40, view.printed.getHeight());
    }

    @Test
    void allMetadataOrANote() throws Exception {
        start("b.jpg");
        edt(presenter::showAllMetadata);
        assertTrue(view.metadata.stream().anyMatch(t -> t.name().equals("Model") && t.value().equals("Canon EOS R6")));
        // A PNG has at least its header.
        edt(presenter::first);
        edt(presenter::showAllMetadata);
        assertTrue(view.metadata.stream().anyMatch(t -> t.directory().equals("PNG-IHDR")), view.metadata.toString());
    }

    @Test
    void zoomGoesToTheViewWhenAPictureIsShown() throws Exception {
        edt(() -> presenter.start(List.of()));
        edt(() -> presenter.zoom(ZoomCommand.IN));
        assertEquals(1, view.beeps);
        start("a.png");
        edt(() -> presenter.zoom(ZoomCommand.FIT));
        assertEquals(List.of(ZoomCommand.FIT), view.zooms);
    }

    @Test
    void settingsAreSavedAndShown() throws Exception {
        start("a.png");
        edt(presenter::toggleSidebar);
        edt(() -> presenter.setFilmStrip(false));
        edt(() -> presenter.setSlideSeconds(10));
        edt(() -> presenter.setShuffle(true));
        edt(() -> presenter.setLoop(false));
        assertTrue(view.settings.sidebar());
        assertEquals(fixture.preferences.settings(), view.settings);
        assertEquals(10, view.settings.slideSeconds());
        assertFalse(view.settings.filmStrip());
    }

    @Test
    void fullScreenOnAndOff() throws Exception {
        start("a.png");
        edt(presenter::toggleFullScreen);
        assertEquals(Boolean.TRUE, view.fullScreen);
        edt(presenter::escape);
        assertEquals(Boolean.FALSE, view.fullScreen);
    }

    @Test
    void exitClosesTheWindow() throws Exception {
        start("a.png");
        edt(presenter::about);
        edt(presenter::exit);
        assertTrue(view.aboutShown);
        assertTrue(view.closed);
        assertEquals(1, fixture.exits);
    }
}
