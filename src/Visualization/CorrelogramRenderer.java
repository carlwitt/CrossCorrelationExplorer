package Visualization;

import Data.Correlation.CorrelationMatrix;
import javax.validation.constraints.NotNull;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javafx.scene.chart.NumberAxis;
import javax.swing.JPanel;

/**
 *  * TODO: align zero correlation offset in the middle of the result data 
 * 0 1 2 3 -> 0 1 2 -1 -> -1 0 1 2
 * The last element has index -1, the second last -2, etc.
 * Display half (round up) of the values as positive values
 * N = 4        N = 5       number of values
 * V = 3        V = 4       number of values excl. 0
 * pos = 2      pos = 2     positive interpretation
 * neg = 1      neg = 2     negative interpretation
 * Cyclic shift of the array by neg (to the right)
 * data'[i] = data[i-neg]
 * data'[i] = data[(i+neg)%N] ?
 * @author Carl Witt
 */
public class CorrelogramRenderer {

    /**
     * The visualization configuration.
     */
    public CorrelogramParameters parameters;
    /**
     * The data to render.
     */
    CorrelationMatrix matrix;

    XYZZBlockRenderer renderer;
    
    /** x axis refers to time (or, more precise, correlation window index)
     *  y axis refers to time lag (tau value)
     *  z axis refers to the correlation value (used in the paint scale legend)
     */
    NumberAxis xAxis, yAxis, zAxis;
            
    public CorrelogramRenderer(CorrelogramParameters specification) {
        this.parameters = specification;
        
        // create paint scale and renderer
        renderer = new XYZZBlockRenderer(specification.paintScale);
        
        // configure x axis,y axis and paint scale axis.
        // label, integer ticks, remove all padding
//        xAxis = new NumberAxis("time");
//        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
//        xAxis.setLowerMargin(0.0);
//        xAxis.setUpperMargin(0.0);
//        yAxis = new NumberAxis("time lag (years)");
//        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
//        yAxis.setLowerMargin(0.0);
//        yAxis.setUpperMargin(0.0);
//        zAxis = new NumberAxis("Correlation mean");
//        zAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        
        
    }


    /**
     * @return A widget that displays the correlogram. 
     */
    public JPanel render(@NotNull final CorrelationMatrix matrix) {
        
        // adjust scale to the range of the data set
        MultiDimensionalPaintScale scale = parameters.paintScale;
        double limit = Math.max(Math.abs(matrix.getMeanMinValue()), Math.abs(matrix.getMeanMaxValue()));
        scale
            .setLowerBounds(new double[]{matrix.getMeanMinValue(),matrix.getStdDevMinValue()})
            .setUpperBounds(new double[]{matrix.getMeanMaxValue(),matrix.getStdDevMaxValue()});
//            .setLowerBounds(new double[]{1500, matrix.getStdDevMinValue()})
//            .setUpperBounds(new double[]{2000, matrix.getStdDevMaxValue()});
//            .setLowerBounds(new double[]{-limit,matrix.getStdDevMinValue()})
//            .setUpperBounds(new double[]{ limit,matrix.getStdDevMaxValue()});
//        renderer.setPaintScale(scale);
//        System.out.println(String.format("Data set range: min = %s, max = %s",matrix.getMeanMinValue(), matrix.getMeanMaxValue()));

        // create legend
        
        // configure (y) time lag axis
        double minLag = Math.max(parameters.minTimeLag, matrix.getMinY());
        double maxLag = Math.min(parameters.maxTimeLag, matrix.getMaxY());
//        plot.getRangeAxis().setLowerBound(minLag-0.5);// setAutoRange(true);
//        plot.getRangeAxis().setUpperBound(maxLag+0.5);//Bound(parameters.minTimeLag);
//System.out.println(String.format("minY %s, maxY %s, minLag: %s, lowerBound: %s", matrix.getMinY(), matrix.getMaxY(),minLag,plot.getRangeAxis().getLowerBound()));
        
        // configure (x) time axis
//        plot.getDomainAxis().setLowerBound(0);
        // assume that all time series have the same length
//        plot.getDomainAxis().setUpperBound(matrix.getSeriesCount());
//        plot.getDomainAxis().setMinorTickCount(matrix.metadata.windowSize);
//        plot.getDomainAxis().setMinorTickMarksVisible(true);
        
//        result.setMouseWheelEnabled(true);
        return null;

        
    }
    
//    XYZToolTipGenerator tooltipGenerator = new XYZToolTipGenerator() {
//        
//        DecimalFormat df = new DecimalFormat("#.000");
//        
//
//        @Override
//        public String generateToolTip(XYZDataset dataset, int series, int item) {
//            
//            CorrelationMatrix m = (CorrelationMatrix) dataset;
//            double mean = m.getZValue(series, item);
//            double stdDev = m.getZ2Value(series, item);
//            return String.format("t=%s, lag=%s, μ=%s, σ=%s",series, item, mean, stdDev);
//        }
//        
//        @Override
//        public String generateToolTip(XYDataset dataset, int series, int item) {
//            CorrelationMatrix m = (CorrelationMatrix) dataset;
//            double mean   = m.getZValue(series, item);
//            double stdDev = m.getZ2Value(series, item);
//            return String.format("t=%s, 𝛕=%s, μ=%s, σ=%s",series, m.splitLag(item), df.format(mean), df.format(stdDev));
//        }
//    };
    
