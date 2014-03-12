/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Gui;

import Data.ComplexSequence;
import Data.SharedData;
import Data.TimeSeries;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

/**
 * FXML Controller class
 *
 * @author macbookdata
 */
public class TimeSeriesViewController implements Initializable {

    private SharedData sharedData;
    
    @FXML private LineChart lineChart; 
    @FXML private NumberAxis xAxis; // time axis
    @FXML private NumberAxis yAxis; // temperature axis
    
     public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;

        // the list of loaded time series listens to changes in the data model
        sharedData.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
                
                lineChart.getData().clear();
                for (TimeSeries ts : sharedData.timeSeries.values()) {
                    XYChart.Series series = new XYChart.Series();
                    series.setName("Series "+ts.id);
                    ComplexSequence data = ts.getDataItems();
                    for (int j = 0; j < data.re.length; j++) {
                        series.getData().add(new XYChart.Data(data.re[j], data.im[j]));
                    }
                    lineChart.getData().add(series);
                }
//                if(change.wasRemoved() && ! change.wasAdded()){
//                    loadedTimeSeries.remove(change.getKey());
//                } else if( ! change.wasRemoved() && change.wasAdded()){
//                    loadedTimeSeries.add(change.getKey());
//                }
            }
        });
    
    }
     
    @Override public void initialize(URL url, ResourceBundle rb) {
        // TODO
    }    
    
    
    
}
