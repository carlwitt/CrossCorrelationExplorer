package Visualization;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import com.sun.javafx.tk.FontLoader;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import javafx.util.converter.NumberStringConverter;

import java.awt.*;
import java.util.List;
import java.util.Locale;

/**
 * Used to draw the correlogram. Takes a matrix with two dimensional entries and renders each of them as a matrix of colored blocks.
 * @author Carl Witt
 */
public class CorrelogramLegend extends CanvasChart {
    
    private double xValueMin;
    private double xValueMax;
    private double yValueMin;
    private double yValueMax;
    private double xTickUnit;
    private double yTickUnit;

    /** These values are shown explicitly in the legend using a small cross mark. 
     * Used to make reading the correlogram easier by displaying the mean and std dev under the mouse pointer. */
    private Double highlightMean;
    private Double highlightStdDev;
    private final NumberStringConverter legendTipConverter = new NumberStringConverter(Locale.ENGLISH);
    
    /** The number of colors used to encode the mean dimension in the correlation matrix. */
    private final int meanColorResolution = 13;
    /** The number of colors used to encode the standard deviation dimension in the correlation matrix. */
    private final int standardDeviationColorResolution = 4;
    
    private SharedData sharedData;
    private final Font legendTipFont = new Font(10);
    
    public CorrelogramLegend(MultiDimensionalPaintScale paintScale){
        this.paintScale = paintScale;
        xAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        yAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        this.allowScroll = false;
        this.allowZoom = false;
    }
    
    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
   
        // render the correlogram if the result matrix has changed
        sharedData.correlationMatrixProperty().addListener(new ChangeListener<CorrelationMatrix>() {
            @Override public void changed(ObservableValue<? extends CorrelationMatrix> ov, CorrelationMatrix t, CorrelationMatrix t1) {
                // Computes a sample from the full range of the current correlation matrix (its correlation means and standard deviations).
                CorrelationMatrix valueRangeSample = valueRangeSample(meanColorResolution, standardDeviationColorResolution);
                setValueRangeSample(valueRangeSample);
            }
        });
        
