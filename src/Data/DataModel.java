package Data;

import Data.IO.FileModel;
import Global.Util;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Stores time series and aggregated data on them.
 * Time series are organized in ensembles, requiring that the time series in a file form an ensemble.
 * Each ensemble is associated with an ID. Currently, only two ensembles are supported, with the IDs 0 and 1.
 *
 * @author Carl Witt
 */
public class DataModel {

    /** Stores the time series by their file id (input file A = 0 or input file B = 1) and their time series ID, which, nevertheless, should be unique. */
    private List<ObservableMap<Integer,TimeSeries>> timeSeriesByEnsemble;
    public ObservableList<TimeSeries> ensemble1TimeSeries = FXCollections.observableArrayList();    // TODO: check if these are really necessary anymore (any observers?)
    public ObservableList<TimeSeries> ensemble2TimeSeries = FXCollections.observableArrayList();

    /** each time series in input set A will be cross correlated with each time series in input set B. should be a set, actually but sequential access is useful for caching routines in cross correlation computations. */
    public final ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList();
    public final ObservableList<TimeSeries> correlationSetB = FXCollections.observableArrayList();

    public final TimeSeriesAverager correlationSetAAggregator = new TimeSeriesAverager(correlationSetA);
    public final TimeSeriesAverager correlationSetBAggregator = new TimeSeriesAverager(correlationSetB);

    /** These cache the minimum and maximum Y value of the current dataset. Are invalidated (set to null) when time series are added or removed.
     * The array index refers to the ensemble ID. */
    private Double[] minY = new Double[2];
    private Double[] maxY = new Double[2];

    public DataModel(List<Collection<TimeSeries>> tsByEnsemble) throws EnsembleIntersectionIsEmptyException {

        timeSeriesByEnsemble = new ArrayList<>();
        for (Collection<TimeSeries> tss : tsByEnsemble){

            // index time series in a hashmap
            HashMap<Integer, TimeSeries> map = new HashMap<>();
            for(TimeSeries ts : tss) map.put(ts.getId(), ts);
            timeSeriesByEnsemble.add(FXCollections.observableMap(map));

        }

        double[][] xValues = new double[][]{
                timeSeriesByEnsemble.get(0).values().stream().findAny().get().getDataItems().re,
                timeSeriesByEnsemble.get(1).values().stream().findAny().get().getDataItems().re,
        };
        Optional<int[][]> ensembleClippings = findEnsembleClippings(xValues);

        if(ensembleClippings.isPresent()){

            xValues[0] = Arrays.copyOfRange(xValues[0], ensembleClippings.get()[0][0], ensembleClippings.get()[0][1]+1);
            xValues[1] = Arrays.copyOfRange(xValues[1], ensembleClippings.get()[1][0], ensembleClippings.get()[1][1]+1);

            assert Util.distanceSmallerThan(xValues[0][0], xValues[1][0], FileModel.X_TOLERANCE); // assert start x values of both ensembles match
            assert Util.distanceSmallerThan(xValues[0][1], xValues[1][1], FileModel.X_TOLERANCE); // assert end x values of both ensembles match

            Platform.runLater(() -> {
                // inform the user that clipping has been performed to adapt offset and length of both ensembles.
                Alert informAboutClipping = new Alert(Alert.AlertType.INFORMATION, String.format("The ensembles have been clipped to a common x value range of [%s, %s]", xValues[0][0], xValues[0][xValues[0].length - 1]));
                informAboutClipping.showAndWait();
            });

            for (int ensembleID = 0; ensembleID < 2; ensembleID++) {

                for(TimeSeries ts : tsByEnsemble.get(ensembleID)){
                    ts.getDataItems().re = xValues[ensembleID];
                    ts.getDataItems().im = Arrays.copyOfRange(ts.getDataItems().im, ensembleClippings.get()[ensembleID][0], ensembleClippings.get()[ensembleID][1]+1);
                }

            }
        } else {
            throw new EnsembleIntersectionIsEmptyException("The time series ensembles have no common x values, no sensible computations can be performed on them.");
        }

        ensemble1TimeSeries = FXCollections.observableArrayList(timeSeriesByEnsemble.get(0).values());
        ensemble2TimeSeries = FXCollections.observableArrayList(timeSeriesByEnsemble.get(1).values());

    }

