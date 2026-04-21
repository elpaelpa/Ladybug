package org.ladybug.ladybugpaint;

import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class DrawingManager {

    private final CanvasManager canvasManager;
    private final LayerManager layerManager;
    private final ToolManager toolManager;
    private final SelectionManager selectionManager;

    private Image smudgeBrush;

    public DrawingManager(CanvasManager cm,
                          LayerManager lm,
                          ToolManager tm,
                          SelectionManager sm) {
        this.canvasManager = cm;
        this.layerManager = lm;
        this.toolManager = tm;
        this.selectionManager = sm;
    }

    // ================= MOUSE HOOK =================
    public void attachMouseEvents() {

        var stack = canvasManager.getCanvasStack();

        stack.setOnMousePressed(e -> {

            if (layerManager.getActiveLayer() == null) return;

            var p = layerManager.getActiveLayer().canvas.sceneToLocal(
                    e.getSceneX(), e.getSceneY()
            );

            double x = p.getX();
            double y = p.getY();

            toolManager.setStart(x, y);

            // ================= SELECTION =================

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                selectionManager.startSelection(x, y);
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                if (selectionManager.hasSelection()) {
                    selectionManager.startMove(x, y);
                }
                return;
            }

            if (selectionManager.hasSelection()) {
                selectionManager.commitSelection();
            }

            // ================= TOOLS =================

            switch (toolManager.getCurrentTool()) {

                case EYEDROPPER -> handleEyedropper(x, y);

                case BUCKET -> {
                    layerManager.saveState(
                            layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(),
                            canvasManager.getCanvasHeight()
                    );
                    floodFill((int)x, (int)y);
                }

                case ERASER -> {
                    layerManager.saveState(
                            layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(),
                            canvasManager.getCanvasHeight()
                    );
                    erase(x, y);
                }

                case SMUDGE -> startSmudge(x, y);

                case BRUSH -> {
                    layerManager.saveState(
                            layerManager.getActiveLayer(),
                            canvasManager.getCanvasWidth(),
                            canvasManager.getCanvasHeight()
                    );

                    GraphicsContext gc = layerManager.getActiveLayer().gc;

                    gc.setStroke(toolManager.getColor());
                    gc.setLineWidth(toolManager.getBrushSize());
                    gc.setGlobalAlpha(toolManager.getBrushOpacity());

                    gc.beginPath();
                    gc.moveTo(x, y);
                    gc.lineTo(x, y);
                    gc.stroke();
                }

                case LINE, RECTANGLE, CIRCLE -> startDraw(x, y);
            }
        });

        stack.setOnMouseDragged(e -> {

            if (layerManager.getActiveLayer() == null) return;

            var p = layerManager.getActiveLayer().canvas.sceneToLocal(
                    e.getSceneX(), e.getSceneY()
            );

            double x = p.getX();
            double y = p.getY();

            // ================= SELECTION =================

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                selectionManager.updateSelection(x, y);
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.updateMove(x, y);
                return;
            }

            // ================= DRAW =================

            switch (toolManager.getCurrentTool()) {

                case BRUSH -> drawBrush(x, y);

                case LINE, RECTANGLE, CIRCLE -> drawShape(x, y);

                case ERASER -> erase(x, y);

                case SMUDGE -> dragSmudge(x, y);
            }
        });

        stack.setOnMouseReleased(e -> {

            if (layerManager.getActiveLayer() == null) return;

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                selectionManager.finalizeSelection();
                selectionManager.drawPreview();
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.endMove();
                selectionManager.drawPreview();
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case LINE, RECTANGLE, CIRCLE -> commitTempDrawing();
            }
        });

        canvasManager.getTempCanvas().setOnMouseMoved(e -> {
            if (selectionManager.hasSelection()) {
                selectionManager.drawPreview();
            }
        });
    }

    // ================= DRAWING =================

    private void startDraw(double x, double y) {

        layerManager.saveState(
                layerManager.getActiveLayer(),
                canvasManager.getCanvasWidth(),
                canvasManager.getCanvasHeight()
        );

        GraphicsContext gc = canvasManager.getTempGc();

        canvasManager.clearTemp();

        gc.setStroke(toolManager.getColor());
        gc.setLineWidth(toolManager.getBrushSize());
        gc.setGlobalAlpha(1.0);

        // 🔥 FIX: always visible preview
        canvasManager.setTempOpacity(1.0);
    }

    private void drawBrush(double x, double y) {
        GraphicsContext gc = layerManager.getActiveLayer().gc;
        gc.lineTo(x, y);
        gc.stroke();
    }

    private void drawShape(double x, double y) {

        GraphicsContext gc = canvasManager.getTempGc();

        // 🔥 FIX: ensure visible every frame
        canvasManager.setTempOpacity(1.0);

        canvasManager.clearTemp();

        double sx = toolManager.getStartX();
        double sy = toolManager.getStartY();

        switch (toolManager.getCurrentTool()) {

            case LINE ->
                    gc.strokeLine(sx, sy, x, y);

            case RECTANGLE ->
                    gc.strokeRect(Math.min(sx, x), Math.min(sy, y),
                            Math.abs(x - sx), Math.abs(y - sy));

            case CIRCLE ->
                    gc.strokeOval(Math.min(sx, x), Math.min(sy, y),
                            Math.abs(x - sx), Math.abs(y - sy));
        }
    }

    private void commitTempDrawing() {

        WritableImage snap = canvasManager.snapshot(canvasManager.getTempCanvas());

        GraphicsContext gc = layerManager.getActiveLayer().gc;

        gc.setGlobalAlpha(toolManager.getBrushOpacity());

        if (selectionManager.hasSelection()) {
            gc.save();

            var r = selectionManager.getBounds();
            gc.beginPath();
            gc.rect(r[0], r[1], r[2], r[3]);
            gc.clip();

            gc.drawImage(snap, 0, 0);

            gc.restore();
        } else {
            gc.drawImage(snap, 0, 0);
        }

        gc.setGlobalAlpha(1.0);

        canvasManager.clearTemp();
    }

    // ================= ERASER =================
    private void erase(double x, double y) {
        double size = toolManager.getBrushSize();
        layerManager.getActiveLayer().gc.clearRect(
                x - size / 2,
                y - size / 2,
                size,
                size
        );
    }

    // ================= SMUDGE =================
    private void startSmudge(double x, double y) {

        layerManager.saveState(
                layerManager.getActiveLayer(),
                canvasManager.getCanvasWidth(),
                canvasManager.getCanvasHeight()
        );

        double size = toolManager.getBrushSize();

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(
                x - size / 2,
                y - size / 2,
                size,
                size
        ));

        smudgeBrush = layerManager.getActiveLayer().canvas.snapshot(sp, null);
    }

    private void dragSmudge(double x, double y) {

        if (smudgeBrush == null) return;

        double size = toolManager.getBrushSize();
        GraphicsContext gc = layerManager.getActiveLayer().gc;

        gc.setGlobalAlpha(0.3);
        gc.drawImage(smudgeBrush, x - size / 2, y - size / 2);
        gc.setGlobalAlpha(1.0);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(
                x - size / 2,
                y - size / 2,
                size,
                size
        ));

        smudgeBrush = layerManager.getActiveLayer().canvas.snapshot(sp, null);
    }

    // ================= EYEDROPPER =================
    private void handleEyedropper(double x, double y) {

        WritableImage snap = canvasManager.snapshotFull();

        int ix = (int)x;
        int iy = (int)y;

        if (ix >= 0 && iy >= 0 &&
                ix < canvasManager.getCanvasWidth() &&
                iy < canvasManager.getCanvasHeight()) {

            Color picked = snap.getPixelReader().getColor(ix, iy);

            if (picked.getOpacity() > 0) {
                javafx.scene.paint.Color newColor = new javafx.scene.paint.Color(
                        picked.getRed(),
                        picked.getGreen(),
                        picked.getBlue(),
                        picked.getOpacity()
                );

                toolManager.setColor(newColor);
                toolManager.setTool(ToolManager.Tool.BRUSH);
            }
        }
    }

    // ================= FLOOD FILL =================
    private void floodFill(int x, int y) {

        WritableImage image = canvasManager.snapshot(layerManager.getActiveLayer().canvas);

        var reader = image.getPixelReader();
        var writer = layerManager.getActiveLayer().gc.getPixelWriter();

        int w = (int) canvasManager.getCanvasWidth();
        int h = (int) canvasManager.getCanvasHeight();

        Color target = reader.getColor(x, y);
        Color fill = toolManager.getColor();

        if (target.equals(fill)) return;

        boolean[][] visited = new boolean[w][h];
        java.util.Queue<int[]> q = new java.util.LinkedList<>();

        q.add(new int[]{x, y});

        while (!q.isEmpty()) {

            int[] p = q.poll();
            int px = p[0], py = p[1];

            if (px < 0 || py < 0 || px >= w || py >= h || visited[px][py])
                continue;

            if (!reader.getColor(px, py).equals(target))
                continue;

            writer.setColor(px, py, fill);
            visited[px][py] = true;

            q.add(new int[]{px + 1, py});
            q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1});
            q.add(new int[]{px, py - 1});
        }
    }
}