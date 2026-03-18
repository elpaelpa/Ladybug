package org.ladybug.ladybugpaint;

import javafx.geometry.Point2D;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public class BrushTool extends BaseTool {



    public BrushTool(String brush, StackPane canvasStackPane, ToolManager toolManager) {
        super(brush,canvasStackPane,toolManager);
    }

    @Override
    public void startStroke(Layer activeLayer, double x, double y) {
        if (activeLayer == null) return;

        activeLayer.gc.setStroke(toolManager.getBrushColor());
        activeLayer.gc.setLineWidth(Math.max(1, toolManager.getBrushSize()));

//      activeLayer.gc.setGlobalAlpha(toolManager.getBrushOpacity());
        activeLayer.gc.setGlobalAlpha(1.0);//WHYYYYYYY

        activeLayer.gc.setLineCap(StrokeLineCap.ROUND);
        activeLayer.gc.setLineJoin(StrokeLineJoin.ROUND);

        activeLayer.gc.beginPath();
        activeLayer.gc.moveTo(x, y);

    }

    @Override
    public void drawStroke(Layer activeLayer, double x, double y) {
        if (activeLayer == null) return;

        activeLayer.gc.lineTo(x, y);
        activeLayer.gc.stroke();
    }

    @Override
    public void endStroke(Layer activeLayer, double x, double y) {

        activeLayer.gc.lineTo(x, y);
        activeLayer.gc.stroke();  // finish the stroke
        activeLayer.gc.closePath();
    }
}