    /** Indicates that two ensembles have no common x values, thus no sensible computation can be performed on them. */
    public static class EnsembleIntersectionIsEmptyException extends Exception { public EnsembleIntersectionIsEmptyException(String m){super(m);} }

    /**
     * Finds the indices of the elements to retain such that both ensembles start at the same x value and have the same length.
     * @param xValues the x values of the first ensembles as xValues[0] and the x values of the second ensemble as xValues[1].
     * @return The returned array is of the form
     * {
     *      {ensemble 0 lower bound, ensemble 0 upper bound},
     *      {ensemble 1 lower bound, ensemble 1 upper bound}
     * }
     * Where each bound specifies the index of the left-/rightmost element that should be retained.
     * If the covered x ranges of the ensembles do not overlap, an empty Optional is returned, indicating that no elements should be retained.
     */
    protected static Optional<int[][]> findEnsembleClippings(double[][] xValues) {

        double deltaX = xValues[0][1] - xValues[0][0];
        assert Util.distanceSmallerThan(xValues[1][1] - xValues[1][0], deltaX, FileModel.X_TOLERANCE) : "Delta X are not identical.";

        // to avoid further complications, xValues that are ordered descending instead of ascending are reversed.
        // this flag memorizes whether the xValues have been reversed.
        boolean reversed = false;
        if(deltaX < 0){
            ArrayUtils.reverse(xValues[0]);
            ArrayUtils.reverse(xValues[1]);
            reversed = true;
        }

        // contains the desired new bounds of the two ensembles to assure that they have the same length and start position.
        int[][] ensembleClipping = new int[][]{
            {0, xValues[0].length-1}, // initially, leave ensembles unchanged
            {0, xValues[1].length-1}
        };
        int LOWER_BOUND = 0;    // refers to the semantics of the ensembleClipping[ensembleID] elements
        int UPPER_BOUND = 1;    // refers to the semantics of the ensembleClipping[ensembleID] elements

        double minXValue0 = xValues[0][LOWER_BOUND];
        double minXValue1 = xValues[1][LOWER_BOUND];
        double maxXValue0 = xValues[0][xValues[0].length - 1];
        double maxXValue1 = xValues[1][xValues[1].length - 1];

        // check whether both x ranges are disjoint
        if(! new BoundingBox(minXValue0, 0, maxXValue0-minXValue0, 1).intersects(minXValue1, 0, maxXValue1 - minXValue1, 1)){
            // if the x values have been reversed, undo the reversing and transform the clipping
            if(reversed){
                ArrayUtils.reverse(xValues[0]);
                ArrayUtils.reverse(xValues[1]);
            }
            // intersection is empty.
            return Optional.empty();
        } else {

            // check whether minimum ensemble x values are within tolerance, otherwise compute smallest common x value
            if(!Util.distanceSmallerThan(minXValue0, minXValue1, FileModel.X_TOLERANCE)){
                // find smallest common x value
                if(minXValue0 < minXValue1){
                    // ensemble 0 starts earlier than ensemble 1 - the earlier values of ensemble 0 are clipped
                    ensembleClipping[0][LOWER_BOUND] = ArrayUtils.indexOf(xValues[0], minXValue1, FileModel.X_TOLERANCE);
                } else {
                    // ensemble 1 starts earlier than ensemble 0 - the earlier values of ensemble 1 are clipped
                    ensembleClipping[1][LOWER_BOUND] = ArrayUtils.indexOf(xValues[1], minXValue0, FileModel.X_TOLERANCE);
                }
            }

            // check whether maximum ensemble x values are within tolerance, otherwise compute largest common x value
            if(!Util.distanceSmallerThan(maxXValue0, maxXValue1, FileModel.X_TOLERANCE)){
                // find largest common x value
                if(maxXValue0 > maxXValue1){
                    // ensemble 0 ends later than ensemble 1 - the later values of ensemble 0 are clipped
                    ensembleClipping[0][UPPER_BOUND] = ArrayUtils.indexOf(xValues[0], maxXValue1, FileModel.X_TOLERANCE);
                } else {
                    // ensemble 1 ends later than ensemble 0 - the later values of ensemble 1 are clipped
                    ensembleClipping[1][UPPER_BOUND] = ArrayUtils.indexOf(xValues[1], maxXValue0, FileModel.X_TOLERANCE);
                }
            }

            // if the x values have been reversed, undo the reversing and transform the clipping
            if(reversed){
                ArrayUtils.reverse(xValues[0]);
                ArrayUtils.reverse(xValues[1]);
                BiFunction<Integer, double[], Integer> reverseIndex = (Integer index, double[] array) -> array.length-1 - index;

                // intersection is non-empty. x values have been reversed.
                return Optional.of(new int[][]{
                        {reverseIndex.apply(ensembleClipping[0][UPPER_BOUND], xValues[0]), reverseIndex.apply(ensembleClipping[0][LOWER_BOUND], xValues[0])},   // upper bounds become lower bounds. indices are reversed.
                        {reverseIndex.apply(ensembleClipping[1][UPPER_BOUND], xValues[1]), reverseIndex.apply(ensembleClipping[1][LOWER_BOUND], xValues[1])}
                });
            }
        }

        // intersection is non-empty. x values have not been reversed.
        return Optional.of(ensembleClipping);
    }

