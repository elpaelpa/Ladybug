package org.ladybug.ladybugpaint;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.util.ArrayList;
import java.util.List;

public class SelectionManager {

    private final CanvasManager canvasManager;
    private final LayerManager layerManager;

    private final Path selectionPath = new Path();
    private final List<Point2D> points = new ArrayList<>();

    private WritableImage selectionImage = null;
    private WritableImage clipboardImage = null;

    // Snapshot of the layer AFTER the selection has been cut out.
    // Used as the base to redraw on every drag update so the floating
    // piece appears to move without leaving a trail.
    private WritableImage layerAfterCut = null;

    // Snapshot taken at drag-start for whole-layer move
    private WritableImage layerMoveSnapshot = null;
    private double layerMoveDragStartX, layerMoveDragStartY;

    private boolean hasSelection = false;
    private boolean dragging = false;
    private boolean creatingNewSelection = false;
    private boolean selectionCut = false;

    private double selX, selY, selW, selH;
    private double offsetX, offsetY;
    private double dashOffset = 0;

    public SelectionManager(CanvasManager cm, LayerManager lm) {
        this.canvasManager = cm;
        this.layerManager = lm;
        setupSelectionPath();
        setupMarchingAnts();
    }

    private void setupSelectionPath() {
        selectionPath.setStroke(Color.WHITE);
        selectionPath.setStrokeWidth(1.5);
        selectionPath.getStrokeDashArray().addAll(5.0, 5.0);
        selectionPath.setFill(Color.color(1.0, 1.0, 1.0, 0.05));
        selectionPath.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 2, 0, 0, 0);");
        selectionPath.setManaged(false);
        selectionPath.setVisible(false);
        canvasManager.getCanvasStack().getChildren().add(selectionPath);
    }

    private void setupMarchingAnts() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (hasSelection || selectionPath.isVisible()) {
                    dashOffset -= 0.6;
                    selectionPath.setStrokeDashOffset(dashOffset);
                }
            }
        }.start();
    }

    public void startSelection(double x, double y) {
        hasSelection = false;
        selectionCut = false;
        layerAfterCut = null;
        creatingNewSelection = true;
        points.clear();
        points.add(new Point2D(x, y));

        selectionPath.getElements().clear();
        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);
        selectionPath.getElements().add(new MoveTo(x, y));

        selectionPath.setVisible(true);
        selectionPath.toFront();
        canvasManager.clearTemp();
    }

    public void updateSelection(double x, double y) {
        if (hasSelection || !creatingNewSelection) return;
        points.add(new Point2D(x, y));
        selectionPath.getElements().add(new LineTo(x, y));
    }

    public void finalizeSelection() {
        if (hasSelection || points.size() < 3) return;

        creatingNewSelection = false;
        selectionPath.getElements().add(new ClosePath());

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (Point2D p : points) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }

        selX = minX; selY = minY;
        selW = maxX - minX; selH = maxY - minY;

        if (selW < 1 || selH < 1) { clearSelection(); return; }

        // Capture the selected pixels (masked to the lasso shape)
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(selX, selY, selW, selH));
        Image rawSnapshot = layerManager.getActiveLayer().canvas.snapshot(sp, null);

        Canvas maskCanvas = new Canvas(selW, selH);
        GraphicsContext maskGc = maskCanvas.getGraphicsContext2D();
        maskGc.beginPath();
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            maskGc.lineTo(p.getX() - selX, p.getY() - selY);
        }
        maskGc.closePath();
        maskGc.clip();
        maskGc.drawImage(rawSnapshot, 0, 0);

        SnapshotParameters spMask = new SnapshotParameters();
        spMask.setFill(Color.TRANSPARENT);
        selectionImage = maskCanvas.snapshot(spMask, null);

        selectionCut = false;
        layerAfterCut = null;
        hasSelection = true;
        selectionPath.toFront();
        // Don't draw preview yet — no cut has happened, show marching ants only
    }

    public void startMove(double x, double y) {
        dragging = true;
        creatingNewSelection = false;

        if (hasSelection) {
            offsetX = x - selX;
            offsetY = y - selY;
            if (!selectionCut) {
                layerManager.saveState(layerManager.getActiveLayer(),
                        canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                cutSelectionFromLayer();
                selectionCut = true;

                // Snapshot the layer NOW (with the hole in it) as our clean base.
                // Every drag frame we restore this and draw the floating piece on top.
                layerAfterCut = new WritableImage(
                        (int) canvasManager.getCanvasWidth(),
                        (int) canvasManager.getCanvasHeight());
                layerManager.getActiveLayer().canvas.snapshot(null, layerAfterCut);
            }
        } else {
            layerManager.saveState(layerManager.getActiveLayer(),
                    canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            layerMoveDragStartX = x;
            layerMoveDragStartY = y;
            layerMoveSnapshot = new WritableImage(
                    (int) canvasManager.getCanvasWidth(),
                    (int) canvasManager.getCanvasHeight());
            layerManager.getActiveLayer().canvas.snapshot(null, layerMoveSnapshot);
        }
    }

    /**
     * Punches the lasso shape out of the active layer using pixel-by-pixel
     * PixelWriter (required because JavaFX clearRect ignores clip paths).
     */
    private void cutSelectionFromLayer() {
        double w = canvasManager.getCanvasWidth();
        double h = canvasManager.getCanvasHeight();
        GraphicsContext gc = layerManager.getActiveLayer().gc;

        WritableImage fullSnap = new WritableImage((int) w, (int) h);
        layerManager.getActiveLayer().canvas.snapshot(null, fullSnap);

        // Build a mask: selection shape filled solid black, rest transparent
        Canvas maskCanvas = new Canvas(w, h);
        GraphicsContext mgc = maskCanvas.getGraphicsContext2D();
        mgc.setFill(Color.BLACK);
        mgc.beginPath();
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            double tx = p.getX() + selectionPath.getTranslateX();
            double ty = p.getY() + selectionPath.getTranslateY();
            if (i == 0) mgc.moveTo(tx, ty);
            else mgc.lineTo(tx, ty);
        }
        mgc.closePath();
        mgc.fill();
        SnapshotParameters msp = new SnapshotParameters();
        msp.setFill(Color.TRANSPARENT);
        WritableImage maskSnap = maskCanvas.snapshot(msp, null);

        // Write result: transparent where mask is opaque, original pixel elsewhere
        WritableImage result = new WritableImage((int) w, (int) h);
        var reader = fullSnap.getPixelReader();
        var maskReader = maskSnap.getPixelReader();
        var writer = result.getPixelWriter();
        for (int py = 0; py < (int) h; py++) {
            for (int px = 0; px < (int) w; px++) {
                if (maskReader.getColor(px, py).getOpacity() > 0.1) {
                    writer.setColor(px, py, Color.TRANSPARENT);
                } else {
                    writer.setColor(px, py, reader.getColor(px, py));
                }
            }
        }

        gc.clearRect(0, 0, w, h);
        gc.drawImage(result, 0, 0);
    }

    public void updateMove(double x, double y) {
        if (!dragging || !hasSelection || selectionImage == null) return;

        double oldX = selX;
        double oldY = selY;
        selX = x - offsetX;
        selY = y - offsetY;

        selectionPath.setTranslateX(selectionPath.getTranslateX() + (selX - oldX));
        selectionPath.setTranslateY(selectionPath.getTranslateY() + (selY - oldY));

        // Restore the cut layer, then draw the floating piece on top.
        // This is done directly on the layer canvas so it's always visible.
        if (layerAfterCut != null) {
            GraphicsContext gc = layerManager.getActiveLayer().gc;
            gc.clearRect(0, 0, canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            gc.drawImage(layerAfterCut, 0, 0);
            gc.drawImage(selectionImage, selX, selY);
        }

        selectionPath.toFront();
    }

    public void updateLayerMove(double x, double y) {
        if (!dragging || layerMoveSnapshot == null) return;
        double dx = x - layerMoveDragStartX;
        double dy = y - layerMoveDragStartY;
        GraphicsContext gc = layerManager.getActiveLayer().gc;
        gc.clearRect(0, 0, canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
        gc.drawImage(layerMoveSnapshot, dx, dy);
    }

    public void endMove() {
        dragging = false;
        layerMoveSnapshot = null;
        // layerAfterCut is kept alive until the selection is committed/cleared
        // so the layer state remains correct if the user moves again.
    }

    public void drawPreview() {
        canvasManager.clearTemp();
        if (selectionImage == null) return;
        canvasManager.getTempGc().drawImage(selectionImage, selX, selY);
        selectionPath.toFront();
    }

    public void commitSelection() {
        if (!hasSelection || selectionImage == null) return;
        if (!selectionCut) {
            layerManager.saveState(layerManager.getActiveLayer(),
                    canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
            cutSelectionFromLayer();
        }
        // Stamp the floating piece at its final position
        layerManager.getActiveLayer().gc.drawImage(selectionImage, selX, selY);
        clearSelection();
    }

    public void clearSelection() {
        // If cut but never committed, stamp the piece back where it currently is
        if (hasSelection && selectionCut && selectionImage != null) {
            layerManager.getActiveLayer().gc.drawImage(selectionImage, selX, selY);
        }
        hasSelection = false;
        selectionCut = false;
        creatingNewSelection = false;
        selectionImage = null;
        layerAfterCut = null;
        layerMoveSnapshot = null;
        selectionPath.setVisible(false);
        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);
        points.clear();
        canvasManager.clearTemp();
    }

    public boolean isClickInside(double x, double y) {
        return selectionPath.isVisible() && selectionPath.getBoundsInParent().contains(x, y);
    }

    public boolean isCreatingNewSelection() { return creatingNewSelection; }

    private void applyPathToGC(GraphicsContext gc, double transX, double transY) {
        gc.beginPath();
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            if (i == 0) gc.moveTo(p.getX() + transX, p.getY() + transY);
            else gc.lineTo(p.getX() + transX, p.getY() + transY);
        }
        gc.closePath();
    }

    public void registerShortcuts(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN), this::copy);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN), this::paste);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D), this::clearSelection);
    }

    public void copy() { if (hasSelection) clipboardImage = selectionImage; }

    public void paste() {
        if (clipboardImage == null) return;
        commitSelection();
        selectionImage = clipboardImage;
        selX = 50; selY = 50;
        selW = selectionImage.getWidth();
        selH = selectionImage.getHeight();
        hasSelection = true;
        selectionCut = true;
        layerAfterCut = new WritableImage(
                (int) canvasManager.getCanvasWidth(),
                (int) canvasManager.getCanvasHeight());
        layerManager.getActiveLayer().canvas.snapshot(null, layerAfterCut);
        points.clear();
        points.add(new Point2D(selX, selY));
        points.add(new Point2D(selX + selW, selY));
        points.add(new Point2D(selX + selW, selY + selH));
        points.add(new Point2D(selX, selY + selH));
        selectionPath.getElements().clear();
        selectionPath.getElements().add(new MoveTo(selX, selY));
        selectionPath.getElements().add(new LineTo(selX + selW, selY));
        selectionPath.getElements().add(new LineTo(selX + selW, selY + selH));
        selectionPath.getElements().add(new LineTo(selX, selY + selH));
        selectionPath.getElements().add(new ClosePath());
        selectionPath.setTranslateX(0);
        selectionPath.setTranslateY(0);
        selectionPath.setVisible(true);
        drawPreview();
    }

    public boolean hasSelection() { return hasSelection; }

    public void applyClipping(GraphicsContext gc) {
        if (hasSelection) {
            gc.save();
            applyPathToGC(gc, selectionPath.getTranslateX(), selectionPath.getTranslateY());
            gc.clip();
        }
    }

    public void restoreClipping(GraphicsContext gc) {
        if (hasSelection) gc.restore();
    }
}