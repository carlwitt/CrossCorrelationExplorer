package Gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * A static helper method for bidirectinally binding properties of different types to each other.
 * The use is twofold: First, creating bidirectional bindings is possible only between properties of the same type.
 * Usually this is used to couple a controller property (e.g. a slider) to a model property (e.g. a level of detail).
 * When updating one property, the other should be updated as well and vice versa. In the properties are not of the same
 * type, an additional conversion logic is needed that can be provided here.
 * The other use is that, without further care, an infinite loop is caused by mutual updates. To avoid this,
 * a flag is set before updating the other property. Any update calls from the descendants of the update call in the call
 * tree are suppressed becaues the flag is unset not before returning to the level in the call tree from which the
 * update call was issued.
 * Created by Carl Witt on 01.11.14.
 */
public class BidirectionalBinding {

    /** Executes updateB when propertyA is changed. Executes updateA when propertyB is changed.
     * Makes sure that no update loops are caused by mutual updates.
     */
    public static <A,B> void bindBidirectional(ObservableValue<A> propertyA, ObservableValue<B> propertyB, ChangeListener<A> updateB, ChangeListener<B> updateA){

        addFlaggedChangeListener(propertyA, updateB);
        addFlaggedChangeListener(propertyB, updateA);

    }

    /**
     * Adds a change listener to a property that will not react to changes caused (transitively) by itself (i.e. from an update call in the call tree that is a descendant of itself.)
     * @param property the property to add a change listener to
     * @param updateProperty the logic to execute when the property changes
     * @param <T> the type of the observable value
     */
    private static <T> void addFlaggedChangeListener(ObservableValue<T> property, ChangeListener<T> updateProperty){
        property.addListener(new ChangeListener<T>() {

        private boolean alreadyCalled = false;

        @Override public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
            if(alreadyCalled) return;
            try {
                alreadyCalled = true;
                updateProperty.changed(observable,oldValue,newValue);
            }
            finally { alreadyCalled = false; }
        }
        });
    }

}
