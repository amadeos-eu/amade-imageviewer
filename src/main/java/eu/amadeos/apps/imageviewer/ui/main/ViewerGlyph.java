package eu.amadeos.apps.imageviewer.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** The viewer's own toolbar icons, next to the {@link eu.amadeos.commons.swing.StandardGlyph}s. */
public enum ViewerGlyph implements ToolIcon.Glyph {
    PREVIOUS, NEXT, FIT, ACTUAL_SIZE, ROTATE_LEFT, ROTATE_RIGHT, SLIDESHOW, PLAY, PAUSE, INFO, FULL_SCREEN,
    LEAVE_FULL_SCREEN;

    // --8<-- [start:paint]
    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case PREVIOUS -> {
                pen.mirror();
                pen.triangle(5, 3, 5, 13, 11.5, 8);
            }
            case NEXT -> pen.triangle(5, 3, 5, 13, 11.5, 8);
            case FIT -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 3.5, 13, 9), pen.paper());
                corners(pen, 3.5, 5.5, 12.5, 10.5);
            }
            case ACTUAL_SIZE -> pen.letter("1:1", new Font(Font.SANS_SERIF, Font.BOLD, 7), 11);
            case ROTATE_LEFT -> {
                pen.mirror();
                rotate(pen);
            }
            case ROTATE_RIGHT -> rotate(pen);
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
            case FULL_SCREEN -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                corners(pen, 3.5, 4.5, 12.5, 11.5);
            }
            case LEAVE_FULL_SCREEN -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                pen.fillAndDraw(new Rectangle2D.Double(5.5, 6.5, 5, 3), pen.light());
            }
        }
    }
    // --8<-- [end:paint]

    /** Arrows from the middle into two corners: the picture grows to the edges. */
    private static void corners(ToolIcon.Pen pen, double x1, double y1, double x2, double y2) {
        pen.line(x1, y1, x1 + 2.5, y1 + 2.5);
        pen.line(x1, y1, x1 + 2, y1);
        pen.line(x1, y1, x1, y1 + 2);
        pen.line(x2, y2, x2 - 2.5, y2 - 2.5);
        pen.line(x2, y2, x2 - 2, y2);
        pen.line(x2, y2, x2, y2 - 2);
    }

    private static void rotate(ToolIcon.Pen pen) {
        pen.bold(1.6f);
        pen.draw(new Arc2D.Double(3, 3, 10, 10, 110, -250, Arc2D.OPEN));
        pen.thin();
        pen.triangle(9, 1, 9, 6.5, 13, 3.5);
    }
}
