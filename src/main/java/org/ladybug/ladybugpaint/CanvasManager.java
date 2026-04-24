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

    // ================= CONSTANTS =================

    // Minimum allowed zoom level (5% of original size)
    private static final double ZOOM_MINIMUM = 0.05;

    // Maximum allowed zoom level (3200% of original size)
    private static final double ZOOM_MAXIMUM = 32.0;

    // Fractional zoom step used by the button-based zoom in/out controls
    private static final double ZOOM_BUTTON_STEP = 0.12;

    // ================= FIELDS =================

    // The stacking pane that holds all layer canvases and the temp canvas
    private final StackPane canvasStack = new StackPane();

    // Transparent overlay canvas used for shape previews and selection indicators
    private Canvas tempCanvas;

    // Graphics context for the temp canvas
    private GraphicsContext tempGraphicsContext;

    // Width of the drawing canvas in pixels
    private double canvasWidth = 0;

    // Height of the drawing canvas in pixels
    private double canvasHeight = 0;

    // Current zoom scale factor (1.0 = 100%)
    private double zoomFactor = 1.0;

    // The Group wrapping canvasStack; zoom scale transform is applied here
    // so canvas coordinate space is never distorted
    private Group zoomGroup;

    // ================= ACCESSORS =================

    /**
     * Returns the temp overlay canvas used for shape and selection previews.
     */
    public Canvas getTempCanvas() {
        return tempCanvas;
    }

    /**
     * Returns the StackPane that contains all layer canvases.
     */
    public StackPane getCanvasStack() {
        return canvasStack;
    }

    /**
     * Returns the GraphicsContext of the temp overlay canvas.
     */
    public GraphicsContext getTempGc() {
        return tempGraphicsContext;
    }

    /**
     * Returns the canvas width in pixels.
     */
    public double getCanvasWidth() {
        return canvasWidth;
    }

    /**
     * Returns the canvas height in pixels.
     */
    public double getCanvasHeight() {
        return canvasHeight;
    }

    /**
     * Returns the current zoom factor.
     */
    public double getZoomFactor() {
        return zoomFactor;
    }

    // ================= INITIALISATION =================

    /**
     * Initialises the canvas surfaces at the given dimensions.
     * Must be called before any other method.
     *
     * @param width  canvas width in pixels; must be positive
     * @param height canvas height in pixels; must be positive
     */
    public void init(double width, double height) {
        this.canvasWidth  = width;
        this.canvasHeight = height;

        // Create the transparent temp overlay for shape/selection previews
        tempCanvas = new Canvas(width, height);
        tempGraphicsContext = tempCanvas.getGraphicsContext2D();

        // Mouse events should fall through the temp canvas to the layers beneath
        tempCanvas.setMouseTransparent(true);

        canvasStack.getChildren().add(tempCanvas);
        tempCanvas.toFront();
    }

    /**
     * Calculates and applies an initial zoom so the canvas fits within the available space.
     * Never zooms above 100% (zoomFactor is clamped to 1.0).
     *
     * @param availableWidth  the width of the area the canvas must fit inside
     * @param availableHeight the height of the area the canvas must fit inside
     */
    public void applyAutoZoom(double availableWidth, double availableHeight) {
        zoomFactor = Math.min(availableWidth / canvasWidth, availableHeight / canvasHeight);

        // Do not zoom in beyond 100% on startup
        if (zoomFactor > 1.0) {
            zoomFactor = 1.0;
        }

        applyZoomTransform();
    }

    private void attachPanning(Node scrollTarget) {
        scrollTarget.setOnScroll(scrollEvent -> {
            // Only pan if we are NOT performing a pinch-to-zoom (optional check)
            if (zoomGroup != null) {
                // Update translation based on scroll deltas
                // deltaX and deltaY are provided by the mouse wheel or trackpad
                zoomGroup.setTranslateX(zoomGroup.getTranslateX() + scrollEvent.getDeltaX());
                zoomGroup.setTranslateY(zoomGroup.getTranslateY() + scrollEvent.getDeltaY());
            }
            scrollEvent.consume();
        });
    }
    // ================= ZOOM =================

    /**
     * Attaches a pinch-to-zoom gesture handler to the given container node.
     *
     * The handler uses JavaFX's ZoomEvent (fired by trackpad pinch gestures and
     * touch screens) rather than scroll events, so two-finger scroll pans normally
     * and only a deliberate pinch gesture zooms.  Zoom is centred on the gesture
     * pivot reported by the event so the point under the fingers stays fixed.
     *
     * @param scrollTarget the node to attach the gesture handler to
     */
    public void attachZoom(Node scrollTarget) {
        // Pinch-to-zoom via ZoomEvent (trackpad pinch / touch screen)
        scrollTarget.setOnZoom(zoomEvent -> {
            double zoomDelta = zoomEvent.getZoomFactor();

            double oldZoomFactor = zoomFactor;
            zoomFactor = Math.max(ZOOM_MINIMUM,
                    Math.min(ZOOM_MAXIMUM, zoomFactor * zoomDelta));

            // Keep the gesture pivot point fixed on screen by adjusting the group translation
            double pivotInStackX = zoomEvent.getX() / oldZoomFactor;
            double pivotInStackY = zoomEvent.getY() / oldZoomFactor;

            double translationDeltaX = (pivotInStackX * oldZoomFactor)
                    - (pivotInStackX * zoomFactor);
            double translationDeltaY = (pivotInStackY * oldZoomFactor)
                    - (pivotInStackY * zoomFactor);

            if (zoomGroup != null) {
                zoomGroup.setTranslateX(zoomGroup.getTranslateX() + translationDeltaX);
                zoomGroup.setTranslateY(zoomGroup.getTranslateY() + translationDeltaY);
            }

            applyZoomTransform();
            zoomEvent.consume();
        });
    }

    /**
     * Zooms in by one button step, centred on the canvas centre.
     */
    public void zoomIn() {
        zoomFactor = Math.min(ZOOM_MAXIMUM, zoomFactor * (1.0 + ZOOM_BUTTON_STEP * 3));
        applyZoomTransform();
    }

    /**
     * Zooms out by one button step, centred on the canvas centre.
     */
    public void zoomOut() {
        zoomFactor = Math.max(ZOOM_MINIMUM, zoomFactor / (1.0 + ZOOM_BUTTON_STEP * 3));
        applyZoomTransform();
    }

    /**
     * Resets the zoom to 100% and clears all panning offsets so the canvas
     * is centred in the viewport.
     */
    public void zoomReset() {
        zoomFactor = 0.5;

        // Clear any translation accumulated during pinch/button zooming
        if (zoomGroup != null) {
            zoomGroup.setTranslateX(0);
            zoomGroup.setTranslateY(0);
        }

        canvasStack.setTranslateX(0);
        canvasStack.setTranslateY(0);

        applyZoomTransform();
    }

    /**
     * Applies the current zoom factor as a Scale transform on the canvasStack.
     * The side panels are not children of canvasStack so they are unaffected.
     */
    private void applyZoomTransform() {
        canvasStack.getTransforms().clear();
        canvasStack.getTransforms().add(new Scale(zoomFactor, zoomFactor));
        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);
    }

    // ================= LAYER MANAGEMENT =================

    /**
     * Adds a layer canvas to the canvas stack and keeps the temp canvas on top.
     *
     * @param layerCanvas the canvas to add
     */
    public void addLayerCanvas(Canvas layerCanvas) {
        canvasStack.getChildren().add(layerCanvas);
        tempCanvas.toFront();
    }

    /**
     * Removes a layer canvas from the canvas stack.
     *
     * @param layerCanvas the canvas to remove
     */
    public void removeLayerCanvas(Canvas layerCanvas) {
        canvasStack.getChildren().remove(layerCanvas);
        tempCanvas.toFront();
    }

    // ================= SNAPSHOT =================

    /**
     * Returns a full-resolution composite snapshot of all visible layers.
     * The temp canvas and any selection path overlays are hidden for the snapshot.
     */
    public WritableImage snapshotFull() {
        boolean tempWasVisible = tempCanvas.isVisible();
        tempCanvas.setVisible(false);

        // Also hide any selection path overlay that may be in the canvas stack
        Node selectionPathNode = canvasStack.getChildren().stream()
                .filter(node -> node instanceof javafx.scene.shape.Path)
                .findFirst()
                .orElse(null);

        boolean pathWasVisible = false;
        if (selectionPathNode != null) {
            pathWasVisible = selectionPathNode.isVisible();
            selectionPathNode.setVisible(false);
        }

        // Snapshot at 1:1 scale regardless of current zoom
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        snapshotParameters.setTransform(new Scale(1.0 / zoomFactor, 1.0 / zoomFactor));

        WritableImage resultImage = new WritableImage((int) canvasWidth, (int) canvasHeight);
        canvasStack.snapshot(snapshotParameters, resultImage);

        // Restore visibility
        tempCanvas.setVisible(tempWasVisible);
        if (selectionPathNode != null) {
            selectionPathNode.setVisible(pathWasVisible);
        }

        return resultImage;
    }

    /**
     * Returns a transparent snapshot of any single node at its natural size.
     *
     * @param targetNode the node to snapshot
     */
    public WritableImage snapshot(Node targetNode) {
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);

        int snapshotWidth  = (int) targetNode.getBoundsInLocal().getWidth();
        int snapshotHeight = (int) targetNode.getBoundsInLocal().getHeight();

        return targetNode.snapshot(snapshotParameters,
                new WritableImage(snapshotWidth, snapshotHeight));
    }

    /**
     * Clears all pixels on the temp overlay canvas.
     */
    public void clearTemp() {
        tempGraphicsContext.clearRect(0, 0, canvasWidth, canvasHeight);
    }

    /**
     * Sets the opacity of the temp overlay canvas.
     *
     * @param opacity opacity value between 0.0 (transparent) and 1.0 (opaque)
     */
    public void setTempOpacity(double opacity) {
        tempCanvas.setOpacity(opacity);
        tempCanvas.toFront();
    }

    // ================= VIEW CREATION =================

    /**
     * Creates and returns the centred canvas viewport.
     *
     * The canvas stack is wrapped in a Group so that the Scale transform applied
     * during zoom does not affect the surrounding BorderPane layout — specifically
     * the left (layer panel) and right (color panel) side panels remain at their
     * fixed sizes regardless of zoom level.
     *
     * The pinch-to-zoom gesture handler is attached to the returned StackPane container.
     *
     * @return a StackPane suitable for placement in BorderPane.setCenter()
     */
    public StackPane createCenteredView() {
        zoomGroup = new Group(canvasStack);

        // Dark workspace background — visible around the canvas when zoomed out
        StackPane viewportContainer = new StackPane(zoomGroup);
        viewportContainer.setStyle("-fx-background-color: #2a2a30;");
        attachZoom(viewportContainer);
        attachPanning(viewportContainer);
        // Attach pinch-to-zoom to the viewport container only.
        // Because this container is only the center region of the BorderPane,
        // the side panels are never inside it and therefore never affected by zoom.
        attachZoom(viewportContainer);

        return viewportContainer;
    }
}