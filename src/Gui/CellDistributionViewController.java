package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationSignificance;
import Data.Correlation.CrossCorrelation;
import Data.SharedData;
import Data.TimeSeries;
import Visualization.Correlogram;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.web.WebView;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.awt.*;
import java.net.URL;
import java.util.ResourceBundle;

public class CellDistributionViewController implements Initializable {

    SharedData sharedData;
    private final DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();

    @FXML private Label numBinsLabel;
    @FXML private Slider numBinsSlider;
    @FXML private WebView webView;

    private static String visualizationPath = Correlogram.class.getResource("histogram.html").toExternalForm();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        webView.getEngine().load(visualizationPath);
    }

    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;

        // redraw histogram on mouse move
        sharedData.highlightedCellProperty().addListener((observable, oldValue, newHighlightedCell) -> computeCellDistribution((Point) newHighlightedCell));

        // compute new critical correlation value on matrix change
        sharedData.correlationMatrixProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null) return;
            CorrelationSignificance correlationSignificance = new CorrelationSignificance(newValue.metadata.windowSize, CorrelationMatrix.getSignificanceLevel(newValue.metadata));
            criticalCorrelationValue = correlationSignificance.getCriticalCorrelationValue();
        });
    }

    double criticalCorrelationValue = 0.8;

    protected void computeCellDistribution(Point activeCell) {

        CorrelationMatrix.CorrelationColumn activeTimeWindow = sharedData.getCorrelationMatrix().getColumn(activeCell.x);

        if(activeTimeWindow != null && activeCell.getY() >= 0){
            int timeLag = activeCell.y + activeTimeWindow.tauMin;
            ObservableList<TimeSeries> setA = sharedData.experiment.dataModel.correlationSetA;
            ObservableList<TimeSeries> setB = sharedData.experiment.dataModel.correlationSetB;
            int windowLength = activeTimeWindow.getSize();
            int nans = 0;
            descriptiveStatistics.clear();
            for (TimeSeries tsA : setA){
                for( TimeSeries tsB : setB){

                    double correlation = CrossCorrelation.correlationCoefficient(
                            tsA, tsB,
                            activeTimeWindow.windowStartIndex, activeTimeWindow.windowStartIndex + windowLength - 1,
                            timeLag);
                    if(! Double.isNaN(correlation)) descriptiveStatistics.addValue(correlation);
                    else nans++;
                }
            }

            webView.getEngine().executeScript(String.format("update(%s);", calcHistogramJSON(descriptiveStatistics, 20)));

//            System.out.println(String.format("median %s iqr %s windowStartIndex %s timeLag %s nans %s",
//                    descriptiveStatistics.getPercentile(50),
//                    descriptiveStatistics.getPercentile(75)-descriptiveStatistics.getPercentile(25),
//                    activeTimeWindow.windowStartIndex,
//                    timeLag,
//                    nans));
        }
    }

    public String calcHistogramJSON(DescriptiveStatistics stats, int numBins) {

        StringBuilder builder = new StringBuilder("[");

        final int[] binCounts = new int[numBins];
        final double binSize = (stats.getMax() - stats.getMin())/numBins;

        for (double d : stats.getValues()) {
            int bin = (int) ((d - stats.getMin()) / binSize);
//            assert bin >= 0 && bin < numBins : "Error on histogram calculation";
//            binCounts[bin] += 1;
            if (bin < 0) { /* this data is smaller than min */ }
            else if (bin >= numBins) { /* this data point is bigger than max */ }
            else {
               binCounts[bin] += 1;
            }
        }

        for (int i = 0; i < binCounts.length; i++) {
            double binStart = stats.getMin() + binSize*i;
            builder.append(String.format("{x: %s, y: %s}", binStart, binCounts[i]));
            if(i<binCounts.length-1) builder.append(",");
        }
        builder.append("]");
//        System.out.println(String.format("{data: %s, criticalCorrelationValue: %s}", builder.toString(), criticalCorrelationValue));
        return String.format("{bins: %s, criticalCorrelationValue: %s}", builder.toString(), criticalCorrelationValue);

    }
}
