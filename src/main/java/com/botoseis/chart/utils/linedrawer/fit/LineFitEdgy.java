package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import com.botoseis.chart.utils.linedrawer.LineDrawer;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;

public final class LineFitEdgy extends LineFit {
    private final LineDrawer lineDrawer;
    private final IntersectionDetector intersection;

    public LineFitEdgy(
            LineDrawer lineDrawer,
            ObservableList<XYChart.Data<Number, Number>> dataList,
            IntersectionDetector intersection,
            Pane lineFitContainer,
            String title) {
        super(lineDrawer, dataList, lineFitContainer, title);
        this.lineDrawer = lineDrawer;
        this.intersection = intersection;
    }

    @Override
    protected double[] computeLineFitBounds() {
        double startX, endX;
        if (intersection.isIntersected()) {
            startX = intersection.getIntersection().getX();
            endX = farthestFromZero(lineDrawer.getPoint1().getX(), lineDrawer.getPoint2().getX());
        } else {
            startX = lineDrawer.getPoint1().getX();
            endX = lineDrawer.getPoint2().getX();
        }
        return new double[]{startX, endX};
    }

    private static double farthestFromZero(double value1, double value2) {
        if (Math.abs(value1) > Math.abs(value2)) {
            return value1;
        } else {
            return value2;
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
