package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Translate;

import java.awt.*;
import java.util.List;

import static Data.Correlation.CorrelationMatrix.*;

/**
 * Used to draw the correlogram. Takes an aggregated correlation matrix and renders its cells as a colored blocks.
 *
 * @author Carl Witt
 */
public class Correlogram extends CanvasChart {

    private SharedData sharedData;

    /** encodes 2D values in a single color */
    private MultiDimensionalPaintScale paintScale;

    // width and height of a cell in the correlogram.
    // using the actual windowSize is not feasible, since windows overlap.
    // a new window starts in each baseWindowOffset steps, each window is assigned that width (but at most the window size, in case of negative overlap)
    double blockWidth, blockHeight;

    Rectangle activeWindowRect = new javafx.scene.shape.Rectangle(10, 10);
    double activeWindowStrokeWidth = 2;

    /** the y offset for drawing correlogram blocks.
     *  this can be used to center blocks around their time lag (e.g. time lag 0 => -0.5 ... 0.5) */
    double blockYOffset = 0;

    // render mode switching
    private RENDER_MODE renderMode = RENDER_MODE.MEAN_STD_DEV;

    /** Defines how the data in the correlation matrix is visualized. */
    public static enum RENDER_MODE { // color each cell by
        MEAN_STD_DEV,           // mean and standard deviation
        MEDIAN_IQR,             // median and interquartile range
        NEGATIVE_SIGNIFICANT,   // percentage of significantly negative correlated window pairs
        POSITIVE_SIGNIFICANT,   // percentage of significantly positive correlated window pairs
        ABSOLUTE_SIGNIFICANT    // percentage of significantly correlated window pairs
    }

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public Correlogram(MultiDimensionalPaintScale paintScale){
        this.paintScale=paintScale;
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);

        chartCanvas.setOnMouseMoved(reportHighlightedCell);

