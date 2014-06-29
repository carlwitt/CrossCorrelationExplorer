package Data;

import Data.Correlation.CorrelationMatrix;
import Data.IO.FileModel;
import Data.Windowing.WindowMetadata;
import com.google.common.collect.Lists;
import ucar.ma2.ArrayDouble;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * This class represents a program document. It stores computed correlograms and their metadata.
 * It handles persisting data to disk in NetCDF format. The aim is mainly to persist correlograms which are expensive to compute.
 * Nevertheless, the file will contain everything to reproduce the correlograms. This is partially to avoid scientists making errors by
 * e.g. assigning a correlogram to a different time series data set than it was computed from. Another important reason is that cell distributions
 * can be recomputed on the fly only if the original time series data is available (otherwise, all intermediate correlograms for an
 * aggregated correlogram would have to be stored, which easily requires Gigabytes of disk space).
 *
 * The overall layout of a data file consists of the original time series (including their artificial IDs), grouped according to the two input files they came from
 * and a number of correlograms and their associated metadata (the parameters that were used used to compute them).
 *
 * The structure looks approximately like this (group structures are flattened because they are not yet supported in the Java api):
 * <pre>
 *  netcdf exampleExperimentFile {
 *  Group TimeSeriesSetA:
 *      TimeSeriesSetA:originalFilePath = "/path/to/file.txt";
 *      dimensions:
 *  	    TimeSeriesSetA_IDs = 1000;      // number of timeseries
 *  	    TimeSeriesSetA_TimeSteps = 10000;   // number of time steps
 *  	variables:
 *          float TimeSeriesSetA_temperature(id, time);
 *                  temperature:units = "degrees celsius";
 *  	data:
 *          time = 1950, 1951, 1952, ...
 *          timeSeriesID   = 100, 101, 102, ...
 *          temperature = -6.4, -7.1, ...
 *
 *  Group TimeSeriesSetB:
 *      // like TimeSeriesSetA.
 *
 *  Group Correlograms:
 *      Group Correlogram1:
 *          // computation parameters are stored as attributes of the correlogram group
 *          Correlogram1:windowSize = "200";
 *          Correlogram1:overlap = "130";
 *          Correlogram1:minTau = "100";
 *          Correlogram1:maxTau = "100";
 *          Correlogram1:correlationSignificance = "0.05";
 *          Correlogram1:neededTime = "00:37:12";
 *
 *          dimensions:
 *              window = 157;       // the column index of the correlation matrix
 *              timeLag = 200;      // the row index of the correlation matrix
 *
 *          variables:
 *              float mean(window, timeLag);
 *              float standardDeviation(window, timeLag);
 *              float median(window, timeLag);
 *              float IQR(window, timeLag);
 *              float positiveSignificant(window, timeLag);
 *              // ...
 *
 *          data:
 *              window = 0, 1, 2, ...
 *              timeLag = -100, -99, ..., 100
 *              mean = 0.5, 0.6, ...
 *              median = 0.45, 0.59, ...
 *
 *      Group Correlogram2:
 *          // etc.
 * </pre>
 *
 *
 *
 * Created by Carl Witt on 27.06.14.
 */
public class Experiment {

    /** the time series. */
    DataModel dataModel;

    /** the correlograms which the user selected to save. */
    public final HashMap<WindowMetadata, CorrelationMatrix> correlograms = new HashMap<>();

    /** Where the input files of the time series were originally located. */
    private String tsAPath, tsBPath;

    /**
     * Creates a new experiment by filling the data model with time series from the input files.
     * @param dataModel The data model to fill with the given time series.
     * @param models The file models containing the time series.
     */
    public Experiment(DataModel dataModel, FileModel... models){

        assert models.length > 0 : "Pass at least one file model to the Experiment constructor.";

        this.dataModel = dataModel;

        tsAPath = models[0].getFilename();
        if(models.length > 1) tsBPath = models[1].getFilename();

        // read time series data
        for (int i = 0; i < models.length; i++) {
            models[i].execute();
            for (int j = 1; j < models[i].getNumberOfTimeSeries(); j++) {
                TimeSeries newTs = new TimeSeries(models[i].getXValues(j),models[i].getYValues(j));
                this.dataModel.put(i, newTs.getId(), newTs);
            }
        }

    }

    /**
     * Loads an existing experiment from a NetCDF file.
     * @param dataModel The data model to fill with the given time series.
     * @param netCDFPath The path to the stored file.
     */
    public Experiment(DataModel dataModel, String netCDFPath){

        this.dataModel = dataModel;
        NetcdfFile dataFile = null;
        try {
            dataFile = NetcdfFile.open(netCDFPath, null);

            // Get the latitude and longitude Variables.
            NetCDFTimeSeriesGroup[] tsGroups = new NetCDFTimeSeriesGroup[2];
            tsGroups[0] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetA", 0);
            tsGroups[1] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetB", 1);

            // The file is closed no matter what by putting inside a try/catch block.
        } catch (java.io.IOException /*| InvalidRangeException */e) {
            e.printStackTrace();
        } finally {
            if (dataFile != null)
                try { dataFile.close(); }
                catch (IOException ioe) { ioe.printStackTrace(); }
        }

    }

    /** for testing. */
    public Experiment(DataModel dataModel){
        this.dataModel = dataModel;
    }

    /**
     * Persists the experiment to a NetCDF file from which it can be read again using {@link #Experiment(Data.DataModel, String)}.
     * @param netCDFPath The path where to store the file.
     */
    public void save(String netCDFPath) {
        NetcdfFileWriter dataFile = null;
        try{
            dataFile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, netCDFPath);

            // declare time series sets
            NetCDFTimeSeriesGroup[] tsGroups = new NetCDFTimeSeriesGroup[2];

            tsGroups[0] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetA", tsAPath, Lists.newArrayList(dataModel.getFileSeries(0).values()));
            tsGroups[1] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetB", tsBPath, Lists.newArrayList(dataModel.getFileSeries(1).values()));

            // start writing data
            dataFile.create();
            tsGroups[0].write(dataFile);
            tsGroups[1].write(dataFile);

        }catch (IOException | InvalidRangeException e) {
            e.printStackTrace(System.err);
        }finally {
            if (dataFile != null)
                try {
                    dataFile.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
        }

    }

    /**
     * Computes and returns the correlation matrix for a given input parameter specification.
     * Stores the result in the {@link #correlograms} data structure to be able to access it later.
     * @param metadata
     * @return
     */
    public CorrelationMatrix getResult(WindowMetadata metadata){
        throw new UnsupportedOperationException("tbd.");
    }

    /**
     * Writes time series data to the output file.
     */
    class NetCDFTimeSeriesGroup {

        /** The name for the time series set (either TimeSeriesSetA or TimeSeriesSetB). This will not be mapped to an actual group, because
         * those are not yet supported. Instead, the name is used to prefix all the names of the variables and dimensions that would belong to the group. */
        private final String timeSeriesGroupName;

        /** The labels for the rows and columns of the {@link #temperatures} matrix (think: axis labels).
         * If for instance the 10th row in the matrix corresponds to the time series with ID 900, this information is stored in ids.
         * Just as the first column might be labelled as corresponding to the year 1950 using the times array.
         * This implies that all time series share the same x-Values. */
        Variable idVar, timeVar;
        /** The data stored in the {@link #idVar} and {@link #timeVar}. */
        ArrayInt.D1 ids;
        ArrayDouble.D1 times;

        /** The actual time series data, indexed by time series ID and time step in that order. */
        Variable temperature;
        /** The data stored in the {@link #temperature} variable. */
        ArrayDouble.D2 temperatures;

        /** Whether there's any data at all to read or write. (Time series sets can be empty.) */
        boolean isEmpty = false;

        /**
         * Constructor for reading.
         * @param dataFile The NetCDF file to read from.
         * @param timeSeriesGroupName See {@link #timeSeriesGroupName}.
         * @throws IOException
         */
        public NetCDFTimeSeriesGroup(NetcdfFile dataFile, String timeSeriesGroupName, int fileID) throws IOException {

            this.timeSeriesGroupName = timeSeriesGroupName;

            idVar = dataFile.findVariable(idDimensionName());
            timeVar = dataFile.findVariable(timeDimensionName());
            temperature = dataFile.findVariable(tempVarName());

            assert temperature != null : "Can't find temperature data in input file "+dataFile.getDetailInfo();

            ids = (ArrayInt.D1) idVar.read();
            times = (ArrayDouble.D1) timeVar.read();
            temperatures = (ArrayDouble.D2) temperature.read();

            double[][] t = (double[][]) temperatures.copyToNDJavaArray();
            int[] idValues = (int[]) ids.copyTo1DJavaArray();
            double[] timeValues = (double[]) times.copyTo1DJavaArray();

            for (int idIdx = 0; idIdx < t.length; idIdx++) {
                TimeSeries newTS = new TimeSeries(idValues[idIdx], timeValues, t[idIdx]);
                dataModel.put(fileID, newTS.getId(), newTS);
            }
        }

        /**
         * Constructor for writing to a file. Does all the declarations that are necessary to later write the data.
         * @param dataFile The NetCDF file to manipulate.
         * @param timeSeriesGroupName See {@link #timeSeriesGroupName}.
         * @param tsFilePath Optional. Corresponds to the file from which the time series were originally loaded.
         * @param timeSeries The actual time series data.
         */
        public NetCDFTimeSeriesGroup(NetcdfFileWriter dataFile, String timeSeriesGroupName, String tsFilePath, List<TimeSeries> timeSeries) {

            this.timeSeriesGroupName = timeSeriesGroupName;

            // empty group: don't initialize data.
            if(timeSeries.size() == 0){ isEmpty = true; return;}

//            System.out.println(String.format("timeSeries.get(0).getDataItems().im: %s", Arrays.toString(timeSeries.get(0).getDataItems().im)));

            // create group with original file path attribute
            if(tsFilePath != null) dataFile.addGroupAttribute(null, filePathAttribute(tsFilePath));   // original time series input file paths

            // add time series ID and time as dimensions for the temperature variable
            Dimension id   = dataFile.addDimension(null, idDimensionName(), timeSeries.size());
            Dimension time = dataFile.addDimension(null, timeDimensionName(), timeSeries.get(0).getSize());

//            System.out.println(String.format("timeSeries.size: %s", timeSeries.size()));
//            System.out.println(String.format("timeSeries.get(0).getSize(): %s", timeSeries.get(0).getSize()));

            idVar   = dataFile.addVariable(null, id.getFullName(), DataType.INT, id.getFullName());
            timeVar = dataFile.addVariable(null, time.getFullName(), DataType.DOUBLE, time.getFullName());
            temperature = dataFile.addVariable(null, tempVarName(), DataType.DOUBLE, Arrays.asList(id, time));

            // write time series IDs and common x axis keyframes
            ids = new ArrayInt.D1(id.getLength());
            times = new ArrayDouble.D1(time.getLength());
            for (int i=0; i<id.getLength(); i++) ids.set(i, timeSeries.get(i).getId());
            for (int i=0; i<time.getLength(); i++) times.set(i, (float) timeSeries.get(0).getDataItems().re[i]);

            // write time series values
            temperatures = new ArrayDouble.D2(id.getLength(), time.getLength());
            for (int i = 0; i < id.getLength(); i++) {
                double[] tsTemperatures = timeSeries.get(i).getDataItems().im;
                for (int j = 0; j < time.getLength(); j++) {
                    temperatures.set(i, j, (float) tsTemperatures[j]);
                }
//                System.out.println(String.format("setting time series values: %s", i));
            }
        }

        /**
         * Writes the time series data to a file.
         * @param dataFile The NetCDF file to write to.
         * @throws IOException
         * @throws InvalidRangeException
         */
        public void write(NetcdfFileWriter dataFile) throws IOException, InvalidRangeException {
            // empty group: don't write anything
            if(isEmpty) return;

            dataFile.write(idVar, ids);
            dataFile.write(timeVar, times);
            dataFile.write(temperature, new int[2], temperatures);
        }

        public Attribute filePathAttribute(String filePath){ return new Attribute(timeSeriesGroupName+"_originalFilePath",filePath); }
        public String idDimensionName(){ return timeSeriesGroupName+"_IDs"; }
        public String timeDimensionName(){ return timeSeriesGroupName+"_TimeSteps"; }
        public String tempVarName(){ return timeSeriesGroupName+"_Temperature"; }

    }

}
