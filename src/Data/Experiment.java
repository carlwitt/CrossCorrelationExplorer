package Data;

import Data.Correlation.CorrelationMatrix;
import Data.IO.FileModel;
import Data.IO.NetCDFCorrelationMatrix;
import Data.IO.NetCDFTimeSeriesGroup;
import Data.Windowing.WindowMetadata;
import com.google.common.collect.Lists;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriter;

import java.io.IOException;
import java.util.*;

/**
 * Represents a program document. It stores computed correlograms and their metadata.
 * It handles persisting data to disk in NetCDF format. The aim is mainly to persist correlograms which are expensive to compute.
 * Nevertheless, the file will contain everything to reproduce the correlograms. This is partially to avoid scientists making errors by
 * e.g. assigning a correlogram to a different time series data set than it was computed from. Another important reason is that cell distributions
 * can be recomputed on the fly only if the original time series data is available (otherwise, all intermediate correlograms for an
 * aggregated correlogram would have to be stored, which easily requires Gigabytes of disk space).
 *
 * NetCDF Java documentation
 *      http://www.unidata.ucar.edu/software/thredds/current/netcdf-java
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
    public DataModel dataModel;

    /** the correlograms/computation results in the document. */
    private final HashMap<WindowMetadata, CorrelationMatrix> correlograms = new HashMap<>();
    /** the keys of the correlograms data structure, for letting the GUI display all available computation results. */
    public final ObservableList<WindowMetadata> cacheKeySet = FXCollections.observableArrayList();

    /** Where the input files of the time series were originally located. */
    // TODO: would be nice if the file paths would be persisted and restored
    public String tsAPath, tsBPath;

    /** The file path where the data is to be serialized to. */
    public String filename;
    final String DEFAULT_FILENAME = "New Experiment File";

    /** Marks whether there have been changes to the experimental data that should be saved before leaving. */
    boolean uncommitedChanges = false;

    /** To detect conflicts in the file structure, arising from the evolution of the file format.
     * Version history
     *      Version 1
     *          norbert marwans symmetric cross correlation matrix algorithm
     *          introduced time lag step size
     *          switched to time lag indices in referencing matrix cells
     *          introduced version numbers
     */
    public static final int VERSION_NUMBER = 1;
    protected Attribute fileFormatVersion = new Attribute("File_Format_Version", VERSION_NUMBER);

    public Experiment(){dataModel = new DataModel(); }
    /**
     * Creates a new experiment by filling the data model with time series from the input files.
     * @param models The file models containing the time series.
     */
    public Experiment(FileModel... models) throws FileModel.UnevenSpacingException {

        this.filename = DEFAULT_FILENAME;
        assert models.length > 1 : "Pass at least two file models to the Experiment constructor.";

        Collection<Collection<TimeSeries>> tsByFile = new ArrayList<>();

        tsAPath = models[0].getFilename();
        tsBPath = models[1].getFilename();

        // read each file
        for (FileModel model : models) {

            model.execute();

            ArrayList<TimeSeries> coll = new ArrayList<>();
            tsByFile.add(coll);
            // and add the time series to the data model
            for (int j = 1; j <= model.getNumberOfTimeSeries(); j++) {
                TimeSeries newTs = new TimeSeries(j, model.getXValues(), model.getYValues(j));
                coll.add(newTs);
//                this.dataModel.put(i, newTs.getId(), newTs);
            }
        }

        dataModel = new DataModel(tsByFile);

    }

    /**
     * Loads an existing experiment from a NetCDF file.
     * @param netCDFPath The path to the stored file.
     */
    public Experiment(String netCDFPath) throws IOException {

        this.filename = netCDFPath;

        this.dataModel = new DataModel();


        NetcdfFile dataFile = null;
        try {
            dataFile = NetcdfFile.open(netCDFPath, null);

            // check the file format version
            Attribute formatVersion = dataFile.findGlobalAttribute(fileFormatVersion.getShortName());
            if(formatVersion == null || (int) formatVersion.getNumericValue() != VERSION_NUMBER)
                throw new IllegalArgumentException("Format is not supported.");

            // Get the latitude and longitude Variables.
            NetCDFTimeSeriesGroup[] tsGroups = new NetCDFTimeSeriesGroup[2];
            tsGroups[0] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetA", 0, dataModel);
            tsGroups[1] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetB", 1, dataModel);

            Iterator<CorrelationMatrix> resultsInFile = NetCDFCorrelationMatrix.getResultsIterator(dataFile, dataModel);
            while(resultsInFile.hasNext()){
                CorrelationMatrix nextResult = resultsInFile.next();
                addResult(nextResult);
            }
        } finally {
            if (dataFile != null)
                try { dataFile.close(); }
                catch (IOException ioe) { ioe.printStackTrace(); }
        }

    }

    // the file is considered to have an extension if it ends with a dot and one or two arbitrary characters
    protected static final String HAS_EXTENSION_REGEX = ".*\\....?$";
    protected static final String DEFAULT_FILE_EXTENSION = ".nc";

    /**
     * Persists the experiment to a NetCDF file from which it can be read again using {@link #Experiment(String)}.
     * @param netCDFPath The path where to store the file.
     */
    public void save(String netCDFPath) {
        NetcdfFileWriter dataFile = null;
        try{
            if(!netCDFPath.matches(HAS_EXTENSION_REGEX)) netCDFPath += DEFAULT_FILE_EXTENSION;

            dataFile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, netCDFPath);

            // declare time series sets
            NetCDFTimeSeriesGroup[] tsGroups = new NetCDFTimeSeriesGroup[2];

            tsGroups[0] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetA", tsAPath, Lists.newArrayList(dataModel.getEnsemble(0).values()));
            tsGroups[1] = new NetCDFTimeSeriesGroup(dataFile, "TimeSeriesSetB", tsBPath, Lists.newArrayList(dataModel.getEnsemble(1).values()));

            List<NetCDFCorrelationMatrix> netCDFCorrelationMatrixes = new ArrayList<>();
            int i = 0;
            for (CorrelationMatrix matrix: correlograms.values())
                netCDFCorrelationMatrixes.add(new NetCDFCorrelationMatrix(dataFile, i++, matrix));

            // add version information
            dataFile.addGroupAttribute(null, fileFormatVersion);

            // start writing data
            dataFile.create();
            tsGroups[0].write();
            tsGroups[1].write();
            for (NetCDFCorrelationMatrix computationResult : netCDFCorrelationMatrixes)
                computationResult.write();

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
        this.filename = netCDFPath;
    }

    /** for testing. */
    public Experiment(DataModel dataModel){
        this.dataModel = dataModel;
    }

    /** Persists changes to disk using the filename the experiment was deserialized from. */
    public void save(){ save(filename); }

    /**
     * Registers a new computation result that will be serialized when saving the file.
     * The computation results are also observable ({@link #cacheKeySet}), allowing GUI components to listen to changes.
     */
    public void addResult(CorrelationMatrix matrix){
        // in case of an update, the old matrix is replaced
        correlograms.put(matrix.metadata, matrix);
        // in case of an update, the metadata object shouldn't be duplicated
        cacheKeySet.remove(matrix.metadata);
        cacheKeySet.add(matrix.metadata);
    }

    /**
     * @return whether the experiment file already has a path to save it to (is not new) or not.
     */
    public boolean isNew(){
        return filename.equals(DEFAULT_FILENAME);
    }
    /** @return {@link #uncommitedChanges} */
    public boolean isChanged(){ return uncommitedChanges; }

    public Collection<CorrelationMatrix> getResults(){
        return correlograms.values();
    }
    public CorrelationMatrix getResult(WindowMetadata metadata){ return correlograms.get(metadata); }

    public boolean hasResult(WindowMetadata metadata) { return correlograms.containsKey(metadata); }
}
