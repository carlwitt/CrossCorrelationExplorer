package Data;

import Data.Correlation.CrossCorrelation;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * Aggregates each time series of a time series set by forming groups of k consecutive data points and averaging them.
 */
public class TimeSeriesAverager {

    /** First dimension refers to time series idx, where 0 is for the x values and 1 ... N is for the y values of the respective time series.
     *  Second dimension refers to data point index.
     */
    private double[][] aggregatedData;

    private ObservableList<TimeSeries> timeSeries;

    /** The granularity of the aggregation. How many data points will be aggregated into one. */
    private final IntegerProperty binSize = new SimpleIntegerProperty(20);

    public final void setBinSize(int step){ binSize.set(step); }
    final int getBinSize(){ return binSize.get(); }
    public final IntegerProperty binSizeProperty(){ return binSize; }

    public TimeSeriesAverager(ObservableList<TimeSeries> ensemble){

        this.timeSeries = ensemble;
        ensemble.addListener(this::compute);
        binSize.addListener((observable, oldValue, newValue) -> compute(null));
        compute(null);

    }

    /** Is triggered when time series are added or removed from the observed correlation set. */
    protected void compute(ListChangeListener.Change<? extends TimeSeries> c) {

        if(timeSeries.isEmpty()) return;

        TimeSeries anyTimeSeries = timeSeries.get(0);
        int binSize = getBinSize();

        int numberOfDataPoints = (int) Math.ceil(1. * anyTimeSeries.getSize() / binSize);
        aggregatedData = new double[timeSeries.size()+1][numberOfDataPoints];

        // sample x values
        int sampleIdx = 0;
        for (int dataPointIdx = 0; dataPointIdx < anyTimeSeries.getSize(); dataPointIdx+=binSize) {
            aggregatedData[0][sampleIdx] = anyTimeSeries.getDataItems().re[dataPointIdx];
            sampleIdx++;
        }

        // aggregate y values
        for (int tsID = 0; tsID < timeSeries.size(); tsID++) {
            sampleIdx = 0;
            for (int i = 0; i < anyTimeSeries.getSize(); i += binSize) {
                aggregatedData[tsID+1][sampleIdx] = CrossCorrelation.mean(timeSeries.get(tsID), i, Math.min(i + binSize - 1, anyTimeSeries.getSize()-1));
                sampleIdx++;
            }
        }

    }

    /** Returns the x values of the aggregated data points. */
    public double[] getXValues(){
        return aggregatedData[0];
    }

    /**
     * @param oneBasedTimeSeriesIdx One based (!) time series index.
     * @return the y values of the aggregated time series with the given ID. */
    public double[] getYValues(int oneBasedTimeSeriesIdx){
        return aggregatedData[oneBasedTimeSeriesIdx];
    }

    public int getNumberOfTimeSeries() {
        return aggregatedData.length-1;
    }
}
