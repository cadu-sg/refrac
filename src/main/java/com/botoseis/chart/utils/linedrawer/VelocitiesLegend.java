package com.botoseis.chart.utils.linedrawer;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class VelocitiesLegend {

    private static final double UNDEFINED = 0;

    private final DoubleProperty v3L;
    private final DoubleProperty v2L;
    private final DoubleProperty v1L;
    private final DoubleProperty v0L;
    private final DoubleProperty v0R;
    private final DoubleProperty v1R;
    private final DoubleProperty v2R;
    private final DoubleProperty v3R;

    public DoubleProperty v3LProperty() {
        return v3L;
    }

    public DoubleProperty v2LProperty() {
        return v2L;
    }

    public DoubleProperty v1LProperty() {
        return v1L;
    }

    public DoubleProperty v0LProperty() {
        return v0L;
    }

    public DoubleProperty v0RProperty() {
        return v0R;
    }

    public DoubleProperty v1RProperty() {
        return v1R;
    }

    public DoubleProperty v2RProperty() {
        return v2R;
    }

    public DoubleProperty v3RProperty() {
        return v3R;
    }

    public VelocitiesLegend(StackPane chartPane,
                            DoubleProperty slope_head3L, DoubleProperty slope_head2L, DoubleProperty slope_head1L, DoubleProperty slope_directL,
                            DoubleProperty slope_directR, DoubleProperty slope_head1R, DoubleProperty slope_head2R, DoubleProperty slope_head3R) {

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

        v3L = getVelocityProperty(slope_head3L);
        v2L = getVelocityProperty(slope_head2L);
        v1L = getVelocityProperty(slope_head1L);
        v0L = getVelocityProperty(slope_directL);
        v0R = getVelocityProperty(slope_directR);
        v1R = getVelocityProperty(slope_head1R);
        v2R = getVelocityProperty(slope_head2R);
        v3R = getVelocityProperty(slope_head3R);

        v3L.addListener((observable, oldValue, newValue) -> label_v3L.setText(computeVelocityText(newValue.doubleValue())));
        v2L.addListener((observable, oldValue, newValue) -> label_v2L.setText(computeVelocityText(newValue.doubleValue())));
        v1L.addListener((observable, oldValue, newValue) -> label_v1L.setText(computeVelocityText(newValue.doubleValue())));
        v0L.addListener((observable, oldValue, newValue) -> label_vDL.setText(computeVelocityText(newValue.doubleValue())));
        v0R.addListener((observable, oldValue, newValue) -> label_vDR.setText(computeVelocityText(newValue.doubleValue())));
        v1R.addListener((observable, oldValue, newValue) -> label_v1R.setText(computeVelocityText(newValue.doubleValue())));
        v2R.addListener((observable, oldValue, newValue) -> label_v2R.setText(computeVelocityText(newValue.doubleValue())));
        v3R.addListener((observable, oldValue, newValue) -> label_v3R.setText(computeVelocityText(newValue.doubleValue())));

        GridPane.setColumnIndex(label_vD, 0);
        GridPane.setRowIndex(label_vD, 1);
        GridPane.setColumnIndex(label_v1, 0);
        GridPane.setRowIndex(label_v1, 2);
        GridPane.setColumnIndex(label_v2, 0);
        GridPane.setRowIndex(label_v2, 3);
        GridPane.setColumnIndex(label_v3, 0);
        GridPane.setRowIndex(label_v3, 4);

        GridPane.setColumnIndex(label_left, 1);
        GridPane.setRowIndex(label_left, 0);
        GridPane.setColumnIndex(label_vDL, 1);
        GridPane.setRowIndex(label_vDL, 1);
        GridPane.setColumnIndex(label_v1L, 1);
        GridPane.setRowIndex(label_v1L, 2);
        GridPane.setColumnIndex(label_v2L, 1);
        GridPane.setRowIndex(label_v2L, 3);
        GridPane.setColumnIndex(label_v3L, 1);
        GridPane.setRowIndex(label_v3L, 4);

        GridPane.setColumnIndex(label_right, 2);
        GridPane.setRowIndex(label_right, 0);
        GridPane.setColumnIndex(label_vDR, 2);
        GridPane.setRowIndex(label_vDR, 1);
        GridPane.setColumnIndex(label_v1R, 2);
        GridPane.setRowIndex(label_v1R, 2);
        GridPane.setColumnIndex(label_v2R, 2);
        GridPane.setRowIndex(label_v2R, 3);
        GridPane.setColumnIndex(label_v3R, 2);
        GridPane.setRowIndex(label_v3R, 4);

        ColumnConstraints[] columns = new ColumnConstraints[3];
        columns[0] = new ColumnConstraints(90);
        columns[1] = new ColumnConstraints(60);
        columns[1].setHalignment(HPos.CENTER);
        columns[2] = new ColumnConstraints(60);
        columns[2].setHalignment(HPos.CENTER);
        RowConstraints[] rows = new RowConstraints[5];
        rows[0] = new RowConstraints(20);
        rows[1] = new RowConstraints(20);
        rows[2] = new RowConstraints(20);
        rows[3] = new RowConstraints(20);
        rows[4] = new RowConstraints(20);

        GridPane container_legend = new GridPane();
        container_legend.getColumnConstraints().addAll(columns);
        container_legend.getRowConstraints().addAll(rows);
        container_legend.getChildren().addAll(
                label_vD, label_v1, label_v2, label_v3,
                label_left, label_vDL, label_v1L, label_v2L, label_v3L,
                label_right, label_vDR, label_v1R, label_v2R, label_v3R);
        container_legend.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        container_legend.setStyle(
                "-fx-background-color: rgba(135, 206, 250, 0.7);"
                        + "-fx-background-radius: 3;"
                        + "-fx-padding: 10;"
                        + "-fx-background-insets: 5;");
        container_legend.setMouseTransparent(true);
        StackPane.setAlignment(container_legend, Pos.TOP_CENTER);

        chartPane.getChildren().add(container_legend);
    }

    private String computeVelocityText(double velocity) {
        if (velocity == UNDEFINED) {
            return "";
        } else {
            return String.format("%.2f", velocity);
        }
    }

    private DoubleProperty getVelocityProperty(DoubleProperty slope) {
        DoubleProperty velocity = new SimpleDoubleProperty(UNDEFINED);
        slope.addListener((observable, oldValue, newValue) -> velocity.set(computeVelocity(newValue.doubleValue())));
        return velocity;
    }

    private double computeVelocity(double slope) {
        if (slope == UNDEFINED) {
            return UNDEFINED;
        } else {
            return Math.abs(1000 / slope);
        }
    }

}
