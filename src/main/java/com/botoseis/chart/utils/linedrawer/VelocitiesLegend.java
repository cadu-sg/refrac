package com.botoseis.chart.utils.linedrawer;

import javafx.beans.property.DoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public final class VelocitiesLegend {

    private static final double UNDEFINED = 0;

    public VelocitiesLegend(StackPane chartPane,
                            DoubleProperty slope_head3L, DoubleProperty slope_head2L, DoubleProperty slope_head1L, DoubleProperty slope_directL,
                            DoubleProperty slope_directR, DoubleProperty slope_head1R, DoubleProperty slope_head2R, DoubleProperty slope_head3R) {

        // Labels
        Label label_vD = new Label("Direct");
        Label label_v1 = new Label("Refracted 1");
        Label label_v2 = new Label("Refracted 2");
        Label label_v3 = new Label("Refracted 3");

        Label label_left = new Label("Left");
        Label label_vDL = new Label();
        Label label_v1L = new Label();
        Label label_v2L = new Label();
        Label label_v3L = new Label();

        Label label_right = new Label("Right");
        Label label_vDR = new Label();
        Label label_v1R = new Label();
        Label label_v2R = new Label();
        Label label_v3R = new Label();

        Label label_average = new Label("Average");
        Label label_vDA = new Label();
        Label label_v1A = new Label();
        Label label_v2A = new Label();
        Label label_v3A = new Label();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        grid.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        grid.setStyle(
                "-fx-background-color: rgba(135, 206, 250, 0.7);"
                        + "-fx-background-radius: 3;"
                        + "-fx-padding: 10;"
                        + "-fx-background-insets: 5;");
        grid.setMouseTransparent(true);

        slope_head3L.addListener((observable, oldValue, newValue) -> {

            label_v3L.setText(computeVelocityText(newValue.doubleValue()));

            label_v3A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head3R.get()));

        });
        slope_head2L.addListener((observable, oldValue, newValue) -> {
            label_v2L.setText(computeVelocityText(newValue.doubleValue()));
            label_v2A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head2R.get()));
        });
        slope_head1L.addListener((observable, oldValue, newValue) -> {
            label_v1L.setText(computeVelocityText(newValue.doubleValue()));
            label_v1A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head1R.get()));
        });
        slope_directL.addListener((observable, oldValue, newValue) -> {
            label_vDL.setText(computeVelocityText(newValue.doubleValue()));
            label_vDA.setText(computeAverageVelocityText(newValue.doubleValue(), slope_directR.get()));
        });
        slope_directR.addListener((observable, oldValue, newValue) -> {
            label_vDR.setText(computeVelocityText(newValue.doubleValue()));
            label_vDA.setText(computeAverageVelocityText(newValue.doubleValue(), slope_directL.get()));
        });
        slope_head1R.addListener((observable, oldValue, newValue) -> {
            label_v1R.setText(computeVelocityText(newValue.doubleValue()));
            label_v1A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head1L.get()));
        });
        slope_head2R.addListener((observable, oldValue, newValue) -> {
            label_v2R.setText(computeVelocityText(newValue.doubleValue()));
            label_v2A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head2L.get()));
        });
        slope_head3R.addListener((observable, oldValue, newValue) -> {
            label_v3R.setText(computeVelocityText(newValue.doubleValue()));
            label_v3A.setText(computeAverageVelocityText(newValue.doubleValue(), slope_head3L.get()));
        });

        // Populate grid
        grid.add(label_vD, 0, 1);
        grid.add(label_v1, 0, 2);
        grid.add(label_v2, 0, 3);
        grid.add(label_v3, 0, 4);

        grid.add(label_left, 1, 0);
        grid.add(label_vDL, 1, 1);
        grid.add(label_v1L, 1, 2);
        grid.add(label_v2L, 1, 3);
        grid.add(label_v3L, 1, 4);

        grid.add(label_right, 2, 0);
        grid.add(label_vDR, 2, 1);
        grid.add(label_v1R, 2, 2);
        grid.add(label_v2R, 2, 3);
        grid.add(label_v3R, 2, 4);

        grid.add(label_average, 3, 0);
        grid.add(label_vDA, 3, 1);
        grid.add(label_v1A, 3, 2);
        grid.add(label_v2A, 3, 3);
        grid.add(label_v3A, 3, 4);

        // Add grid to chartPane
        StackPane.setAlignment(grid, Pos.TOP_CENTER);
        chartPane.getChildren().add(grid);
    }

    private String computeVelocityText(double slope) {
        if (slope == UNDEFINED) {
            return "";
        } else {
            return String.format("%.2f", computeVelocity(slope));
        }
    }

    private double computeVelocity(double slope) {
        return Math.abs(1000 / slope);
    }

    private String computeAverageVelocityText(double slope1, double slope2) {
        if (slope1 == UNDEFINED || slope2 == UNDEFINED) {
            return "";
        } else {
            return String.format("%.2f", computeAverageVelocity(slope1, slope2));
        }
    }

    private double computeAverageVelocity(double slope1, double slope2) {
        return 2000 / (Math.abs(slope1) + Math.abs(slope2));
    }

}
