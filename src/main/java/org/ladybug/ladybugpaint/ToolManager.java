package org.ladybug.ladybugpaint;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class ToolManager {

    public enum Tool {
        BRUSH, ERASER, SMUDGE, LINE, RECTANGLE, CIRCLE, BUCKET, EYEDROPPER, SELECT, MOVE
    }

    private Tool currentTool = Tool.BRUSH;

    private final Slider      brushSize;
    private final Slider      brushOpacity;
    private final ColorPicker colorPicker;

    private ToggleButton brushBtn, eraserBtn, smudgeBtn,
            lineBtn, rectBtn, circleBtn, bucketBtn, eyeBtn, selectBtn, moveBtn;

    private double startX, startY;

    public ToolManager(Slider brushSize, Slider brushOpacity, ColorPicker colorPicker) {
        this.brushSize    = brushSize;
        this.brushOpacity = brushOpacity;
        this.colorPicker  = colorPicker;
    }

    public Tool   getCurrentTool()  { return currentTool; }
    public double getBrushSize()    { return brushSize.getValue(); }
    public double getBrushOpacity() { return brushOpacity.getValue(); }
    public Color  getColor()        { return colorPicker.getValue(); }
    public double getStartX()       { return startX; }
    public double getStartY()       { return startY; }

    public void setStart(double x, double y) { this.startX = x; this.startY = y; }

    // ================= TOOLBAR =================

    public HBox createToolbar(Runnable undoAction, Runnable redoAction,
                              Runnable exportAction, Runnable importAction,
                              Runnable zoomIn, Runnable zoomOut, Runnable zoomReset) {

        ToggleGroup tools = new ToggleGroup();

        brushBtn   = createTool("Brush",      Tool.BRUSH,      tools);
        eraserBtn  = createTool("Eraser",     Tool.ERASER,     tools);
        smudgeBtn  = createTool("Smudge",     Tool.SMUDGE,     tools);
        lineBtn    = createTool("Line",       Tool.LINE,       tools);
        rectBtn    = createTool("Rectangle",  Tool.RECTANGLE,  tools);
        circleBtn  = createTool("Circle",     Tool.CIRCLE,     tools);
        bucketBtn  = createTool("Bucket",     Tool.BUCKET,     tools);
        eyeBtn     = createTool("Eyedropper", Tool.EYEDROPPER, tools);
        moveBtn    = createTool("Move",       Tool.MOVE,       tools);

        // Select toggles off when pressed again
        selectBtn = new ToggleButton("Select");
        selectBtn.setToggleGroup(tools);
        selectBtn.setOnAction(e -> {
            if (currentTool == Tool.SELECT && !selectBtn.isSelected()) {
                currentTool = Tool.BRUSH;
                brushBtn.setSelected(true);
            } else {
                currentTool = Tool.SELECT;
            }
        });

        // Action buttons
        Button importBtn = new Button("Import");
        Button undoBtn   = new Button("Undo");
        Button redoBtn   = new Button("Redo");
        Button exportBtn = new Button("Export PNG");

        importBtn.setOnAction(e -> importAction.run());
        undoBtn.setOnAction(e -> undoAction.run());
        redoBtn.setOnAction(e -> redoAction.run());
        exportBtn.setOnAction(e -> exportAction.run());

        // Zoom buttons
        Button zoomInBtn    = new Button("+");
        Button zoomOutBtn   = new Button("−");
        Button zoomResetBtn = new Button("100%");

        zoomInBtn.setOnAction(e -> zoomIn.run());
        zoomOutBtn.setOnAction(e -> zoomOut.run());
        zoomResetBtn.setOnAction(e -> zoomReset.run());

        zoomInBtn.setPrefWidth(32);
        zoomOutBtn.setPrefWidth(32);

        return new HBox(6,
                brushBtn, eraserBtn, smudgeBtn,
                lineBtn, rectBtn, circleBtn,
                bucketBtn, eyeBtn,
                selectBtn, moveBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                importBtn, undoBtn, redoBtn, exportBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                zoomOutBtn, zoomResetBtn, zoomInBtn
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
            switch (e.getCode()) {
                case I -> { currentTool = Tool.EYEDROPPER; eyeBtn.setSelected(true); }
                case B -> { currentTool = Tool.BRUSH;      brushBtn.setSelected(true); }
                case E -> { currentTool = Tool.ERASER;     eraserBtn.setSelected(true); }
                case S -> { currentTool = Tool.SMUDGE;     smudgeBtn.setSelected(true); }
                case V -> { currentTool = Tool.MOVE;       moveBtn.setSelected(true); }
                case M -> { currentTool = Tool.SELECT;     selectBtn.setSelected(true); }
                case Z -> {
                    if (e.isShiftDown()) redoAction.run();
                    else undoAction.run();
                }
                default -> {}
            }
        });
    }

    public void setColor(Color color) { colorPicker.setValue(color); }

    public void setTool(Tool tool) {
        this.currentTool = tool;
        switch (tool) {
            case BRUSH      -> brushBtn.setSelected(true);
            case ERASER     -> eraserBtn.setSelected(true);
            case SMUDGE     -> smudgeBtn.setSelected(true);
            case LINE       -> lineBtn.setSelected(true);
            case RECTANGLE  -> rectBtn.setSelected(true);
            case CIRCLE     -> circleBtn.setSelected(true);
            case BUCKET     -> bucketBtn.setSelected(true);
            case EYEDROPPER -> eyeBtn.setSelected(true);
            case SELECT     -> selectBtn.setSelected(true);
            case MOVE       -> moveBtn.setSelected(true);
        }
    }
}