package com.botoseis.chart.utils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * This class adds a zoom functionality to a given XY chart. Zoom means that a
 * user can select a region in the chart that should be displayed at a larger
 * scale.
 */
public class Zoom {

    private final Pane chartPane;
    private final XYChart<Number, Number> chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;
    private Rectangle selectionRectangle;
    private Label infoLabel;

    private Point2D selectionRectangleStart;
    private Point2D selectionRectangleEnd;

    private final MousePressedHandler mousePressedHandler;
    private final MouseDraggedHandler mouseDraggedHandler;
    private final MouseReleasedHandler mouseReleasedHandler;
    private final EscapeKeyHandler escapeKeyHandler;
    private boolean isZoomed;

    /**
     * Create a new instance of this class with the given chart and pane
     * instances. The {@link Pane} instance is needed as a parent for the
     * rectangle that represents the user selection.
     *
     * @param chart the chart to which the zoom support should be added
     * @param pane  the pane on which the selection rectangle will be drawn.
     */
    public Zoom(XYChart<Number, Number> chart, Pane pane) {
        this.chartPane = pane;
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();

        addInfoLabel();
        addSelectionRectangle();

        // event handlers
        mousePressedHandler = new MousePressedHandler();
        mouseDraggedHandler = new MouseDraggedHandler();
        mouseReleasedHandler = new MouseReleasedHandler();
        escapeKeyHandler = new EscapeKeyHandler();
    }

    private BooleanProperty enabled;

