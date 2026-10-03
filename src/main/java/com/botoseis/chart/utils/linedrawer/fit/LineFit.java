package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.LineDrawer;
import com.botoseis.math.LinearRegression;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

import java.util.List;
import java.util.stream.Collectors;

public abstract class LineFit {

    protected static final double[] UNDEFINED = new double[]{0, 0};

    private final LineDrawer lineDrawer;
    private final ObservableList<XYChart.Data<Number, Number>> dataList;

    protected LineFit(LineDrawer lineDrawer,
                      ObservableList<XYChart.Data<Number, Number>> dataList) {
        this.lineDrawer = lineDrawer;
        this.dataList = dataList;
    }

    public final LineDrawer getLineDrawer() {
        return lineDrawer;
    }

    public final boolean canFit() {
        return lineDrawer.isDrawn();
    }

    public final void fit() {
        if (!canFit()) {
            return;
        }
        double[] lineFitBounds = computeLineFitBounds();
        double startX = lineFitBounds[0];
        double endX = lineFitBounds[1];

        double[] slopeAndIntercept = computeLinearRegression(startX, endX);
        if (slopeAndIntercept != UNDEFINED) {
            double slope = slopeAndIntercept[0];
            double intercept = slopeAndIntercept[1];
            fitLineDrawer(slope, intercept);
        }
    }

    protected abstract double[] computeLineFitBounds();

    protected double[] computeLinearRegression(double startX, double endX) {
        if (startX > endX) {
            double swap = startX;
            startX = endX;
            endX = swap;
        }
        double finalStartX = startX;
        double finalEndX = endX;
        List<XYChart.Data<Number, Number>> dataInClosedRange = dataList.stream()
                .filter(data -> containedInClosedInterval(data.getXValue().doubleValue(), finalStartX, finalEndX))
                .collect(Collectors.toList());
        if (dataInClosedRange.size() < 2) {
            return UNDEFINED;
        }
        LinearRegression.LeastSquares adjustment = new LinearRegression.LeastSquares(dataInClosedRange);
        double slope = adjustment.getSlope();
        double intercept = adjustment.getIntercept();
        return new double[]{slope, intercept};
    }

    private static boolean containedInClosedInterval(double value, double start, double end) {
        return value >= start && value <= end;
    }

    protected abstract void fitLineDrawer(double slope, double intercept);

}
