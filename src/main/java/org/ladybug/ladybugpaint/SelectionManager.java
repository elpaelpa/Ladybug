package org.ladybug.ladybugpaint;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.util.ArrayList;
import java.util.List;

public class SelectionManager {

    // ================= FIELDS =================

    // Provides access to the canvas stack, temp surface, and zoom
    private final CanvasManager canvasManager;

    // Provides access to the active layer and undo/redo history
    private final LayerManager layerManager;

    // The marching-ants path drawn on top of the canvas stack
    private final Path selectionPath = new Path();

    // Ordered list of canvas points defining the selection boundary
    private final List<Point2D> selectionPoints = new ArrayList<>();

    // Pixels captured inside the selection boundary for move/paste
    private WritableImage selectionImage = null;

    // Image held on the internal clipboard for copy/paste
    private WritableImage clipboardImage = null;

    // Snapshot of the layer after the selection pixels have been cut out;
    // restored as the base on every drag frame so the floating piece moves cleanly
    private WritableImage layerAfterCut = null;

    // Snapshot of the full layer taken at the start of a whole-layer move
    private WritableImage layerMoveSnapshot = null;

    // Canvas X position where a whole-layer move drag started
    private double layerMoveDragStartX = 0;

    // Canvas Y position where a whole-layer move drag started
    private double layerMoveDragStartY = 0;

    // True when a selection region is currently active
    private boolean hasSelection = false;

    // True while the user is dragging a selection or layer
    private boolean isDragging = false;

    // True while the user is drawing a new selection boundary
    private boolean isCreatingNewSelection = false;

    // True once the selected pixels have been cut from the active layer
    private boolean selectionHasBeenCut = false;

    // When true the select tool uses an axis-aligned rectangle; otherwise freeform lasso
    private boolean rectangleModeEnabled = false;

    // Canvas X of the top-left corner of the selection bounding box
    private double selectionX = 0;

    // Canvas Y of the top-left corner of the selection bounding box
    private double selectionY = 0;

    // Width of the selection bounding box in canvas pixels
    private double selectionWidth = 0;

    // Height of the selection bounding box in canvas pixels
    private double selectionHeight = 0;

    // Offset from drag start to the selection origin, used during move
    private double dragOffsetX = 0;

    // Offset from drag start to the selection origin, used during move
    private double dragOffsetY = 0;

    // Animated dash offset that drives the marching-ants effect
    private double marchingAntsDashOffset = 0;

    // ================= CONSTRUCTOR =================

    /**
     * Constructs the SelectionManager and adds the selection path overlay to the canvas stack.
     *
     * @param canvasManager the canvas manager; must not be null
     * @param layerManager  the layer manager; must not be null
     */
    public SelectionManager(CanvasManager canvasManager, LayerManager layerManager) {
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

        setupSelectionPath();
        setupMarchingAnts();
    }

    // ================= GETTERS / SETTERS =================

    /**
     * Returns true when a selection region is currently active.
     */
    public boolean hasSelection() {
        return hasSelection;
    }

    /**
     * Returns true while the user is drawing a new selection boundary.
     */
    public boolean isCreatingNewSelection() {
        return isCreatingNewSelection;
    }

    /**
     * Returns true when the select tool is in rectangle mode.
     */
    public boolean isRectangleModeEnabled() {
        return rectangleModeEnabled;
    }

    /**
     * Enables or disables rectangle selection mode.
     *
     * @param newRectangleModeEnabled true for rectangle, false for freeform lasso
     */
    public void setRectangleModeEnabled(boolean newRectangleModeEnabled) {
        this.rectangleModeEnabled = newRectangleModeEnabled;
    }

    // ================= SETUP =================

    /**
     * Configures the selection path overlay and adds it to the canvas stack.
     */
    private void setupSelectionPath() {
        selectionPath.setStroke(Color.WHITE);
        selectionPath.setStrokeWidth(1.5);
        selectionPath.getStrokeDashArray().addAll(5.0, 5.0);
        selectionPath.setFill(Color.color(1.0, 1.0, 1.0, 0.04));
        selectionPath.setStyle(
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 2, 0, 0, 0);");
        selectionPath.setManaged(false);
        selectionPath.setVisible(false);
        canvasManager.getCanvasStack().getChildren().add(selectionPath);
    }

