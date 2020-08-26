package com.botoseis.math;

import javafx.beans.property.DoubleProperty;

public class InterpretationCalculator {

    private final double UNDEFINED = 0;

    private final DoubleProperty slope_directL;
    private final DoubleProperty slope_directR;
    private final DoubleProperty slope_head1L;
    private final DoubleProperty slope_head1R;
    private final DoubleProperty slope_head2L;
    private final DoubleProperty slope_head2R;
    private final DoubleProperty slope_head3L;
    private final DoubleProperty slope_head3R;
    private final DoubleProperty intercept_head1L;
    private final DoubleProperty intercept_head1R;
    private final DoubleProperty intercept_head2L;
    private final DoubleProperty intercept_head2R;
    private final DoubleProperty intercept_head3L;
    private final DoubleProperty intercept_head3R;

    private double thickness1;
    private double thickness2;
    private double thickness3;

    private double velocity1;
    private double velocity2;
    private double velocity3;

    public InterpretationCalculator(
            DoubleProperty slope_head3L,
            DoubleProperty slope_head2L,
            DoubleProperty slope_head1L,
            DoubleProperty slope_directL,
            DoubleProperty slope_directR,
            DoubleProperty slope_head1R,
            DoubleProperty slope_head2R,
            DoubleProperty slope_head3R,
            DoubleProperty intercept_head3L,
            DoubleProperty intercept_head2L,
            DoubleProperty intercept_head1L,
            DoubleProperty intercept_head1R,
            DoubleProperty intercept_head2R,
            DoubleProperty intercept_head3R) {

        this.slope_directL = slope_directL;  // m/ms
        this.slope_directR = slope_directR;  // m/ms
        this.slope_head1L = slope_head1L;  // m/ms
        this.slope_head1R = slope_head1R;  // m/ms
        this.slope_head2L = slope_head2L;  // m/ms
        this.slope_head2R = slope_head2R;  // m/ms
        this.slope_head3L = slope_head3L;  // m/ms
        this.slope_head3R = slope_head3R;  // m/ms
        this.intercept_head1L = intercept_head1L;  // ms
        this.intercept_head1R = intercept_head1R;  // ms
        this.intercept_head2L = intercept_head2L;  // ms
        this.intercept_head2R = intercept_head2R;  // ms
        this.intercept_head3L = intercept_head3L;  // ms
        this.intercept_head3R = intercept_head3R;  // ms
    }

    private double computeVelocity(double slope) {
        if (slope != UNDEFINED) {
            return Math.abs(1000 / slope);
        } else {
            return UNDEFINED;
        }
    }

    private double computeVelocity(double slope1, double slope2) {
        if (slope1 != UNDEFINED && slope2 != UNDEFINED) {
            return 1000 / ((Math.abs(slope1) + Math.abs(slope2)) / 2);
        } else if (slope1 != UNDEFINED) {
            return 1000 / Math.abs(slope1);
        } else if (slope2 != UNDEFINED) {
            return 1000 / Math.abs(slope2);
        } else {
            return UNDEFINED;
        }
    }

    private double computeIntercept(double intercept1, double intercept2) {
        if (intercept1 < 0 || intercept2 < 0) {
            return UNDEFINED;
        } else if (intercept1 != UNDEFINED && intercept2 != UNDEFINED) {
            return (intercept1 + intercept2) / 2000;
        } else if (intercept1 != UNDEFINED) {
            return intercept1 / 1000;
        } else if (intercept2 != UNDEFINED) {
            return intercept2 / 1000;
        } else {
            return UNDEFINED;
        }
    }

