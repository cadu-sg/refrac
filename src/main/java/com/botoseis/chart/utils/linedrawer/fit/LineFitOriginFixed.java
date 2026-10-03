package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import com.botoseis.chart.utils.linedrawer.LineDrawer;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

public final class LineFitOriginFixed extends LineFit {

    private final LineDrawer lineDrawer;
    private final IntersectionDetector intersection;

    public LineFitOriginFixed(
            LineDrawer lineDrawer,
            ObservableList<XYChart.Data<Number, Number>> dataList,
            IntersectionDetector intersection) {
        super(lineDrawer, dataList);
        this.lineDrawer = lineDrawer;
        this.intersection = intersection;
    }

    @Override
    protected double[] computeLineFitBounds() {
        double startX, endX;
        if (intersection.isIntersected()) {
            startX = lineDrawer.getPoint1().getX();
            endX = intersection.getIntersection().getX();
        } else {
            startX = lineDrawer.getPoint1().getX();
            endX = lineDrawer.getPoint2().getX();
        }
        return new double[]{startX, endX};
    }

    @Override
    protected void fitLineDrawer(double slope, double intercept) {
        double x = lineDrawer.getPoint2().getX();
        double y = x * slope + intercept;
        lineDrawer.drawnProperty().set(false);
        lineDrawer.setPoint2(x, y);
        lineDrawer.drawnProperty().set(true);
    }
}