    /**
     * Starts an AnimationTimer that advances the marching-ants dash offset each frame.
     */
    private void setupMarchingAnts() {
        new AnimationTimer() {
            @Override
            public void handle(long currentTime) {
                if (hasSelection || selectionPath.isVisible()) {
                    marchingAntsDashOffset -= 0.6;
                    selectionPath.setStrokeDashOffset(marchingAntsDashOffset);
                }
            }
        }.start();
    }

    // ================= SELECTION CREATION =================

    /**
     * Begins a new selection at the given canvas position, resetting all prior state.
     *
     * @param canvasX starting X in canvas coordinates
     * @param canvasY starting Y in canvas coordinates
     */
    public void startSelection(double canvasX, double canvasY) {
        hasSelection           = false;
        selectionHasBeenCut    = false;
        layerAfterCut          = null;
        isCreatingNewSelection = true;

        selectionPoints.clear();
        selectionPoints.add(new Point2D(canvasX, canvasY));

        selectionPath.getElements().clear();
        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);
        selectionPath.getElements().add(new MoveTo(canvasX, canvasY));
        selectionPath.setVisible(true);
        selectionPath.toFront();

        canvasManager.clearTemp();
    }

    /**
     * Updates the selection boundary as the mouse moves.
     * Rectangle mode redraws the path as a live rectangle preview.
     * Freeform mode appends each new point to the lasso path.
     *
     * @param canvasX current X in canvas coordinates
     * @param canvasY current Y in canvas coordinates
     */
    public void updateSelection(double canvasX, double canvasY) {
        if (hasSelection || !isCreatingNewSelection) {
            return;
        }

        if (rectangleModeEnabled) {
            // Rebuild the path each frame so the rectangle preview tracks the cursor
            double startCornerX = selectionPoints.get(0).getX();
            double startCornerY = selectionPoints.get(0).getY();

            selectionPath.getElements().clear();
            selectionPath.getElements().add(new MoveTo(startCornerX, startCornerY));
            selectionPath.getElements().add(new LineTo(canvasX,      startCornerY));
            selectionPath.getElements().add(new LineTo(canvasX,      canvasY));
            selectionPath.getElements().add(new LineTo(startCornerX, canvasY));
            selectionPath.getElements().add(new ClosePath());
        } else {
            // Append each new position to the growing freeform lasso
            selectionPoints.add(new Point2D(canvasX, canvasY));
            selectionPath.getElements().add(new LineTo(canvasX, canvasY));
        }
    }

    /**
     * Closes the selection boundary and captures the enclosed pixels.
     * Rectangle mode requires at least a start and one drag point.
     * Freeform mode requires at least three distinct points.
     */
    public void finalizeSelection() {
        boolean tooFewPoints = rectangleModeEnabled
                ? selectionPoints.size() < 2
                : selectionPoints.size() < 3;

        if (hasSelection || tooFewPoints) {
            return;
        }

        isCreatingNewSelection = false;

        if (rectangleModeEnabled) {
            finalizeRectangleSelection();
        } else {
            finalizeFreeformSelection();
        }
    }

    /**
     * Finalizes the selection as an axis-aligned rectangle derived from the start corner
     * and the last cursor position recorded during updateSelection.
     */
    private void finalizeRectangleSelection() {
        double startCornerX = selectionPoints.get(0).getX();
        double startCornerY = selectionPoints.get(0).getY();

        // Find the end corner from the last LineTo element in the current path
        double endCornerX = startCornerX;
        double endCornerY = startCornerY;

        for (PathElement element : selectionPath.getElements()) {
            if (element instanceof LineTo lineToElement) {
                endCornerX = lineToElement.getX();
                endCornerY = lineToElement.getY();
            }
        }

        selectionX      = Math.min(startCornerX, endCornerX);
        selectionY      = Math.min(startCornerY, endCornerY);
        selectionWidth  = Math.abs(endCornerX - startCornerX);
        selectionHeight = Math.abs(endCornerY - startCornerY);

        if (selectionWidth < 1 || selectionHeight < 1) {
            clearSelection();
            return;
        }

        // Store the four rectangle corners for later use by cutSelectionFromLayer
        selectionPoints.clear();
        selectionPoints.add(new Point2D(selectionX,                   selectionY));
        selectionPoints.add(new Point2D(selectionX + selectionWidth,  selectionY));
        selectionPoints.add(new Point2D(selectionX + selectionWidth,  selectionY + selectionHeight));
        selectionPoints.add(new Point2D(selectionX,                   selectionY + selectionHeight));

        // Rebuild the path as a clean closed rectangle
        selectionPath.getElements().clear();
        selectionPath.getElements().add(new MoveTo(selectionX,                   selectionY));
        selectionPath.getElements().add(new LineTo(selectionX + selectionWidth,  selectionY));
        selectionPath.getElements().add(new LineTo(selectionX + selectionWidth,  selectionY + selectionHeight));
        selectionPath.getElements().add(new LineTo(selectionX,                   selectionY + selectionHeight));
        selectionPath.getElements().add(new ClosePath());

        captureSelectionPixels();
    }

    /**
     * Finalizes the selection as a freeform lasso polygon.
     * Computes the bounding box from all recorded lasso points,
     * then masks the captured pixels to the polygon shape.
     */
    private void finalizeFreeformSelection() {
        selectionPath.getElements().add(new ClosePath());

        double minimumX = Double.MAX_VALUE;
        double minimumY = Double.MAX_VALUE;
        double maximumX = Double.MIN_VALUE;
        double maximumY = Double.MIN_VALUE;

        for (Point2D point : selectionPoints) {
            minimumX = Math.min(minimumX, point.getX());
            minimumY = Math.min(minimumY, point.getY());
            maximumX = Math.max(maximumX, point.getX());
            maximumY = Math.max(maximumY, point.getY());
        }

        selectionX      = minimumX;
        selectionY      = minimumY;
        selectionWidth  = maximumX - minimumX;
        selectionHeight = maximumY - minimumY;

        if (selectionWidth < 1 || selectionHeight < 1) {
            clearSelection();
            return;
        }

        // Snapshot the bounding box region from the active layer
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        snapshotParameters.setViewport(
                new Rectangle2D(selectionX, selectionY, selectionWidth, selectionHeight));

        javafx.scene.image.Image rawSnapshot =
                layerManager.getActiveLayer().canvas.snapshot(snapshotParameters, null);

        // Mask the snapshot to the lasso polygon shape
        Canvas maskCanvas = new Canvas(selectionWidth, selectionHeight);
        GraphicsContext maskGraphicsContext = maskCanvas.getGraphicsContext2D();
        maskGraphicsContext.beginPath();
        for (Point2D point : selectionPoints) {
            maskGraphicsContext.lineTo(point.getX() - selectionX, point.getY() - selectionY);
        }
        maskGraphicsContext.closePath();
        maskGraphicsContext.clip();
        maskGraphicsContext.drawImage(rawSnapshot, 0, 0);

        SnapshotParameters maskSnapshotParameters = new SnapshotParameters();
        maskSnapshotParameters.setFill(Color.TRANSPARENT);
        selectionImage = maskCanvas.snapshot(maskSnapshotParameters, null);

        selectionHasBeenCut = false;
        layerAfterCut       = null;
        hasSelection        = true;
        selectionPath.toFront();
    }

    /**
     * Directly captures the pixels within the rectangular bounding box (used for rectangle mode).
     */
    private void captureSelectionPixels() {
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        snapshotParameters.setViewport(
                new Rectangle2D(selectionX, selectionY, selectionWidth, selectionHeight));

        selectionImage      = layerManager.getActiveLayer().canvas.snapshot(snapshotParameters, null);
        selectionHasBeenCut = false;
        layerAfterCut       = null;
        hasSelection        = true;
        selectionPath.toFront();
    }

    // ================= MOVE =================

    /**
     * Begins a move operation.
     * If a selection is active, its pixels are cut from the layer on the first move drag.
     * If no selection is active, the entire layer is prepared for a whole-layer move.
     *
     * @param canvasX drag start X in canvas coordinates
     * @param canvasY drag start Y in canvas coordinates
     */
    public void startMove(double canvasX, double canvasY) {
        isDragging             = true;
        isCreatingNewSelection = false;

        if (hasSelection) {
            dragOffsetX = canvasX - selectionX;
            dragOffsetY = canvasY - selectionY;

            // Cut the selection pixels from the layer on the first move drag
            if (!selectionHasBeenCut) {
                layerManager.saveState(layerManager.getActiveLayer(),
                        canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                cutSelectionFromLayer();
                selectionHasBeenCut = true;

                // Snapshot the layer-with-hole so we can restore it cleanly on each drag frame
                layerAfterCut = new WritableImage(
                        (int) canvasManager.getCanvasWidth(),
                        (int) canvasManager.getCanvasHeight());
                layerManager.getActiveLayer().canvas.snapshot(null, layerAfterCut);
            }
        } else {
            // No selection: prepare to move the whole layer
            layerManager.saveState(layerManager.getActiveLayer(),
                    canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            layerMoveDragStartX = canvasX;
            layerMoveDragStartY = canvasY;
            layerMoveSnapshot = new WritableImage(
                    (int) canvasManager.getCanvasWidth(),
                    (int) canvasManager.getCanvasHeight());
            layerManager.getActiveLayer().canvas.snapshot(null, layerMoveSnapshot);
        }
    }

    /**
     * Removes the selected pixels from the active layer by punching the selection
     * shape out using pixel-by-pixel PixelWriter.
     * (clearRect is not used because it ignores clip paths in JavaFX.)
     */
    private void cutSelectionFromLayer() {
        double totalWidth  = canvasManager.getCanvasWidth();
        double totalHeight = canvasManager.getCanvasHeight();
        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;

        // Full layer snapshot to read pixel values from
        WritableImage fullLayerSnapshot = new WritableImage((int) totalWidth, (int) totalHeight);
        layerManager.getActiveLayer().canvas.snapshot(null, fullLayerSnapshot);

        // Build a mask canvas: selection shape filled solid black, rest transparent
        Canvas maskCanvas = new Canvas(totalWidth, totalHeight);
        GraphicsContext maskGraphicsContext = maskCanvas.getGraphicsContext2D();
        maskGraphicsContext.setFill(Color.BLACK);
        maskGraphicsContext.beginPath();

        for (int pointIndex = 0; pointIndex < selectionPoints.size(); pointIndex++) {
            Point2D currentPoint = selectionPoints.get(pointIndex);
            double translatedX   = currentPoint.getX() + selectionPath.getTranslateX();
            double translatedY   = currentPoint.getY() + selectionPath.getTranslateY();
            if (pointIndex == 0) {
                maskGraphicsContext.moveTo(translatedX, translatedY);
            } else {
                maskGraphicsContext.lineTo(translatedX, translatedY);
            }
        }

        maskGraphicsContext.closePath();
        maskGraphicsContext.fill();

        SnapshotParameters maskSnapshotParameters = new SnapshotParameters();
        maskSnapshotParameters.setFill(Color.TRANSPARENT);
        WritableImage maskSnapshot = maskCanvas.snapshot(maskSnapshotParameters, null);

        // Compose result: transparent where mask is opaque, original pixel elsewhere
        WritableImage resultImage    = new WritableImage((int) totalWidth, (int) totalHeight);
        var originalPixelReader      = fullLayerSnapshot.getPixelReader();
        var maskPixelReader          = maskSnapshot.getPixelReader();
        var resultPixelWriter        = resultImage.getPixelWriter();

        for (int rowIndex = 0; rowIndex < (int) totalHeight; rowIndex++) {
            for (int columnIndex = 0; columnIndex < (int) totalWidth; columnIndex++) {
                if (maskPixelReader.getColor(columnIndex, rowIndex).getOpacity() > 0.1) {
                    // Inside selection — erase the pixel
                    resultPixelWriter.setColor(columnIndex, rowIndex, Color.TRANSPARENT);
                } else {
                    // Outside selection — keep the original
                    resultPixelWriter.setColor(columnIndex, rowIndex,
                            originalPixelReader.getColor(columnIndex, rowIndex));
                }
            }
        }

        graphicsContext.clearRect(0, 0, totalWidth, totalHeight);
        graphicsContext.drawImage(resultImage, 0, 0);
    }

    /**
     * Updates the position of the floating selection piece during a drag.
     * Restores the cut-layer base and redraws the floating piece at the new position.
     * Uses SRC_OVER blending so the floating piece's original transparency is preserved.
     *
     * @param canvasX current drag X in canvas coordinates
     * @param canvasY current drag Y in canvas coordinates
     */
    public void updateMove(double canvasX, double canvasY) {
        if (!isDragging || !hasSelection || selectionImage == null) {
            return;
        }

        double previousSelectionX = selectionX;
        double previousSelectionY = selectionY;

        selectionX = canvasX - dragOffsetX;
        selectionY = canvasY - dragOffsetY;

        // Translate the visual selection path by the same delta
        selectionPath.setTranslateX(
                selectionPath.getTranslateX() + (selectionX - previousSelectionX));
        selectionPath.setTranslateY(
                selectionPath.getTranslateY() + (selectionY - previousSelectionY));

        // Restore the layer-with-hole, then draw the floating piece on top.
        // globalAlpha is reset to 1.0 and blendMode to null so that the
        // selectionImage's own alpha channel controls its transparency exactly
        // as it was when the selection was captured — no extra opacity applied.
        if (layerAfterCut != null) {
            GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;
            graphicsContext.setGlobalBlendMode(null);
            graphicsContext.setGlobalAlpha(1.0);
            graphicsContext.clearRect(0, 0,
                    canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            graphicsContext.drawImage(layerAfterCut, 0, 0);

            // Draw the floating piece; its per-pixel alpha is respected by SRC_OVER
            graphicsContext.drawImage(selectionImage, selectionX, selectionY);
        }

        selectionPath.toFront();
    }

    /**
     * Moves the entire active layer by the delta from the drag start position.
     * Uses SRC_OVER blending so per-pixel transparency in the layer snapshot is preserved.
     *
     * @param canvasX current drag X in canvas coordinates
     * @param canvasY current drag Y in canvas coordinates
     */
    public void updateLayerMove(double canvasX, double canvasY) {
        if (!isDragging || layerMoveSnapshot == null) {
            return;
        }

        double deltaX = canvasX - layerMoveDragStartX;
        double deltaY = canvasY - layerMoveDragStartY;

        GraphicsContext graphicsContext = layerManager.getActiveLayer().gc;

        // Reset blend state so the snapshot's alpha channel is respected
        graphicsContext.setGlobalBlendMode(null);
        graphicsContext.setGlobalAlpha(1.0);
        graphicsContext.clearRect(0, 0,
                canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());

        // Draw the layer snapshot at the offset position; transparency is preserved by SRC_OVER
        graphicsContext.drawImage(layerMoveSnapshot, deltaX, deltaY);
    }

    /**
     * Ends the current move drag without committing the selection.
     */
    public void endMove() {
        isDragging        = false;
        layerMoveSnapshot = null;
        // layerAfterCut is retained so repeated moves within the same selection are correct
    }

    // ================= COMMIT / CLEAR =================

    /**
     * Draws the selection image preview on the temp canvas.
     */
    public void drawPreview() {
        canvasManager.clearTemp();
        if (selectionImage == null) {
            return;
        }
        canvasManager.getTempGc().drawImage(selectionImage, selectionX, selectionY);
        selectionPath.toFront();
    }

    /**
     * Stamps the floating selection piece at its current position onto the active layer
     * and clears all selection state.
     */
    public void commitSelection() {
        if (!hasSelection || selectionImage == null) {
            return;
        }
        if (!selectionHasBeenCut) {
            layerManager.saveState(layerManager.getActiveLayer(),
                    canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            cutSelectionFromLayer();
        }
        // Stamp the floating piece at its final position; SRC_OVER preserves its alpha
        layerManager.getActiveLayer().gc.setGlobalAlpha(1.0);
        layerManager.getActiveLayer().gc.setGlobalBlendMode(null);
        layerManager.getActiveLayer().gc.drawImage(selectionImage, selectionX, selectionY);
        clearSelection();
    }

    /**
     * Clears the active selection.
     * If the selection was cut but never committed, stamps it back at its current position.
     */
    public void clearSelection() {
        // Stamp the floating piece back if it was cut but not committed
        if (hasSelection && selectionHasBeenCut && selectionImage != null) {
            layerManager.getActiveLayer().gc.setGlobalAlpha(1.0);
            layerManager.getActiveLayer().gc.setGlobalBlendMode(null);
            layerManager.getActiveLayer().gc.drawImage(selectionImage, selectionX, selectionY);
        }

        hasSelection           = false;
        selectionHasBeenCut    = false;
        isCreatingNewSelection = false;
        selectionImage         = null;
        layerAfterCut          = null;
        layerMoveSnapshot      = null;

        selectionPath.setVisible(false);
        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);

        selectionPoints.clear();
        canvasManager.clearTemp();
    }

    // ================= HELPERS =================

    /**
     * Returns true when the given canvas position falls within the visible selection path bounds.
     *
     * @param canvasX X position to test in canvas coordinates
     * @param canvasY Y position to test in canvas coordinates
     */
    public boolean isClickInside(double canvasX, double canvasY) {
        return selectionPath.isVisible()
                && selectionPath.getBoundsInParent().contains(canvasX, canvasY);
    }

    /**
     * Applies the selection shape as a clip region to the given GraphicsContext.
     * Saves the GraphicsContext state first so restoreClipping() can undo it.
     *
     * @param graphicsContext the GraphicsContext to clip
     */
    public void applyClipping(GraphicsContext graphicsContext) {
        if (hasSelection) {
            graphicsContext.save();
            graphicsContext.beginPath();
            for (int pointIndex = 0; pointIndex < selectionPoints.size(); pointIndex++) {
                Point2D currentPoint = selectionPoints.get(pointIndex);
                double translatedX   = currentPoint.getX() + selectionPath.getTranslateX();
                double translatedY   = currentPoint.getY() + selectionPath.getTranslateY();
                if (pointIndex == 0) {
                    graphicsContext.moveTo(translatedX, translatedY);
                } else {
                    graphicsContext.lineTo(translatedX, translatedY);
                }
            }
            graphicsContext.closePath();
            graphicsContext.clip();
        }
    }

    /**
     * Restores the GraphicsContext state after applyClipping().
     *
     * @param graphicsContext the GraphicsContext to restore
     */
    public void restoreClipping(GraphicsContext graphicsContext) {
        if (hasSelection) {
            graphicsContext.restore();
        }
    }

    // ================= COPY / PASTE =================

    /**
     * Copies the current selection image to the internal clipboard.
     */
    public void copy() {
        if (hasSelection) {
            clipboardImage = selectionImage;
        }
    }

    /**
     * Pastes the clipboard image as a new floating selection near the top-left of the canvas.
     */
    public void paste() {
        if (clipboardImage == null) {
            return;
        }

        // Commit any existing selection before pasting
        commitSelection();

        selectionImage  = clipboardImage;
        selectionX      = 50;
        selectionY      = 50;
        selectionWidth  = selectionImage.getWidth();
        selectionHeight = selectionImage.getHeight();

        hasSelection        = true;
        selectionHasBeenCut = true;

        // Snapshot the current layer as the base for move restoration
        layerAfterCut = new WritableImage(
                (int) canvasManager.getCanvasWidth(),
                (int) canvasManager.getCanvasHeight());
        layerManager.getActiveLayer().canvas.snapshot(null, layerAfterCut);

        // Record the four rectangle corners as selection points
        selectionPoints.clear();
        selectionPoints.add(new Point2D(selectionX,                   selectionY));
        selectionPoints.add(new Point2D(selectionX + selectionWidth,  selectionY));
        selectionPoints.add(new Point2D(selectionX + selectionWidth,  selectionY + selectionHeight));
        selectionPoints.add(new Point2D(selectionX,                   selectionY + selectionHeight));

        // Build the selection path around the pasted image
        selectionPath.getElements().clear();
        selectionPath.getElements().add(new MoveTo(selectionX,                   selectionY));
        selectionPath.getElements().add(new LineTo(selectionX + selectionWidth,  selectionY));
        selectionPath.getElements().add(new LineTo(selectionX + selectionWidth,  selectionY + selectionHeight));
        selectionPath.getElements().add(new LineTo(selectionX,                   selectionY + selectionHeight));
        selectionPath.getElements().add(new ClosePath());

        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);
        selectionPath.setVisible(true);

        drawPreview();
    }

    // ================= SHORTCUTS =================

    /**
     * Registers keyboard accelerators for copy, paste, and clear-selection.
     *
     * @param targetScene the scene to register the shortcuts on
     */
    public void registerShortcuts(Scene targetScene) {
        targetScene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN), this::copy);
        targetScene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN), this::paste);
        targetScene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D), this::clearSelection);
    }
}