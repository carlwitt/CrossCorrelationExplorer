package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import java.awt.Point;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Translate;

/**
 * Used to draw the correlogram. Takes a matrix with two dimensional entries and renders each of them as a matrix of colored blocks.
 * @author Carl Witt
 */
public class Correlogram extends CanvasChart {
    
    private SharedData sharedData;
    
    // encodes 2D values in a single color
    MultiDimensionalPaintScale paintScale;
    
    public Correlogram(MultiDimensionalPaintScale paintScale){
        this.paintScale=paintScale;
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);
        
        chartCanvas.setOnMouseMoved(reportHighlightedCell);
    }
    
    void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;
        
        // listen to changes in the correlation result matrix
        sharedData.correlationMatrixProperty().addListener(new ChangeListener<CorrelationMatrix>() {
            @Override public void changed(ObservableValue<? extends CorrelationMatrix> ov, CorrelationMatrix t, CorrelationMatrix m) {
                paintScale.setLowerBounds(m.getMeanMinValue(), m.getStdDevMinValue());
                paintScale.setUpperBounds(m.getMeanMaxValue(), m.getStdDevMaxValue());
                resetView();
                drawContents();
            }
        });
        
    }
    

    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
        Affine dataToScreen = dataToScreen();

        if (sharedData.getCorrelationMatrix()== null) return;

        // retrieve data to render
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        List<CorrelationMatrix.Column> columns = matrix.getResultItems();

        // configure paintscale
        paintScale.setLowerBounds(matrix.getMeanMinValue(), matrix.getStdDevMinValue());
        paintScale.setUpperBounds(matrix.getMeanMaxValue(), matrix.getStdDevMaxValue());

        // for each column of the matrix (or, equivalently, for each time window)
        for (int i = 0; i < columns.size(); i++) {
            
            CorrelationMatrix.Column column = columns.get(i);
            int columnLength = column.mean.length;
            
            double minX, minY, width;
            double height = 1;      // each time lag value is rendered as unit height block // each cell represents a time lag applied to a window. time lags are discrete, as we operate on discrete functions
            double yOffset = -0.5;  // center blocks around their time lag (e.g. time lag 0 => -0.5 ... 0.5)
            
            // window covers a time period equal to the length of the resulting cross-correlation
            width = columnLength;
            minX = column.windowXOffset;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int idx = 0; idx < columnLength; idx++) {
                
                minY = CorrelationMatrix.splitLag(idx, columnLength)*height + yOffset + 1;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + width, minY + height);
                
                gc.setFill(paintScale.getPaint(column.mean[idx], column.stdDev[idx]));
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                
                if( i == sharedData.getHighlightedCell().getX() && idx == sharedData.getHighlightedCell().getY() ){
                    gc.save();
                    gc.setStroke(Color.YELLOW);
                    gc.setLineWidth(2);
                    gc.strokeRoundRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY(), 5, 5);
                    gc.restore();
                } else {
                    gc.setStroke(paintScale.getPaint(column.mean[idx], column.stdDev[idx]));
                    gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                }

            }
            
            // missing: offset by -height/2 
            //          mirror along x axis
//            if(i == getHighlightedTimeWindow() ){
//                System.out.println(String.format("highlight %s %s %s %s", minX, CorrelationMatrix.minLag(columnLength), width, height*(CorrelationMatrix.maxLag(columnLength)-CorrelationMatrix.minLag(columnLength))));
//                gc.save();
//                gc.setFill(Color.YELLOW);
//                ulc = dataToScreen.transform(minX, CorrelationMatrix.minLag(columnLength));
//                brc = dataToScreen.transform(minX + width, CorrelationMatrix.minLag(columnLength) + height*(CorrelationMatrix.maxLag(columnLength)-CorrelationMatrix.minLag(columnLength)));
//                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
//                gc.restore();
//            }
        }
        
        xAxis.drawContents();
        yAxis.drawContents();

    }
    
    EventHandler<MouseEvent> reportHighlightedCell = new EventHandler<MouseEvent>() {
        @Override public void handle(MouseEvent t) {
            
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            if(matrix == null) return;
            
            Point2D dataCoordinates;
            try { dataCoordinates = dataToScreen().inverseTransform(t.getX(), t.getY()); }
            catch (NonInvertibleTransformException ex) {
                dataCoordinates = new Point2D(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
                System.err.println("Couldn't invert data to screen transform on mouse over (correlogram).");
            }
            
            Point activeCell = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
            
            int windowSize = matrix.metadata.windowSize;
            double windowOffset = matrix.getResultItems().get(0).windowXOffset;
            double activeTimeWindowIdx = Math.floor((dataCoordinates.getX() - windowOffset)/windowSize);
            
            if(activeTimeWindowIdx >= 0 && activeTimeWindowIdx < matrix.getResultItems().size()){
                // the length of the last window can be shorter than that of all others
                int specialWindowSize = matrix.getResultItems().get((int)activeTimeWindowIdx).mean.length;
                int activeTimeLag = (int) Math.floor(dataCoordinates.getY()+0.5);
                if(activeTimeLag < CorrelationMatrix.minLag(specialWindowSize) || activeTimeLag > CorrelationMatrix.maxLag(specialWindowSize))
                    activeTimeLag = Integer.MAX_VALUE;
                activeCell = new Point((int) activeTimeWindowIdx, (activeTimeLag+specialWindowSize)%specialWindowSize);
            }
            
            sharedData.setHighlightedCell(activeCell);
            drawContents();
        }
    };

    /**
     * Resets the axes such that they fit the matrix bounds.
     * Performs a redraw.
     */
    void resetView() {
        CorrelationMatrix m = sharedData.getCorrelationMatrix();
        
        if(m == null) return;
        xAxis.setLowerBound(m.getMinX());
        xAxis.setUpperBound(m.getMaxX());
        if(m.getMaxY() == 0){
            yAxis.setLowerBound(-0.5);
            yAxis.setUpperBound(+0.5);
        } else {
            yAxis.setLowerBound(m.getMinY()-0.5);
            yAxis.setUpperBound(m.getMaxY()+0.5);
        } 
    }
    
    public MultiDimensionalPaintScale getPaintScale() {
        return paintScale;
    }

    public void setPaintScale(MultiDimensionalPaintScale paintScale) {
        this.paintScale = paintScale;
    }

}