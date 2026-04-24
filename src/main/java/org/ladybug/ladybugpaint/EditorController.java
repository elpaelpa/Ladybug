package org.ladybug.ladybugpaint;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.image.WritableImage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;

public class EditorController {

    // ================= CONSTANTS =================

    // Minimum allowed canvas dimension in pixels
    private static final double MINIMUM_CANVAS_DIMENSION = 100;

    // Maximum allowed canvas dimension in pixels
    private static final double MAXIMUM_CANVAS_DIMENSION = 8000;

    // Default canvas width in pixels
    private static final double DEFAULT_CANVAS_WIDTH = 1920;

    // Default canvas height in pixels
    private static final double DEFAULT_CANVAS_HEIGHT = 1080;

    // Default maximum brush size shown on the slider (user can raise this in settings)
    private static final double DEFAULT_MAX_BRUSH_SIZE = 100;

    // Absolute ceiling the user can unlock for the brush size maximum
    private static final double ABSOLUTE_MAX_BRUSH_SIZE = 1000;

    // Smallest value the user can set as the brush size maximum
    private static final double MINIMUM_MAX_BRUSH_SIZE = 100;

    // ================= FIELDS =================

    // The primary application window
    private final Stage primaryStage;

    // Slider controlling the brush stroke width; upper bound is adjustable via settings
    private final Slider brushSizeSlider = new Slider(1, DEFAULT_MAX_BRUSH_SIZE, 5);

    // Slider controlling the brush transparency level
    private final Slider brushOpacitySlider = new Slider(0, 1, 1);

    // Color picker widget used to select the active drawing color
    private final ColorPicker colorPicker = new ColorPicker(Color.BLACK);

    // Width of the drawing canvas in pixels
    private double canvasWidth = DEFAULT_CANVAS_WIDTH;

    // Height of the drawing canvas in pixels
    private double canvasHeight = DEFAULT_CANVAS_HEIGHT;

    // The currently active upper bound on the brush size slider
    private double currentMaxBrushSize = DEFAULT_MAX_BRUSH_SIZE;

    // Manages canvas zoom, panning, and layer stacking
    private CanvasManager canvasManager;

    // Manages the list of drawing layers
    private LayerManager layerManager;

    // Manages tool selection and toolbar UI
    private ToolManager toolManager;

    // Manages selection operations on the canvas
    private SelectionManager selectionManager;

    // Manages all drawing operations on the canvas
    private DrawingManager drawingManager;

    // ================= CONSTRUCTOR =================

    /**
     * Constructs the editor controller bound to the given application stage.
     *
     * @param primaryStage the main application window; must not be null
     */
    public EditorController(Stage primaryStage) {
        // Validate that the stage is not null before assigning
        if (primaryStage != null) {
            this.primaryStage = primaryStage;
        } else {
            // Assign null as sentinel; the application cannot function without a stage
            this.primaryStage = null;
        }

        // Force the color picker to open directly into the custom color editor
        // rather than showing the default swatch palette first
        colorPicker.getStyleClass().add("button");
    }

    // ================= SCENE CREATION =================

    /**
     * Returns the initial scene shown when the application launches.
     */
    public Scene createScene() {
        return createHomeScene();
    }

