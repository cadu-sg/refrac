package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import com.botoseis.chart.utils.linedrawer.LineDrawer;
import com.botoseis.chart.utils.linedrawer.OriginFixedLineDrawer;
import com.botoseis.chart.utils.linedrawer.StandardLineDrawer;
import com.botoseis.math.LinearRegression;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

public class LineFit {

    private Pane lineFitParent;

    private final LineDrawer lineDrawer;
    private final ObservableList<XYChart.Data<Number, Number>> dataList;
    private final IntersectionDetector intersection;
    private final Pane lineFitNodeContainer;
    private final LineFitNode lineFitNode;

    public LineFit(LineDrawer lineDrawer,
                   ObservableList<XYChart.Data<Number, Number>> dataList,
                   IntersectionDetector intersection,
                   Pane lineFitNodeContainer) {
        this.lineDrawer = lineDrawer;
        this.intersection = intersection;
        this.dataList = dataList;
        this.lineFitNodeContainer = lineFitNodeContainer;
        lineFitNode = new LineFitNode();
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

    private class LineFitNode extends VBox {
        public LineFitNode() {
            Label label = new Label("Adjust selected line drawer");
            Button button = new Button("Adjust");
//            button.setOnAction(event -> {
//                handleButton();
//                event.consume();
//            });
            this.getChildren().addAll(label, button);
            this.setAlignment(Pos.CENTER);
        }
    }

    private static double[] getSlopeIntercept(List<XYChart.Data<Number, Number>> dataList, double startX, double endX) {
        if (startX > endX) {
            double swap = startX;
            startX = endX;
            endX = swap;
        }
        double finalStartX = startX;
        double finalEndX = endX;
        LinearRegression.LeastSquares adjustment = new LinearRegression.LeastSquares(dataList.stream()
                .filter(data -> containedInClosedInterval(data.getXValue().doubleValue(), finalStartX, finalEndX))
                .collect(Collectors.toList())
        );
        double slope = adjustment.getSlope();
        double intercept = adjustment.getIntercept();
        return new double[]{slope, intercept};
    }

    private static boolean containedInClosedInterval(double value, double start, double end) {
        return value >= start && value <= end;
    }

    private static void fitLineDrawer(LineDrawer lineDrawer, double slope, double intercept) {
        if (lineDrawer instanceof OriginFixedLineDrawer) {

            double x = lineDrawer.getPoint2().getX();
            double y = x * slope + intercept;
            lineDrawer.setPoint2(x, y);

        } else if (lineDrawer instanceof StandardLineDrawer) {

            double x1 = lineDrawer.getPoint1().getX();
            double y1 = x1 * slope + intercept;
            double x2 = lineDrawer.getPoint2().getX();
            double y2 = x2 * slope + intercept;

            lineDrawer.setPoint1(x1, y1);
            lineDrawer.setPoint2(x2, y2);
        }
    }

}