        // when the use hovers over a correlogram cell, extract the mean and standard deviation of this cell for display in the legend (legendTip)
        sharedData.highlightedCellProperty().addListener(new ChangeListener() {
            @Override public void changed(ObservableValue ov, Object t, Object t1) {
                Point activeCell = (Point) t1;
                // check whether the column exists the 
                if(activeCell.x >= 0 && activeCell.x < sharedData.getCorrelationMatrix().getResultItems().size() ){
                    CorrelationMatrix.Column activeColumn = sharedData.getCorrelationMatrix().getResultItems().get(activeCell.x);
                    // check whether the row exists
                    if(activeCell.y >= 0 && activeCell.y < activeColumn.mean.length){
                        highlightMean = activeColumn.mean[activeCell.y];
                        highlightStdDev = activeColumn.stdDev[activeCell.y];
                        drawContents();
                    }
                }
            }
        });
        
        
    }
    
    /** This contains the sample values to display when rendering the legend. */
    private final ObjectProperty<CorrelationMatrix> valueRangeSample = new SimpleObjectProperty<>();
    CorrelationMatrix getValueRangeSample(){return valueRangeSample.get();}
    public ObjectProperty<CorrelationMatrix> valueRangeProperty(){return valueRangeSample;}
    void setValueRangeSample(CorrelationMatrix m){
        
        valueRangeSample.set(m);

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
    private MultiDimensionalPaintScale paintScale;

    /**
     * Draws the legend and the
     */
    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
        Affine dataToScreen = dataToScreen();

        if (getValueRangeSample() == null) return;

        // retrieve data to render
        CorrelationMatrix matrix = getValueRangeSample();
        List<CorrelationMatrix.Column> columns = matrix.getResultItems();

        // configure paintscale
        // center mean zero at the middle of the axis
        double meanRangeMax = Math.max(Math.abs(matrix.getMeanMinValue()), Math.abs(matrix.getMeanMaxValue()));
        paintScale.setLowerBounds(-meanRangeMax, matrix.getStdDevMinValue());
        paintScale.setUpperBounds(meanRangeMax, matrix.getStdDevMaxValue());
        // always have a range of [-1, 1] in both dimensions
//        paintScale.setLowerBounds(-1., -1.);
//        paintScale.setUpperBounds(1., 1.);

        // for each column of the matrix (or, equivalently, for each distinct mean value)
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

            }
        }
        drawLegendTip(gc, dataToScreen);
        
        xAxis.drawContents();
        yAxis.drawContents();

    }

    /**
     * Highlights the position of a certain mean and standard deviation by drawing a cross and rendering the values as text.
     * @param gc canvas to draw on
     * @param dataToScreen transformation between legend (mean, std dev) and screen coordinates
     */
    private void drawLegendTip(GraphicsContext gc, Affine dataToScreen) {

        // handle null and NaN
        if(highlightMean == null || highlightStdDev == null) return;
        String meanString = Double.isNaN(highlightMean) ? "Not a Number" : legendTipConverter.toString(highlightMean);
        String stdDevString = Double.isNaN(highlightStdDev) ? "Not a Number" : legendTipConverter.toString(highlightStdDev);

        // cross size in pixels
        double crossSize = 5;
        gc.save();
        gc.setFont(legendTipFont);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);

        // compute position in legend
        highlightMean = Double.isNaN(highlightMean) ? 0 : highlightMean;
        highlightStdDev = Double.isNaN(highlightStdDev) ? 0 : highlightStdDev;
        Point2D sc = dataToScreen.transform(highlightMean, highlightStdDev);

        // draw crosshair
        gc.strokeLine(sc.getX()-crossSize, sc.getY(), sc.getX()+crossSize, sc.getY());
        gc.strokeLine(sc.getX(), sc.getY()-crossSize, sc.getX(), sc.getY()+crossSize);

        // position label relative to the crosshair (left/right, top/bottom) where the most space is available

        String label = String.format("μ = %s, σ = %s", meanString, stdDevString);
        Point2D labelSize = renderedTextSize(label, legendTipFont);

        double xOffset = sc.getX() < getWidth()/2 ?
                crossSize :                         // right
                - crossSize - labelSize.getX();     // left
        double yOffset = sc.getY() < getHeight()/2 ?
                crossSize + labelSize.getY() :      // bottom
                - crossSize;                        // top

        gc.setStroke(Color.WHITE); gc.setLineWidth(2);
        gc.strokeText(label, sc.getX()+xOffset, sc.getY()+yOffset);
        gc.setStroke(Color.BLACK); gc.setLineWidth(1);
        gc.strokeText(label, sc.getX()+xOffset, sc.getY()+yOffset);
        gc.restore();
    }

    /** 
     * Computes evenly spaced samples from the two value ranges of the current correlation matrix.
     * @param meanResolution number of samples taken from the value range of the first dimension (correlation mean). Must be at least two. 
     * @param stdDevResolution number of samples taken from the value range of the second dimension (correlation standard deviation). Must be at least two. 
     * @return The resulting matrix might have a lower resolution, if there's only a single value along one dimension, or null if there is no current correlation matrix
     */
    protected CorrelationMatrix valueRangeSample(int meanResolution, int stdDevResolution){
        
        CorrelationMatrix m = sharedData.getCorrelationMatrix();
        if(m == null) return null;

        // center mean zero at the middle of the axis
        double meanRangeMax = Math.max(Math.abs(m.getMeanMinValue()), Math.abs(m.getMeanMaxValue()));
        double[] meanRange = new double[]{-meanRangeMax, meanRangeMax};
        double[] stdDevRange = new double[]{m.getStdDevMinValue(), m.getStdDevMaxValue()};
        
        // if the lower bound equals the upper bound, use only one return value in that dimension
        if(meanRange[1]-meanRange[0] < 1e-10){
            meanResolution = 1;
        }
        if(stdDevRange[1]-stdDevRange[0] < 1e-10){
            stdDevResolution = 1;
        }
        
        // increments of the values in each step. resolution is taken -1 because N requested values give N-1 intervals
        double meanStep = meanResolution > 1 ? (meanRange[1]-meanRange[0])/(meanResolution-1) : 0, 
               stdDevStep = stdDevResolution > 1 ? (stdDevRange[1]-stdDevRange[0])/(stdDevResolution-1) : 0;
        
        CorrelationMatrix result = new CorrelationMatrix(null);
        
        // put all combinations of sample values along the two dimensions in a matrix
        for (int i = 0; i < meanResolution; i++) {
            
            double currentMean = meanRange[0] + i*meanStep;
            
            // each column has the length of the standard deviation resolution
            double[] meanValues = new double[stdDevResolution];
            double[] stdDevValues = new double[stdDevResolution];

            for (int j = 0; j < stdDevResolution; j++) {
                
                double currentStdDev = stdDevRange[0] + j*stdDevStep;
                
                meanValues[j] = currentMean;
                stdDevValues[j] = currentStdDev;
                
            }
            ComplexSequence columnValues = ComplexSequence.create(meanValues, stdDevValues);
            result.append(result.new Column(columnValues, i, i));
        }   
//System.out.println(String.format("value range sample: %s", result));
        return result;
    }
    
    
    /**
     * Resets the axes such that they fit the matrix bounds.
     * Performs a redraw.
     */
    public void resetView() {

        CorrelationMatrix m = getValueRangeSample();
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

    private final FontLoader fontLoader = com.sun.javafx.tk.Toolkit.getToolkit().getFontLoader();
    /**
     * Computes the width and height of string. Used to align tick labels.
     * @param string The string to draw.
     * @param font The font name and font size in which the string is drawn.
     * @return The width (x component) and height (y component) of the string if plotted.
     */
    Point2D renderedTextSize(String string, Font font){
        return new Point2D(fontLoader.computeStringWidth(string, font),fontLoader.getFontMetrics(font).getLineHeight());
    }
    
}