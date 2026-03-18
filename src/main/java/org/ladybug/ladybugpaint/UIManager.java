package org.ladybug.ladybugpaint;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.util.Objects;

public class UIManager {

    //Sliders
    private final Slider brushSizeSlider = new Slider(1, 50, 5);
    private final Slider brushOpacitySlider = new Slider(0, 1, 1);
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);

    private final ColorPicker picker = new ColorPicker();

    //Contains Tool Objects
    public ToolManager toolManager;
    private ImageHandler imageHandler;


    private final ListView<Layer> layerListView = new ListView<>();
    private final StackPane canvasStack = new StackPane();
    private Layer strokeLayer;

    private final LadybugState state;
    private  Stage currentStage;
    public UIManager(LadybugState state,Stage stage){
        this.state = state;
        this.currentStage = stage;

        addNewLayer();
        toolManager = new ToolManager(canvasStack);
        imageHandler = new ImageHandler(currentStage,this);

        //Initial Values for sliders
        picker.setValue(Color.BLACK);
        brushOpacitySlider.setValue(1.0);
        brushSizeSlider.setValue(20.0);

        setupMouseEvents();
        layerListView.setCellFactory(lv -> new ListCell<Layer>(){
            @Override
            protected void updateItem(Layer layer, boolean empty) {
                super.updateItem(layer, empty);
                if (empty || layer == null) {
                    setText(null);
                    setGraphic(null);
                }
                else {

                    // Label for name
                    Label nameLabel = new Label(layer.getName());

                    HBox cellBox = new HBox(5, nameLabel);
                    setGraphic(cellBox);
                }
            }
        });
    }

    //Layers
    public void addNewLayer() {
        Layer newLayer = state.layerManager.createLayer();
        canvasStack.getChildren().add(newLayer.canvas);
        layerListView.getItems().setAll(state.layerManager.getLayers());
        layerListView.getSelectionModel().select(newLayer);
    }
    public void deleteSelectedLayer() {
        Layer selectedLayer = layerListView.getSelectionModel().getSelectedItem();

        if(!Objects.equals(selectedLayer.getName(), "Background")) {
            state.layerManager.removeLayer(selectedLayer);
            canvasStack.getChildren().remove(selectedLayer.getCanvas());

            layerListView.getItems().setAll(state.layerManager.getLayers());//update layer logic

            Layer newActive = state.layerManager.getActiveLayer();
            if (newActive != null) {
                layerListView.getSelectionModel().select(newActive);
            };
        };
    }

    public StackPane getCanvasStack() {
        return canvasStack;
    }
    public VBox createColorPanel() {


        picker.setOnAction(e -> {
            toolManager.setBrushColor(picker.getValue());
        });

        brushSizeSlider.valueProperty().addListener((obs, o, n) -> {
//            if (state.layerManager.getActiveLayer() != null)
//                state.layerManager.getActiveLayer().gc.setLineWidth(n.doubleValue());
            toolManager.setBrushSize(n.doubleValue());
        });

        brushOpacitySlider.valueProperty().addListener((obs, o, n) -> {
//            if (state.layerManager.getActiveLayer()  != null)
//                state.layerManager.getActiveLayer().gc.setGlobalAlpha(n.doubleValue());
            toolManager.setBrushOpacity(n.doubleValue());
        });



        VBox panel = new VBox(
                15.0,
                new Label("Color"),
                picker,
                new Label("Brush Size"),
                brushSizeSlider,
                new Label("Brush Opacity"),
                brushOpacitySlider
        );

        panel.setPadding(new Insets(15));
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPrefWidth(180);

        return panel;
    }
    public VBox createLayerPanel() {
        Button addLayer = new Button("Add Layer");
        addLayer.setOnAction(e -> addNewLayer());

        Button deleteLayerBtn = new Button("Delete Layer");
        deleteLayerBtn.getStyleClass().add("delete-button");
        deleteLayerBtn.setOnAction(e -> deleteSelectedLayer());

        // Layer Opacity Slider Logic
        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (state.layerManager.getActiveLayer() != null) {
                state.layerManager.getActiveLayer() .canvas.setOpacity(n.doubleValue());
            }
        });

        layerListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            int index = layerListView.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                state.layerManager.setActiveLayer(state.layerManager.getLayers().get(index));
                state.layerManager.getActiveLayer().gc.setLineWidth(toolManager.getBrushSize());
                state.layerManager.getActiveLayer().gc.setGlobalAlpha(toolManager.getBrushOpacity());

                // Update slider to match this layer's current opacity
                layerOpacitySlider.setValue(state.layerManager.getActiveLayer().canvas.getOpacity());
            }
        });

        VBox box = new VBox(10, new Label("Layers"), layerListView, new Label("Layer Opacity"), layerOpacitySlider, addLayer, deleteLayerBtn);
        box.setPadding(new Insets(10));
        box.setPrefWidth(170);
        return box;
    }
    public HBox createToolbar() {
        ToggleGroup toolGroup = new ToggleGroup();
        HBox bar = new HBox(8);// brush, eraser, line, rect, circle, bucket, eye undoBtn, redoBtn, export;

        toolManager.getTools().forEach(tool -> {
            bar.getChildren().add(createToolButton(tool,toolGroup));
        });

        bar.setPadding(new Insets(10));

//        ToggleButton brush = createTool("Brush", Tool.BRUSH, tools);
//        ToggleButton eraser = createTool("Eraser", Tool.ERASER, tools);
//        ToggleButton line = createTool("Line", Tool.LINE, tools);
//        ToggleButton rect = createTool("Rectangle", Tool.RECTANGLE, tools);
//        ToggleButton circle = createTool("Circle", Tool.CIRCLE, tools);
//        ToggleButton bucket = createTool("Bucket", Tool.BUCKET, tools);
//        ToggleButton eye = createTool("Eyedropper", Tool.EYEDROPPER, tools);

        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        //undoBtn.setOnAction(e -> undo());
//        redoBtn.setOnAction(e -> redo());



        Button export = new Button("Export PNG");
//
        export.setOnAction(e -> {
            imageHandler.exportImage();
        });

        bar.getChildren().add(export);
//        bar.getChildren().add(new Button("Undo"));
//        bar.getChildren().add(new Button("Redo"));



        return bar;
    }


    private ToggleButton createToolButton(BaseTool tool, ToggleGroup group) {

        ToggleButton btn = new ToggleButton(tool.toolName);
        btn.setToggleGroup(group);

        if (Objects.equals(tool.toolName, "Brush"))
            btn.setSelected(true);

        btn.setOnAction(e -> {
            toolManager.setCurrentTool(tool);
        });
        return btn;
    }
    private void setupMouseEvents() {
        canvasStack.setOnMousePressed(e -> {
            strokeLayer = state.layerManager.getActiveLayer();
            if(strokeLayer == null) return;

            Point2D p = strokeLayer.getCanvas().sceneToLocal(e.getSceneX(), e.getSceneY());

            toolManager.getCurrentTool().startStroke(
                    strokeLayer,
                    p.getX(),
                    p.getY()
            );
        });
        canvasStack.setOnMouseDragged(e -> {
            if(strokeLayer == null) return;
            Point2D p = strokeLayer.getCanvas().sceneToLocal(e.getSceneX(), e.getSceneY());
            toolManager.getCurrentTool().drawStroke(
                    strokeLayer,
                    p.getX(),
                    p.getY()
            );
        });
        canvasStack.setOnMouseReleased(e -> {
            if(strokeLayer == null) return;
            Point2D p = strokeLayer.getCanvas().sceneToLocal(e.getSceneX(), e.getSceneY());

            toolManager.getCurrentTool().endStroke(
                    strokeLayer,
                    p.getX(),
                    p.getY()
            );
            strokeLayer = null;
        });
    }

}
