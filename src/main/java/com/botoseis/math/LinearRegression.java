package com.botoseis.math;

import javafx.scene.chart.XYChart;

import java.util.List;

public abstract class LinearRegression {

    public final static class LeastSquares {

        private double slope;
        private double intercept;

        public LeastSquares(double[] x, double[] y) {
            if (x.length != y.length) {
                throw new IllegalArgumentException("array lengths should be equal");
            }
            compute(x, y, x.length);
        }

        public LeastSquares(List<XYChart.Data<Number, Number>> dataList) {
            int dataLength = dataList.size();

            double[] x = new double[dataLength];
            double[] y = new double[dataLength];

            for (int i = 0; i < dataLength; i++) {
                x[i] = dataList.get(i).getXValue().doubleValue();
                y[i] = dataList.get(i).getYValue().doubleValue();
            }

            compute(x, y, dataLength);
        }

        private void compute(double[] x, double[] y, int dataLength) {
            // Averages of x and y
            double sumX = 0, sumY = 0;
            for (int i = 0; i < dataLength; i++) {
                sumX += x[i];
                sumY += y[i];
            }
            double averageX = sumX / dataLength;
            double averageY = sumY / dataLength;

            double n1 = 0, n2 = 0;

            for (int i = 0; i < dataLength; i++) {
                n1 += x[i] * (y[i] - averageY);
                n2 += x[i] * (x[i] - averageX);
            }

            slope = n1 / n2;
            intercept = averageY - slope * averageX;
        }

        public final double getSlope() {
            return slope;
        }

        public final double getIntercept() {
            return intercept;
        }

    }

}
