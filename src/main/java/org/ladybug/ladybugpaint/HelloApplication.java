package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {


    //Default Window size
    private double WIDTH = 500.0,
            HEIGHT = 500.0;


    private double lastX = -1.0;
    private double lastY = -1;


    @Override
    public void start(Stage stage) throws IOException {

        //Create Canvas
        Canvas canvas = new Canvas();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        //INitialize proprties of stroke
        gc.setLineWidth(20);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setStroke(Color.DARKOLIVEGREEN);



        //Mouse CLick = > this runs
        canvas.setOnMousePressed(e -> {
            // Start of stroke
            lastX = e.getX();
            lastY = e.getY();
        });

        //Mouse gets Dragged => this runs
        canvas.setOnMouseDragged(e -> {

            //gets current key every mouse drag
            double currentX = e.getX();
            double currentY = e.getY();


            // Draw line from last point to current point
            gc.strokeLine(lastX, lastY, currentX, currentY);


            // Update last point
            lastX = currentX;
            lastY = currentY;
        });


        BorderPane rootPane = new BorderPane();
        rootPane.setCenter(canvas);

        Scene scene = new Scene(rootPane,WIDTH,HEIGHT);
        canvas.widthProperty().bind(scene.widthProperty());
        canvas.heightProperty().bind(scene.heightProperty());


        //Slider Group

        Group sliderGroup = new Group();
        Slider slider1 = new Slider(0,255,0);

        sliderGroup.getChildren().add(slider1);
        rootPane.getChildren().add(sliderGroup);


        stage.setScene(scene);
        stage.show();
    }

}

