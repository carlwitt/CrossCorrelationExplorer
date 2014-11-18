package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.DataModel;
import Data.SharedData;
import Data.TimeSeries;
import Visualization.HistogramTimeSeriesChart;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.util.converter.NumberStringConverter;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Carl Witt
 */
public class TimeSeriesViewController {
    
    private SharedData sharedData;          // data that is shared between the views
    
    /** maps a color to each time series ensemble (for instance the time series in correlation set A, in correlation set B and temporary time series for preview). */
    private final HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
    
    private final HistogramTimeSeriesChart timeSeriesChart = new HistogramTimeSeriesChart();

    @FXML protected Label groupSizeLabel;
    @FXML protected TextField binningYAxisResolutionTextField;
    @FXML protected Label binningYAxisResolutionLabel;
    @FXML protected Button increaseResolutionButton;
    @FXML protected Button decreaseResolutionButton;
    @FXML CheckBox ensemble1CheckBox;
    @FXML CheckBox ensemble2CheckBox;

    @FXML ToggleGroup transferFunction;
    @FXML Toggle transferLinearToggle;

    @FXML CheckBox polyCheckBox;
    @FXML CheckBox gridCheckBox;

    @FXML protected AnchorPane timeSeriesPane;

    /** The color for ensemble 1 (green) and ensemble 2 (blue). */
    public static final Color[] ensembleColors = new Color[]{Color.web("#00cc52"), Color.web("#4333ff")};

