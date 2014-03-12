
import java.util.Set;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
 
 
public class LineChartSample extends Application {
 
    @Override public void start(Stage stage) {

        //defining the axes
        final NumberAxis xAxis = new NumberAxis(); final NumberAxis yAxis = new NumberAxis(); xAxis.setLabel("Number of Month");
        
        //creating the chart
        final LineChart<Number,Number> lineChart = new LineChart<>(xAxis,yAxis);
        
        lineChart.setCreateSymbols(false);        
        lineChart.setTitle("Stock Monitoring, 2010");
        
        for (int i = 0; i < 100; i++) {
            XYChart.Series series = new XYChart.Series();
            series.setName("Series "+i);
            for (int j = 0; j < 1500; j+=4) {
                series.getData().add(new XYChart.Data(j, Math.random()));
            }
            lineChart.getData().add(series);
        }
        
        Set<Node> nodes = lineChart.lookupAll("*");
        for (Node n: nodes) {
          n.setStyle("-fx-stroke-width:2; -fx-effect: null;");
        }
        
        Scene scene  = new Scene(lineChart,800,600);
       
        stage.setScene(scene);
        stage.show();
    }
 
    public static void main(String[] args) {
        launch(args);
    }
}