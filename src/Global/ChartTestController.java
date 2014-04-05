package Global;

import Visualization.Correlogram;
import Visualization.NumberAxis;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

/**
 * FXML Controller class
 *
 * @author macbookdata
 */
public class ChartTestController implements Initializable {
    
    Correlogram c = new Correlogram();
            
    @FXML private AnchorPane rootPane;
    @FXML private AnchorPane canvasPane;
    @FXML private Canvas canvas;
    
    private NumberAxis xAxis;
    private NumberAxis yAxis;
    
    Point2D dragStartMousePositionSC;
    Rectangle2D dragStartAxisBoundsDC;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        xAxis = new NumberAxis();
        xAxis.setIsHorizontal(true);
        xAxis.setLabel("x Axis with a very long title");
        xAxis.setLowerBound(1940);
        xAxis.setUpperBound(10000);
        
        yAxis = new NumberAxis();
        yAxis.setIsHorizontal(false);
        yAxis.setLabel("y Axis with another, even longer title");
        yAxis.setLowerBound(-200);
        yAxis.setUpperBound(200);
//        NumberStringConverter tickLabelFormatter = new NumberStringConverter(new DecimalFormat("0.###E0",DecimalFormatSymbols.getInstance(Locale.ENGLISH)));
//        xAxis.setTickLabelFormatter(tickLabelFormatter);
//        yAxis.setTickLabelFormatter(tickLabelFormatter);
        
        canvasPane.getChildren().add(xAxis);
        canvasPane.getChildren().add(yAxis);
        
        // record drag start position
        canvas.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent t) {
                if(t.getButton() == MouseButton.PRIMARY){
                    dragStartMousePositionSC = new Point2D(t.getX(), t.getY());
                    dragStartAxisBoundsDC = new Rectangle2D(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
                }
            }
        });
        
        // pan via mouse drag
        canvas.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent t) {
                canvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
                canvas.getGraphicsContext2D().fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
//canvas.getGraphicsContext2D().strokeText("started @ " + dragStartMousePositionSC.getX()+"|"+dragStartMousePositionSC.getY(), 100, 80);
//canvas.getGraphicsContext2D().strokeText("drag " + t.getX()+"|"+t.getY(), 100, 100);
                
                double offsetX = xAxis.fromScreen(dragStartMousePositionSC.getX()) - xAxis.fromScreen(t.getX()),
                       offsetY = yAxis.fromScreen(dragStartMousePositionSC.getY()) - yAxis.fromScreen(t.getY());
                
                xAxis.setLowerBound(dragStartAxisBoundsDC.getMinX() + offsetX);
                xAxis.setUpperBound(dragStartAxisBoundsDC.getMaxX() + offsetX);
                
                yAxis.setLowerBound(dragStartAxisBoundsDC.getMinY() + offsetY);
                yAxis.setUpperBound(dragStartAxisBoundsDC.getMaxY() + offsetY);
                
                xAxis.drawContents();
                yAxis.drawContents();
                
//canvas.getGraphicsContext2D().strokeLine(dragStartMousePositionSC.getX(), dragStartMousePositionSC.getY(), t.getX(), t.getY());
            }
        });
        
        // zoom via mouse wheel
        canvas.setOnScroll(new EventHandler<ScrollEvent>() {
            
            @Override
            public void handle(ScrollEvent t) {
                canvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
                canvas.getGraphicsContext2D().fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
//canvas.getGraphicsContext2D().strokeText("scroll " + t.getDeltaX()+"|"+t.getDeltaY(), 100, 140);
                
                Bounds boundsData = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
                
//                Scale zoomScale = new Scale(Math.signum(t.getDeltaY())*0.99, 1, xAxisMarks.fromScreen(t.getX()), yAxisMarks.fromScreen(t.getY()));
                double scrollAmount = t.getDeltaY() / 1000;
                Scale zoomScale = new Scale(1 - scrollAmount, 1 - scrollAmount, xAxis.fromScreen(t.getX()), yAxis.fromScreen(t.getY()) );
                Bounds boundsZoomed = zoomScale.transform(boundsData);
                xAxis.setLowerBound(boundsZoomed.getMinX());
                xAxis.setUpperBound(boundsZoomed.getMaxX());
                yAxis.setLowerBound(boundsZoomed.getMinY());
                yAxis.setUpperBound(boundsZoomed.getMaxY());
                xAxis.drawContents();
                yAxis.drawContents();
            }
        });
        
        rootPane.layoutBoundsProperty().addListener(new ChangeListener<Bounds>() {
            
            @Override
            public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
                
                double[] margins = new double[]{10, 10, 40, 50};
                int TOP = 0,
                    RIGHT = 1,
                    BOTTOM = 2,
                    LEFT = 3;
                // setting to the width of the root pane layout bounds avoids getting out of sync with the actual canvas pane layout bounds
                
                AnchorPane.setTopAnchor(canvasPane, margins[TOP]);
                AnchorPane.setRightAnchor(canvasPane, margins[RIGHT]);
                AnchorPane.setBottomAnchor(canvasPane, margins[BOTTOM]);
                AnchorPane.setLeftAnchor(canvasPane, margins[LEFT]);
                
                canvas.setWidth(t1.getWidth()-margins[LEFT]-margins[RIGHT]-1);
                canvas.setHeight(t1.getHeight()-margins[TOP]-margins[BOTTOM]-1); // not to occlude the border of the containing pane (the axis line)
                canvas.setTranslateY(-1);
                
                xAxis.setWidth(t1.getWidth()-margins[LEFT]-margins[RIGHT]);
                xAxis.setHeight(margins[BOTTOM]);
                xAxis.setTranslateY(t1.getHeight()-margins[TOP]-margins[BOTTOM]);
                xAxis.setTranslateX(-1);
                
                yAxis.setHeight(t1.getHeight()-margins[TOP]-margins[BOTTOM]);
                yAxis.setWidth(margins[LEFT]);
                yAxis.setTranslateX(-margins[LEFT]);
                
//                canvas.setLayoutX(20);    // doesn't have any effect
                
                xAxis.drawContents();
                yAxis.drawContents();
                
                canvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
                canvas.getGraphicsContext2D().fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                
                canvas.getGraphicsContext2D().setFill(Color.BLACK);
                canvas.getGraphicsContext2D().setStroke(Color.BLACK);
//                canvas.getGraphicsContext2D().setLineWidth(4);
                canvas.getGraphicsContext2D().appendSVGPath("M150 0 L75 200 L225 200 Z");
            }
        });
        
    }
    
    
    
}