    public DataModel(){
        timeSeriesByEnsemble = Arrays.asList(
                FXCollections.observableMap(new HashMap<>()),
                FXCollections.observableMap(new HashMap<>()));
    }

    public int getTimeSeriesLength(int ensembleID) {
        if (timeSeriesByEnsemble.get(ensembleID).isEmpty()) {
            return 0;
        }
        return timeSeriesByEnsemble.get(ensembleID).values().iterator().next().getDataItems().re.length;
    }

    /** @return the smallest x value among all time series. It is assumed that it is the same for each time series.
     * @param ensembleID the time series group among which the smallest x value is found.
     */
    public double getMinX(int ensembleID) {

        assert timeSeriesByEnsemble.get(ensembleID).values().size() > 0 : "Ensemble " + ensembleID + " has no time series.";
        TimeSeries anyTimeSeries = timeSeriesByEnsemble.get(ensembleID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(ensembleID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[0];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
//            }
    }

    /** @return the largest x value among all time series. It is assumed that it is the same for each time series.
     * @param ensembleID the time series group among which the largest x value is found.
     */
    public double getMaxX(int ensembleID) {

        TimeSeries anyTimeSeries = timeSeriesByEnsemble.get(ensembleID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(ensembleID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[anyTimeSeries.getDataItems().re.length-1];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//        for (TimeSeries ts : values()) {
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
//            }
//        }
    }

    public double getMinY(int ensembleID) {
        // compute if not cached
        if(minY[ensembleID] == null){
            minY[ensembleID] = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByEnsemble.get(ensembleID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(ensembleID).values()) {
                minY[ensembleID] = Math.min(minY[ensembleID], ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
            }

        }
        return minY[ensembleID];
    }

    public double getMaxY(int ensembleID) {
        // compute if not cached
        if(maxY[ensembleID] == null){
            maxY[ensembleID] = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByEnsemble.get(ensembleID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(ensembleID).values()) {
                maxY[ensembleID] = Math.max(maxY[ensembleID], ts.getDataItems().getMax(ComplexSequence.Part.IMAGINARY));
            }

        }

        return maxY[ensembleID];
    }

    /** @return the number of data points in the specified interval (e.g. time period) */
    public int getNumDataPointsInRange(int ensembleID, double lowerBound, double upperBound) {

        TimeSeries anyTimeSeries = ensemble1TimeSeries.get(ensembleID);
        double spacing = anyTimeSeries.getDataItems().re[1]-anyTimeSeries.getDataItems().re[0];
        return (int) Math.ceil((upperBound-lowerBound)/spacing);
    }

    public void put(int ensembleID, int timeSeriesID, TimeSeries timeSeries) {
        timeSeriesByEnsemble.get(ensembleID).put(timeSeriesID, timeSeries);
        if(ensembleID == 0) ensemble1TimeSeries.add(timeSeries);
        if(ensembleID == 1) ensemble2TimeSeries.add(timeSeries);
    }

    public TimeSeries get(int ensembleID, int i) {
        TimeSeries timeSeries = timeSeriesByEnsemble.get(ensembleID).get(i);
        if(timeSeries != null) return timeSeries;
        assert false : String.format("Time series with the given ID %s doesn't exist in the data model.", i);
        return null;
    }

    public Map<Integer, TimeSeries> getEnsemble(int ensembleID) {
        return timeSeriesByEnsemble.get(ensembleID);
    }

    public void clear() {
        for (ObservableMap<Integer,TimeSeries> m : timeSeriesByEnsemble){
            m.clear();
        }
    }

    public int getNumEnsembles() {
        return timeSeriesByEnsemble.size();
    }

    public Iterator<TimeSeries> getTimeSeriesIterator() {
        return new Iterator<TimeSeries>() {
            int ensembleID = 0;
            Iterator<TimeSeries> currentIterator = timeSeriesByEnsemble.get(ensembleID).values().iterator();
            @Override public boolean hasNext() {
                ObservableMap<Integer, TimeSeries> nextSet = timeSeriesByEnsemble.get(ensembleID + 1);
                return currentIterator.hasNext() || (nextSet != null && nextSet.size() > 0);
            }

            @Override public TimeSeries next() {
                if(!currentIterator.hasNext()){
                    ensembleID++;
                    currentIterator = timeSeriesByEnsemble.get(ensembleID).values().iterator();
                }
                return currentIterator.next();
            }
        };
    }

    public int getNumberOfTimeSeries() {
        int count = 0;
        for (Map<Integer, TimeSeries> map : timeSeriesByEnsemble) count += map.size();
        return count;
    }
    public int getNumberOfTimeSeries(int ensembleID) {
        return timeSeriesByEnsemble.get(ensembleID).size();
    }

    public int getNumberOfEnsembles() { return timeSeriesByEnsemble.size(); }

    /**
     * @return null if no time series are present. the minimum and maximum x and y values of all time series in all ensembles otherwise.
     */
    public static Bounds getDataBounds(List<TimeSeries> ensemble1, List<TimeSeries> ensemble2) {

        assert !(ensemble1.isEmpty() && ensemble2.isEmpty()) : "The data bounds of two empty ensembles are undefined.";

        if(ensemble1.isEmpty()) return getDataBounds(ensemble2);
        if(ensemble2.isEmpty()) return getDataBounds(ensemble1);

        return Util.union(getDataBounds(ensemble1), getDataBounds(ensemble2));

    }

    /**
     * @return null if no time series are present. the minimum and maximum x and y values of all time series in all ensembles otherwise.
     */
    public static Bounds getDataBounds(List<TimeSeries> ensemble) {

        if(ensemble.size() == 0) return null;

        double minX = ensemble.stream().map(TimeSeries::getMinX).reduce(Math::min).get();
        double maxX = ensemble.stream().map(TimeSeries::getMaxX).reduce(Math::max).get();
        double minY = ensemble.stream().map(TimeSeries::getMinY).reduce(Math::min).get();
        double maxY = ensemble.stream().map(TimeSeries::getMaxY).reduce(Math::max).get();

        return new BoundingBox(minX, minY, maxX-minX, maxY-minY);
    }
}