package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import java.awt.Point;
import java.util.List;

import Data.TimeSeries;
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

    /** encodes 2D values in a single color */
    private MultiDimensionalPaintScale paintScale;

    /** the y offset for drawing correlogram blocks.
     *  this can be used to center blocks around their time lag (e.g. time lag 0 => -0.5 ... 0.5) */
    double blockYOffset = 0;

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

                // center mean zero at the middle of the axis
                double meanRangeMax = Math.max(Math.abs(m.getMeanMinValue()), Math.abs(m.getMeanMaxValue()));
                paintScale.setLowerBounds(-meanRangeMax, m.getStdDevMinValue());
                paintScale.setUpperBounds(meanRangeMax, m.getStdDevMaxValue());
                resetView();
                drawContents();
            }
        });

        // when the mouse moves to another cell, redraw to update highlight
        sharedData.highlightedCellProperty().addListener(new ChangeListener() {
            @Override public void changed(ObservableValue observable, Object oldValue, Object newValue) {
                drawContents();
            }
        });
        
    }

    /**
     * Renders the correlogram.
     * The width of a window is windowSize - overlap (overlap = |w| - baseWindowOffset)
     * Columns are centered around the window average. E.g. [1956..1958] -> 1956 + (1958-1956+1)/2 = 1957.5
     * The offset between two subsequent windows is still baseWindowOffset.
     */
    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
        Affine dataToScreen = dataToScreen();

        if (sharedData.getCorrelationMatrix()== null || sharedData.getCorrelationMatrix().metadata.setA.size() == 0) return;

        // retrieve data to render
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        TimeSeries ts = matrix.metadata.setA.get(0);
        double[] xValues = ts.getDataItems().re;
        List<CorrelationMatrix.Column> columns = matrix.getResultItems();

        // configure paintscale
        // center mean = zero at the middle of the axis
        double meanRangeMax = Math.max(Math.abs(matrix.getMeanMinValue()), Math.abs(matrix.getMeanMaxValue()));
        paintScale.setLowerBounds(-meanRangeMax, matrix.getStdDevMinValue());
        paintScale.setUpperBounds(meanRangeMax, matrix.getStdDevMaxValue());

        // width and height of a cell in the correlogram.
        // using the actual windowSize is not feasible, since windows overlap.
        // a new window starts in each baseWindowOffset steps, each window is assigned that width (but at most the window size, in case of negative overlap)
        double blockWidth = Math.min(matrix.metadata.windowSize, matrix.metadata.baseWindowOffset),
               blockHeight = 1;

        // for each column of the matrix (or, equivalently, for each time window)
        for (int i = 0; i < columns.size(); i++) {

            CorrelationMatrix.Column column = columns.get(i);

            double minX, minY;

            // center around window center
            minX = xValues[columns.get(i).windowStartIndex] + 0.5*matrix.metadata.windowSize - blockWidth/2;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            int columnLength = column.mean.length;
            for (int lag = 0; lag < columnLength; lag++) {
                
                minY = column.tauMin + lag + 1 + blockYOffset; //CorrelationMatrix.splitLag(idx, columnLength)*height + yOffset + 1;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + blockWidth, minY + blockHeight);

                // draw cell
                gc.setFill(paintScale.getPaint(column.mean[lag], column.stdDev[lag]));
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());

                // draw highlighted cell, or otherwise a border in the same color (avoids moiré effects because of tiny gaps between cells)
                if( i == sharedData.getHighlightedCell().getX() && lag == sharedData.getHighlightedCell().getY() ){
                    gc.save();
                    gc.setStroke(Color.YELLOW);
                    gc.setLineWidth(2);
                    gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                    gc.restore();
                } else {
                    gc.setStroke(paintScale.getPaint(column.mean[lag], column.stdDev[lag]));
                    gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                }

            }
            
        }
        
        xAxis.drawContents();
        yAxis.drawContents();

    }

    /**
     * This handler listens to mouse moves on the correlogram and informs the shared data object about
     * the correlation matrix cell index (window index index and lag index) under the mouse cursor.
     */
    private final EventHandler<MouseEvent> reportHighlightedCell = new EventHandler<MouseEvent>() {
        @Override public void handle(MouseEvent t) {

            // get matrix
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            if(matrix == null) return;

            // transform from mouse position into data coordinates
            Point2D dataCoordinates;
            try { dataCoordinates = dataToScreen().inverseTransform(t.getX(), t.getY()); }
            catch (NonInvertibleTransformException ex) {
                dataCoordinates = new Point2D(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
                System.err.println("Couldn't invert data to screen transform on mouse over (correlogram).");
            }

            // determine block sizes and overall correlogram offset on X axis
            int windowSize = matrix.metadata.windowSize;
            double blockWidth = Math.min(windowSize, matrix.metadata.baseWindowOffset);
            double windowOffset = matrix.getStartXValueInTimeSeries() + 0.5*matrix.metadata.windowSize - blockWidth/2;

            // use equi-distance property to calculate the window index by division
            int activeTimeWindowIdx = (int) Math.floor((dataCoordinates.getX() - windowOffset)/blockWidth);

            Point activeCell;
            // check whether the mouse points to any column
            if(activeTimeWindowIdx >= 0 && activeTimeWindowIdx < matrix.getResultItems().size()){

                CorrelationMatrix.Column activeTimeWindow = matrix.getItembyID(activeTimeWindowIdx);

                int posOnYAxis = (int) Math.floor(dataCoordinates.getY() - blockYOffset);

                // check whether the cell is outside the column
                if(posOnYAxis < activeTimeWindow.tauMin || posOnYAxis > activeTimeWindow.tauMin + activeTimeWindow.getSize()){
                    posOnYAxis = Integer.MAX_VALUE;
//                    System.out.println("top/bottom out.");
                }

                int timeLagIndex = posOnYAxis - activeTimeWindow.tauMin;
                activeCell = new Point(activeTimeWindowIdx, timeLagIndex);

            } else {
//                System.out.println("left/right out.");
                activeCell = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }

            // report only if changes have occured
            if( ! sharedData.getHighlightedCell().equals(activeCell))
                sharedData.setHighlightedCell(activeCell);
        }
    };

    /**
     * Resets the axes such that they fit the matrix bounds.
     * Performs a redraw.
     */
    void resetView() {
        CorrelationMatrix m = sharedData.getCorrelationMatrix();
        
        if(m == null) return;
        xAxis.setLowerBound(m.getStartXValueInTimeSeries());
        xAxis.setUpperBound(m.getEndXValueInTimeSeries()+1);
        if(m.maxLag() == 0){
            yAxis.setLowerBound(blockYOffset);
            yAxis.setUpperBound(-blockYOffset);
        } else {
            yAxis.setLowerBound(m.minLag()+blockYOffset);
            yAxis.setUpperBound(m.maxLag()-blockYOffset);
        } 
    }
    
    public MultiDimensionalPaintScale getPaintScale() {
        return paintScale;
    }

    public void setPaintScale(MultiDimensionalPaintScale paintScale) {
        this.paintScale = paintScale;
    }

}