package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.LineDrawer;
import com.botoseis.math.LinearRegression;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.List;
import java.util.stream.Collectors;

abstract class LineFit {

    protected static final double[] UNDEFINED = new double[]{0, 0};

    private final LineDrawer lineDrawer;
    private final ObservableList<XYChart.Data<Number, Number>> dataList;
    private final Pane lineFitNodeContainer;
    private final LineFitNode lineFitNode;

    protected LineFit(LineDrawer lineDrawer,
                      ObservableList<XYChart.Data<Number, Number>> dataList,
                      Pane lineFitNodeContainer,
                      String text) {
        this.lineDrawer = lineDrawer;
        this.dataList = dataList;
        this.lineFitNodeContainer = lineFitNodeContainer;
        this.lineFitNode = new LineFitNode(text);
        lineDrawer.drawnProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                addGUI();
            } else {
                removeGUI();
            }
        });
    }

    private void removeGUI() {
        this.lineFitNodeContainer.getChildren().remove(lineFitNode);
    }

    private void addGUI() {
        this.lineFitNodeContainer.getChildren().add(lineFitNode);
    }

    private class LineFitNode extends Button {
        public LineFitNode(String text) {
            this.setMaxWidth(Double.MAX_VALUE);
            this.setText(text);
            this.setStyle("-fx-text-fill: " + lineDrawer.getColor() + ";");
            this.setOnAction(event -> {
                handleFit();
                event.consume();
            });
        }
    }

    protected final void handleFit() {
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

    protected static double nearestZero(double value1, double value2) {
        if (Math.abs(value1) < Math.abs(value2)) {
            return value1;
        } else {
            return value2;
        }
    }

    protected static double farthestFromFirstValue(double value1, double value2, double value3) {
        double absValue1 = Math.abs(value1);
        double absValue2 = Math.abs(value2);
        double absValue3 = Math.abs(value3);
        if (Math.abs(absValue1 - absValue2) > Math.abs(absValue1 - absValue3)) {
            return value2;
        } else {
            return value3;
        }
    }


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