    /**
     * Builds and returns the home/welcome screen scene.
     */
    private Scene createHomeScene() {
        // Attempt to load the logo from application resources
        ImageView logoImageView = null;
        try {
            Image logoImage = new Image(Objects.<InputStream>requireNonNull(
                    getClass().getResourceAsStream("Ladybug.png")));
            logoImageView = new ImageView(logoImage);
            logoImageView.setFitWidth(380);
            logoImageView.setPreserveRatio(true);
        } catch (Exception loadException) {
            System.out.println("Logo not found: " + loadException.getMessage());
        }

        // Application title
        Label titleLabel = new Label("Ladybug Paint");
        titleLabel.getStyleClass().add("title-label");

        // Subdued tagline beneath the title
        Label taglineLabel = new Label("A clean canvas for every idea.");
        taglineLabel.setStyle("-fx-text-fill: #504d58; -fx-font-size: 13px;");

        // Canvas dimension inputs
        TextField widthInputField  = new TextField("1920");
        TextField heightInputField = new TextField("1080");
        widthInputField.setPrefWidth(88);
        heightInputField.setPrefWidth(88);

        // Small heading above the dimension inputs
        Label dimensionHeadingLabel = new Label("CANVAS SIZE");
        dimensionHeadingLabel.getStyleClass().add("section-header");

        // Row of width × height inputs
        HBox resolutionInputBox = new HBox(8,
                new Label("W"), widthInputField,
                new Label("×"), heightInputField,
                new Label("px"));
        resolutionInputBox.setAlignment(Pos.CENTER);

        VBox dimensionBox = new VBox(6, dimensionHeadingLabel, resolutionInputBox);
        dimensionBox.setAlignment(Pos.CENTER);

        // Prominent call-to-action button in brand rose
        Button newProjectButton = new Button("New Project");
        newProjectButton.setStyle(
                "-fx-background-color: #b86070; -fx-text-fill: #fff0f2; " +
                        "-fx-font-size: 14px; -fx-font-weight: 700; " +
                        "-fx-padding: 12 40; -fx-background-radius: 8; " +
                        "-fx-border-color: transparent; -fx-cursor: hand;");

        newProjectButton.setOnMouseEntered(event ->
                newProjectButton.setStyle(
                        "-fx-background-color: #c87080; -fx-text-fill: #fff0f2; " +
                                "-fx-font-size: 14px; -fx-font-weight: 700; " +
                                "-fx-padding: 12 40; -fx-background-radius: 8; " +
                                "-fx-border-color: transparent; -fx-cursor: hand;"));

        newProjectButton.setOnMouseExited(event ->
                newProjectButton.setStyle(
                        "-fx-background-color: #b86070; -fx-text-fill: #fff0f2; " +
                                "-fx-font-size: 14px; -fx-font-weight: 700; " +
                                "-fx-padding: 12 40; -fx-background-radius: 8; " +
                                "-fx-border-color: transparent; -fx-cursor: hand;"));

        newProjectButton.setOnAction(event -> {
            // Parse user-entered dimensions; fall back to defaults on invalid input
            try {
                double parsedWidth  = Double.parseDouble(widthInputField.getText());
                double parsedHeight = Double.parseDouble(heightInputField.getText());

                if (parsedWidth >= MINIMUM_CANVAS_DIMENSION
                        && parsedWidth <= MAXIMUM_CANVAS_DIMENSION) {
                    canvasWidth = parsedWidth;
                } else {
                    canvasWidth = DEFAULT_CANVAS_WIDTH;
                }

                if (parsedHeight >= MINIMUM_CANVAS_DIMENSION
                        && parsedHeight <= MAXIMUM_CANVAS_DIMENSION) {
                    canvasHeight = parsedHeight;
                } else {
                    canvasHeight = DEFAULT_CANVAS_HEIGHT;
                }
            } catch (NumberFormatException parseException) {
                canvasWidth  = DEFAULT_CANVAS_WIDTH;
                canvasHeight = DEFAULT_CANVAS_HEIGHT;
            }
            primaryStage.setScene(createEditorScene());
        });

        // Assemble home screen layout, with or without logo
        VBox homeContentBox;
        if (logoImageView != null) {
            homeContentBox = new VBox(24,
                    logoImageView, titleLabel, taglineLabel,
                    new Separator(), dimensionBox, newProjectButton);
        } else {
            homeContentBox = new VBox(24,
                    titleLabel, taglineLabel,
                    new Separator(), dimensionBox, newProjectButton);
        }
        homeContentBox.setAlignment(Pos.CENTER);
        homeContentBox.setPadding(new Insets(60, 0, 60, 0));
        homeContentBox.setMaxWidth(460);

        // Dark full-screen background
        StackPane homeRoot = new StackPane(homeContentBox);
        homeRoot.setStyle("-fx-background-color: #1a1a1f;");

        Scene homeScene = new Scene(homeRoot, 1200, 750);
        applyStylesheet(homeScene);
        return homeScene;
    }

