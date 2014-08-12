package Data.IO;

import Data.DataModel;
import Data.TimeSeries;
import ucar.ma2.ArrayDouble;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Writes time series data to a NetCDF output file.
 */
public class NetCDFTimeSeriesGroup extends NetCDFWriter{

    /** The name for the time series set (either TimeSeriesSetA or TimeSeriesSetB). This will not be mapped to an actual group, because
     * those are not yet supported. Instead, the name is used to prefix all the names of the variables and dimensions that would belong to the group. */
    private final String timeSeriesGroupName;

    /** The labels for the rows and columns of the {@link #temperatures} matrix (think: axis labels).
     * If for instance the 10th row in the matrix corresponds to the time series with ID 900, this information is stored in ids.
     * Just as the first column might be labelled as corresponding to the year 1950 using the times array.
     * This implies that all time series share the same x-Values. */
    private Variable idVar;
    private Variable timeVar;
    /** The data stored in the {@link #idVar} and {@link #timeVar}. */
    private ArrayInt.D1 ids;
    private ArrayDouble.D1 times;

    /** The actual time series data, indexed by time series ID and time step in that order. */
    private Variable temperature;
    /** The data stored in the {@link #temperature} variable. */
    private ArrayDouble.D2 temperatures;

    /** Whether there's any data at all to read or write. (Time series sets can be empty.) */
    private boolean isEmpty = false;

    /** The file to write to, if constructor for writing was called. Ensures that the declarations happen on the same file to which data is written. */
    private NetcdfFileWriter dataFile = null;

    /**
     * Prepares writing to a file. Does all the declarations and variable creations.
     * @param dataFile The NetCDF file to manipulate.
     * @param timeSeriesGroupName See {@link #timeSeriesGroupName}.
     * @param tsFilePath Optional. Corresponds to the file from which the time series were originally loaded.
     * @param timeSeries The actual time series data.
     */
    public NetCDFTimeSeriesGroup(NetcdfFileWriter dataFile, String timeSeriesGroupName, String tsFilePath, List<TimeSeries> timeSeries) {

        this.timeSeriesGroupName = timeSeriesGroupName;
        this.dataFile = dataFile;

        // empty group: don't initialize data.
        if(timeSeries.size() == 0){ isEmpty = true; return;}

        // create group with original file path attribute
        if(tsFilePath != null) dataFile.addGroupAttribute(null, filePathAttribute(tsFilePath));   // original time series input file paths

        // add time series ID and time as dimensions for the temperature variable
        Dimension id   = dataFile.addDimension(null, idDimensionName(), timeSeries.size());
        Dimension time = dataFile.addDimension(null, timeDimensionName(), timeSeries.get(0).getSize());

        // declare variables in the file
        idVar   = dataFile.addVariable(null, id.getFullName(), DataType.INT, id.getFullName());
        timeVar = dataFile.addVariable(null, time.getFullName(), DataType.DOUBLE, time.getFullName());
        temperature = dataFile.addVariable(null, tempVarName(), DataType.DOUBLE, Arrays.asList(id, time));

        // set time series IDs and common x axis values
        ids = new ArrayInt.D1(id.getLength());
        times = new ArrayDouble.D1(time.getLength());
        for (int i=0; i<  id.getLength(); i++)  ids.set(i, timeSeries.get(i).getId());
        for (int i=0; i<time.getLength(); i++)  times.set(i, (float) timeSeries.get(0).getDataItems().re[i]);

        // set time series values
        temperatures = new ArrayDouble.D2(id.getLength(), time.getLength());
        for (int i = 0; i < id.getLength(); i++) {
            double[] tsTemperatures = timeSeries.get(i).getDataItems().im;
            for (int j = 0; j < time.getLength(); j++) {
                temperatures.set(i, j, (float) tsTemperatures[j]);
            }
        }
    }

    /**
     * Reads time series from the given file and populates the given data model with them.
     * @param dataFile The NetCDF file to read from.
     * @param timeSeriesGroupName See {@link #timeSeriesGroupName}.
     * @param dataModel The datamodel to add the time series to.
     * @throws java.io.IOException
     */
    public NetCDFTimeSeriesGroup(NetcdfFile dataFile, String timeSeriesGroupName, int fileID, DataModel dataModel) throws IOException {

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
     * Writes the time series data to a file.
     * @throws java.io.IOException
     * @throws ucar.ma2.InvalidRangeException
     */
    public void write() throws IOException, InvalidRangeException {
        // empty group: don't write anything
        if(isEmpty) return;

        dataFile.write(idVar, ids);
        dataFile.write(timeVar, times);
        dataFile.write(temperature, new int[2], temperatures);
    }

    Attribute filePathAttribute(String filePath){ return new Attribute(timeSeriesGroupName+"_originalFilePath",filePath); }
    String idDimensionName(){ return timeSeriesGroupName+"_IDs"; }
    String timeDimensionName(){ return timeSeriesGroupName+"_TimeSteps"; }
    String tempVarName(){ return timeSeriesGroupName+"_Temperature"; }

}
