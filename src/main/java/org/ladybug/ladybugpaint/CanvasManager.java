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
    public Canvas getTempCanvas() {
        return tempCanvas;
    }
    public void init(double width, double height) {
        this.canvasWidth = width;
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
        canvasStack.getTransforms().clear();
        canvasStack.getTransforms().add(new Scale(zoomFactor, zoomFactor));
        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);
    }

    public StackPane getCanvasStack() { return canvasStack; }
    public GraphicsContext getTempGc() { return tempGc; }
    public double getCanvasWidth() { return canvasWidth; }
    public double getCanvasHeight() { return canvasHeight; }

    public void addLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().add(canvas);
        tempCanvas.toFront();
    }

    public void removeLayerCanvas(Canvas canvas) {
        canvasStack.getChildren().remove(canvas);
        tempCanvas.toFront();
    }

    public WritableImage snapshotFull() {
        // Hide the temporary preview and the path lines before snapping
        boolean tempVis = tempCanvas.isVisible();
        tempCanvas.setVisible(false);

        // Find the selection path if it exists and hide it too
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
        sp.setTransform(new Scale(1 / zoomFactor, 1 / zoomFactor));

        WritableImage snap = new WritableImage((int) canvasWidth, (int) canvasHeight);
        WritableImage result = canvasStack.snapshot(sp, snap);

        // Restore visibility
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

    public StackPane createCenteredView() {
        return new StackPane(new Group(canvasStack));
    }

    public WritableImage snapshot(Node node) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        int w = (int) node.getBoundsInLocal().getWidth();
        int h = (int) node.getBoundsInLocal().getHeight();
        return node.snapshot(sp, new WritableImage(w, h));
    }
}