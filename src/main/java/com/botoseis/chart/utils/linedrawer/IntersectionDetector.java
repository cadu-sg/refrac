package com.botoseis.chart.utils.linedrawer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;

public class IntersectionDetector {

    private final LineDrawer lineDrawer1;
    private final LineDrawer lineDrawer2;

    private final BooleanProperty intersected;
    private Point2D intersection;

    public BooleanProperty intersectedProperty() {
        return intersected;
    }

    public boolean isIntersected() {
        return intersected.get();
    }

    public Point2D getIntersection() {
        return intersection;
    }

    public IntersectionDetector(LineDrawer lineDrawer1, LineDrawer lineDrawer2) {
        this.lineDrawer1 = lineDrawer1;
        this.lineDrawer2 = lineDrawer2;

        this.intersected = new SimpleBooleanProperty(false);

        BooleanProperty drawn1 = lineDrawer1.drawnProperty();
        BooleanProperty drawn2 = lineDrawer2.drawnProperty();

        drawn1.addListener((observable, oldValue, newValue) -> {
            if (newValue && drawn2.get()) {
                checkForIntersection();
            } else {
                intersected.set(false);
            }
        });
        drawn2.addListener((observable, oldValue, newValue) -> {
            if (newValue && drawn1.get()) {
                checkForIntersection();
            } else {
                intersected.set(false);
            }
        });
    }

    protected void checkForIntersection() {
        intersection = getLineIntersection(
                lineDrawer1.getPoint1(), lineDrawer1.getPoint2(),
                lineDrawer2.getPoint1(), lineDrawer2.getPoint2());
        intersected.set(intersection.getX() != Double.MAX_VALUE);
    }

    private static Point2D getLineIntersection(Point2D line1_p1, Point2D line1_p2, Point2D line2_p1, Point2D line2_p2) {

        double line1_x1 = line1_p1.getX();
        double line1_y1 = line1_p1.getY();
        double line1_x2 = line1_p2.getX();
        double line1_y2 = line1_p2.getY();

        double line2_x1 = line2_p1.getX();
        double line2_y1 = line2_p1.getY();
        double line2_x2 = line2_p2.getX();
        double line2_y2 = line2_p2.getY();

        // Line 1 (AB) represented as: a1 x + b1 y = c1
        double a1 = line1_y2 - line1_y1;
        double b1 = line1_x1 - line1_x2;
        double c1 = a1 * (line1_x1) + b1 * (line1_y1);

        // Line 2 (CD) represented as: a2 x + b2 y = c2
        double a2 = line2_y2 - line2_y1;
        double b2 = line2_x1 - line2_x2;
        double c2 = a2 * (line2_x1) + b2 * (line2_y1);

        double determinant = a1 * b2 - a2 * b1;

        if (determinant >= -10 && determinant <= 10) {
            // The lines are parallel
            // This is represented by returning a pair of Double.MAX_VALUE
            return new Point2D(Double.MAX_VALUE, Double.MAX_VALUE);
        } else {
            float x = (float) ((b2 * c1 - b1 * c2) / determinant);
            float y = (float) ((a1 * c2 - a2 * c1) / determinant);
            return new Point2D(x, y);
        }
    }

}
