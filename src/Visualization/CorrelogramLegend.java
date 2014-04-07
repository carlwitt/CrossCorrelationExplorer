package Visualization;

import Data.Correlation.CorrelationMatrix;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Used to draw the correlogram. Takes a matrix with two dimensional entries and renders each of them as a matrix of colored blocks.
 * @author Carl Witt
 */
public class CorrelogramLegend extends CanvasChart {
    
    double xValueMin, xValueMax, yValueMin, yValueMax;
    double xTickUnit, yTickUnit;
    
    public CorrelogramLegend(MultiDimensionalPaintScale paintScale){
        this.paintScale = paintScale;
        xAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        yAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        this.allowScroll = false;
        this.allowZoom = false;
    }
    
    private final ObjectProperty<CorrelationMatrix> matrix = new SimpleObjectProperty<>();
    public CorrelationMatrix getMatrix(){return matrix.get();}
    public ObjectProperty<CorrelationMatrix> matrixProperty(){return matrix;}
    public void setMatrix(CorrelationMatrix m){
        
        matrix.set(m);

        paintScale.setLowerBounds(m.getMeanMinValue(), m.getStdDevMinValue());
        paintScale.setUpperBounds(m.getMeanMaxValue(), m.getStdDevMaxValue());
        
        // adjust display bounds
        xValueMin = m.getMeanMinValue();
        xValueMax = m.getMeanMaxValue();
        yValueMin = m.getStdDevMinValue();
        yValueMax = m.getStdDevMaxValue(); 
        // handle special case of only one value along the axis
        int meanValues = m.getResultItems().size();
        xTickUnit = meanValues > 1 ? (xValueMax-xValueMin)/(meanValues-1) : 1;
        // handle special case of only one value along the axis
        int stdDevValues = m.getResultItems().get(0).stdDev.length;
        yTickUnit = stdDevValues > 1 ? (yValueMax-yValueMin)/(stdDevValues-1) : 1;
        
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
            
            // the mean resolution is expected to be ≥ 3 (at least two columns)
            width = xTickUnit;
            height = yTickUnit;

            minX = matrix.getMeanMinValue() + i*width - width/2;
            startY = matrix.getStdDevMinValue()+height/2;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int lag = 0; lag < column.mean.length; lag++) {
                Paint paint = paintScale.getPaint(column.mean[lag], column.stdDev[lag]);
                gc.setFill(paint);  gc.setStroke(paint);
                
                minY = lag*height + startY;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + width, minY + height);
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
System.out.println(String.format(
        "translate data block (%s,%s) (%s, %s) to "
      + "%s, %s m=%s s=%s to color %s",
        minX, minY, minX+width, minY+height, 
        ulc, brc, column.mean[lag], column.stdDev[lag], gc.getFill()));
            }
        }
        
        // draw highlight on legend entry
//        gc.save();
//        gc.setStroke(Color.YELLOW);
//        gc.setLineWidth(1);
//        gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
//        gc.restore();
        
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
        
        xAxis.setTickOrigin(m.getMeanMinValue());
        xAxis.setTickUnit(xTickUnit); 
        
        yAxis.setTickOrigin(yValueMin);
        yAxis.setTickUnit(yTickUnit);
        
        xAxis.setLowerBound(xValueMin - xAxis.getTickUnit()/2);
        xAxis.setUpperBound(xValueMax + xAxis.getTickUnit()/2);
        yAxis.setLowerBound(yValueMin - yAxis.getTickUnit()/2);
        yAxis.setUpperBound(yValueMax + yAxis.getTickUnit()/2);
        
    }
    
    public MultiDimensionalPaintScale getPaintScale() {
        return paintScale;
    }

    public void setPaintScale(MultiDimensionalPaintScale paintScale) {
        this.paintScale = paintScale;
    }
}