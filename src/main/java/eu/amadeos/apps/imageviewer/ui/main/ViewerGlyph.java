package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** The viewer's own toolbar icons, next to the {@link eu.amadeos.commons.swing.StandardGlyph}s. */
public enum ViewerGlyph implements ToolIcon.Glyph {
    PREVIOUS, NEXT, SLIDESHOW, PLAY, PAUSE, INFO;

    // --8<-- [start:paint]
    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case PREVIOUS -> {
                pen.mirror();
                pen.triangle(5, 3, 5, 13, 11.5, 8);
            }
            case NEXT -> pen.triangle(5, 3, 5, 13, 11.5, 8);
            case SLIDESHOW -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 10), pen.paper());
                pen.triangle(6.5, 5, 6.5, 10, 10.5, 7.5);
                pen.line(8, 12.5, 8, 14.5);
                pen.line(5, 14.5, 11, 14.5);
            }
            case PLAY -> pen.triangle(4.5, 2.5, 4.5, 13.5, 13, 8);
            case PAUSE -> {
                pen.fill(new Rectangle2D.Double(4, 3, 3, 10), pen.ink());
                pen.fill(new Rectangle2D.Double(9, 3, 3, 10), pen.ink());
            }
            case INFO -> {
                pen.fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), pen.paper());
                pen.letter("i", new Font(Font.SERIF, Font.BOLD, 10), 12);
            }
        }
    }
    // --8<-- [end:paint]
}
