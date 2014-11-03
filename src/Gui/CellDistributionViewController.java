package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationSignificance;
import Data.SharedData;
import Data.Statistics.AggregatedCorrelationMatrix;
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

    @FXML private Slider numBinsSlider;
    @FXML private Label numberOfBinsLabel;
    @FXML private WebView webView;

    private static String visualizationPath = Correlogram.class.getResource("d3/histogram.html").toExternalForm();

    int numBins = 40;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        webView.getEngine().load(visualizationPath);

        numberOfBinsLabel.setText("Number of Bins: " + numBins);
        numBinsSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null) return;
            numBins = newValue.intValue();
            numberOfBinsLabel.setText("Number of Bins: " + numBins);
            visualizeCellDistribution(sharedData.getActiveCorrelationMatrixRegion());
        });

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

    double criticalCorrelationValue = 1.;

    /**
     * Retrieves the distribution within the given active matrix region and updates the histogram plot.
     */
    protected void visualizeCellDistribution(AggregatedCorrelationMatrix.MatrixRegionData activeRegion) {

        //TODO switch to precomputed histogram data!
        CorrelationMatrix correlationMatrix = sharedData.getCorrelationMatrix();
        if(correlationMatrix == null || activeRegion == null) return;

        if(activeRegion.column >= correlationMatrix.getSize()) return;

        int columnIndex = activeRegion.column;
        int lagIndex = activeRegion.row;

        if(lagIndex >= 0 && lagIndex < Integer.MAX_VALUE){
            descriptiveStatistics.clear();
            double[] correlationValues = correlationMatrix.computeSingleCell(columnIndex, lagIndex);
            for(double r : correlationValues)
                if( ! Double.isNaN(r)) descriptiveStatistics.addValue(r);

            webView.getEngine().executeScript(String.format("update(%s);", calcHistogramJSON(descriptiveStatistics, numBins)));

        }
    }

    public String calcHistogramJSON(DescriptiveStatistics stats, int numBins) {

        StringBuilder builder = new StringBuilder("[");

        final int[] binCounts = new int[numBins];
        double min = -1; //stats.getMin();
        double max =  1; //stats.getMax();
        final double binSize = (max - min)/numBins;

        double[] values = stats.getValues();
        for (double d : values) {
            int bin = (int) ((d - min) / binSize);
            assert bin >= 0 && bin <= numBins : String.format("Invalid bin idx: %s not in [%s, %s] value %s (min %s)", bin, 0, numBins, d, min);
            binCounts[bin == numBins ? numBins - 1 : bin] += 1;
        }

        for (int i = 0; i < binCounts.length; i++) {
            double binStart = min + binSize*i;
            builder.append(String.format("{x: %s, y: %s}", binStart, binCounts[i]));
            if(i<binCounts.length-1) builder.append(",");
        }
        builder.append("]");
//        System.out.println(String.format("{data: %s, criticalCorrelationValue: %s}", builder.toString(), criticalCorrelationValue));
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
