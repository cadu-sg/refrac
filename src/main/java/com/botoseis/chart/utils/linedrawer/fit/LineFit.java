package com.botoseis.chart.utils.linedrawer.fit;

import com.botoseis.chart.utils.linedrawer.LineDrawer;
import com.botoseis.chart.utils.linedrawer.OriginFixedLineDrawer;
import com.botoseis.chart.utils.linedrawer.StandardLineDrawer;
import com.botoseis.math.LinearRegression;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

public class LineFit {

    private Pane lineFitParent;

    private VBox lineFitContainer;

    private static double[] getFitParameters(List<XYChart.Data<Number, Number>> dataList, double startX, double endX) {
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
