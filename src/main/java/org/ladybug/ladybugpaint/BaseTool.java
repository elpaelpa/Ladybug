package org.ladybug.ladybugpaint;
import javafx.scene.layout.StackPane;



//create child class in ToolManager constructor
public abstract class BaseTool {
    protected StackPane canvasStack;
    protected String toolName = null;
    protected ToolManager toolManager;

    public BaseTool(String toolName, StackPane canvasStack,ToolManager toolManager){
        this.toolName= toolName;
        this.canvasStack = canvasStack;
        this.toolManager = toolManager;
    }

    public abstract void startStroke(Layer activeLayer,double x,double y);
    public abstract void drawStroke(Layer activeLayer,double x,double y);
    public abstract void endStroke(Layer activeLayer, double x, double y);




}
