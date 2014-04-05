package Visualization;

import Data.Correlation.CorrelationMatrix;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Used to draw the correlogram. Takes a matrix with two dimensional entries and renders each of them as a matrix of colored blocks.
 * @author Carl Witt
 */
public class Correlogram extends CanvasChart {
    
    public Correlogram(MultiDimensionalPaintScale paintScale){
        this.paintScale=paintScale;
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);
    }
    
    private final ObjectProperty<CorrelationMatrix> matrix = new SimpleObjectProperty<>();
    public CorrelationMatrix getMatrix(){return matrix.get();}
    public ObjectProperty<CorrelationMatrix> matrixProperty(){return matrix;}
    public void setMatrix(CorrelationMatrix m){
        matrix.set(m);
        paintScale.setLowerBounds(m.getMeanMinValue(), m.getStdDevMinValue());
        paintScale.setUpperBounds(m.getMeanMaxValue(), m.getStdDevMaxValue());
        resetView();
        drawContents();
    }
    
    // encodes 2D values in a single color
    MultiDimensionalPaintScale paintScale;

    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
//        PixelWriter pw = gc.getPixelWriter();
//        for (int i = 0; i < 10; i++) {
//            pw.setColor(i, i, Color.AQUA);
//        }
        
        Affine dataToScreen = dataToScreen();

        if (getMatrix() == null) return;

        // retrieve data to render
        CorrelationMatrix matrix = getMatrix();
        List<CorrelationMatrix.Column> columns = matrix.getResultItems();

        // configure paintscale
        paintScale.setLowerBounds(matrix.getMeanMinValue(), matrix.getStdDevMinValue());
        paintScale.setUpperBounds(matrix.getMeanMaxValue(), matrix.getStdDevMaxValue());

        // for each column of the matrix (or, equivalently, for each time window)
        for (int i = 0; i < columns.size(); i++) {
            CorrelationMatrix.Column column = columns.get(i);
            
            double minX, minY, startY, width, height;
            
            // window covers a time period equal to the length of the resulting cross-correlation
            width = column.mean.length;
            // center windows around their starting point because shifts extend in both directions
            minX = column.windowXOffset;// - 0.5*width;
            height = 1; // each cell represents a time lag applied to a window. time lags are discrete, as we operate on discrete functions
            startY = 1;


            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int lag = 0; lag < column.mean.length; lag++) {
                gc.setFill(paintScale.getPaint(column.mean[lag], column.stdDev[lag]));
                gc.setStroke(paintScale.getPaint(column.mean[lag], column.stdDev[lag]));
                
                minY = lag*height + startY;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + width, minY + height);
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());

            }
        }
        
        xAxis.drawContents();
        yAxis.drawContents();

    }

    /**
     * Resets the axes such that they fit the matrix bounds.
     * Performs a redraw.
     */
    void resetView() {
        CorrelationMatrix m = matrix.get();
        
        if(m == null) return;
        
        xAxis.setLowerBound(m.getMinX());
        xAxis.setUpperBound(m.getMaxX());
        yAxis.setLowerBound(m.getMinY());
        yAxis.setUpperBound(m.getMaxY());
    }
    
    public MultiDimensionalPaintScale getPaintScale() {
        return paintScale;
    }

    public void setPaintScale(MultiDimensionalPaintScale paintScale) {
        this.paintScale = paintScale;
    }
}