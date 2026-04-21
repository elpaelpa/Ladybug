package org.ladybug.ladybugpaint;

import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class SelectionManager {

    // ================= DEPENDENCIES =================
    private final CanvasManager canvasManager;
    private final LayerManager layerManager;

    // ================= SELECTION STATE =================
    private final Rectangle selectionRect = new Rectangle();

    private WritableImage selectionImage = null;
    private WritableImage clipboardImage = null;

    private boolean hasSelection = false;
    private boolean dragging = false;

    private double selX, selY, selW, selH;
    private double offsetX, offsetY;

    private double startX, startY;

    // marching ants
    private double dashOffset = 0;

    // ================= CONSTRUCTOR =================
    public SelectionManager(CanvasManager cm, LayerManager lm) {
        this.canvasManager = cm;
        this.layerManager = lm;

        setupSelectionRect();
        setupMarchingAnts();
    }

    // ================= INIT =================
    private void setupSelectionRect() {
        selectionRect.setStroke(Color.BLACK);
        selectionRect.setFill(Color.TRANSPARENT);
        selectionRect.getStrokeDashArray().addAll(6.0, 6.0);
        selectionRect.setVisible(false);

        canvasManager.getCanvasStack().getChildren().add(selectionRect);
    }

    private void setupMarchingAnts() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (hasSelection) {
                    dashOffset += 0.5;
                    selectionRect.setStrokeDashOffset(dashOffset);
                }
            }
        }.start();
    }

    // ================= START SELECTION =================
    public void startSelection(double x, double y) {
        hasSelection = false;
        startX = x;
        startY = y;

        selectionRect.setVisible(true);
        selectionRect.setX(x);
        selectionRect.setY(y);
        selectionRect.setWidth(0);
        selectionRect.setHeight(0);

        canvasManager.clearTemp();
    }

    // ================= UPDATE SELECTION =================
    public void updateSelection(double x, double y) {
        double minX = Math.min(startX, x);
        double minY = Math.min(startY, y);
        double w = Math.abs(x - startX);
        double h = Math.abs(y - startY);

        selectionRect.setX(minX);
        selectionRect.setY(minY);
        selectionRect.setWidth(w);
        selectionRect.setHeight(h);
    }

    // ================= FINISH SELECTION =================
    public void finalizeSelection() {
        selX = selectionRect.getX();
        selY = selectionRect.getY();
        selW = selectionRect.getWidth();
        selH = selectionRect.getHeight();

        if (selW <= 0 || selH <= 0) return;

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(selX, selY, selW, selH));

        // Capture selected pixels
        selectionImage = layerManager.getActiveLayer().canvas.snapshot(sp, null);

        // 🔥 FIX: remove pixels from original layer
        layerManager.getActiveLayer().gc.clearRect(selX, selY, selW, selH);

        hasSelection = true;

        // 🔥 FIX: show preview immediately
        drawPreview();
    }

    // ================= MOVE =================
    public void startMove(double x, double y) {
        if (!hasSelection) return;

        dragging = true;
        offsetX = x - selX;
        offsetY = y - selY;
    }

    public void updateMove(double x, double y) {
        if (!dragging || selectionImage == null) return;

        selX = x - offsetX;
        selY = y - offsetY;

        selectionRect.setX(selX);
        selectionRect.setY(selY);

        GraphicsContext tempGc = canvasManager.getTempGc();

        // 🔥 ensure visible
        canvasManager.setTempOpacity(1.0);

        tempGc.clearRect(0, 0,
                canvasManager.getCanvasWidth(),
                canvasManager.getCanvasHeight());

        tempGc.drawImage(selectionImage, selX, selY);
    }

    public void endMove() {
        dragging = false;
    }

    // ================= DRAW PREVIEW =================
    public void drawPreview() {
        if (!hasSelection || dragging || selectionImage == null) return;

        GraphicsContext tempGc = canvasManager.getTempGc();

        canvasManager.setTempOpacity(1.0);

        tempGc.clearRect(0, 0,
                canvasManager.getCanvasWidth(),
                canvasManager.getCanvasHeight());

        tempGc.drawImage(selectionImage, selX, selY);
    }

    // ================= COMMIT =================
    public void commitSelection() {
        if (!hasSelection || selectionImage == null) return;

        layerManager.saveState(
                layerManager.getActiveLayer(),
                canvasManager.getCanvasWidth(),
                canvasManager.getCanvasHeight()
        );

        layerManager.getActiveLayer().gc.drawImage(selectionImage, selX, selY);

        clearSelection();
    }

    // ================= CLEAR =================
    public void clearSelection() {
        hasSelection = false;
        selectionImage = null;

        selectionRect.setVisible(false);
        canvasManager.clearTemp();
    }

    // ================= COPY / PASTE =================
    public void copy() {
        if (hasSelection) {
            clipboardImage = selectionImage;
        }
    }

    public void paste() {
        if (clipboardImage == null) return;

        selectionImage = clipboardImage;

        selX = 50;
        selY = 50;
        selW = selectionImage.getWidth();
        selH = selectionImage.getHeight();

        hasSelection = true;

        selectionRect.setVisible(true);
        selectionRect.setX(selX);
        selectionRect.setY(selY);
        selectionRect.setWidth(selW);
        selectionRect.setHeight(selH);

        drawPreview();
    }

    // ================= SHORTCUTS =================
    public void registerShortcuts(javafx.scene.Scene scene) {

        scene.getAccelerators().put(
                new KeyCodeCombination(javafx.scene.input.KeyCode.C, KeyCombination.CONTROL_DOWN),
                this::copy
        );

        scene.getAccelerators().put(
                new KeyCodeCombination(javafx.scene.input.KeyCode.V, KeyCombination.CONTROL_DOWN),
                this::paste
        );
    }

    // ================= GETTERS =================
    public boolean hasSelection() {
        return hasSelection;
    }

    public double[] getBounds() {
        return new double[]{selX, selY, selW, selH};
    }
}