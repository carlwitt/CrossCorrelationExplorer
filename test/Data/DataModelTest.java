/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Data;

import javafx.embed.swing.JFXPanel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 *
 * @author Carl Witt
 */
public class DataModelTest {
    
    public DataModelTest() {
    
        // load JavaFX toolkit
        new JFXPanel();
        
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

 /*   @Test @Ignore
    // can one retrieve the key set once and observe changes via changes to the reference? No.
    public void testObserveMapViaKeySet(){
        
        final ObservableMap<Integer, TimeSeries> index = FXCollections.observableHashMap();
        
        final ObservableList<Integer> keys = FXCollections.observableArrayList(index.keySet());
        
        System.out.println(String.format("Initial keyset: %s", Arrays.toString(keys.toArray())));
        
        keys.addListener(new ListChangeListener<Integer>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends Integer> change) {
                System.out.println(String.format("The list has changed. New size %s\nContent: %s", keys.size(), Arrays.toString(keys.toArray()) ));
            }
        });
        
        index.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
                System.out.println(String.format("Map change: key %s value added %s value removed %s", change.getKey(), change.getValueAdded(),change.getValueRemoved()));
                if(change.wasRemoved() && ! change.wasAdded()){
                    keys.remove(change.getKey());
                } else if( ! change.wasRemoved() && change.wasAdded()){
                    keys.add(change.getKey());
                }
            }
        });
        
        index.put(1, new TimeSeries(1, new double[]{1,2,3}));
        index.put(1000, new TimeSeries(1, new double[]{19,20,123}));
        index.put(1, new TimeSeries(1, new double[]{1119,112320,12312123}));
        
        System.out.println(String.format("Final keyset: %s", Arrays.toString(keys.toArray())));
        
    }*/
    
}
