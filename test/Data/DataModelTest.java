/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Data;

import Data.TimeSeries;
import Data.DataModel;
import java.util.Arrays;
import java.util.Collection;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.embed.swing.JFXPanel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

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

    @Test
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
        
        index.put(1, new TimeSeries(new double[]{1,2,3}));
        index.put(1000, new TimeSeries(new double[]{19,20,123}));
        index.put(1, new TimeSeries(new double[]{1119,112320,12312123}));
        
        System.out.println(String.format("Final keyset: %s", Arrays.toString(keys.toArray())));
        
    }
    
    /**
     * Test of getSize method, of class DataModel.
     */
    @Test @Ignore
    public void testGetSize() {
        System.out.println("getSize");
        DataModel instance = new DataModel();
        int expResult = 0;
        int result = instance.getSize();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of contains method, of class DataModel.
     */
    @Test @Ignore
    public void testContains() {
        System.out.println("contains");
        int id = 0;
        DataModel instance = new DataModel();
        boolean expResult = false;
        boolean result = instance.contains(id);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getAll method, of class DataModel.
     */
    @Test @Ignore
    public void testGetAllContainers() {
        System.out.println("getAllContainers");
        DataModel instance = new DataModel();
        Collection<TimeSeries> expResult = null;
        Collection<TimeSeries> result = instance.getAll();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of get method, of class DataModel.
     */
    @Test @Ignore
    public void testGetContainerById() {
        System.out.println("getContainerById");
        int id = 0;
        DataModel instance = new DataModel();
        TimeSeries expResult = null;
        TimeSeries result = instance.get(id);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of add method, of class DataModel.
     */
    @Test @Ignore
    public void testAppend() {
        System.out.println("append");
        TimeSeries ts = null;
        DataModel instance = new DataModel();
        DataModel expResult = null;
        DataModel result = instance.add(ts);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
}
