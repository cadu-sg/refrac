package com.botoseis.chart.utils;

import javafx.scene.Node;
import javafx.scene.chart.XYChart;

public class SeriesLayout {
    private final XYChart.Series<Number, Number> series;

    private String symbolColor;
    private Double symbolSize;
    private String symbolShape;

    /**
     * This API should only be used after adding the series to the chart;
     *
     * @param series series to be stylized
     */
    public SeriesLayout(XYChart.Series<Number, Number> series) {
        this.series = series;
    }

    public void setSymbolsStyle(String color, String shape, double size) {
        String styleString = getSymbolStyleString(color, shape);
        series.getData().forEach(data -> {
            Node dataNode = data.getNode();
            dataNode.setStyle(styleString);
            dataNode.setScaleX(size);
            dataNode.setScaleY(size);
        });
    }

    public void setSymbolsColorShape(String color, String shape) {
        String styleString = getSymbolStyleString(color, shape);
        series.getData().forEach(data -> {
            Node dataNode = data.getNode();
            dataNode.setStyle(styleString);
        });
    }

    public void setSymbolsColorSize(String color, double size) {
        String styleString = getColorSymbolStyleString(color);
        series.getData().forEach(data -> {
            Node dataNode = data.getNode();
            dataNode.setStyle(styleString);
            dataNode.setScaleX(size);
            dataNode.setScaleY(size);
        });
    }

    public void setSymbolsShapeSize(String shape, double size) {
        String styleString = getShapeSymbolStyleString(shape);
        series.getData().forEach(data -> {
            Node dataNode = data.getNode();
            dataNode.setStyle(styleString);
            dataNode.setScaleX(size);
            dataNode.setScaleY(size);
        });
    }

    public static void setDataStyle(XYChart.Data<Number, Number> data, String color, String shape) {
        data.getNode().setStyle(getSymbolStyleString(color, shape));
    }

    public void setSymbolsColor(String color) {
        symbolColor = color;

        if (symbolSize != null && symbolShape != null) {

            setSymbolsStyle(symbolColor, symbolShape, symbolSize);

        } else if (symbolShape != null && symbolSize == null) {

            setSymbolsColorShape(symbolColor, symbolShape);

        } else if (symbolShape == null && symbolSize != null) {

            setSymbolsColorSize(symbolColor, symbolSize);

        } else {
            String styleString = getColorSymbolStyleString(color);
            series.getData().forEach(data -> {
                data.getNode().setStyle(styleString);
            });

        }
    }

    public void setSymbolsShape(String shape) {
        symbolShape = shape;

        if (symbolColor != null && symbolSize != null) {

            setSymbolsStyle(symbolColor, symbolShape, symbolSize);

        } else if (symbolColor != null && symbolSize == null) {

            setSymbolsColorShape(symbolColor, symbolShape);

        } else if (symbolColor == null && symbolSize != null) {

            setSymbolsShapeSize(symbolShape, symbolSize);

        } else {
            String styleString = getShapeSymbolStyleString(symbolShape);
            series.getData().forEach(data -> {
                data.getNode().setStyle(styleString);
            });

        }
    }

    public void setSymbolsSize(double size) {
        symbolSize = size;

        if (symbolColor != null && symbolShape != null) {

            setSymbolsStyle(symbolColor, symbolShape, symbolSize);

        } else if (symbolColor != null && symbolShape == null) {

            setSymbolsColorSize(symbolColor, symbolSize);

        } else if (symbolColor == null && symbolShape != null) {

            setSymbolsShapeSize(symbolShape, symbolSize);

        } else {

            series.getData().forEach(data -> {
                Node dataNode = data.getNode();
                dataNode.setScaleX(symbolSize);
                dataNode.setScaleY(symbolSize);
            });

        }
    }

    private static String getSymbolStyleString(String color, String shape) {
        if (shape.charAt(0) == 'h') {
            return getColorSymbolStyleString(color + ", white") + getShapeSymbolStyleString(shape);
        } else {
            return getColorSymbolStyleString(color) + getShapeSymbolStyleString(shape);
        }
    }

    private static String getColorSymbolStyleString(String color) {
        return "-fx-background-color: " + color + ";";
    }

    private static String getShapeSymbolStyleString(String shape) {
        switch (shape) {
            case "^":
                return "-fx-shape: \"M5,0 L10,8 L0,8 Z\";";
            case "s":
                return "-fx-background-radius: 0;\n"
                        + "-fx-background-insets: 0.2";
            case "hs":
                return "-fx-background-radius: 0;\n"
                        + "-fx-background-insets: 0.2, 1.8;";
            case "D":
                return "-fx-shape: \"M5,0 L10,9 L5,18 L0,9 Z\";\n";
            case "hD":
                return "-fx-shape: \"M5,0 L10,9 L5,18 L0,9 Z\";\n"
                        + "-fx-background-insets: -0.9, 1;";
            default:
                return "";
        }
    }

    public void setLineStyle(String width, String color) {
        series.getNode().setStyle("-fx-stroke-width: " + width + ";" // line width
                + "-fx-stroke: " + color + ";"); // line color
    }

    public void setLineColor(String color) {
        series.getNode().setStyle("-fx-stroke: " + color + ";");
    }

    public void setSymbolsVisible(boolean value) {
        series.getData().forEach(data ->
                data.getNode().setVisible(value)
        );
    }

    public void setLineVisible(boolean value) {
        series.getNode().setVisible(value);
    }
}