    public void computeAvailableInterpretations() {

        boolean interpretation1Available = interpretation1Available();
        boolean interpretation2Available = interpretation2Available();
        boolean interpretation3Available = interpretation3Available();

        if (interpretation1Available && interpretation2Available && interpretation3Available) {

            double v1 = computeVelocity(slope_directL.get(), slope_directR.get());
            double va2 = computeVelocity(slope_head1L.get());
            double va3 = computeVelocity(slope_head2L.get());
            double va4 = computeVelocity(slope_head3L.get());
            double vb2 = computeVelocity(slope_head1R.get());
            double vb3 = computeVelocity(slope_head2R.get());
            double vb4 = computeVelocity(slope_head3R.get());

            if (isUndefined(va2)) va2 = vb2;
            if (isUndefined(vb2)) vb2 = va2;

            if (isUndefined(va3)) va3 = vb3;
            if (isUndefined(vb3)) vb3 = va3;

            if (isUndefined(va4)) va4 = vb4;
            if (isUndefined(vb4)) vb4 = va4;

            double t2 = computeIntercept(intercept_head1L.get(), intercept_head1R.get());
            double t3 = computeIntercept(intercept_head2L.get(), intercept_head2R.get());
            double t4 = computeIntercept(intercept_head3L.get(), intercept_head3R.get());

            double[] results1 = computeThicknessLayer1(v1, va2, vb2, t2);
            double[] results2 = computeThicknessLayer2(v1, va3, vb3, t3, results1);

            thickness1 = results1[0];
            thickness2 = results2[0];
            thickness3 = computeThicknessLayer3(v1, va4, vb4, t4, results1, results2);

            velocity1 = v1;
            velocity2 = results1[1];
            velocity3 = results2[1];

        } else if (interpretation1Available && interpretation2Available) {

            double v1 = computeVelocity(slope_directL.get(), slope_directR.get());
            double va2 = computeVelocity(slope_head1L.get());
            double va3 = computeVelocity(slope_head2L.get());
            double vb2 = computeVelocity(slope_head1R.get());
            double vb3 = computeVelocity(slope_head2R.get());

            if (isUndefined(va2)) va2 = vb2;
            if (isUndefined(vb2)) vb2 = va2;

            if (isUndefined(va3)) va3 = vb3;
            if (isUndefined(vb3)) vb3 = va3;

            double t2 = computeIntercept(intercept_head1L.get(), intercept_head1R.get());
            double t3 = computeIntercept(intercept_head2L.get(), intercept_head2R.get());

            double[] results1 = computeThicknessLayer1(v1, va2, vb2, t2);
            double[] results2 = computeThicknessLayer2(v1, va3, vb3, t3, results1);

            thickness1 = results1[0];
            thickness2 = results2[0];
            thickness3 = UNDEFINED;

            velocity1 = v1;
            velocity2 = results1[1];
            velocity3 = results2[1];

        } else if (interpretation1Available) {

            double v1 = computeVelocity(slope_directL.get(), slope_directR.get());
            double va2 = computeVelocity(slope_head1L.get());
            double vb2 = computeVelocity(slope_head1R.get());

            if (isUndefined(va2)) va2 = vb2;
            if (isUndefined(vb2)) vb2 = va2;

            double t2 = computeIntercept(intercept_head1L.get(), intercept_head1R.get());

            double[] results1 = computeThicknessLayer1(v1, va2, vb2, t2);

            thickness1 = results1[0];
            thickness2 = UNDEFINED;
            thickness3 = UNDEFINED;

            velocity1 = v1;
            velocity2 = results1[1];
            velocity3 = UNDEFINED;

        } else {
            thickness1 = UNDEFINED;
            thickness2 = UNDEFINED;
            thickness3 = UNDEFINED;

            velocity1 = UNDEFINED;
            velocity2 = UNDEFINED;
            velocity3 = UNDEFINED;
        }
    }

    public double[] getLayerThicknesses() {
        return new double[]{thickness1, thickness2, thickness3};
    }

    public double[] getVelocities() {
        return new double[]{velocity1, velocity2, velocity3};
    }

    private boolean interpretation1Available() {
        return atLeastOneDefined(slope_directL.get(), slope_directR.get())
                && atLeastOneDefined(slope_head1L.get(), slope_head1R.get())
                && atLeastOneDefined(intercept_head1L.get(), intercept_head1R.get());
    }

    private boolean interpretation2Available() {
        return atLeastOneDefined(slope_head2L.get(), slope_head2R.get())
                && atLeastOneDefined(intercept_head2L.get(), intercept_head2R.get());
    }

    private boolean interpretation3Available() {
        return atLeastOneDefined(slope_head3L.get(), slope_head3R.get())
                && atLeastOneDefined(intercept_head3L.get(), intercept_head3R.get());
    }

