/*
 * Copyright (c) 2012 Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package Gui.experimental.html;

import Visualization.MultiDimensionalPaintScale;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;

/**
 * Currently, one bipolar is generated first and the other rows are derived by decreasing saturation.
 * The idea was that generating the 2D color palette column by column yields better results.
 * The problem is that if setting the primary color of a multidimensional paint scale and flagging it unipolar,
 * the first color is not the primary color. This is due to some adjustments I made in the code to improve the overall impression of the scale,
 * while extending it to 2D, but it also renders the column wise generation approach infeasible (since the first row / zero uncertainty / hue dimension)
 * doesn't have the intended colors.
 */
public class TwoDimensionalColorPaletteGenerator extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    Canvas canvas = new Canvas(1024, 768);

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Drawing Operations Test");
        Group root = new Group();

        final GraphicsContext gc = canvas.getGraphicsContext2D();

        int desiredHues = 2;
        int sats = 5;

        MultiDimensionalPaintScale multiDimensionalPaintScale = new MultiDimensionalPaintScale(desiredHues,sats);
        multiDimensionalPaintScale.setBiPolar(false);
        multiDimensionalPaintScale.setPrimaryColor(new Color(19./255,163./255,254./255,1.));
        Color[][] colors = multiDimensionalPaintScale.getColors();

        int hues = colors[0].length;

        Color[][] pieceWise = new Color[sats][hues];

        System.out.println(String.format("colors.length: %s", colors.length));
        System.out.println(String.format("colors[0].length: %s", colors[0].length));


        for (int col = 0; col < hues; col++) {
            MultiDimensionalPaintScale columnPaintscale = new MultiDimensionalPaintScale(3,sats);
            columnPaintscale.setBiPolar(false);
            columnPaintscale.setPrimaryColor(colors[0][col]);
            System.out.println(String.format("primary color for column %s: (%.2f, %.2f, %.2f)", col, (colors[0][col].getRed()*255) , (colors[0][col].getGreen()*255) , (colors[0][col].getBlue()*255) ));

            Color[][] columnColors = columnPaintscale.getColors();
            for (int row = 0; row < sats; row++) {
                Color columnColor = columnColors[row][1];
                pieceWise[row][col] = columnColor;
            }
        }

//        drawColors(Arrays.asList(Arrays.asList(Color.RED, Color.GREEN, Color.BLUE), Arrays.asList(Color.RED.deriveColor(0, 1, 1, 0.5), Color.GREEN.deriveColor(0, 1, 1, 0.5), Color.BLUE.deriveColor(0, 1, 1, 0.5))), gc);
        drawColors(colors, gc);

        root.getChildren().add(canvas);
        Button switchButton = new Button("Switch");
        root.getChildren().add(switchButton);

        switchButton.setOnAction(new EventHandler<ActionEvent>() {
            boolean original = false;
            @Override public void handle(ActionEvent event) {
                drawColors(original ? colors : pieceWise, gc);
                original = !original;
            }
        });

        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Draws a series of basic shapes on the specified GraphicsContext.
     * @param gc The GraphicsContext object to draw on
     */
    private void drawColors(Color[][] colors, GraphicsContext gc) {

        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double width = canvas.getWidth() / colors[0].length;
        double height = canvas.getHeight() / colors.length;

        for (int i = 0; i < colors.length; i++) {
            Color[] row = colors[i];
            for (int j = 0; j < row.length; j++) {
                Color color = row[j];
                gc.setFill(color);
                gc.fillRect(j * width+10, i * height+10, width-10, height-10);
            }
        }

    }
}