    /** Renders two z values for each x-y combination by merging them into two color dimensions, namely hue and saturation.*/
    public static class XYZZBlockRenderer {
    
    /**
     * The block width (defaults to 1.0).
     */
    public double blockWidth = 1.0;

    /**
     * The block height (defaults to 1.0).
     */
    private double blockHeight = 1.0;

    /**
     * The anchor point used to align each block to its (x, y) location.  The
     * default value is <code>RectangleAnchor.CENTER</code>.
     */

    /** Temporary storage for the x-offset used to align the block anchor. */
    private double xOffset = 0.;

    /** Temporary storage for the y-offset used to align the block anchor. */
    private double yOffset = -0.5;

    /** The paint scale. */
    private MultiDimensionalPaintScale paintScale;
        
    public XYZZBlockRenderer(MultiDimensionalPaintScale paintScale){
        this.paintScale = paintScale;
    }
        
//    public void drawItem(Graphics2D g2, XYItemRendererState state,
//            Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
//            ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset,
//            int series, int item, CrosshairState crosshairState, int pass) {
//
//        double x = dataset.getXValue(series, item);
//        double y = dataset.getYValue(series, item);
//        double mean = 0.0;
//        double stdDev = 0.0;
//        if (dataset instanceof CorrelationMatrix) {
//            CorrelationMatrix m = (CorrelationMatrix) dataset;
//            mean = m.getZValue(series, item);
//            stdDev = m.getZ2Value(series, item);
//        }
//        
////        stdDev = 0.;
////        blockWidth = Math.max(0.01,1-paintScale.interpolate(stdDev, 1));// stdDev
////        xOffset = -blockWidth/2;
////        blockHeight = Math.max(0.01,1-paintScale.interpolate(stdDev, 1));// stdDev
////        yOffset = -blockHeight/2;
//        
//        
////        System.out.println(String.format("Draw block for series %d, item %d.\nDataset: mean = %s, stddev = %s\n",
////                series,item,mean,stdDev));
//        Paint p = paintScale.getPaint(new double[]{mean,stdDev});
//        
//        
//                //= this.paintScale.getPaint(mean,stdDev);
//        double xx0 = domainAxis.valueToJava2D(x + this.xOffset, dataArea,
//                plot.getDomainAxisEdge());
//        double yy0 = rangeAxis.valueToJava2D(y + this.yOffset, dataArea,
//                plot.getRangeAxisEdge());
//        double xx1 = domainAxis.valueToJava2D(x + this.blockWidth
//                + this.xOffset, dataArea, plot.getDomainAxisEdge());
//        double yy1 = rangeAxis.valueToJava2D(y + this.blockHeight
//                + this.yOffset, dataArea, plot.getRangeAxisEdge());
//        Rectangle2D block;
//        PlotOrientation orientation = plot.getOrientation();
//        if (orientation.equals(PlotOrientation.HORIZONTAL)) {
//            block = new Rectangle2D.Double(Math.min(yy0, yy1),
//                    Math.min(xx0, xx1), Math.abs(yy1 - yy0),
//                    Math.abs(xx0 - xx1));
//        }
//        else {
//            block = new Rectangle2D.Double(Math.min(xx0, xx1),
//                    Math.min(yy0, yy1), Math.abs(xx1 - xx0),
//                    Math.abs(yy1 - yy0));
//        }
//        g2.setPaint(p);
////        g2.fillOval((int)block.getX(), (int)block.getY(), (int)block.getWidth(),(int) block.getHeight());
//        g2.fill(block);
//        g2.setStroke(new BasicStroke(1.0f));
//        g2.draw(block);
//        
////        g2.setPaint(Color.black);
////        g2.drawString("mean: "+mean, (int)(block.getX()+this.xOffset), (int)(block.getY()+this.yOffset));
////        g2.drawString("mean: "+mean, 0,0);
//
//        EntityCollection entities = state.getEntityCollection();
//        if (entities != null) {
//            addEntity(entities, block, dataset, series, item, 0.0, 0.0);
//        }
//
//    }
//        
    }
    
}
