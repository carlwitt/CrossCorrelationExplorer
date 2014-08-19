package Data.IO;

import Data.Correlation.CorrelationMatrix;
import Data.DataModel;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import ucar.ma2.*;
import ucar.nc2.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Stores a computation result (i.e. a correlation matrix) in NetCDF file format.
 * Each computed statistic (e.g. the mean, median, iqr) is stored in a 2D variable that holds one value for each cell.
 * Each cell is addressed by the window index (ranging from 0 to num windows - 1) and the time lag index (ranging from 0 to #different time lags-1).
 *
 *  See {@link NetCDFTimeSeriesGroup} for more detailed information on the NetCDF data format.
 */
public class NetCDFComputationResult {

    private final String computationResultName;

    /** Contains the metadata (window size, window offset, lag range, etc.) including custom parameters (e.g. significance level).
     * The last entries might be null, if a custom parameter is not of type string and thus cannot easily be deserialized from a string value. */
    private final Attribute[] metadataAttributes;

    /** Index aliases to address metadata attributes in {@link #metadataAttributes}. */
    private final static int WINDOW_SIZE = 0;
    private final static int BASEWINDOW_OFFSET = 1;
    private final static int TAU_MIN = 2;
    private final static int TAU_MAX = 3;
    private final static int TAU_STEP = 4;
    private final static int SET_A_IDS = 5;
    private final static int SET_B_IDS = 6;
    private final static int SIGNIFICANCE_LEVEL = 7;
    private final static int NUM_ATTRIBUTES = 8;
    private final /*static */String[] attributeNames = new String[]{
            "WindowSize",
            "WindowOffset",
            "TimeLagMin",
            "TimeLagMax",
            "TimeLagStep",
            "TimeSeriesSet_A_IDs",
            "TimeSeriesSet_B_IDs",
            "Significance_Level"};

    /** The different values for each cell. See the documentation for the constants in the {@link Data.Correlation.CorrelationMatrix} class
     * for more information on the variables (e.g. {@link Data.Correlation.CorrelationMatrix#MEAN}). */
    private Variable[] statistics;                  // mean, stdDev, median, iqr, negSig, posSig, absSig;
    private ArrayDouble.D2[] valuesForStatistics;   // values of the means, stdDevs, medians, iqrs, negSigs, posSigs, absSigs;

    /** Holds the actual time lags (time lag index 0: minimum time lag, time lag index 1: minimum time lag + 1*time lag step, etc...) */
    private Variable timeLags;
    private Array    dataTimeLags;

    /** The file to write to, if constructor     for writing was called. Ensures that the declarations happen on the same file to which data is written. */
    private NetcdfFileWriter dataFile = null;

    /** The field where the result of a read operation ({@link #NetCDFComputationResult(ucar.nc2.NetcdfFile, int, Data.DataModel)}) is written to. */
    private CorrelationMatrix matrix;

    /**
     * Prepares writing to a file. Handles all the declarations and variable creations.
     * @param dataFile The NetCDF file to manipulate.
     * @param id Some number unique to this result (to create a unique prefix for the variables and attributes).
     * @param matrix
     */
    public NetCDFComputationResult(NetcdfFileWriter dataFile, int id, CorrelationMatrix matrix) {

        computationResultName = "CorrelationMatrix"+id;
        this.dataFile = dataFile;
        WindowMetadata metadata = matrix.metadata;

        // create variables and value arrays
        statistics = new Variable[CorrelationMatrix.NUM_STATS];
        valuesForStatistics = new ArrayDouble.D2[CorrelationMatrix.NUM_STATS];

        // declare and set metadata attributes
        metadataAttributes = new Attribute[NUM_ATTRIBUTES];
        metadataAttributes[WINDOW_SIZE] = new Attribute(attributeName(WINDOW_SIZE), metadata.windowSize);
        metadataAttributes[BASEWINDOW_OFFSET] = new Attribute(attributeName(BASEWINDOW_OFFSET), metadata.baseWindowOffset);
        metadataAttributes[TAU_MIN] = new Attribute(attributeName(TAU_MIN), metadata.tauMin);
        metadataAttributes[TAU_MAX] = new Attribute(attributeName(TAU_MAX), metadata.tauMax);
        metadataAttributes[TAU_STEP] = new Attribute(attributeName(TAU_STEP), metadata.tauStep);
        // time series index sets
        ArrayInt.D1 setAIds = new ArrayInt.D1(metadata.setA.size()),
                    setBIds = new ArrayInt.D1(metadata.setB.size());
        for (int i = 0; i < metadata.setA.size(); i++) setAIds.set(i, metadata.setA.get(i).getId());
        for (int i = 0; i < metadata.setB.size(); i++) setBIds.set(i, metadata.setB.get(i).getId());
        metadataAttributes[SET_A_IDS] = new Attribute(attributeName(SET_A_IDS), setAIds);
        metadataAttributes[SET_B_IDS] = new Attribute(attributeName(SET_B_IDS), setBIds);
        metadataAttributes[SIGNIFICANCE_LEVEL] = new Attribute(attributeName(SIGNIFICANCE_LEVEL), CorrelationMatrix.getSignificanceLevel(metadata));

        for(Attribute a : metadataAttributes)
            dataFile.addGroupAttribute(null, a);

        // create dimensions for the result matrix
        Dimension windowIndex  = dataFile.addDimension(null, windowDimensionName(), matrix.getSize());
        Dimension timeLagIndex = dataFile.addDimension(null, timeLagDimensionName(), metadata.getNumberOfDifferentTimeLags());
        assert matrix.getSize() == metadata.numBaseWindows : String.format("Computed number of base windows %s doesn't match number of matrix columns %s.",metadata.numBaseWindows, matrix.getSize());
        List<Dimension> dims = Arrays.asList(windowIndex, timeLagIndex);

        // create a variable holding the different time lags
        timeLags = dataFile.addVariable(null, timeLagDimensionName(), DataType.INT, timeLagDimensionName());
        dataTimeLags = Array.factory(DataType.INT, new int[]{metadata.getNumberOfDifferentTimeLags()});
        int[] differentTimeLags = metadata.getDifferentTimeLags();
        for (int i = 0; i < differentTimeLags.length; i++) dataTimeLags.setInt(i, differentTimeLags[i]);

        // declare variables that hold the different statistics for each cell
        for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++) {
            statistics[stat] = dataFile.addVariable(null, variableName(stat), DataType.DOUBLE, dims);
        }

        // allocate the memory and set the matrix values
        for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++){
            valuesForStatistics[stat] = new ArrayDouble.D2(windowIndex.getLength(), timeLagIndex.getLength());
            for (int windowIdx = 0; windowIdx < windowIndex.getLength(); windowIdx++) {
                CorrelationMatrix.CorrelationColumn column = matrix.getColumn(windowIdx);
                if(column.data[stat] == null) continue;
                for (int timeLagIdx = 0; timeLagIdx < timeLagIndex.getLength(); timeLagIdx++){
                    valuesForStatistics[stat].set(windowIdx, timeLagIdx, column.data[stat][timeLagIdx]);
                }
            }
        }

    }

    /**
     * @param dataFile input file
     * @param dataModel the data model containing the source time series
     * @return an iterator over the deserialized correlation matrices stored in the NetCDF file.
     * @throws IOException If something goes wrong reading the input file
     */
    public static Iterator<CorrelationMatrix> getResultsIterator(NetcdfFile dataFile, DataModel dataModel) throws IOException, IllegalArgumentException {
        return new Iterator<CorrelationMatrix>() {
            int correlationMatrixIdx = 0;
            NetCDFComputationResult nextResult = new NetCDFComputationResult(dataFile, correlationMatrixIdx, dataModel);
            @Override public boolean hasNext() { return nextResult.matrix != null; }
            @Override public CorrelationMatrix next() {
                CorrelationMatrix result = nextResult.matrix;

                correlationMatrixIdx++;
                try { nextResult = new NetCDFComputationResult(dataFile, correlationMatrixIdx, dataModel); }
                catch (IOException e) { e.printStackTrace(); }

                return result;
            }
        };
    }
    /**
     * Reads a computation result from the given file
     * @param dataFile input file
     * @param id the result index (zero based)
     * @throws java.lang.IllegalArgumentException if the file format version of the given data file is not compatible.
     */
    private NetCDFComputationResult(NetcdfFile dataFile, int id, DataModel dataModel) throws IOException, IllegalArgumentException {
        computationResultName = "CorrelationMatrix"+id;

        metadataAttributes = new Attribute[NUM_ATTRIBUTES];

        metadataAttributes[TAU_MIN] = dataFile.findGlobalAttribute(attributeName(TAU_MIN));

        // if the attribute is not found, the computation result with the given ID is expected not to exist.
        if(metadataAttributes[TAU_MIN] == null) return;

        metadataAttributes[TAU_MAX] = dataFile.findGlobalAttribute(attributeName(TAU_MAX));
        metadataAttributes[TAU_STEP] = dataFile.findGlobalAttribute(attributeName(TAU_STEP));
        metadataAttributes[WINDOW_SIZE] = dataFile.findGlobalAttribute(attributeName(WINDOW_SIZE));
        metadataAttributes[BASEWINDOW_OFFSET] = dataFile.findGlobalAttribute(attributeName(BASEWINDOW_OFFSET));
        metadataAttributes[SET_A_IDS] = dataFile.findGlobalAttribute(attributeName(SET_A_IDS));
        metadataAttributes[SET_B_IDS] = dataFile.findGlobalAttribute(attributeName(SET_B_IDS));
        metadataAttributes[SIGNIFICANCE_LEVEL] = dataFile.findGlobalAttribute(attributeName(SIGNIFICANCE_LEVEL));

        int setASize = metadataAttributes[SET_A_IDS].getLength(),
            setBSize = metadataAttributes[SET_B_IDS].getLength();
        ArrayList<TimeSeries> setASeries  = new ArrayList<>(setASize),
                              setBSeries = new ArrayList<>(setBSize);
        for (int i = 0; i < setASize; i++) setASeries.add(dataModel.get(0, (int) metadataAttributes[SET_A_IDS].getValue(i)));
        for (int i = 0; i < setBSize; i++) setBSeries.add(dataModel.get(1, (int) metadataAttributes[SET_B_IDS].getValue(i)));

        // re-create metadata object
        WindowMetadata metadata = new WindowMetadata.Builder(
                (int) metadataAttributes[TAU_MIN].getNumericValue(),
                (int) metadataAttributes[TAU_MAX].getNumericValue(),
                (int) metadataAttributes[WINDOW_SIZE].getNumericValue(),
                (int) metadataAttributes[TAU_STEP].getNumericValue(),
                (int) metadataAttributes[BASEWINDOW_OFFSET].getNumericValue())
                .tsA(setASeries)
                .tsB(setBSeries)
                .build();
        CorrelationMatrix.setSignificanceLevel(metadata, (double) metadataAttributes[SIGNIFICANCE_LEVEL].getNumericValue());

        // re-create matrix and its column structure
        matrix = new CorrelationMatrix(metadata);
        int columnLength = metadata.getNumberOfDifferentTimeLags();
        for (int i = 0; i < metadata.numBaseWindows; i++){
            CorrelationMatrix.CorrelationColumn column = matrix.new CorrelationColumnBuilder(i * metadata.baseWindowOffset, metadata.tauMin).build();
            for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++) column.data[stat] = new double[columnLength];
            matrix.append(column);
        }

        // fill columns with values
        statistics = new Variable[CorrelationMatrix.NUM_STATS];
        valuesForStatistics = new ArrayDouble.D2[statistics.length];
        for (int stat = 0; stat < statistics.length; stat++) {
            statistics[stat] = dataFile.findVariable(variableName(stat));
            if(statistics[stat] != null) {
                valuesForStatistics[stat] = (ArrayDouble.D2) statistics[stat].read();
                for (int windowIdx = 0; windowIdx < metadata.numBaseWindows; windowIdx++) {
                    CorrelationMatrix.CorrelationColumn column = matrix.getColumn(windowIdx);
                    assert Arrays.equals(valuesForStatistics[stat].getShape(), new int[]{metadata.numBaseWindows, columnLength}) : String.format("Malformed double array to read from: %s, expected: %s",Arrays.toString(valuesForStatistics[stat].getShape()),Arrays.toString(new int[]{metadata.numBaseWindows, columnLength})) ;
                    for (int lagIdx = 0; lagIdx < columnLength; lagIdx++)
                        column.data[stat][lagIdx] = valuesForStatistics[stat].get(windowIdx,lagIdx);
                }
            }
        }

    }

    /**
     * Writes the data to disk.
     */
    public void write() throws IOException, InvalidRangeException {
        dataFile.write(timeLags, dataTimeLags);
        for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++)
            dataFile.write(statistics[stat], valuesForStatistics[stat]);
    }

    // generators for dimension-, attribute- and variable names (need to be the same when writing and reading from a file).
    private String windowDimensionName() { return computationResultName + "_WindowIndex"; }
    private String timeLagDimensionName() { return computationResultName + "_TimeLag"; }
    String attributeName(int attribute){ return computationResultName + "_" + attributeNames[attribute]; }
    String variableName(int statistic){
        String[] statisticsLabels = new String[]{"mean", "standard_deviation", "median", "interquartile_range", "%_positive_significant", "%_negative_significant", "%_significant"};
        assert statisticsLabels.length == CorrelationMatrix.NUM_STATS : "Add variable names to the persist logic.";
        return computationResultName + "_" + statisticsLabels[statistic];
    }
}
