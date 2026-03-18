package org.ladybug.ladybugpaint;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;

public class ToolManager {

    private final List<BaseTool> tools = new ArrayList<>();
    private BaseTool currentTool;

    //Shared brush fields (probably gonna move this to  Tool Interface)
    private Color brushColor;
    private double brushOpacity;
    private double brushSize;

    public ToolManager(StackPane canvasStackPane) {
        //Adds tools here
        tools.add(new BrushTool("Brush",canvasStackPane,this));
        tools.add(new EraserTool("Eraser",canvasStackPane,this));
        tools.add(new RectangleTool("Rectangle",canvasStackPane,this));
        tools.add(new CircleTool("Circle",canvasStackPane,this));

        tools.add(new BucketTool("Bucket",canvasStackPane,this));

        this.currentTool = tools.get(0); //Starting Tool
    }
    public void setCurrentTool(BaseTool currentTool) {
        this.currentTool = currentTool;
    }
    public void setBrushColor(Color brushColor) {
        this.brushColor = brushColor;
    }
    public void setBrushOpacity(double brushOpacity) {
        this.brushOpacity = brushOpacity;
    }
    public void setBrushSize(double brushSize) {
        this.brushSize = brushSize;
    }
    public BaseTool getCurrentTool() {
        return currentTool;
    }
    public Color getBrushColor() {
        return brushColor;
    }
    public double getBrushOpacity() {
        return brushOpacity;
    }
    public double getBrushSize() {
        return brushSize;
    }
    public List<BaseTool> getTools() {
        return tools;
    }
}