    /**
     * Builds and returns the main drawing editor scene.
     */
    private Scene createEditorScene() {
        BorderPane rootLayout = new BorderPane();
        rootLayout.setStyle("-fx-background-color: #1a1a1f;");

        // Initialise all sub-managers
        canvasManager    = new CanvasManager();
        canvasManager.init(canvasWidth, canvasHeight);
        canvasManager.applyAutoZoom(850, 600);

        layerManager     = new LayerManager(canvasManager.getCanvasStack());
        toolManager      = new ToolManager(brushSizeSlider, brushOpacitySlider, colorPicker,
                currentMaxBrushSize);
        selectionManager = new SelectionManager(canvasManager, layerManager);
        drawingManager   = new DrawingManager(canvasManager, layerManager, toolManager,
                selectionManager);

        // Add the background layer filled white
        layerManager.addLayer("Background", canvasWidth, canvasHeight, brushSizeSlider.getValue());
        layerManager.getActiveLayer().gc.setFill(Color.WHITE);
        layerManager.getActiveLayer().gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // Add the default transparent drawing layer
        layerManager.addLayer("Layer 1", canvasWidth, canvasHeight, brushSizeSlider.getValue());

        drawingManager.attachMouseEvents();

        // FIX: Wrap the zoomable canvas view in a clipped Pane to keep UI menus in place
        StackPane canvasView = canvasManager.createCenteredView();
        Pane canvasViewport = new Pane(canvasView);
        canvasViewport.getStyleClass().add("canvas-wrapper");

        // Apply a clipping rectangle to ensure zoomed content stays in the center region
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(canvasViewport.widthProperty());
        clip.heightProperty().bind(canvasViewport.heightProperty());
        canvasViewport.setClip(clip);

        rootLayout.setCenter(canvasViewport);
        rootLayout.setLeft(layerManager.createLayerPanel());
        rootLayout.setRight(createColorPanel());

        // Toolbar — import and export have been moved to the bottom bar
        rootLayout.setTop(toolManager.createToolbar(
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight),
                canvasManager::zoomIn,
                canvasManager::zoomOut,
                canvasManager::zoomReset
        ));

        // Bottom bar contains import, export, and settings anchored to the right
        rootLayout.setBottom(createBottomBar());

        Scene editorScene = new Scene(rootLayout, 1200, 750);

