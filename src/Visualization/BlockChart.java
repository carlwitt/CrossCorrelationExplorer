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
public class BlockChart extends CanvasChart {
    
    public BlockChart(){
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
    
    /** If legendMode is set to true, the value pairs in the matrix will be rendered at coordinates corresponding to the value pairs and thus label them via the tick marks. */
    public boolean legendMode = false;
    
    // encodes 2D values in a single color
    MultiDimensionalPaintScale paintScale = new MultiDimensionalPaintScale(null, null);

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
            
            if(legendMode){
                // the mean resolution is expected to be ≥ 3 (at least two columns)
                width = Math.max(1, columns.get(1).windowXOffset - columns.get(0).windowXOffset);
                height = Math.max(1, columns.get(0).stdDev[1] - columns.get(0).stdDev[0]);
                
                minX = matrix.getMeanMinValue() + i*width - width/2;
                startY = matrix.getStdDevMinValue()+height/2;
            } else {
                // window covers a time period equal to the length of the resulting cross-correlation
                width = column.mean.length;
                // center windows around their starting point because shifts extend in both directions
                minX = column.windowXOffset;// - 0.5*width;
                height = 1; // each cell represents a time lag applied to a window. time lags are discrete, as we operate on discrete functions
                startY = 1;
            }


            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int lag = 0; lag < column.mean.length; lag++) {
                gc.setFill(paintScale.getPaint(column.mean[lag], column.stdDev[lag]));
                
                minY = lag*height + startY;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + width, minY + height);
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
//if(legendMode) System.out.println(String.format(
//        "translate data block (%s,%s) (%s, %s) to "
//      + "%s, %s m=%s s=%s to color %s",
//        minX, minY, minX+width, minY+height, 
//        ulc, brc, column.mean[lag], column.stdDev[lag], gc.getFill()));

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
        
        double xMin, xMax, yMin, yMax;
        if(legendMode){
            xMin = m.getMeanMinValue();
            xMax = Math.max(xMin+1, m.getMeanMaxValue());
//            xMax = m.getMeanMaxValue();
            xAxis.setTickUnit((xMax-xMin)/(m.getResultItems().size()-1));
            yMin = m.getStdDevMinValue();
            yMax = Math.max(yMin+1, m.getStdDevMaxValue());
//            yMax = m.getStdDevMaxValue();
            yAxis.setTickUnit((yMax-yMin)/(m.getResultItems().get(0).stdDev.length-1));
//System.out.println(String.format("reset view: x tick units %s y tick units %s",xAxis.getTickUnit(), yAxis.getTickUnit()));
        } else {
            xMin = m.getMinX();
            xMax = m.getMaxX();
            yMin = m.getMinY();
            yMax = m.getMaxY();
        }
        
        xAxis.setLowerBound(xMin);
        xAxis.setUpperBound(xMax);
        yAxis.setLowerBound(yMin);
        yAxis.setUpperBound(yMax);
    }
    
    public MultiDimensionalPaintScale getPaintScale() {
        return paintScale;
    }

    public void setPaintScale(MultiDimensionalPaintScale paintScale) {
        this.paintScale = paintScale;
    }
}