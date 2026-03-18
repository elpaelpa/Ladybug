package org.ladybug.ladybugpaint;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.LinkedList;
import java.util.Queue;

public class BucketTool extends BaseTool {
    public BucketTool(String toolName, StackPane canvasStackPane, ToolManager toolManager) {
        super(toolName,canvasStackPane,toolManager);

    }

    @Override
    public void startStroke(Layer activeLayer, double x, double y) {
        floodFill(activeLayer,(int)x,(int)y);
    }

    @Override
    public void drawStroke(Layer activeLayer, double x, double y) {

    }

    @Override
    public void endStroke(Layer activeLayer, double x, double y) {

    }

    private void floodFill(Layer layer, int x, int y) {
        WritableImage image = layer.canvas.snapshot(null, null);
        PixelReader reader = image.getPixelReader();
        PixelWriter writer = layer.gc.getPixelWriter();
        int w = (int) layer.canvas.getWidth();
        int h = (int) layer.canvas.getHeight();

        Color target = reader.getColor(x, y);
        if (target.equals(toolManager.getBrushColor())) return;

        Queue<int[]> q = new LinkedList<>();
        boolean[][] visited = new boolean[w][h];
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            if (px < 0 || py < 0 || px >= w || py >= h || visited[px][py]) continue;
            if (!reader.getColor(px, py).equals(target)) continue;
            writer.setColor(px, py, toolManager.getBrushColor());
            visited[px][py] = true;
            q.add(new int[]{px + 1, py}); q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1}); q.add(new int[]{px, py - 1});
        }
    }

}
