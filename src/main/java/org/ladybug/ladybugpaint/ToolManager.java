package org.ladybug.ladybugpaint;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class ToolManager {



    // ================= TOOL ENUM =================
    public enum Tool {
        BRUSH, ERASER, SMUDGE, LINE, RECTANGLE, CIRCLE, BUCKET, EYEDROPPER, SELECT, MOVE
    }

    private Tool currentTool = Tool.BRUSH;

    // ================= UI CONTROLS =================
    private final Slider brushSize;
    private final Slider brushOpacity;
    private final ColorPicker colorPicker;

    private ToggleButton brushBtn, eraserBtn, smudgeBtn,
            lineBtn, rectBtn, circleBtn, bucketBtn, eyeBtn, selectBtn, moveBtn;

    // ================= STATE =================
    private double startX, startY;

    // ================= CONSTRUCTOR =================
    public ToolManager(Slider brushSize, Slider brushOpacity, ColorPicker colorPicker) {
        this.brushSize = brushSize;
        this.brushOpacity = brushOpacity;
        this.colorPicker = colorPicker;
    }

    // ================= GETTERS =================
    public Tool getCurrentTool() {
        return currentTool;
    }

    public double getBrushSize() {
        return brushSize.getValue();
    }

    public double getBrushOpacity() {
        return brushOpacity.getValue();
    }

    public javafx.scene.paint.Color getColor() {
        return colorPicker.getValue();
    }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }

    public void setStart(double x, double y) {
        this.startX = x;
        this.startY = y;
    }

    // ================= TOOLBAR =================
    public HBox createToolbar(Runnable undoAction, Runnable redoAction, Runnable exportAction) {

        ToggleGroup tools = new ToggleGroup();

        brushBtn = createTool("Brush", Tool.BRUSH, tools);
        eraserBtn = createTool("Eraser", Tool.ERASER, tools);
        smudgeBtn = createTool("Smudge", Tool.SMUDGE, tools);
        lineBtn = createTool("Line", Tool.LINE, tools);
        rectBtn = createTool("Rectangle", Tool.RECTANGLE, tools);
        circleBtn = createTool("Circle", Tool.CIRCLE, tools);
        bucketBtn = createTool("Bucket", Tool.BUCKET, tools);
        eyeBtn = createTool("Eyedropper", Tool.EYEDROPPER, tools);
        selectBtn = createTool("Select", Tool.SELECT, tools);
        moveBtn = createTool("Move", Tool.MOVE, tools);

        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        Button exportBtn = new Button("Export PNG");

        undoBtn.setOnAction(e -> undoAction.run());
        redoBtn.setOnAction(e -> redoAction.run());
        exportBtn.setOnAction(e -> exportAction.run());

        return new HBox(8,
                selectBtn, moveBtn,
                brushBtn, eraserBtn, smudgeBtn,
                lineBtn, rectBtn, circleBtn,
                bucketBtn, eyeBtn,
                undoBtn, redoBtn, exportBtn
        );
    }

    private ToggleButton createTool(String name, Tool tool, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(name);
        btn.setToggleGroup(group);

        if (tool == Tool.BRUSH) btn.setSelected(true);

        btn.setOnAction(e -> currentTool = tool);

        return btn;
    }

    // ================= SHORTCUTS =================
    public void registerShortcuts(Scene scene, Runnable undoAction, Runnable redoAction) {

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.I) {
                currentTool = Tool.EYEDROPPER;
                eyeBtn.setSelected(true);
            }
            else if (e.getCode() == KeyCode.B) {
                currentTool = Tool.BRUSH;
                brushBtn.setSelected(true);
            }
            else if (e.getCode() == KeyCode.E) {
                currentTool = Tool.ERASER;
                eraserBtn.setSelected(true);
            }
            else if (e.getCode() == KeyCode.S) {
                currentTool = Tool.SMUDGE;
                smudgeBtn.setSelected(true);
            }
            else if (e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) redoAction.run();
                else undoAction.run();
            }
            else if (e.getCode() == KeyCode.V) {
                currentTool = Tool.MOVE;
                moveBtn.setSelected(true);
            }
            else if (e.getCode() == KeyCode.M) {
                currentTool = Tool.SELECT;
                selectBtn.setSelected(true);
            }
        });
    }
    public void setColor(javafx.scene.paint.Color color) {
        colorPicker.setValue(color);
    }
    public void setTool(Tool tool) {
        this.currentTool = tool;

        // Sync UI buttons
        switch (tool) {
            case BRUSH -> brushBtn.setSelected(true);
            case ERASER -> eraserBtn.setSelected(true);
            case SMUDGE -> smudgeBtn.setSelected(true);
            case LINE -> lineBtn.setSelected(true);
            case RECTANGLE -> rectBtn.setSelected(true);
            case CIRCLE -> circleBtn.setSelected(true);
            case BUCKET -> bucketBtn.setSelected(true);
            case EYEDROPPER -> eyeBtn.setSelected(true);
            case SELECT -> selectBtn.setSelected(true);
            case MOVE -> moveBtn.setSelected(true);
        }
    }
}