        // initialize the active window highlight rectangle
        activeWindowRect.setFill(Color.rgb(0, 0, 0, 0));    // transparent fill
        activeWindowRect.setStroke(Color.web("#fff500")); // yellow border
        activeWindowRect.setStrokeType(StrokeType.OUTSIDE);
        activeWindowRect.setVisible(false);
        activeWindowRect.setStrokeWidth(activeWindowStrokeWidth);
        activeWindowRect.setMouseTransparent(true);
        canvasPane.getChildren().add(activeWindowRect);
    }
    
    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;
        
        // listen to changes in the correlation result matrix
        sharedData.correlationMatrixProperty().addListener(new ChangeListener<CorrelationMatrix>() {
            @Override public void changed(ObservableValue<? extends CorrelationMatrix> ov, CorrelationMatrix t, CorrelationMatrix m) {

                resetView();
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
        List columns = matrix.getResultItems();

        // depending on the render mode, configure the paintscale
        configurePaintscale(matrix);

        computeBlockDimensions();

        // clipping on the time axis
        int columnIdxFrom = 0,
            columnIdxTo = columns.size()-1;
        if(getAxesRanges() != null){
            columnIdxFrom = (int) Math.max(0., Math.ceil(getAxesRanges().getMinX() - 1950.)/blockWidth-matrix.metadata.baseWindowOffset);
            columnIdxTo   = (int) Math.min(columns.size()-1, Math.ceil(getAxesRanges().getMaxX()- 1950.)/blockWidth+matrix.metadata.baseWindowOffset);
        }

        // clipping on the resolution
        Point2D blockDimensionsScreen = dataToScreen.deltaTransform(blockWidth, -blockHeight); // negative height because the transformation takes data coordinates and gives screen coordinates
        int windowStep = Math.max(1, (int) Math.floor(1. / blockDimensionsScreen.getX()));
        int lagStep = Math.max(1, (int) Math.floor(2./blockDimensionsScreen.getY()));
        blockHeight *= lagStep;
        blockWidth  *= windowStep;

        switch(renderMode){
            case MEAN_STD_DEV:
                drawContentsMultivariate(gc, dataToScreen, matrix, xValues, columns, columnIdxFrom, columnIdxTo, windowStep, lagStep, MEAN, STD_DEV);
                break;
            case MEDIAN_IQR:
                drawContentsMultivariate(gc, dataToScreen, matrix, xValues, columns, columnIdxFrom, columnIdxTo, windowStep, lagStep, MEDIAN, IQR);
                break;
            case NEGATIVE_SIGNIFICANT:
                drawContentsUnivariate(gc, dataToScreen, matrix, xValues, columns, columnIdxFrom, columnIdxTo, windowStep, lagStep, NEGATIVE_SIGNIFICANT);
                break;
            case POSITIVE_SIGNIFICANT:
                drawContentsUnivariate(gc, dataToScreen, matrix, xValues, columns, columnIdxFrom, columnIdxTo, windowStep, lagStep, POSITIVE_SIGNIFICANT);
                break;
            case ABSOLUTE_SIGNIFICANT:
                drawContentsUnivariate(gc, dataToScreen, matrix, xValues, columns, columnIdxFrom, columnIdxTo, windowStep, lagStep, ABSOLUTE_SIGNIFICANT);
                break;
        }


        xAxis.drawContents();
        yAxis.drawContents();
        highlightActiveWindow();

    }

    protected void drawContentsUnivariate(GraphicsContext gc, Affine dataToScreen, CorrelationMatrix matrix, double[] xValues, List columns, int columnIdxFrom, int columnIdxTo, int windowStep, int lagStep, int DIM) {// for each column of the matrix (or, equivalently, for each time window)
        for (int i = columnIdxFrom; i <= columnIdxTo; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = (CorrelationMatrix.CorrelationColumn) columns.get(i);

            double minX, minY;

            // center around window center
            minX = xValues[column.windowStartIndex] + 0.5* matrix.metadata.windowSize - blockWidth/2;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell, the same for the last drawn cell
            int lagIdxFrom = 0,
                lagIdxTo = column.data[DIM].length-1;

            // clipping on the lag axis
            if(getAxesRanges() != null){
                lagIdxFrom = (int) Math.max(0., Math.floor(getAxesRanges().getMinY() - column.tauMin)-1);
                lagIdxTo   = (int) Math.min(column.data[DIM].length-1, Math.ceil(getAxesRanges().getMaxY() - column.tauMin)+1);
            }

            for (int lag = lagIdxFrom; lag <= lagIdxTo; lag += lagStep) {

                minY = column.tauMin + lag + 1 + blockYOffset; //CorrelationMatrix.splitLag(idx, columnLength)*height + yOffset + 1;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + blockWidth, minY + blockHeight);

                // draw cell
//                System.out.print(column.data[DIM][lag] + ",");
                gc.setFill(paintScale.getPaint(column.data[DIM][lag]));
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX() +1, ulc.getY() - brc.getY()+1);

            }
//            System.out.println();
        }
    }

    protected void drawContentsMultivariate(GraphicsContext gc, Affine dataToScreen, CorrelationMatrix matrix, double[] xValues, List columns, int columnIdxFrom, int columnIdxTo, int windowStep, int lagStep, int DIM1, int DIM2) {// for each column of the matrix (or, equivalently, for each time window)
        for (int i = columnIdxFrom; i <= columnIdxTo; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = (CorrelationMatrix.CorrelationColumn) columns.get(i);

            double minX, minY;

            // center around window center
            minX = xValues[column.windowStartIndex] + 0.5* matrix.metadata.windowSize - blockWidth/2;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell, the same for the last drawn cell
            int lagIdxFrom = 0,
                lagIdxTo = column.data[DIM1].length-1;

            // clipping on the lag axis
            if(getAxesRanges() != null){
                lagIdxFrom = (int) Math.max(0., Math.floor(getAxesRanges().getMinY() - column.tauMin)-1);
                lagIdxTo   = (int) Math.min(column.data[DIM1].length-1, Math.ceil(getAxesRanges().getMaxY() - column.tauMin)+1);
            }

            for (int lag = lagIdxFrom; lag <= lagIdxTo; lag += lagStep) {

                minY = column.tauMin + lag + 1 + blockYOffset; //CorrelationMatrix.splitLag(idx, columnLength)*height + yOffset + 1;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + blockWidth, minY + blockHeight);

                // draw cell
                gc.setFill(paintScale.getPaint(column.data[DIM1][lag], column.data[DIM2][lag]));
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX() +1, ulc.getY() - brc.getY()+1);

            }

        }
    }

    protected void configurePaintscale(CorrelationMatrix matrix) {

        switch (renderMode){
            case MEAN_STD_DEV:
                paintScale.setBiPolar(true);
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setSecondaryColor(Color.RED);
                // center mean = zero at the middle of the axis
                double meanRangeMax = Math.max(Math.abs(matrix.getMin(MEAN)), Math.abs(matrix.getMax(MEAN)));
                paintScale.setLowerBounds(-meanRangeMax, matrix.getMin(STD_DEV));
                paintScale.setUpperBounds(meanRangeMax, matrix.getMax(STD_DEV));
                break;
            case MEDIAN_IQR:
                paintScale.setBiPolar(true);
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setSecondaryColor(Color.RED);
                // center median = zero at the middle of the axis
                double medianRangeMax = Math.max(Math.abs(matrix.getMin(MEDIAN)), Math.abs(matrix.getMax(MEDIAN)));
                paintScale.setLowerBounds(-medianRangeMax, matrix.getMin(IQR));
                paintScale.setUpperBounds(medianRangeMax, matrix.getMax(IQR));
                break;
            case NEGATIVE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
                break;
            case POSITIVE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.RED);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
                break;
            case ABSOLUTE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.GREEN);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
        }
        paintScale.compute();
    }

    private void computeBlockDimensions() {
        WindowMetadata metadata = sharedData.getCorrelationMatrix().metadata;
        blockWidth = Math.min(metadata.windowSize, metadata.baseWindowOffset);
        blockHeight = 1;
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

            // determine the space for each column in the correlogram and the offset of the first column on the X axis
            double blockWidth = matrix.metadata.baseWindowOffset;
            double windowOffset = matrix.getStartXValueInTimeSeries() + 0.5*matrix.metadata.windowSize - blockWidth/2;

            // use equi-distance property (of columns) to calculate the window index by division
            int activeTimeWindowIdx = (int) Math.floor((dataCoordinates.getX() - windowOffset)/blockWidth);

            Point activeCell;
            // check whether the mouse points to any column
            if(activeTimeWindowIdx >= 0 && activeTimeWindowIdx < matrix.getResultItems().size()){

                CorrelationMatrix.CorrelationColumn activeTimeWindow = matrix.getColumn(activeTimeWindowIdx);

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
            if( ! sharedData.getHighlightedCell().equals(activeCell)){
                sharedData.setHighlightedCell(activeCell);
                highlightActiveWindow();
            }
        }
    };

    /**
     * Highlights the active window, i.e. the (time,lag) coordinate in the correlogram that is hovered by the user.
     * This is done by moving a rectangle in the scene graph over the according position.
     */
    void highlightActiveWindow(){

        Point activeWindow = sharedData.getHighlightedCell();
        // check whether the active window is a valid coordinate
        boolean drawWindow = activeWindow.x >= 0 && activeWindow.x != Integer.MAX_VALUE && activeWindow.y >= 0 && activeWindow.y != Integer.MAX_VALUE;

        // position rectangle over the window in the correlogram
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(drawWindow && matrix != null){

            computeBlockDimensions();
            // retrieve block anchor
            double[] xValues = matrix.metadata.setA.get(0).getDataItems().re;
            double minX = xValues[matrix.getResultItems().get(activeWindow.x).windowStartIndex] + 0.5* matrix.metadata.windowSize - blockWidth/2;
            double minY = matrix.getResultItems().get(activeWindow.x).tauMin + activeWindow.y + 1 + blockYOffset;

            // transform data to screen coordinates
            Affine dataToScreen = dataToScreen();
            Point2D anchorScreen = dataToScreen.transform(minX, minY);
            Point2D dimensionsScreen = dataToScreen.deltaTransform(blockWidth, -blockHeight);

            // check that the rectangle doesn't extend the panes
            BoundingBox boundsScreen = new BoundingBox(anchorScreen.getX()-activeWindowStrokeWidth, anchorScreen.getY()-activeWindowStrokeWidth, dimensionsScreen.getX()+2*activeWindowStrokeWidth, dimensionsScreen.getY()+2*activeWindowStrokeWidth);
            if(canvasPane.getLayoutBounds().contains(boundsScreen)){
                activeWindowRect.setLayoutX(anchorScreen.getX());
                activeWindowRect.setLayoutY(anchorScreen.getY());
                activeWindowRect.setWidth(dimensionsScreen.getX());
                activeWindowRect.setHeight(dimensionsScreen.getY());
            } else drawWindow = false;

        }

        activeWindowRect.setVisible(drawWindow);
    }

    /**
     * Resets the axes such that they fit the matrix bounds.
     * Performs a redraw.
     */
    public void resetView() {
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

    /**
     * @param renderMode see {@link RENDER_MODE}
     */
    public void setRenderMode(RENDER_MODE renderMode) {
        this.renderMode = renderMode;

    }


    /**
     * @return an image of the current contents of the visualization window
     */
    public WritableImage getCurrentViewAsImage(){

        int width = (int) chartCanvas.getWidth(),
            height = (int) chartCanvas.getHeight();

        WritableImage wim = new WritableImage(width, height);

        chartCanvas.snapshot(null, wim);

        return wim;
    }

}