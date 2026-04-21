package org.ladybug.ladybugpaint;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

public class CanvasManager {

    private final StackPane canvasStack = new StackPane();
    private Canvas tempCanvas;
    private GraphicsContext tempGc;
    private double canvasWidth;
    private double canvasHeight;
    private double zoomFactor = 1.0;

    // The outer Group that wraps canvasStack — zoom is applied here
    // so the canvas coordinate system is never distorted.
    private Group zoomGroup;
    // Pivot offsets used to keep zoom centred on the mouse position
    private double pivotX = 0;
    private double pivotY = 0;

    private static final double ZOOM_MIN  = 0.05;
    private static final double ZOOM_MAX  = 32.0;
    private static final double ZOOM_STEP = 0.12; // fractional step per scroll tick

    public Canvas getTempCanvas() { return tempCanvas; }

    public void init(double width, double height) {
        this.canvasWidth  = width;
        this.canvasHeight = height;
        tempCanvas = new Canvas(width, height);
        tempGc = tempCanvas.getGraphicsContext2D();
        tempCanvas.setMouseTransparent(true);
        canvasStack.getChildren().add(tempCanvas);
        tempCanvas.toFront();
    }

    public void applyAutoZoom(double availableW, double availableH) {
        zoomFactor = Math.min(availableW / canvasWidth, availableH / canvasHeight);
        if (zoomFactor > 1.0) zoomFactor = 1.0;
        applyZoomTransform();
    }

    // ================= ZOOM =================

    /**
     * Attaches smooth scroll-to-zoom to the given container node.
     * Zoom is centred on the mouse cursor so the point under the cursor
     * stays fixed as you zoom in/out
     *
     * Call this after createCenteredView() so zoomGroup is initialised.
     */
    public void attachZoom(Node scrollTarget) {
        scrollTarget.setOnScroll(e -> {
            if (e.getDeltaY() == 0) return;

            double oldZoom = zoomFactor;
            double factor  = e.getDeltaY() > 0
                    ? (1.0 + ZOOM_STEP)
                    : (1.0 / (1.0 + ZOOM_STEP));

            zoomFactor = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomFactor * factor));

            // Convert mouse position to canvas-stack-local coords at the OLD zoom
            // so we can compute how much to translate to keep that point fixed.
            double mouseInStackX = e.getX() / oldZoom;
            double mouseInStackY = e.getY() / oldZoom;

            // After zoom, the same canvas point appears at a different screen position.
            // Adjust the group's translate to compensate.
            double dx = (mouseInStackX * oldZoom) - (mouseInStackX * zoomFactor);
            double dy = (mouseInStackY * oldZoom) - (mouseInStackY * zoomFactor);

            if (zoomGroup != null) {
                zoomGroup.setTranslateX(zoomGroup.getTranslateX() + dx);
                zoomGroup.setTranslateY(zoomGroup.getTranslateY() + dy);
            }

            applyZoomTransform();
            e.consume();
        });
    }

    /** Zoom in by one step, centred on the canvas centre. */
    public void zoomIn() {
        zoomFactor = Math.min(ZOOM_MAX, zoomFactor * (1.0 + ZOOM_STEP * 3));
        applyZoomTransform();
    }

    /** Zoom out by one step, centred on the canvas centre. */
    public void zoomOut() {
        zoomFactor = Math.max(ZOOM_MIN, zoomFactor / (1.0 + ZOOM_STEP * 3));
        applyZoomTransform();
    }

    /** Reset zoom to fit the canvas in the available space (same as initial auto-zoom). */
    /** * Reset zoom to 100% (1:1 scale) and center the canvas
     * by clearing all translation offsets.
     */
    public void zoomReset() {
        // 1. Reset the zoom factor to 100%
        zoomFactor = 1.0;

        // 2. Clear any panning/offsets applied during scroll-zoom
        if (zoomGroup != null) {
            zoomGroup.setTranslateX(0);
            zoomGroup.setTranslateY(0);
        }

        // 3. Ensure the canvasStack itself isn't carrying internal offsets
        canvasStack.setTranslateX(0);
        canvasStack.setTranslateY(0);

        // 4. Re-apply the scale transform
        applyZoomTransform();
    }

    public double getZoomFactor() { return zoomFactor; }

    private void applyZoomTransform() {
        canvasStack.getTransforms().clear();
        canvasStack.getTransforms().add(new Scale(zoomFactor, zoomFactor));
        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);
    }

    // ================= ACCESSORS =================

    public StackPane getCanvasStack() { return canvasStack; }
    public GraphicsContext getTempGc() { return tempGc; }
    public double getCanvasWidth()  { return canvasWidth; }
    public double getCanvasHeight() { return canvasHeight; }

    public void addLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().add(canvas);
        tempCanvas.toFront();
    }

    public void removeLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().remove(canvas);
        tempCanvas.toFront();
    }

    // ================= SNAPSHOT =================

    public WritableImage snapshotFull() {
        boolean tempVis = tempCanvas.isVisible();
        tempCanvas.setVisible(false);

        Node selPath = canvasStack.getChildren().stream()
                .filter(n -> n instanceof javafx.scene.shape.Path)
                .findFirst().orElse(null);
        boolean pathVis = false;
        if (selPath != null) {
            pathVis = selPath.isVisible();
            selPath.setVisible(false);
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setTransform(new Scale(1.0 / zoomFactor, 1.0 / zoomFactor));

        WritableImage snap   = new WritableImage((int) canvasWidth, (int) canvasHeight);
        WritableImage result = canvasStack.snapshot(sp, snap);

        tempCanvas.setVisible(tempVis);
        if (selPath != null) selPath.setVisible(pathVis);

        return result;
    }

    public void clearTemp() {
        tempGc.clearRect(0, 0, canvasWidth, canvasHeight);
    }

    public void setTempOpacity(double opacity) {
        tempCanvas.setOpacity(opacity);
        tempCanvas.toFront();
    }

    /**
     * Returns a centred, scrollable view of the canvas.
     * Zoom is applied to the inner Group so panning (via translate) is independent.
     */
    public StackPane createCenteredView() {
        zoomGroup = new Group(canvasStack);
        StackPane container = new StackPane(zoomGroup);
        container.setStyle("-fx-background-color: #3a3a3a;"); // checkerboard-grey workspace bg
        // Attach zoom scroll handler to the container
        attachZoom(container);
        return container;
    }

    public WritableImage snapshot(Node node) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        int w = (int) node.getBoundsInLocal().getWidth();
        int h = (int) node.getBoundsInLocal().getHeight();
        return node.snapshot(sp, new WritableImage(w, h));
    }
}