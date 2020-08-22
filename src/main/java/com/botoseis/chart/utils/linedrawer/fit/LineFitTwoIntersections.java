package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import com.botoseis.chart.utils.linedrawer.LineDrawer;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;

public final class LineFitTwoIntersections extends LineFit {

    private final LineDrawer lineDrawer;
    private final IntersectionDetector intersection1;
    private final IntersectionDetector intersection2;

    public LineFitTwoIntersections(
            LineDrawer lineDrawer,
            ObservableList<XYChart.Data<Number, Number>> dataList,
            IntersectionDetector intersection1,
            IntersectionDetector intersection2,
            Pane lineFitContainer,
            String title) {
        super(lineDrawer, dataList, lineFitContainer, title);
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
