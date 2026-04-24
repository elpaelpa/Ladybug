package org.ladybug.ladybugpaint;

import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class DrawingManager {

    // ================= FIELDS =================

    // Provides access to the canvas stack and temp drawing surface
    private final CanvasManager canvasManager;

    // Provides access to the active layer and undo/redo history
    private final LayerManager layerManager;

    // Provides access to the current tool, brush settings, and color
    private final ToolManager toolManager;

    // Provides access to the selection region and clipping utilities
    private final SelectionManager selectionManager;

    // Circular pixel sample carried forward between smudge drag steps
    private WritableImage smudgeBrush = null;

    // When true, Rectangle and Circle shapes are filled in addition to being stroked
    private boolean autoFillEnabled = false;

    // ================= CONSTRUCTOR =================

    /**
     * Constructs the DrawingManager with the required manager dependencies.
     * All parameters must be non-null.
     *
     * @param canvasManager    manages the canvas surfaces and zoom
     * @param layerManager     manages layers and pixel history
     * @param toolManager      manages the active tool and brush settings
     * @param selectionManager manages the active selection region
     */
    public DrawingManager(CanvasManager canvasManager, LayerManager layerManager,
                          ToolManager toolManager, SelectionManager selectionManager) {
        if (canvasManager != null) {
            this.canvasManager = canvasManager;
        } else {
            this.canvasManager = null;
        }

        if (layerManager != null) {
            this.layerManager = layerManager;
        } else {
            this.layerManager = null;
        }

        if (toolManager != null) {
            this.toolManager = toolManager;
        } else {
            this.toolManager = null;
        }

        if (selectionManager != null) {
            this.selectionManager = selectionManager;
        } else {
            this.selectionManager = null;
        }
    }

    // ================= GETTERS / SETTERS =================

    /**
     * Returns true when auto-fill is enabled for shape tools.
     */
    public boolean isAutoFillEnabled() {
        return autoFillEnabled;
    }

    /**
     * Enables or disables automatic interior fill for Rectangle and Circle tools.
     *
     * @param newAutoFillEnabled true to fill shapes, false for outline only
     */
    public void setAutoFillEnabled(boolean newAutoFillEnabled) {
        this.autoFillEnabled = newAutoFillEnabled;
    }

    // ================= MOUSE EVENT ATTACHMENT =================

    /**
     * Attaches mouse pressed, dragged, and released handlers to the canvas stack.
     * All drawing, erasing, selection, and move operations are dispatched here.
     */
    public void attachMouseEvents() {
        var canvasStack = canvasManager.getCanvasStack();

        canvasStack.setOnMousePressed(event -> {
            if (layerManager.getActiveLayer() == null) {
                return;
            }

            // Convert scene-space mouse position to canvas-local coordinates
            var localPoint = layerManager.getActiveLayer().canvas.sceneToLocal(
                    event.getSceneX(), event.getSceneY());
            double localX = localPoint.getX();
            double localY = localPoint.getY();

            toolManager.setStart(localX, localY);

            // Selection tool: start move if inside selection, otherwise start new selection
            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.hasSelection() && selectionManager.isClickInside(localX, localY)) {
                    selectionManager.startMove(localX, localY);
                } else {
                    selectionManager.startSelection(localX, localY);
                }
                return;
            }

            // Move tool: always delegate to the selection manager
            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.startMove(localX, localY);
                return;
            }

            // Dispatch all other tools
            switch (toolManager.getCurrentTool()) {
                case EYEDROPPER -> handleEyedropper(localX, localY);
                case BUCKET -> {
                    layerManager.saveState(layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    floodFill((int) localX, (int) localY);
                }
                case ERASER -> {
                    layerManager.saveState(layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    eraseCircle(localX, localY);
                }
                case SMUDGE -> startSmudge(localX, localY);
                case BRUSH -> {
                    layerManager.saveState(layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    startBrush(localX, localY);
                }
                case LINE, RECTANGLE, CIRCLE -> startDraw(localX, localY);
            }
        });

        canvasStack.setOnMouseDragged(event -> {
            if (layerManager.getActiveLayer() == null) {
                return;
            }

            var localPoint = layerManager.getActiveLayer().canvas.sceneToLocal(
                    event.getSceneX(), event.getSceneY());
            double localX = localPoint.getX();
            double localY = localPoint.getY();

            // Selection drag: extend selection or move it
            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.hasSelection() && !selectionManager.isCreatingNewSelection()) {
                    selectionManager.updateMove(localX, localY);
                } else {
                    selectionManager.updateSelection(localX, localY);
                }
                return;
            }

            // Move drag: move selection content or the full layer
            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                if (selectionManager.hasSelection()) {
                    selectionManager.updateMove(localX, localY);
                } else {
                    selectionManager.updateLayerMove(localX, localY);
                }
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case BRUSH                   -> drawBrush(localX, localY);
                case LINE, RECTANGLE, CIRCLE -> drawShape(localX, localY);
                case ERASER                  -> eraseCircle(localX, localY);
                case SMUDGE                  -> dragSmudge(localX, localY);
            }
        });

        canvasStack.setOnMouseReleased(event -> {
            if (layerManager.getActiveLayer() == null) {
                return;
            }

            // Finalize or end selection operations
            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.isCreatingNewSelection()) {
                    selectionManager.finalizeSelection();
                } else {
                    selectionManager.endMove();
                }
                return;
            }

            // End move operation
            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.endMove();
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case LINE, RECTANGLE, CIRCLE -> commitTempDrawing();
                case BRUSH -> selectionManager.restoreClipping(layerManager.getActiveLayer().gc);
            }
        });
    }

    // ================= BRUSH =================

    /**
     * Begins a fresh brush stroke at the given canvas position.
     *
     * A new path is always started here with beginPath() + moveTo() so that
     * no connection is ever drawn from a previous operation (such as an
     * eyedropper click) to the first point of this stroke.
     *
     * @param canvasX starting X in canvas coordinates
     * @param canvasY starting Y in canvas coordinates
     */
    private void startBrush(double canvasX, double canvasY) {
        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;

        // Clip the stroke to the selection boundary if a selection is active
        selectionManager.applyClipping(graphicsContext);

        // beginPath() discards any path state left over from a previous tool
        // (e.g. the implicit moveTo recorded during an eyedropper click),
        // preventing a ghost line from that position to this stroke's start.
        graphicsContext.beginPath();
        graphicsContext.setStroke(toolManager.getColor());
        graphicsContext.setLineWidth(toolManager.getBrushSize());
        graphicsContext.setGlobalAlpha(toolManager.getBrushOpacity());

        // moveTo then lineTo the same point so a single click draws a visible dot
        graphicsContext.moveTo(canvasX, canvasY);
        graphicsContext.lineTo(canvasX, canvasY);
        graphicsContext.stroke();
    }

    /**
     * Extends the current brush stroke to the given canvas position.
     *
     * @param canvasX current X in canvas coordinates
     * @param canvasY current Y in canvas coordinates
     */
    private void drawBrush(double canvasX, double canvasY) {
        layerManager.getActiveLayer().gc.lineTo(canvasX, canvasY);
        layerManager.getActiveLayer().gc.stroke();
    }

    // ================= SHAPES =================

    /**
     * Prepares the temp canvas for a new shape preview and saves an undo state.
     *
     * @param canvasX unused start X (recorded by toolManager.setStart before this call)
     * @param canvasY unused start Y
     */
    private void startDraw(double canvasX, double canvasY) {
        layerManager.saveState(layerManager.getActiveLayer(),
                canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
        canvasManager.clearTemp();
        canvasManager.setTempOpacity(1.0);
    }

    /**
     * Redraws the shape preview on the temp canvas during a drag.
     * When auto-fill is enabled, Rectangle and Circle shapes are also filled.
     *
     * @param canvasX current drag X in canvas coordinates
     * @param canvasY current drag Y in canvas coordinates
     */
    private void drawShape(double canvasX, double canvasY) {
        GraphicsContext graphicsContext = canvasManager.getTempGc();
        canvasManager.clearTemp();

        double shapeStartX = toolManager.getStartX();
        double shapeStartY = toolManager.getStartY();

        // Bounding box for rectangle and oval shapes
        double boundingBoxX      = Math.min(shapeStartX, canvasX);
        double boundingBoxY      = Math.min(shapeStartY, canvasY);
        double boundingBoxWidth  = Math.abs(canvasX - shapeStartX);
        double boundingBoxHeight = Math.abs(canvasY - shapeStartY);

        graphicsContext.setStroke(toolManager.getColor());
        graphicsContext.setFill(toolManager.getColor());
        graphicsContext.setLineWidth(toolManager.getBrushSize());

        // Draw the shape, optionally filling its interior
        switch (toolManager.getCurrentTool()) {
            case LINE -> {
                graphicsContext.strokeLine(shapeStartX, shapeStartY, canvasX, canvasY);
            }
            case RECTANGLE -> {
                if (autoFillEnabled) {
                    graphicsContext.fillRect(
                            boundingBoxX, boundingBoxY, boundingBoxWidth, boundingBoxHeight);
                }
                graphicsContext.strokeRect(
                        boundingBoxX, boundingBoxY, boundingBoxWidth, boundingBoxHeight);
            }
            case CIRCLE -> {
                if (autoFillEnabled) {
                    graphicsContext.fillOval(
                            boundingBoxX, boundingBoxY, boundingBoxWidth, boundingBoxHeight);
                }
                graphicsContext.strokeOval(
                        boundingBoxX, boundingBoxY, boundingBoxWidth, boundingBoxHeight);
            }
        }
    }

    /**
     * Stamps the temp canvas preview onto the active layer at the active brush opacity.
     * Applies and restores the selection clip if one exists.
     */
    private void commitTempDrawing() {
        WritableImage tempSnapshot = canvasManager.snapshot(canvasManager.getTempCanvas());
        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;

        graphicsContext.setGlobalAlpha(toolManager.getBrushOpacity());
        selectionManager.applyClipping(graphicsContext);
        graphicsContext.drawImage(tempSnapshot, 0, 0);
        selectionManager.restoreClipping(graphicsContext);
        graphicsContext.setGlobalAlpha(1.0);

        canvasManager.clearTemp();
    }

    // ================= CIRCULAR ERASER =================

    /**
     * Erases a circular region of pixels at the given canvas position.
     * Uses pixel-writer iteration because JavaFX clearRect ignores clip paths.
     *
     * @param canvasX center X of the erase circle in canvas coordinates
     * @param canvasY center Y of the erase circle in canvas coordinates
     */
    private void eraseCircle(double canvasX, double canvasY) {
        double brushSize   = toolManager.getBrushSize();
        double brushRadius = brushSize / 2.0;

        int totalCanvasWidth  = (int) canvasManager.getCanvasWidth();
        int totalCanvasHeight = (int) canvasManager.getCanvasHeight();

        // Bounding box clamped to canvas bounds
        int boundingBoxLeft   = (int) Math.max(0, canvasX - brushRadius - 1);
        int boundingBoxTop    = (int) Math.max(0, canvasY - brushRadius - 1);
        int boundingBoxWidth  = (int) Math.min(totalCanvasWidth  - boundingBoxLeft, brushSize + 2);
        int boundingBoxHeight = (int) Math.min(totalCanvasHeight - boundingBoxTop,  brushSize + 2);

        if (boundingBoxWidth <= 0 || boundingBoxHeight <= 0) {
            return;
        }

        // Snapshot only the bounding box for performance
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        snapshotParameters.setViewport(new Rectangle2D(
                boundingBoxLeft, boundingBoxTop, boundingBoxWidth, boundingBoxHeight));

        WritableImage patchImage = layerManager.getActiveLayer().canvas.snapshot(
                snapshotParameters, null);

        var pixelWriter  = patchImage.getPixelWriter();
        double radiusSquared = brushRadius * brushRadius;

        // Set every pixel inside the circle radius to transparent
        for (int pixelRow = 0; pixelRow < boundingBoxHeight; pixelRow++) {
            for (int pixelColumn = 0; pixelColumn < boundingBoxWidth; pixelColumn++) {
                double deltaX = (boundingBoxLeft + pixelColumn) - canvasX;
                double deltaY = (boundingBoxTop  + pixelRow)    - canvasY;
                if (deltaX * deltaX + deltaY * deltaY <= radiusSquared) {
                    pixelWriter.setColor(pixelColumn, pixelRow, Color.TRANSPARENT);
                }
            }
        }

        // Redraw the modified patch back onto the layer
        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;
        graphicsContext.clearRect(boundingBoxLeft, boundingBoxTop, boundingBoxWidth, boundingBoxHeight);
        graphicsContext.drawImage(patchImage, boundingBoxLeft, boundingBoxTop);
    }

    // ================= CIRCULAR SMUDGE =================

    /**
     * Begins a smudge operation by sampling the brush region at the start position.
     *
     * @param canvasX center X of the initial sample in canvas coordinates
     * @param canvasY center Y of the initial sample in canvas coordinates
     */
    private void startSmudge(double canvasX, double canvasY) {
        layerManager.saveState(layerManager.getActiveLayer(),
                canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
        smudgeBrush = sampleCircularRegion(canvasX, canvasY);
    }

    /**
     * Continues the smudge by painting the sampled brush at the new position,
     * then re-samples to pick up freshly painted pixels.
     *
     * @param canvasX current drag X in canvas coordinates
     * @param canvasY current drag Y in canvas coordinates
     */
    private void dragSmudge(double canvasX, double canvasY) {
        if (smudgeBrush == null) {
            return;
        }

        double brushRadius = toolManager.getBrushSize() / 2.0;

        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;
        selectionManager.applyClipping(graphicsContext);
        graphicsContext.setGlobalAlpha(0.35);
        graphicsContext.drawImage(smudgeBrush, canvasX - brushRadius, canvasY - brushRadius);
        graphicsContext.setGlobalAlpha(1.0);
        selectionManager.restoreClipping(graphicsContext);

        // Re-sample so the trail picks up its own freshly painted pixels
        smudgeBrush = sampleCircularRegion(canvasX, canvasY);
    }

    /**
     * Snapshots a square region and masks it to a circle for the smudge brush.
     * Returns null if the region falls entirely outside the canvas bounds.
     *
     * @param centerX center X of the sample in canvas coordinates
     * @param centerY center Y of the sample in canvas coordinates
     * @return a circularly masked WritableImage, or null
     */
    private WritableImage sampleCircularRegion(double centerX, double centerY) {
        double brushSize   = toolManager.getBrushSize();
        double brushRadius = brushSize / 2.0;

        int totalCanvasWidth  = (int) canvasManager.getCanvasWidth();
        int totalCanvasHeight = (int) canvasManager.getCanvasHeight();

        int sampleLeft   = (int) Math.max(0, centerX - brushRadius);
        int sampleTop    = (int) Math.max(0, centerY - brushRadius);
        int sampleWidth  = (int) Math.min(totalCanvasWidth  - sampleLeft, brushSize);
        int sampleHeight = (int) Math.min(totalCanvasHeight - sampleTop,  brushSize);

        if (sampleWidth <= 0 || sampleHeight <= 0) {
            return null;
        }

        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        snapshotParameters.setViewport(
                new Rectangle2D(sampleLeft, sampleTop, sampleWidth, sampleHeight));

        WritableImage sampleImage = layerManager.getActiveLayer().canvas.snapshot(
                snapshotParameters, null);

        // Mask pixels outside the circle to transparent
        var pixelWriter  = sampleImage.getPixelWriter();
        double radiusSquared = brushRadius * brushRadius;

        for (int pixelRow = 0; pixelRow < sampleHeight; pixelRow++) {
            for (int pixelColumn = 0; pixelColumn < sampleWidth; pixelColumn++) {
                double deltaX = (sampleLeft + pixelColumn) - centerX;
                double deltaY = (sampleTop  + pixelRow)    - centerY;
                if (deltaX * deltaX + deltaY * deltaY > radiusSquared) {
                    pixelWriter.setColor(pixelColumn, pixelRow, Color.TRANSPARENT);
                }
            }
        }

        return sampleImage;
    }

    // ================= OTHER TOOLS =================

    /**
     * Samples the composite canvas color at the given position, sets it as the active color,
     * and switches to the Brush tool.
     *
     * After switching to Brush, beginPath() is called on the active layer's GraphicsContext
     * to discard any stale path state.  Without this reset, the next brush stroke would draw
     * a ghost line from the eyedropper click position to wherever the stroke begins.
     *
     * @param canvasX X position to sample in canvas coordinates
     * @param canvasY Y position to sample in canvas coordinates
     */
    private void handleEyedropper(double canvasX, double canvasY) {
        WritableImage compositeSnapshot = canvasManager.snapshotFull();
        int sampleX = (int) canvasX;
        int sampleY = (int) canvasY;

        boolean xIsInBounds = sampleX >= 0 && sampleX < canvasManager.getCanvasWidth();
        boolean yIsInBounds = sampleY >= 0 && sampleY < canvasManager.getCanvasHeight();

        if (xIsInBounds && yIsInBounds) {
            Color sampledColor = compositeSnapshot.getPixelReader().getColor(sampleX, sampleY);
            if (sampledColor.getOpacity() > 0) {
                toolManager.setColor(new Color(
                        sampledColor.getRed(),
                        sampledColor.getGreen(),
                        sampledColor.getBlue(),
                        sampledColor.getOpacity()));
                toolManager.setTool(ToolManager.Tool.BRUSH);

                // Reset the GraphicsContext path so the next brush stroke starts clean.
                // Without this, a line would be drawn from this click position to the
                // first point of the next stroke.
                if (layerManager.getActiveLayer() != null) {
                    layerManager.getActiveLayer().gc.beginPath();
                }
            }
        }
    }

    /**
     * Performs a queue-based flood fill from the given pixel position.
     * Replaces all connected same-color pixels with the active fill color.
     *
     * @param startX starting X pixel coordinate
     * @param startY starting Y pixel coordinate
     */
    private void floodFill(int startX, int startY) {
        WritableImage layerSnapshot = canvasManager.snapshot(layerManager.getActiveLayer().canvas);
        var pixelReader = layerSnapshot.getPixelReader();
        var pixelWriter = layerManager.getActiveLayer().gc.getPixelWriter();

        int totalWidth  = (int) canvasManager.getCanvasWidth();
        int totalHeight = (int) canvasManager.getCanvasHeight();

        Color targetColor = pixelReader.getColor(startX, startY);
        Color fillColor   = toolManager.getColor();

        // Nothing to fill if the target is already the fill color
        if (targetColor.equals(fillColor)) {
            return;
        }

        boolean[][] visitedPixels = new boolean[totalWidth][totalHeight];
        java.util.Queue<int[]> pixelQueue = new java.util.LinkedList<>();
        pixelQueue.add(new int[]{startX, startY});

        while (!pixelQueue.isEmpty()) {
            int[] currentPixel  = pixelQueue.poll();
            int   currentPixelX = currentPixel[0];
            int   currentPixelY = currentPixel[1];

            boolean xOutOfBounds = currentPixelX < 0 || currentPixelX >= totalWidth;
            boolean yOutOfBounds = currentPixelY < 0 || currentPixelY >= totalHeight;
            if (xOutOfBounds || yOutOfBounds) {
                continue;
            }
            if (visitedPixels[currentPixelX][currentPixelY]) {
                continue;
            }
            if (!pixelReader.getColor(currentPixelX, currentPixelY).equals(targetColor)) {
                continue;
            }

            // Fill and mark this pixel visited
            pixelWriter.setColor(currentPixelX, currentPixelY, fillColor);
            visitedPixels[currentPixelX][currentPixelY] = true;

            // Enqueue the four orthogonal neighbours
            pixelQueue.add(new int[]{currentPixelX + 1, currentPixelY});
            pixelQueue.add(new int[]{currentPixelX - 1, currentPixelY});
            pixelQueue.add(new int[]{currentPixelX,     currentPixelY + 1});
            pixelQueue.add(new int[]{currentPixelX,     currentPixelY - 1});
        }
    }
}