package org.ladybug.ladybugpaint;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public class EraserTool extends BaseTool {
    public EraserTool(String name, StackPane canvasStackPane, ToolManager toolManager) {
        super(name,canvasStackPane,toolManager);
    }

    @Override
    public void startStroke(Layer activeLayer, double x, double y) {

    }

    @Override
    public void drawStroke(Layer activeLayer, double x, double y) {

    }

    @Override
    public void endStroke(Layer activeLayer, double x, double y) {

    }


}
