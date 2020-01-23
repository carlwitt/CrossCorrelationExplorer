package com.fastdtw;

import Data.DataModel;
import com.fastdtw.dtw.TimeWarpInfo;
import com.fastdtw.dtw.WarpPath;
import com.fastdtw.timeseries.TimeSeries;
import com.fastdtw.util.DistanceFunction;
import com.fastdtw.util.DistanceFunctionFactory;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Map;
import java.util.TreeMap;

/**
* Created by macbookdata on 09.10.14.
*/
public class EnsembleDelayHistogram {

    private TreeMap<Integer, Integer>[] pointHistogams;

    public int radius = 10;

    final DistanceFunction distFn = DistanceFunctionFactory.getDistFnByName("EuclideanDistance");

    public int maxNegativeDelay, maxPositiveDelay;

    public EnsembleDelayHistogram(DataModel dataModel, int ensembleID){

        int numPoints = dataModel.getTimeSeriesLength(ensembleID);

        // create data structures
        pointHistogams = new TreeMap[numPoints];
        for (int i = 0; i < numPoints; i++) {
            pointHistogams[i] = new TreeMap<Integer, Integer>();
        }

        // create time series in FastDTW format
        int tsCount = dataModel.getNumberOfTimeSeries(ensembleID);
        TimeSeries[] tss = new TimeSeries[tsCount];
        for (int i = 0; i < tsCount; i++) {
            tss[i] = new TimeSeries(dataModel.get(ensembleID,i+1).getDataItems().im);
        }

        // compute median time series as probe series
        double[] medianValues = new double[numPoints];
        DescriptiveStatistics ds = new DescriptiveStatistics();

        // for each time point
        for (int i = 0; i < numPoints; i++) {
            // for each time series
            for (int j = 1; j <= dataModel.getNumberOfTimeSeries(ensembleID); j++) {
                double yVal = dataModel.get(ensembleID, j).getDataItems().im[i];
                if( ! Double.isNaN(yVal)) ds.addValue(yVal);
            }
            medianValues[i] = ds.getPercentile(50);
            ds.clear();
        }

        TimeSeries probeSeries = new TimeSeries(medianValues);


        // build ensemble delay histogram
        // for each time series other than the probe series
        for (int i = 0; i <tsCount; i++) {
            // align the series to the probe series
            final TimeWarpInfo info = com.fastdtw.dtw.FastDTW.getWarpInfoBetween(probeSeries, tss[i], radius, distFn);
            WarpPath path = info.getPath();
            // go through each data point of the probe series
            for (int index = 0; index <= path.maxI(); index++) {
                // and record the delays to which it is mapped

                for(int match : path.getMatchingIndexesForI(index)){
                    int offset = match-index; // zero, if the mapped point occurs at the same time, negative, if it occurs earlier, positve if later
                    add(index, offset, 1);
                }

                maxNegativeDelay = Math.min(maxNegativeDelay, getMaxNegativeDelay(index));
                maxPositiveDelay = Math.max(maxPositiveDelay, getMaxPositiveDelay(index));
            }
            if(i%100==0) System.out.println(String.format("%s ", i));
        }

        print();
        System.out.println(String.format("maxNegativeDelay: %s", maxNegativeDelay));
        System.out.println(String.format("maxPositiveDelay: %s", maxPositiveDelay));

    }

    public void add(int index, int bin, int amount){
        Integer prevValue = pointHistogams[index].get(bin);
        pointHistogams[index].put(bin, prevValue == null ? amount : prevValue + amount);
    }

    public int getMaxNegativeDelay(int index){
        return pointHistogams[index].firstKey();
    }
    public int getMaxPositiveDelay(int index){
        return pointHistogams[index].lastKey();
    }


    public void print(){
        for(Map map : pointHistogams){
            System.out.println(String.format("map: %s", map));
        }
    }



}
