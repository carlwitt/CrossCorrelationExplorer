package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.RangeSlider;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controls the filter ranges for the correlogram.
 * Because of a bug in the range slider control, sliders are hidden when the admissible range is too small (e.g. no uncertainty in a 1x1 time series correlation matrix).
 * The fact is reflected
 *
 * Created by Carl Witt on 19.08.14.
 */
public class MatrixFilterController implements Initializable{

    private SharedData sharedData;

    @FXML private Button resetButton;
    @FXML private Button updateButton;
    @FXML private CheckBox autoUpdateCheckBox;
    @FXML private GridPane sliderGrid;

    RangeSlider[] sliders = new RangeSlider[CorrelationMatrix.NUM_STATS];
    Label[] lowValueLabels = new Label[CorrelationMatrix.NUM_STATS];    // the labels reflect the current slider positions.
    Label[] highValueLabels = new Label[CorrelationMatrix.NUM_STATS];

    @Override public void initialize(URL location, ResourceBundle resources) {

        // create sliders and labels
        for (int i = 0; i < sliders.length; i++) {

            final RangeSlider slider = new RangeSlider(0, 1, 0, 1);
            Label low = new Label(),
                  high = new Label();
            String imgURL = MatrixFilterController.class.getResource("img/glyphicons_207_remove_2.png").toExternalForm();
            ImageView graphic = new ImageView(imgURL);
            graphic.setFitHeight(10); graphic.setFitWidth(10);
            Button resetButton = new Button("", graphic);
            resetButton.setPrefWidth(10); resetButton.setPrefHeight(10);

            // add listeners to slider changes
            slider.highValueProperty().addListener(this::sliderChanged);
            slider.lowValueProperty().addListener(this::sliderChanged);

            // bind label texts to current slider range positions
            low.textProperty().bind(slider.lowValueProperty().asString("%.3f"));
            high.textProperty().bind(slider.highValueProperty().asString("%.3f"));

            resetButton.setOnAction(e -> {slider.setLowValue(slider.getMin()); slider.setHighValue(slider.getMax());});

            // add to grid pane
            int rowIndex = i;
            sliderGrid.add(low,    1, rowIndex);
            sliderGrid.add(slider, 2, rowIndex);
            sliderGrid.add(high,   3, rowIndex);
            sliderGrid.add(resetButton, 4, rowIndex);
            GridPane.setHalignment(low, HPos.RIGHT);

            // keep in memory
            sliders[i] = slider;
            lowValueLabels[i] = low;
            highValueLabels[i] = high;
        }

        // when enabling auto update, trigger one update immediately
        autoUpdateCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue) publishChanges(null);
        });

    }

    public void setSharedData(SharedData sharedData){
        this.sharedData = sharedData;

        // when the correlation matrix changes, update the slider bounds
        sharedData.correlationMatrixProperty().addListener((observable, oldValue, newMatrix) -> {

            for (int STAT = 0; STAT < sliders.length; STAT++) {

                double newMin = newMatrix.getMin(STAT);
                double newMax = newMatrix.getMax(STAT);

                // TODO update ControlsFX and simplify code
                // if the range is too small, the range slider control causes a stack overflow! (was reported and is fixed in the next version)
                if(Math.abs(newMax - newMin) > 1e-10){

                    sliders[STAT].setMin(newMin);
                    sliders[STAT].setMax(newMax);

                    sliders[STAT].setVisible(true);
                    lowValueLabels[STAT].setVisible(true);
                    highValueLabels[STAT].setVisible(true);

                } else {
                    sliders[STAT].setVisible(false);
                    lowValueLabels[STAT].setVisible(false);
                    highValueLabels[STAT].setVisible(false);
                }

            }

            // when selecting a new correlogram, reset sliders to make sure that the entire correlogram is visible.
            resetSliders(null);

        });

    }

    /**
     * Handles manipulations of the slider ranges. If the auto update check box is set, publishes the new filter ranges.
     */
    private void sliderChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if(autoUpdateCheckBox.isSelected()) publishChanges(null);
    }

    /**
     * Gathers the current slider ranges and publishes them on the shared data object.
     */
    public void publishChanges(ActionEvent e) {

        double[][] newFilterRanges = new double[sliders.length][2];

        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(matrix == null) return;

        for (int i = 0; i < sliders.length; i++) {
            if(sliders[i].isVisible()){
                newFilterRanges[i][0] = sliders[i].getLowValue();
                newFilterRanges[i][1] = sliders[i].getHighValue();
//                System.out.println(String.format("%s: [%s, %s]", CorrelationMatrix.statisticsLabels[i], newFilterRanges[i][0], newFilterRanges[i][1]));
            } else {
                newFilterRanges[i] = null;///[0] = matrix.getMin(i);
//                newFilterRanges[i][1] = matrix.getMax(i);
            }

        }
        sharedData.setMatrixFilterRanges(newFilterRanges);

    }

    public void resetSliders(ActionEvent e){

//        // temporarily disable the auto update to avoid a chain of update requests
//        boolean previousState = autoUpdateCheckBox.isSelected();
//        autoUpdateCheckBox.setSelected(false);

        // reset slider values
        for(RangeSlider slider : sliders){
            slider.setLowValue(slider.getMin());
            slider.setHighValue(slider.getMax());
        }

        publishChanges(null);

//        // restore previous auto update state
//        autoUpdateCheckBox.setSelected(previousState);
//        // trigger the pending update if auto update was enabled previously
//        sliderChanged(null, null, null);

    }

}
