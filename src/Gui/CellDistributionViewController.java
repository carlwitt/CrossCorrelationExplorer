package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationSignificance;
import Data.SharedData;
import Data.Statistics.AggregatedCorrelationMatrix;
import Data.Statistics.CorrelationHistogram;
import Visualization.Correlogram;
import Visualization.DeferredDrawing;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.web.WebView;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.net.URL;
import java.util.ResourceBundle;

//TODO check for divergent values between computed matrix and on the fly computation.
public class CellDistributionViewController implements Initializable, DeferredDrawing {

    SharedData sharedData;
    private final DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();

    @FXML private Slider resolutionLevelSlider;
    @FXML private Label numberOfBinsLabel;
    @FXML private WebView webView;

    private static String visualizationPath = Correlogram.class.getResource("d3/histogram.html").toExternalForm();

    int numBins = CorrelationHistogram.NUM_BINS;

    /** The minimum absolute correlation value that is significant. Will be visualized in the chart using gray shaded areas. */
    double criticalCorrelationValue = 1.;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        webView.getEngine().load(visualizationPath);

        numberOfBinsLabel.setText("Number of Bins: " + numBins);

        resolutionLevelSlider.setMax(CorrelationHistogram.divisors.length-1);
        resolutionLevelSlider.setValue(CorrelationHistogram.divisors.length-1);

        resolutionLevelSlider.valueProperty().addListener((observable, oldValue, newResolutionLevel) -> {
            if(newResolutionLevel == null) return;
            numBins = numBinsFromResolutionLevel(newResolutionLevel.intValue());
            numberOfBinsLabel.setText("Number of Bins: " + numBins);
            visualizeCellDistribution(sharedData.getActiveCorrelationMatrixRegion());
        });

    }

    protected int numBinsFromResolutionLevel(int resolutionLevel){
        int[] divisors = CorrelationHistogram.divisors;
        int divisor = divisors[divisors.length-1 - resolutionLevel];
        return CorrelationHistogram.NUM_BINS / divisor;
    }

    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;

        // redraw histogram on mouse move
        sharedData.activeCorrelationMatrixRegionProperty().addListener((observable, oldValue, newValue) -> drawContents());

        // compute new critical correlation value on matrix change
        sharedData.correlationMatrixProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null) return;
            CorrelationSignificance correlationSignificance = new CorrelationSignificance(newValue.metadata.windowSize, CorrelationMatrix.getSignificanceLevel(newValue.metadata));
            criticalCorrelationValue = correlationSignificance.getCriticalCorrelationValue();
        });
    }

    /**
     * Retrieves the distribution within the given active matrix region and updates the histogram plot.
     */
    protected void visualizeCellDistribution(AggregatedCorrelationMatrix.MatrixRegionData activeRegion) {

        CorrelationMatrix correlationMatrix = sharedData.getCorrelationMatrix();
        if(correlationMatrix == null) return;
        int[] binCounts;

        if(activeRegion != null){

            binCounts = activeRegion.cellDistribution;

            // compute histogram if no histogram is present (old data)
            if(binCounts == null){
                int columnIndex = activeRegion.column;
                int lagIndex = activeRegion.row;

                descriptiveStatistics.clear();
                double[] correlationValues = correlationMatrix.computeSingleCell(columnIndex, lagIndex);
                for(double r : correlationValues)
                    if( ! Double.isNaN(r)) descriptiveStatistics.addValue(r);

                binCounts = CorrelationHistogram.computeHistogram(descriptiveStatistics, numBins);
            }

            // if the desired granularity is coarser then the full resolution, merge adjacent bins
            if(numBins < CorrelationHistogram.NUM_BINS){
                binCounts = aggregateHistogram(binCounts, numBins);
            }
        } else{
            // if there is no active region, empty the histogram (do not show any distribution)
            binCounts = new int[]{};
        }

        webView.getEngine().executeScript(String.format("update(%s);", convertHistogramToJSON(binCounts)));
    }

    private int[] aggregateHistogram(int[] binCounts, int numBins) {
        int[] aggregated = new int[numBins];
        int divisor = binCounts.length / numBins;
        for (int i = 0; i < binCounts.length; i++) aggregated[i / divisor] += binCounts[i];
        return aggregated;
    }

    /**
     * Computes a histogram from the given data and converts it into JSON format.
     * @param binCounts The frequency distribution of the correlation values.
     * @return JSON of the following form:
     * {bins:
     *      [
     *          {x: -1, y: first bin frequency},
     *          {x: second bin start, y: second bin frequency}, ...
     *      ],
     *  criticalCorrelationValue:
     *      minimum significant correlation value
     * }
     */
    public String convertHistogramToJSON(final int[] binCounts) {

        StringBuilder builder = new StringBuilder("[");

        double min = -1; //stats.getMin();
        double max =  1; //stats.getMax();
        final double binSize = (max - min)/numBins;

        for (int i = 0; i < binCounts.length; i++) {
            double binStart = min + binSize*i;
            builder.append(String.format("{x: %s, y: %s}", binStart, binCounts[i]));
            if(i<binCounts.length-1) builder.append(",");
        }
        builder.append("]");

        return String.format("{bins: %s, criticalCorrelationValue: %s}", builder.toString(), criticalCorrelationValue);

    }

    @Override public void drawContents() {
        if(isDeferringDrawRequests()) return;
        visualizeCellDistribution(sharedData.getActiveCorrelationMatrixRegion());
    }

    boolean isDeferringDrawRequests = false;
    @Override public boolean isDeferringDrawRequests() { return isDeferringDrawRequests; }

    @Override public void setDeferringDrawRequests(boolean deferDrawRequests) {
        if(isDeferringDrawRequests() && ! deferDrawRequests){
            isDeferringDrawRequests = false;
            drawContents();
        } else {
            isDeferringDrawRequests = deferDrawRequests;
        }

    }
}
