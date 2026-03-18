package org.ladybug.ladybugpaint;
import javafx.scene.layout.StackPane;



//create child class in ToolManager constructor
public abstract class BaseTool {
    public StackPane canvasStack;
    public String toolName = null;
    public ToolManager toolManager;

    public BaseTool(String toolName, StackPane canvasStack,ToolManager toolManager){
        this.toolName= toolName;
        this.canvasStack = canvasStack;
        this.toolManager = toolManager;
    }
    public abstract void startStroke(Layer activeLayer,double x,double y);
    public abstract void drawStroke(Layer activeLayer,double x,double y);
    public abstract void endStroke(Layer activeLayer, double x, double y);

}