    public void initialize(){

        // add chart component to the scene graph
        timeSeriesPane.getChildren().add(0, timeSeriesChart);
        timeSeriesChart.toBack(); // the reset button etc. are to be displayed on top of the chart
        AnchorPane.setTopAnchor(timeSeriesChart, 0.);
        AnchorPane.setRightAnchor(timeSeriesChart, 0.);
        AnchorPane.setBottomAnchor(timeSeriesChart, 25.);
        AnchorPane.setLeftAnchor(timeSeriesChart, 20.);

        // auto adjust tick labels and detail slider
        timeSeriesChart.clipRegionDCProperty().addListener(this::updateTickUnits);

        // axes configuration
        timeSeriesChart.xAxis.setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("####")));
        timeSeriesChart.xAxis.setLabel("Year");
        timeSeriesChart.yAxis.setLabel("Temperature ˚C");

        // ensemble check boxes
        timeSeriesChart.drawEnsemble1Property().bind(ensemble1CheckBox.selectedProperty());
        timeSeriesChart.drawEnsemble2Property().bind(ensemble2CheckBox.selectedProperty());

        timeSeriesChart.binSizeProperty().addListener((observable, oldValue, newValue) -> {
            binningYAxisResolutionTextField.setText(String.format("%.7f", newValue.doubleValue()));
            drawChart();
        });

        // parse and report user changes to the time series binning (on the y axis)
        binningYAxisResolutionTextField.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                double newResolution;
                try {
                    newResolution = Double.parseDouble(binningYAxisResolutionTextField.getText());
                    if (Double.isNaN(newResolution) || newResolution <= 0) throw new NumberFormatException("The parsed value is not sensible.");
                    timeSeriesChart.setBinSize(newResolution);
                    drawChart();
                } catch (NumberFormatException e) {
                    Dialog<Void> d = new Dialog<>();
                    d.setContentText("Could not parse value.");
                    System.err.println("Couldn't parse value.");
                }
            }
        });

        // double/half bin size buttons
        increaseResolutionButton.setOnAction(event -> {
            timeSeriesChart.setBinSize(timeSeriesChart.getBinSize()/2);
        });
        decreaseResolutionButton.setOnAction(event -> {
            timeSeriesChart.setBinSize(timeSeriesChart.getBinSize()*2);
        });

        // line and polygon draw options
        polyCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            timeSeriesChart.drawPoly = newValue;
            timeSeriesChart.drawContents();
        });
        gridCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            (timeSeriesChart).drawGrid = newValue;
            timeSeriesChart.drawContents();
        });

        // transfer function toggle
        transferFunction.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            (timeSeriesChart).useLinearTransfer = newValue == transferLinearToggle;
            timeSeriesChart.drawContents();
        });

    }

    public void setSharedData(final SharedData sharedData){
        this.sharedData = sharedData;

        timeSeriesChart.setSharedData(sharedData);

        // define the two time series sets with their respective colors
        seriesSets.put(new Color(0, 0, 0, 0.5), sharedData.previewTimeSeries);
        seriesSets.put(ensembleColors[0], sharedData.experiment.dataModel.correlationSetA);
        seriesSets.put(ensembleColors[1], sharedData.experiment.dataModel.correlationSetB);
        timeSeriesChart.seriesSets = seriesSets;

        // when a new correlation matrix has been computed, reset the view
        sharedData.correlationMatrixProperty().addListener((ov, t, newMatrix) -> {
            setScrollBarRangesToDataBounds(newMatrix);
            resetView();
        });

        // when the ensemble selection changes, update the data bounds
        ensemble1CheckBox.selectedProperty().addListener((ov, old, newState) -> setScrollBarRangesToDataBounds(sharedData.getCorrelationMatrix()));
        ensemble2CheckBox.selectedProperty().addListener((ov, old, newState) -> setScrollBarRangesToDataBounds(sharedData.getCorrelationMatrix()));


        // TODO this should be updating some overlay that indicates the input time windows.
//        sharedData.activeCorrelationMatrixRegionProperty().addListener((ov, t, t1) -> timeSeriesChart.drawChart());

        // listen to and report changes in zoom and pan
        sharedData.visibleTimeRangeProperty().bindBidirectional(timeSeriesChart.clipRegionDCProperty());

        // adapt text of the group size label to the currently used group size
        sharedData.experiment.dataModel.correlationSetAAggregator.groupSizeProperty().addListener((observable, oldValue, newValue) -> {
            groupSizeLabel.setText(
                    newValue.intValue() == 1 ? "Showing full resolution." : "group size: " + newValue.intValue()
            );
        });

    }

    public void setScrollBarRangesToDataBounds(DataModel dataModel) {
        Bounds dataBounds = DataModel.getDataBounds(dataModel.ensemble1TimeSeries, dataModel.ensemble2TimeSeries);
        assert dataBounds != null;
        timeSeriesChart.xAxis.setScrollBarBoundsDC(dataBounds);
        timeSeriesChart.yAxis.setScrollBarBoundsDC(dataBounds);
    }

    private void setScrollBarRangesToDataBounds(CorrelationMatrix matrix) {

        if(matrix == null){
            System.out.println(String.format("return"));
            return;
        }
        assert matrix.metadata != null;

        Bounds dataBounds;
        // if both or none is selected, use the bounds for both ensembles
        if (ensemble1CheckBox.isSelected() == ensemble2CheckBox.isSelected()) {
            dataBounds = DataModel.getDataBounds(matrix.metadata.setA, matrix.metadata.setB);
            // otherwise use the data bounds of the selected ensemble
        } else {
            List<TimeSeries> activeEnsemble = ensemble1CheckBox.isSelected() ? matrix.metadata.setA : matrix.metadata.setB;
            dataBounds = DataModel.getDataBounds(activeEnsemble);
        }
        if(dataBounds != null){
            timeSeriesChart.xAxis.setScrollBarBoundsDC(dataBounds);
            timeSeriesChart.yAxis.setScrollBarBoundsDC(dataBounds);
        }
    }

    void updateTickUnits(ObservableValue<? extends Bounds> observable, Bounds oldValue, Bounds newValue){
        double xRange = timeSeriesChart.xAxis.getRange(),
               yRange = timeSeriesChart.yAxis.getRange();
        
        // always display approximately the same number of ticks
        double xTickUnit = Math.max(1, xRange / 20),
               yTickUnit = Math.max(1, yRange / 5);
        timeSeriesChart.xAxis.setTickUnit(xTickUnit);
        timeSeriesChart.yAxis.setTickUnit(yTickUnit);
    }

    // Is also wired to the reset button
    public void resetView() {
        timeSeriesChart.resetView();
    }


    public void drawChart() {
        if(timeSeriesChart.getClipRegionDC() == null) timeSeriesChart.resetView();
        timeSeriesChart.drawContents();
    }

    public void setDeferringDrawRequests(boolean deferringDrawRequests) {
        timeSeriesChart.setDeferringDrawRequests(deferringDrawRequests);
    }
}
