package org.ladybug.ladybugpaint;

import javafx.geometry.Point2D;
import javafx.scene.layout.StackPane;

public class CircleTool extends BaseTool {
    public CircleTool(String toolName, StackPane canvasStackPane, ToolManager toolManager) {
        super(toolName,canvasStackPane,toolManager);
    }

    private Point2D
            startPoint = null,
            endPoint = null;


    @Override
    public void startStroke(Layer activeLayer, double x, double y) {
        startPoint = new Point2D(x,y);
        activeLayer.gc.setStroke(toolManager.getBrushColor());
        activeLayer.gc.setLineWidth(Math.max(1, toolManager.getBrushSize()));
    }

    @Override
    public void drawStroke(Layer activeLayer, double x, double y) {

    }

    @Override
    public void endStroke(Layer activeLayer, double x, double y) {
        endPoint = new Point2D(x,y);
        activeLayer.gc.strokeOval(

                Math.min(startPoint.getX(), endPoint.getX()),
                Math.min(startPoint.getY(), endPoint.getY()),

                Math.abs(startPoint.getX() - endPoint.getX()),
                Math.abs(startPoint.getY() - endPoint.getY()));
    }
}
