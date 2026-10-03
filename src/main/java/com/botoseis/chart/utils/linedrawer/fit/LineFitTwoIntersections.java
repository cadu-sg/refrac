package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import com.botoseis.chart.utils.linedrawer.LineDrawer;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

public final class LineFitTwoIntersections extends LineFit {

    private final LineDrawer lineDrawer;
    private final IntersectionDetector intersection1;
    private final IntersectionDetector intersection2;

    public LineFitTwoIntersections(
            LineDrawer lineDrawer,
            ObservableList<XYChart.Data<Number, Number>> dataList,
            IntersectionDetector intersection1,
            IntersectionDetector intersection2) {
        super(lineDrawer, dataList);
        this.lineDrawer = lineDrawer;
        this.intersection1 = intersection1;
        this.intersection2 = intersection2;
    }

    @Override
    protected double[] computeLineFitBounds() {
        double startX, endX;
        if (intersection1.isIntersected() && intersection2.isIntersected()) {
            startX = intersection1.getIntersection().getX();
            endX = intersection2.getIntersection().getX();
        } else if (intersection1.isIntersected()) {
            startX = intersection1.getIntersection().getX();
            endX = farthestFromFirstValue(startX, lineDrawer.getPoint1().getX(), lineDrawer.getPoint2().getX());
        } else if (intersection2.isIntersected()) {
            startX = intersection2.getIntersection().getX();
            endX = farthestFromFirstValue(startX, lineDrawer.getPoint1().getX(), lineDrawer.getPoint2().getX());
        } else {
            startX = lineDrawer.getPoint1().getX();
            endX = lineDrawer.getPoint2().getX();
        }
        return new double[]{startX, endX};
    }

    private static double farthestFromFirstValue(double value1, double value2, double value3) {
        double absValue1 = Math.abs(value1);
        double absValue2 = Math.abs(value2);
        double absValue3 = Math.abs(value3);
        if (Math.abs(absValue1 - absValue2) > Math.abs(absValue1 - absValue3)) {
            return value2;
        } else {
            return value3;
        }
    }

    @Override
    protected void fitLineDrawer(double slope, double intercept) {
        double x1 = lineDrawer.getPoint1().getX();
        double y1 = x1 * slope + intercept;
        double x2 = lineDrawer.getPoint2().getX();
        double y2 = x2 * slope + intercept;
        lineDrawer.drawnProperty().set(false);
        lineDrawer.setPoint1(x1, y1);
        lineDrawer.setPoint2(x2, y2);
        lineDrawer.drawnProperty().set(true);
    }
}