    private boolean atLeastOneDefined(double... values) {
        for (double value : values) {
            if (isDefined(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefined(double value) {
        return value != UNDEFINED;
    }

    private boolean isUndefined(double value) {
        return value == UNDEFINED;
    }

    /**
     * @param v1  velocity of layer 1 (m/s)
     * @param va2 apparent velocity of layer 2 in the a direction (m/s)
     * @param vb2 apparent velocity of layer 2 in the b direction (m/s)
     * @param t2  intercept time at x = 0 for the refraction from interface 2 (s)
     * @return h1 (m), v2 (m/s) and w2 (rad)
     */
    private static double[] computeThicknessLayer1(double v1, double va2, double vb2, double t2) {

        double alpha1_1 = Math.asin(v1 / va2);
        double beta1_1 = Math.asin(v1 / vb2);

        double h1 = (v1 / (Math.cos(alpha1_1) + Math.cos(beta1_1))) * t2;  // Used after

        double a1_1 = (alpha1_1 + beta1_1) / 2;

        double v2 = v1 / Math.sin(a1_1); // Used after

        double w2 = (alpha1_1 - beta1_1) / 2;  // Used after

        return new double[]{h1, v2, w2};
    }

    /**
     * @param v1       velocity of layer 1 (m/s)
     * @param va3      apparent velocity of layer 3 in the a direction (m/s)
     * @param vb3      apparent velocity of layer 3 in the b direction (m/s)
     * @param t3       intercept time at x = 0 for the refraction from interface 3 (s)
     * @param results1 double array containing h1 (m), v2 (m/s) and w2 (m)
     * @return h2 (m), v3 (m/s) and w3 (rad)
     */
    public static double[] computeThicknessLayer2(double v1, double va3, double vb3, double t3, double[] results1) {

        double h1 = results1[0];
        double v2 = results1[1];
        double w2 = results1[2];

        double alpha1_2 = Math.asin(v1 / va3);
        double beta1_2 = Math.asin(v1 / vb3);

        double a1_2 = alpha1_2 - w2;
        double b1_2 = beta1_2 + w2;

        double P2_2 = Math.asin((v2 / v1) * Math.sin(a1_2));
        double Q2_2 = Math.asin((v2 / v1) * Math.sin(b1_2));

        double alpha2_2 = P2_2 + w2;
        double beta2_2 = Q2_2 - w2;

        double h2 = (v2 / (Math.cos(alpha2_2) + Math.cos(beta2_2))) *
                (t3 - (h1 * (Math.cos(alpha1_2) + Math.cos(beta1_2)) / v1));  // Used after

        double a2_2 = (alpha2_2 + beta2_2) / 2;

        double v3 = v2 / Math.sin(a2_2);  // Used after

        double w3 = (alpha2_2 - beta2_2) / 2;  // Used after

        return new double[]{h2, v3, w3};
    }

    /**
     * @param v1       velocity of layer 1 (m/s)
     * @param va4      apparent velocity of layer 4 in the a direction (m/s)
     * @param vb4      apparent velocity of layer 4 in the b direction (m/s)
     * @param t4       intercept time at x = 0 for the refraction from interface 4 (s)
     * @param results1 double array containing h1 (m), v2 (m/s) and w2 (rad)
     * @param results2 double array containing h2 (m), v3 (m/2) and w3 (rad)
     * @return h3 (m)
     */
    public static double computeThicknessLayer3(double v1, double va4, double vb4, double t4, double[] results1, double[] results2) {
        double h1 = results1[0];
        double v2 = results1[1];
        double w2 = results1[2];

        double h2 = results2[0];
        double v3 = results2[1];
        double w3 = results2[2];

        double alpha1_3 = Math.asin(v1 / va4);
        double beta1_3 = Math.asin(v1 / vb4);

        double a1_3 = alpha1_3 - w2;
        double b1_3 = beta1_3 + w2;

        double P2_3 = Math.asin((v2 / v1) * Math.sin(a1_3));
        double Q2_3 = Math.asin((v2 / v1) * Math.sin(b1_3));

        double alpha2_3 = P2_3 + w2;
        double beta2_3 = Q2_3 - w2;

        double a2_3 = alpha2_3 - w3;
        double b2_3 = beta2_3 + w3;

        double P3_3 = Math.asin((v3 / v2) * Math.sin(a2_3));
        double Q3_3 = Math.asin((v3 / v2) * Math.sin(b2_3));

        double alpha3_3 = P3_3 + w3;
        double beta3_3 = Q3_3 - w3;

        return (v3 / (Math.cos(alpha3_3) + Math.cos(beta3_3))) *
                (t4 - ((h1 * (Math.cos(alpha1_3) + Math.cos(beta1_3)) / v1) +
                        (h2 * (Math.cos(alpha2_3) + Math.cos(beta2_3)) / v2)));
    }

}