        // Register keyboard shortcuts
        toolManager.registerShortcuts(editorScene,
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight));
        selectionManager.registerShortcuts(editorScene);

        // Keyboard zoom shortcuts
        editorScene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.EQUALS,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomIn);
        editorScene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.MINUS,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomOut);
        editorScene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DIGIT0,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomReset);

        applyStylesheet(editorScene);
        return editorScene;
    }

    // ================= UI PANELS =================

    /**
     * Creates the right-side panel containing the color picker and brush controls.
     * Includes live value readouts for the brush size and opacity sliders.
     */
    private VBox createColorPanel() {
        // Section headings styled as small uppercase labels
        Label colorHeadingLabel = new Label("COLOR");
        colorHeadingLabel.getStyleClass().add("section-header");

        Label brushHeadingLabel = new Label("BRUSH");
        brushHeadingLabel.getStyleClass().add("section-header");

        // Live pixel readout for the brush size slider
        Label brushSizeValueLabel = new Label(
                (int) brushSizeSlider.getValue() + " px");
        brushSizeValueLabel.setStyle("-fx-text-fill: #b86070; -fx-font-size: 11px;");
        brushSizeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                brushSizeValueLabel.setText(newValue.intValue() + " px"));

        HBox brushSizeLabelRow = new HBox();
        brushSizeLabelRow.setAlignment(Pos.CENTER_LEFT);
        Region brushSizeSpacerRegion = new Region();
        HBox.setHgrow(brushSizeSpacerRegion, Priority.ALWAYS);
        brushSizeLabelRow.getChildren().addAll(
                new Label("Size"), brushSizeSpacerRegion, brushSizeValueLabel);

        // Live percentage readout for the opacity slider
        Label brushOpacityValueLabel = new Label(
                (int) (brushOpacitySlider.getValue() * 100) + "%");
        brushOpacityValueLabel.setStyle("-fx-text-fill: #b86070; -fx-font-size: 11px;");
        brushOpacitySlider.valueProperty().addListener((observable, oldValue, newValue) ->
                brushOpacityValueLabel.setText((int) (newValue.doubleValue() * 100) + "%"));

        HBox brushOpacityLabelRow = new HBox();
        brushOpacityLabelRow.setAlignment(Pos.CENTER_LEFT);
        Region opacitySpacerRegion = new Region();
        HBox.setHgrow(opacitySpacerRegion, Priority.ALWAYS);
        brushOpacityLabelRow.getChildren().addAll(
                new Label("Opacity"), opacitySpacerRegion, brushOpacityValueLabel);

        // Make sliders stretch to fill the panel width
        brushSizeSlider.setMaxWidth(Double.MAX_VALUE);
        brushOpacitySlider.setMaxWidth(Double.MAX_VALUE);

        VBox colorPanel = new VBox(10,
                colorHeadingLabel,
                colorPicker,
                new Separator(),
                brushHeadingLabel,
                brushSizeLabelRow,
                brushSizeSlider,
                brushOpacityLabelRow,
                brushOpacitySlider);

        colorPanel.setPrefWidth(178);
        colorPanel.setStyle(
                "-fx-padding: 14 12 14 12; " +
                        "-fx-background-color: #1c1c22; " +
                        "-fx-border-color: transparent transparent transparent #2a2838; " +
                        "-fx-border-width: 0 0 0 1;");
        return colorPanel;
    }

    /**
     * Creates the bottom status bar.
     * Import and export buttons sit above the settings button, all anchored to the right.
     */
    private HBox createBottomBar() {
        // Spacer pushes the button group to the far right
        Region spacerRegion = new Region();
        HBox.setHgrow(spacerRegion, Priority.ALWAYS);

        // File operation buttons placed side by side
        Button importButton = new Button("↑  Import");
        Button exportButton = new Button("↓  Export");
        importButton.setOnAction(event -> importImage());
        exportButton.setOnAction(event -> exportImage());

        HBox fileButtonRow = new HBox(6, importButton, exportButton);
        fileButtonRow.setAlignment(Pos.CENTER_RIGHT);

        // Settings button beneath the file buttons
        Button settingsButton = new Button("⚙  Settings");
        settingsButton.setOnAction(event -> openSettingsDialog());

        // Stack the file row above settings
        VBox rightButtonGroup = new VBox(6, fileButtonRow, settingsButton);
        rightButtonGroup.setAlignment(Pos.CENTER_RIGHT);
        rightButtonGroup.setStyle("-fx-background-color: transparent;");

        HBox bottomBar = new HBox(spacerRegion, rightButtonGroup);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(8, 12, 8, 12));
        bottomBar.getStyleClass().add("bottom-bar");
        return bottomBar;
    }

    // ================= SETTINGS DIALOG =================

    /**
     * Opens a modal settings dialog where every setting is independently optional.
     * The user may change any combination and leave the rest at their current values.
     *
     * Settings:
     * - Canvas size  (leave unchanged to skip resize)
     * - Maximum brush size  (100 – 1000 px slider)
     * - Auto-fill for shape tools  (checkbox)
     * - Select tool mode  (rectangle vs freeform radio buttons)
     */
    private void openSettingsDialog() {
        Stage settingsStage = new Stage();
        settingsStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        settingsStage.initOwner(primaryStage);
        settingsStage.setTitle("Settings");
        settingsStage.setResizable(false);

        // ---- Canvas Size ----
        Label canvasSectionLabel = new Label("CANVAS SIZE");
        canvasSectionLabel.getStyleClass().add("section-header");

        Label canvasHintLabel = new Label("Edit to resize. Leave unchanged to keep current size.");
        canvasHintLabel.setStyle("-fx-text-fill: #504d58; -fx-font-size: 11px;");

        // Pre-populate with the current dimensions
        TextField canvasWidthField  = new TextField(String.valueOf((int) canvasWidth));
        TextField canvasHeightField = new TextField(String.valueOf((int) canvasHeight));
        canvasWidthField.setPrefWidth(88);
        canvasHeightField.setPrefWidth(88);

        HBox canvasDimensionRow = new HBox(8,
                new Label("W"), canvasWidthField,
                new Label("×"), canvasHeightField,
                new Label("px"));
        canvasDimensionRow.setAlignment(Pos.CENTER_LEFT);

        // ---- Brush Size Maximum ----
        Label brushMaxSectionLabel = new Label("BRUSH SIZE LIMIT");
        brushMaxSectionLabel.getStyleClass().add("section-header");

        Label brushMaxHintLabel = new Label("Adjusts the maximum value of the brush size slider.");
        brushMaxHintLabel.setStyle("-fx-text-fill: #504d58; -fx-font-size: 11px;");

        // Slider ranges from 100 to 1000, snapping to multiples of 100
        Slider maxBrushSizeSlider = new Slider(
                MINIMUM_MAX_BRUSH_SIZE, ABSOLUTE_MAX_BRUSH_SIZE, currentMaxBrushSize);
        maxBrushSizeSlider.setMajorTickUnit(100);
        maxBrushSizeSlider.setMinorTickCount(0);
        maxBrushSizeSlider.setSnapToTicks(true);
        maxBrushSizeSlider.setShowTickMarks(true);
        maxBrushSizeSlider.setShowTickLabels(true);
        maxBrushSizeSlider.setMaxWidth(Double.MAX_VALUE);

        // Live label showing the value next to the slider
        Label maxBrushValueLabel = new Label((int) currentMaxBrushSize + " px");
        maxBrushValueLabel.setStyle("-fx-text-fill: #b86070; -fx-font-size: 11px; -fx-min-width: 52;");
        maxBrushSizeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                maxBrushValueLabel.setText(newValue.intValue() + " px"));

        HBox brushSliderRow = new HBox(10, maxBrushSizeSlider, maxBrushValueLabel);
        brushSliderRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(maxBrushSizeSlider, Priority.ALWAYS);

        // ---- Shape Auto-Fill ----
        Label shapeSectionLabel = new Label("SHAPE TOOLS");
        shapeSectionLabel.getStyleClass().add("section-header");

        CheckBox autoFillCheckBox = new CheckBox("Auto-fill shapes (Rectangle, Circle)");
        autoFillCheckBox.setSelected(drawingManager.isAutoFillEnabled());

        // ---- Select Tool Mode ----
        Label selectSectionLabel = new Label("SELECT TOOL MODE");
        selectSectionLabel.getStyleClass().add("section-header");

        ToggleGroup selectModeToggleGroup = new ToggleGroup();
        RadioButton rectangleSelectRadioButton = new RadioButton("Rectangle selection");
        RadioButton freeformSelectRadioButton  = new RadioButton("Freeform (lasso) selection");

        rectangleSelectRadioButton.setToggleGroup(selectModeToggleGroup);
        freeformSelectRadioButton.setToggleGroup(selectModeToggleGroup);

        // Reflect the current active mode
        if (selectionManager.isRectangleModeEnabled()) {
            rectangleSelectRadioButton.setSelected(true);
        } else {
            freeformSelectRadioButton.setSelected(true);
        }

        VBox selectModeBox = new VBox(8, rectangleSelectRadioButton, freeformSelectRadioButton);

        // ---- Action Buttons ----
        Button applyButton  = new Button("Apply");
        Button cancelButton = new Button("Cancel");

        applyButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);

        // Style the apply button to stand out in brand rose
        applyButton.setStyle(
                "-fx-background-color: #b86070; -fx-text-fill: #fff0f2; " +
                        "-fx-font-weight: 700; -fx-padding: 8 22; " +
                        "-fx-background-radius: 6; -fx-border-color: transparent;");

        applyButton.setOnAction(event -> {
            // Apply auto-fill and select-mode immediately — no side effects
            drawingManager.setAutoFillEnabled(autoFillCheckBox.isSelected());
            selectionManager.setRectangleModeEnabled(rectangleSelectRadioButton.isSelected());

            // Apply brush size maximum if it changed
            double newMaxBrushSize = maxBrushSizeSlider.getValue();
            boolean brushMaxHasChanged = newMaxBrushSize != currentMaxBrushSize;
            if (brushMaxHasChanged) {
                currentMaxBrushSize = newMaxBrushSize;
                // Update the brush size slider range; clamp the current value if needed
                brushSizeSlider.setMax(currentMaxBrushSize);
                if (brushSizeSlider.getValue() > currentMaxBrushSize) {
                    brushSizeSlider.setValue(currentMaxBrushSize);
                }
            }

            // Apply canvas resize only when a dimension actually changed
            double newWidth          = canvasWidth;
            double newHeight         = canvasHeight;
            boolean resizeIsRequired = false;

            try {
                double parsedWidth  = Double.parseDouble(canvasWidthField.getText());
                double parsedHeight = Double.parseDouble(canvasHeightField.getText());

                boolean widthIsValid  = parsedWidth  >= MINIMUM_CANVAS_DIMENSION
                        && parsedWidth  <= MAXIMUM_CANVAS_DIMENSION;
                boolean heightIsValid = parsedHeight >= MINIMUM_CANVAS_DIMENSION
                        && parsedHeight <= MAXIMUM_CANVAS_DIMENSION;

                if (widthIsValid && parsedWidth != canvasWidth) {
                    newWidth         = parsedWidth;
                    resizeIsRequired = true;
                }

                if (heightIsValid && parsedHeight != canvasHeight) {
                    newHeight        = parsedHeight;
                    resizeIsRequired = true;
                }
            } catch (NumberFormatException parseException) {
                // Non-numeric input — skip resize silently
                resizeIsRequired = false;
            }

            if (resizeIsRequired) {
                canvasWidth  = newWidth;
                canvasHeight = newHeight;
                primaryStage.setScene(createEditorScene());
            }

            settingsStage.close();
        });

        cancelButton.setOnAction(event -> settingsStage.close());

        HBox buttonRow = new HBox(8, applyButton, cancelButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        // Assemble the full settings layout
        VBox settingsLayout = new VBox(14,
                canvasSectionLabel,
                canvasHintLabel,
                canvasDimensionRow,
                new Separator(),
                brushMaxSectionLabel,
                brushMaxHintLabel,
                brushSliderRow,
                new Separator(),
                shapeSectionLabel,
                autoFillCheckBox,
                new Separator(),
                selectSectionLabel,
                selectModeBox,
                new Separator(),
                buttonRow);

        settingsLayout.setPadding(new Insets(22));
        settingsLayout.setPrefWidth(400);
        settingsLayout.setStyle("-fx-background-color: #1c1c22;");

        Scene settingsScene = new Scene(settingsLayout);
        applyStylesheet(settingsScene);
        settingsStage.setScene(settingsScene);
        settingsStage.showAndWait();
    }

    // ================= FILE OPERATIONS =================

    /**
     * Opens a file chooser and imports the selected image as a new layer.
     * Scales the image down to fit the canvas if it is too large.
     */
    private void importImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Photo");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile == null) {
            return;
        }

        try {
            Image importedImage = new Image(selectedFile.toURI().toString());
            layerManager.addLayer(selectedFile.getName(), canvasWidth, canvasHeight,
                    brushSizeSlider.getValue());

            double imageNaturalWidth  = importedImage.getWidth();
            double imageNaturalHeight = importedImage.getHeight();
            double drawWidth  = imageNaturalWidth;
            double drawHeight = imageNaturalHeight;

            // Scale down to fit the canvas if the image is larger
            if (imageNaturalWidth > canvasWidth || imageNaturalHeight > canvasHeight) {
                double scaleFactor = Math.min(
                        canvasWidth  / imageNaturalWidth,
                        canvasHeight / imageNaturalHeight);
                drawWidth  = imageNaturalWidth  * scaleFactor;
                drawHeight = imageNaturalHeight * scaleFactor;
            }

            // Centre the image on the canvas
            double drawX = (canvasWidth  - drawWidth)  / 2.0;
            double drawY = (canvasHeight - drawHeight) / 2.0;

            layerManager.getActiveLayer().gc.drawImage(
                    importedImage, drawX, drawY, drawWidth, drawHeight);
        } catch (Exception importException) {
            System.err.println("Import failed: " + importException.getMessage());
        }
    }

    /**
     * Snapshots all visible layers and writes the merged result to a PNG file.
     */
    private void exportImage() {
        WritableImage canvasSnapshot = canvasManager.snapshotFull();
        int snapshotWidth  = (int) canvasSnapshot.getWidth();
        int snapshotHeight = (int) canvasSnapshot.getHeight();

        BufferedImage outputBufferedImage = new BufferedImage(
                snapshotWidth, snapshotHeight, BufferedImage.TYPE_INT_ARGB);

        var pixelReader = canvasSnapshot.getPixelReader();

        // Copy each pixel from the JavaFX WritableImage to the AWT BufferedImage
        for (int rowIndex = 0; rowIndex < snapshotHeight; rowIndex++) {
            for (int columnIndex = 0; columnIndex < snapshotWidth; columnIndex++) {
                Color pixelColor = pixelReader.getColor(columnIndex, rowIndex);
                int argbValue = ((int) (pixelColor.getOpacity() * 255) << 24)
                        | ((int) (pixelColor.getRed()   * 255) << 16)
                        | ((int) (pixelColor.getGreen() * 255) << 8)
                        | ((int) (pixelColor.getBlue()  * 255));
                outputBufferedImage.setRGB(columnIndex, rowIndex, argbValue);
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png"));
        File destinationFile = fileChooser.showSaveDialog(primaryStage);

        if (destinationFile != null) {
            try {
                ImageIO.write(outputBufferedImage, "png", destinationFile);
            } catch (IOException exportException) {
                System.err.println("Export failed: " + exportException.getMessage());
            }
        }
    }

    // ================= HELPERS =================

    /**
     * Applies the application stylesheet to the given scene if it can be located.
     *
     * @param targetScene the scene to receive the stylesheet
     */
    private void applyStylesheet(Scene targetScene) {
        var stylesheetUrl = getClass().getResource("style.css");
        if (stylesheetUrl != null) {
            targetScene.getStylesheets().add(stylesheetUrl.toExternalForm());
        }
    }
}