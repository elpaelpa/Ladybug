package org.ladybug.ladybugpaint;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public class ToolManager {

    // ================= TOOL ENUMERATION =================

    /**
     * All drawing tools available in the editor.
     */
    public enum Tool {
        BRUSH, ERASER, SMUDGE, LINE, RECTANGLE, CIRCLE, BUCKET, EYEDROPPER, SELECT, MOVE
    }

    // ================= CONSTANTS =================

    // Minimum allowed brush opacity (fully transparent)
    private static final double MINIMUM_BRUSH_OPACITY = 0.0;

    // Maximum allowed brush opacity (fully opaque)
    private static final double MAXIMUM_BRUSH_OPACITY = 1.0;

    // Minimum brush size that can be returned when the slider is unavailable
    private static final double FALLBACK_BRUSH_SIZE = 1.0;

    // ================= FIELDS =================

    // The currently active tool
    private Tool currentTool = Tool.BRUSH;

    // Slider that controls the width of the brush stroke
    private final Slider brushSizeSlider;

    // Slider that controls the transparency level of the brush stroke
    private final Slider brushOpacitySlider;

    // Color picker used to select the active drawing color
    private final ColorPicker colorPicker;

    // Toggle button for the brush tool
    private ToggleButton brushToggleButton;

    // Toggle button for the eraser tool
    private ToggleButton eraserToggleButton;

    // Toggle button for the smudge tool
    private ToggleButton smudgeToggleButton;

    // Toggle button for the line shape tool
    private ToggleButton lineToggleButton;

    // Toggle button for the rectangle shape tool
    private ToggleButton rectangleToggleButton;

    // Toggle button for the circle shape tool
    private ToggleButton circleToggleButton;

    // Toggle button for the flood-fill bucket tool
    private ToggleButton bucketToggleButton;

    // Toggle button for the eyedropper/color-sample tool
    private ToggleButton eyedropperToggleButton;

    // Toggle button for the selection tool
    private ToggleButton selectToggleButton;

    // Toggle button for the layer-move tool
    private ToggleButton moveToggleButton;

    // Canvas X coordinate recorded at the start of a drag or shape operation
    private double startX = 0;

    // Canvas Y coordinate recorded at the start of a drag or shape operation
    private double startY = 0;

    // ================= CONSTRUCTOR =================

    /**
     * Constructs the ToolManager and enforces the brush slider range.
     *
     * @param brushSizeSlider    slider controlling brush width; must not be null
     * @param brushOpacitySlider slider controlling brush opacity; must not be null
     * @param colorPicker        color picker for the active drawing color; must not be null
     * @param maximumBrushSize   the upper bound to apply to the brush size slider (>= 1)
     */
    public ToolManager(Slider brushSizeSlider, Slider brushOpacitySlider,
                       ColorPicker colorPicker, double maximumBrushSize) {
        // Validate and assign the brush size slider
        if (brushSizeSlider != null) {
            this.brushSizeSlider = brushSizeSlider;
        } else {
            this.brushSizeSlider = null;
        }

        // Validate and assign the opacity slider
        if (brushOpacitySlider != null) {
            this.brushOpacitySlider = brushOpacitySlider;
        } else {
            this.brushOpacitySlider = null;
        }

        // Validate and assign the color picker
        if (colorPicker != null) {
            this.colorPicker = colorPicker;
        } else {
            this.colorPicker = null;
        }

        // Apply the maximum brush size to the slider; must be at least 1
        if (this.brushSizeSlider != null) {
            if (maximumBrushSize >= 1) {
                this.brushSizeSlider.setMax(maximumBrushSize);
            } else {
                this.brushSizeSlider.setMax(1);
            }
        }
    }

    // ================= GETTERS =================

    /**
     * Returns the currently active tool.
     */
    public Tool getCurrentTool() {
        return currentTool;
    }

    /**
     * Returns the current brush size from the slider.
     * Returns the fallback size if the slider is unavailable.
     */
    public double getBrushSize() {
        if (brushSizeSlider != null) {
            return brushSizeSlider.getValue();
        }
        return FALLBACK_BRUSH_SIZE;
    }

    /**
     * Returns the current brush opacity from the slider.
     * Returns fully opaque if the slider is unavailable.
     */
    public double getBrushOpacity() {
        if (brushOpacitySlider != null) {
            return brushOpacitySlider.getValue();
        }
        return MAXIMUM_BRUSH_OPACITY;
    }

    /**
     * Returns the currently selected drawing color.
     * Returns black if the color picker is unavailable.
     */
    public Color getColor() {
        if (colorPicker != null) {
            return colorPicker.getValue();
        }
        return Color.BLACK;
    }

    /**
     * Returns the X canvas coordinate recorded at the start of the current operation.
     */
    public double getStartX() {
        return startX;
    }

    /**
     * Returns the Y canvas coordinate recorded at the start of the current operation.
     */
    public double getStartY() {
        return startY;
    }

    // ================= SETTERS =================

    /**
     * Records the canvas start position for shape or drag operations.
     * Negative coordinates are clamped to zero.
     *
     * @param newStartX starting X in canvas space
     * @param newStartY starting Y in canvas space
     */
    public void setStart(double newStartX, double newStartY) {
        if (newStartX >= 0) {
            this.startX = newStartX;
        } else {
            this.startX = 0;
        }

        if (newStartY >= 0) {
            this.startY = newStartY;
        } else {
            this.startY = 0;
        }
    }

    /**
     * Updates the active drawing color in the color picker.
     *
     * @param newColor the new color; must not be null
     */
    public void setColor(Color newColor) {
        if (colorPicker != null && newColor != null) {
            colorPicker.setValue(newColor);
        }
    }

    /**
     * Switches the active tool and updates the corresponding toggle button.
     *
     * @param newTool the tool to activate; must not be null
     */
    public void setTool(Tool newTool) {
        if (newTool == null) {
            return;
        }
        this.currentTool = newTool;
        switch (newTool) {
            case BRUSH      -> brushToggleButton.setSelected(true);
            case ERASER     -> eraserToggleButton.setSelected(true);
            case SMUDGE     -> smudgeToggleButton.setSelected(true);
            case LINE       -> lineToggleButton.setSelected(true);
            case RECTANGLE  -> rectangleToggleButton.setSelected(true);
            case CIRCLE     -> circleToggleButton.setSelected(true);
            case BUCKET     -> bucketToggleButton.setSelected(true);
            case EYEDROPPER -> eyedropperToggleButton.setSelected(true);
            case SELECT     -> selectToggleButton.setSelected(true);
            case MOVE       -> moveToggleButton.setSelected(true);
        }
    }

    // ================= TOOLBAR =================

    /**
     * Builds and returns the horizontal toolbar.
     * Import and export have been removed from the toolbar and placed in the bottom bar.
     *
     * @param undoAction  called when the undo button is pressed
     * @param redoAction  called when the redo button is pressed
     * @param zoomIn      called when the zoom-in button is pressed
     * @param zoomOut     called when the zoom-out button is pressed
     * @param zoomReset   called when the zoom-reset button is pressed
     */
    public HBox createToolbar(Runnable undoAction, Runnable redoAction,
                              Runnable zoomIn, Runnable zoomOut, Runnable zoomReset) {

        // All tool buttons share one ToggleGroup for mutual exclusion
        ToggleGroup toolToggleGroup = new ToggleGroup();

        brushToggleButton      = createToolToggleButton("Brush",      Tool.BRUSH,      toolToggleGroup);
        eraserToggleButton     = createToolToggleButton("Eraser",     Tool.ERASER,     toolToggleGroup);
        smudgeToggleButton     = createToolToggleButton("Smudge",     Tool.SMUDGE,     toolToggleGroup);
        lineToggleButton       = createToolToggleButton("Line",       Tool.LINE,       toolToggleGroup);
        rectangleToggleButton  = createToolToggleButton("Rectangle",  Tool.RECTANGLE,  toolToggleGroup);
        circleToggleButton     = createToolToggleButton("Circle",     Tool.CIRCLE,     toolToggleGroup);
        bucketToggleButton     = createToolToggleButton("Bucket",     Tool.BUCKET,     toolToggleGroup);
        eyedropperToggleButton = createToolToggleButton("Eyedropper", Tool.EYEDROPPER, toolToggleGroup);
        moveToggleButton       = createToolToggleButton("Move",       Tool.MOVE,       toolToggleGroup);

        // The select button reverts to Brush when pressed a second time while already active
        selectToggleButton = new ToggleButton("Select");
        selectToggleButton.setToggleGroup(toolToggleGroup);
        selectToggleButton.setOnAction(event -> {
            if (currentTool == Tool.SELECT && !selectToggleButton.isSelected()) {
                currentTool = Tool.BRUSH;
                brushToggleButton.setSelected(true);
            } else {
                currentTool = Tool.SELECT;
            }
        });

        // History buttons
        Button undoButton = new Button("Undo");
        Button redoButton = new Button("Redo");
        undoButton.setOnAction(event -> undoAction.run());
        redoButton.setOnAction(event -> redoAction.run());

        // Zoom control buttons
        Button zoomInButton    = new Button("+");
        Button zoomOutButton   = new Button("−");
        Button zoomResetButton = new Button("100%");
        zoomInButton.setOnAction(event -> zoomIn.run());
        zoomOutButton.setOnAction(event -> zoomOut.run());
        zoomResetButton.setOnAction(event -> zoomReset.run());
        zoomInButton.setPrefWidth(32);
        zoomOutButton.setPrefWidth(32);

        // Spacer so the zoom controls sit at the right end of the toolbar
        Region spacerRegion = new Region();
        HBox.setHgrow(spacerRegion, javafx.scene.layout.Priority.ALWAYS);

        HBox toolbar = new HBox(5,
                brushToggleButton, eraserToggleButton, smudgeToggleButton,
                lineToggleButton, rectangleToggleButton, circleToggleButton,
                bucketToggleButton, eyedropperToggleButton,
                selectToggleButton, moveToggleButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                undoButton, redoButton,
                spacerRegion,
                zoomOutButton, zoomResetButton, zoomInButton
        );
        toolbar.setStyle(
                "-fx-background-color: #18181e; " +
                        "-fx-padding: 8 12 8 12; " +
                        "-fx-border-color: transparent transparent #26242e transparent; " +
                        "-fx-border-width: 0 0 1 0;");
        return toolbar;
    }

    /**
     * Creates a single tool toggle button and registers it in the given toggle group.
     *
     * @param labelText       display text for the button
     * @param associatedTool  the tool this button activates
     * @param toolToggleGroup the shared mutual-exclusion group
     * @return the configured ToggleButton
     */
    private ToggleButton createToolToggleButton(String labelText, Tool associatedTool,
                                                ToggleGroup toolToggleGroup) {
        ToggleButton toggleButton = new ToggleButton(labelText);
        toggleButton.setToggleGroup(toolToggleGroup);

        // Pre-select Brush as the default active tool
        if (associatedTool == Tool.BRUSH) {
            toggleButton.setSelected(true);
        }

        toggleButton.setOnAction(event -> currentTool = associatedTool);
        return toggleButton;
    }

    // ================= KEYBOARD SHORTCUTS =================

    /**
     * Registers keyboard shortcuts for tool selection and undo/redo on the given scene.
     *
     * @param targetScene the scene to attach keyboard event handlers to
     * @param undoAction  called when the undo shortcut fires
     * @param redoAction  called when the redo shortcut fires
     */
    public void registerShortcuts(Scene targetScene, Runnable undoAction, Runnable redoAction) {
        targetScene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case I -> { currentTool = Tool.EYEDROPPER; eyedropperToggleButton.setSelected(true); }
                case B -> { currentTool = Tool.BRUSH;      brushToggleButton.setSelected(true); }
                case E -> { currentTool = Tool.ERASER;     eraserToggleButton.setSelected(true); }
                case S -> { currentTool = Tool.SMUDGE;     smudgeToggleButton.setSelected(true); }
                case V -> { currentTool = Tool.MOVE;       moveToggleButton.setSelected(true); }
                case M -> { currentTool = Tool.SELECT;     selectToggleButton.setSelected(true); }
                case Z -> {
                    if (event.isShiftDown()) {
                        redoAction.run();
                    } else {
                        undoAction.run();
                    }
                }
                default -> {}
            }
        });
    }
}