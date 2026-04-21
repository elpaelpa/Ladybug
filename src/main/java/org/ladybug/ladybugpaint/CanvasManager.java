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

    // ================= CORE =================
    private final StackPane canvasStack = new StackPane();

    private Canvas tempCanvas;
    private GraphicsContext tempGc;

    private double canvasWidth;
    private double canvasHeight;
    private double zoomFactor = 1.0;

    // ================= INIT =================
    public void init(double width, double height) {
        this.canvasWidth = width;
        this.canvasHeight = height;

        tempCanvas = new Canvas(width, height);
        tempGc = tempCanvas.getGraphicsContext2D();

        tempCanvas.setMouseTransparent(true);

        canvasStack.getChildren().add(tempCanvas);

        // 🔥 FIX: always keep temp canvas on top
        tempCanvas.toFront();
    }

    // ================= ZOOM =================
    public void applyAutoZoom(double availableW, double availableH) {
        zoomFactor = Math.min(availableW / canvasWidth, availableH / canvasHeight);
        if (zoomFactor > 1.0) zoomFactor = 1.0;

        canvasStack.getTransforms().clear();
        canvasStack.getTransforms().add(new Scale(zoomFactor, zoomFactor));

        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    // ================= GETTERS =================
    public StackPane getCanvasStack() {
        return canvasStack;
    }

    public Canvas getTempCanvas() {
        return tempCanvas;
    }

    public GraphicsContext getTempGc() {
        return tempGc;
    }

    public double getCanvasWidth() {
        return canvasWidth;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    // ================= LAYER MANAGEMENT =================
    public void addLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().add(canvas);

        // 🔥 CRITICAL: keep temp canvas above all layers
        tempCanvas.toFront();
    }

    public void removeLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().remove(canvas);

        // 🔥 Safety: keep temp canvas on top even after removal
        tempCanvas.toFront();
    }

    // ================= SNAPSHOT =================
    public WritableImage snapshot(Node node) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setTransform(new Scale(1, 1)); // ignore zoom

        int w = (int) node.getBoundsInLocal().getWidth();
        int h = (int) node.getBoundsInLocal().getHeight();

        WritableImage snap = new WritableImage(w, h);
        return node.snapshot(sp, snap);
    }

    public WritableImage snapshotFull() {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setTransform(new Scale(1 / zoomFactor, 1 / zoomFactor));

        WritableImage snap = new WritableImage((int) canvasWidth, (int) canvasHeight);
        return canvasStack.snapshot(sp, snap);
    }

    // ================= TEMP CANVAS =================
    public void clearTemp() {
        tempGc.clearRect(0, 0, canvasWidth, canvasHeight);
    }

    public void setTempOpacity(double opacity) {
        tempCanvas.setOpacity(opacity);

        // 🔥 EXTRA SAFETY: ensure visibility every time it's used
        tempCanvas.toFront();
    }

    // ================= VIEW WRAPPER =================
    public StackPane createCenteredView() {
        return new StackPane(new Group(canvasStack));
    }
}