package Visualization;

import Data.Correlation.CorrelationMatrix;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
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
            int columnLength = column.mean.length;
            
            double minX, minY, width;
            double height = 1;      // each time lag value is rendered as unit height block // each cell represents a time lag applied to a window. time lags are discrete, as we operate on discrete functions
            double yOffset = -0.5;  // center blocks around their time lag (e.g. time lag 0 => -0.5 ... 0.5)
            
            // window covers a time period equal to the length of the resulting cross-correlation
            width = columnLength;
            minX = column.windowXOffset;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int idx = 0; idx < columnLength; idx++) {
                
                gc.setFill(paintScale.getPaint(column.mean[idx], column.stdDev[idx]));
                gc.setStroke(paintScale.getPaint(column.mean[idx], column.stdDev[idx]));
                
                minY = CorrelationMatrix.splitLag(idx, columnLength)*height + yOffset + 1;
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