    public BooleanProperty enabledProperty() {
        if (enabled == null) {
            enabled = new BooleanPropertyBase(false) {
                @Override
                public Object getBean() {
                    return Zoom.this;
                }

                @Override
                public String getName() {
                    return "selected";
                }
            };
            enabled.addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                if (newValue) {
                    // Mecanismo para selecionar a area no gráfico que deve ser mostrada em escala maior
                    chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
                    chartPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    chartPane.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
                    chartPane.addEventHandler(KeyEvent.KEY_RELEASED, escapeKeyHandler);
                    if (isZoomed) {
                        infoLabel.setVisible(true);
                        chartPane.requestFocus();
                    }
                } else {
                    chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
                    chartPane.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    chartPane.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
                    chartPane.removeEventHandler(KeyEvent.KEY_RELEASED, escapeKeyHandler);
                    infoLabel.setVisible(false);
                }
            });
        }
        return enabled;
    }

    public final void setEnabled(boolean value) {
        this.enabledProperty().set(value);
    }

    public final boolean isEnabled() {
        return enabled != null && enabled.get();
    }

    /**
     * The info label shows a short info text that tells the user how to reset
     * the zoom level.
     */
    private void addInfoLabel() {
        infoLabel = new Label("Press ESC to reset the zoom level.");
        infoLabel.setVisible(false);
        infoLabel.setStyle(
                "-fx-background-color: rgba(135, 206, 250, 0.8);\n"
                        + "-fx-font-size: 12;\n"
                        + "-fx-padding: 1;\n"
                        + "-fx-background-radius: 2;");
        StackPane.setAlignment(infoLabel, Pos.TOP_RIGHT);
        chartPane.getChildren().add(infoLabel);
    }

    private void addSelectionRectangle() {
        selectionRectangle = new Rectangle();
        selectionRectangle.setStyle(
                "-fx-stroke: rgba(135, 206, 250, 0.8);\n"
                        + "-fx-stroke-type: inside;\n"
                        + "-fx-fill: rgba(135, 206, 250, 0.2);");
        selectionRectangle.setVisible(false);
        selectionRectangle.setManaged(false);
        selectionRectangle.setMouseTransparent(true);
        chartPane.getChildren().add(selectionRectangle);
    }

    private Point2D computeRectanglePoint(double eventX, double eventY) {
        double lowerBoundX = computeOffsetInChart(xAxis, false);
        double upperBoundX = lowerBoundX + xAxis.getWidth();
        double lowerBoundY = computeOffsetInChart(yAxis, true);
        double upperBoundY = lowerBoundY + yAxis.getHeight();
        // make sure the rectangle's end point is in the interval defined by the lower and upper bounds for each
        // dimension
        double x = Math.max(lowerBoundX, Math.min(eventX, upperBoundX));
        double y = Math.max(lowerBoundY, Math.min(eventY, upperBoundY));
        return new Point2D(x, y);
    }

    /**
     * Computes the pixel offset of the given node inside the chart node.
     *
     * @param node     the node for which to compute the pixel offset
     * @param vertical flag that indicates whether the horizontal or the
     *                 vertical dimension should be taken into account
     * @return the offset inside the chart node
     */
    private double computeOffsetInChart(Node node, boolean vertical) {
        double offset = 0;
        do {
            if (vertical) {
                offset += node.getLayoutY();
            } else {
                offset += node.getLayoutX();
            }
            node = node.getParent();
        } while (node != chart);
        return offset;
    }

    private final class MousePressedHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(final MouseEvent event) {

            if (event.isPrimaryButtonDown()) {  // Botão esquerdo do mouse
                // Guarda a posição do clique inicial
                selectionRectangleStart = computeRectanglePoint(event.getX(), event.getY());
                selectionRectangle.setWidth(0);
                selectionRectangle.setHeight(0);
                selectionRectangle.setVisible(true);
                event.consume();
            }
        }
    }

    private final class MouseDraggedHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(final MouseEvent event) {

            if (event.isPrimaryButtonDown()) {  // Botão esquerdo do mouse
                // Guarda a posição atual do cursor
                selectionRectangleEnd = computeRectanglePoint(event.getX(), event.getY());

                double x = Math.min(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
                double y = Math.min(selectionRectangleStart.getY(), selectionRectangleEnd.getY());
                double width = Math.abs(selectionRectangleStart.getX() - selectionRectangleEnd.getX());
                double height = Math.abs(selectionRectangleStart.getY() - selectionRectangleEnd.getY());
                drawSelectionRectangle(x, y, width, height);

                event.consume();
            }
        }

        /**
         * Draws a selection box in the view.
         *
         * @param x      the x position of the selection box
         * @param y      the y position of the selection box
         * @param width  the width of the selection box
         * @param height the height of the selection box
         */
        private void drawSelectionRectangle(final double x, final double y, final double width, final double height) {
            selectionRectangle.setX(x);
            selectionRectangle.setY(y);
            selectionRectangle.setWidth(width);
            selectionRectangle.setHeight(height);
        }
    }

    private final class MouseReleasedHandler implements EventHandler<MouseEvent> {

        /**
         * Defines a minimum width for the selected area. If the selected
         * rectangle is not wider than this value, no zooming will take place.
         * This helps prevent accidental zooming.
         */
        private static final double MIN_RECTANGLE_WIDTH = 15;

        /**
         * Defines a minimum height for the selected area. If the selected
         * rectangle is not wider than this value, no zooming will take place.
         * This helps prevent accidental zooming.
         */
        private static final double MIN_RECTANGLE_HEIGHT = 15;

        @Override
        public void handle(final MouseEvent event) {

            // Hides the selection rectangle
            selectionRectangle.setVisible(false);

            // Cancel if the selection is null or if the rectangle is too small
            if (selectionRectangleStart == null || selectionRectangleEnd == null || isRectangleTooSmall()) {
                return;
            }

            // ZOOM!
            setAxisBounds();

            // Shows reset zoom level info
            infoLabel.setVisible(true);

            // Reset the rectangle with null values
            selectionRectangleStart = null;
            selectionRectangleEnd = null;

            // Needed for the key event handler to receive events
            chartPane.requestFocus();

            event.consume();
        }

        private boolean isRectangleTooSmall() {
            double width = Math.abs(selectionRectangleEnd.getX() - selectionRectangleStart.getX());
            double height = Math.abs(selectionRectangleEnd.getY() - selectionRectangleStart.getY());
            return width < MIN_RECTANGLE_WIDTH || height < MIN_RECTANGLE_HEIGHT;
        }

        private void setAxisBounds() {

            // Disable auto ranging
            xAxis.setAutoRanging(false);
            yAxis.setAutoRanging(false);

            // compute new bounds for the chart's x and y axes
            double selectionMinX = Math.min(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
            double selectionMaxX = Math.max(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
            double selectionMinY = Math.min(selectionRectangleStart.getY(), selectionRectangleEnd.getY());
            double selectionMaxY = Math.max(selectionRectangleStart.getY(), selectionRectangleEnd.getY());

            setHorizontalBounds(selectionMinX, selectionMaxX);
            setVerticalBounds(selectionMinY, selectionMaxY);

            isZoomed = true;
        }

        /**
         * Sets new bounds for the chart's x axis.
         *
         * @param minPixelPosition the x position of the selection rectangle's
         *                         left edge (in pixels)
         * @param maxPixelPosition the x position of the selection rectangle's
         *                         right edge (in pixels)
         */
        private void setHorizontalBounds(double minPixelPosition, double maxPixelPosition) {
            double currentLowerBound = xAxis.getLowerBound();
            double currentUpperBound = xAxis.getUpperBound();
            double offset = computeOffsetInChart(xAxis, false);
            setLowerBoundX(minPixelPosition, currentLowerBound, currentUpperBound, offset);
            setUpperBoundX(maxPixelPosition, currentLowerBound, currentUpperBound, offset);
        }

        /**
         * Sets new bounds for the chart's y axis.
         *
         * @param minPixelPosition the y position of the selection rectangle's
         *                         upper edge (in pixels)
         * @param maxPixelPosition the y position of the selection rectangle's
         *                         lower edge (in pixels)
         */
        private void setVerticalBounds(double minPixelPosition, double maxPixelPosition) {
            double currentLowerBound = yAxis.getLowerBound();
            double currentUpperBound = yAxis.getUpperBound();
            double offset = computeOffsetInChart(yAxis, true);
            setLowerBoundY(maxPixelPosition, currentLowerBound, currentUpperBound, offset);
            setUpperBoundY(minPixelPosition, currentLowerBound, currentUpperBound, offset);
        }

        private void setLowerBoundX(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                    double offset) {
            double newLowerBound = computeBound(pixelPosition, offset, xAxis.getWidth(), currentLowerBound,
                    currentUpperBound, false);
            xAxis.setLowerBound(newLowerBound);
        }

        private void setUpperBoundX(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                    double offset) {
            double newUpperBound = computeBound(pixelPosition, offset, xAxis.getWidth(), currentLowerBound,
                    currentUpperBound, false);
            xAxis.setUpperBound(newUpperBound);
        }

        private void setLowerBoundY(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                    double offset) {
            double newLowerBound = computeBound(pixelPosition, offset, yAxis.getHeight(), currentLowerBound,
                    currentUpperBound, true);
            yAxis.setLowerBound(newLowerBound);
        }

        private void setUpperBoundY(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                    double offset) {
            double newUpperBound = computeBound(pixelPosition, offset, yAxis.getHeight(), currentLowerBound,
                    currentUpperBound, true);
            yAxis.setUpperBound(newUpperBound);
        }

        private double computeBound(double pixelPosition, double pixelOffset, double pixelLength, double lowerBound,
                                    double upperBound, boolean axisInverted) {
            double pixelPositionWithoutOffset = pixelPosition - pixelOffset;
            double relativePosition = pixelPositionWithoutOffset / pixelLength;
            double axisLength = upperBound - lowerBound;

            // The screen's y axis grows from top to bottom, whereas the chart's y axis goes from bottom to top.
            // That's
            // why we need to have this distinction here.
            double offset;
            int sign;
            if (axisInverted) {
                offset = upperBound;
                sign = -1;
            } else {
                offset = lowerBound;
                sign = 1;
            }

            return offset + sign * relativePosition * axisLength;  // new bound
        }
    }

    private final class EscapeKeyHandler implements EventHandler<KeyEvent> {

        @Override
        public void handle(KeyEvent event) {
            // the ESCAPE key lets the user reset the zoom level
            if (KeyCode.ESCAPE.equals(event.getCode()) && isZoomed) {
                // Reset axis bounds - reset zoom level
                xAxis.setAutoRanging(true);
                yAxis.setAutoRanging(true);

                // Hide reset zoom level info label
                infoLabel.setVisible(false);

                isZoomed = false;
            }
        }

    }
}
