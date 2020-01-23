package Gui.experimental.html;

import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import com.sun.prism.impl.Disposer.Record;
import org.controlsfx.control.action.Action;

import java.util.List;

/**
 * Author : Abhinay_Agarwal
 */
public class TableViewDeleteSample extends Application {

    private TableView<Person> tableView = new TableView<Person>();


    private final ObservableList<Person> data =
            FXCollections.observableArrayList(
                    new Person("Eliza", "Smith", "eliza.smith@javafxpro.com"),
                    new Person("Isabella", "Johnson", "isabella.johnson@javafxpro.com"),
                    new Person("Imran", "Williams", "imran.williams@javafxpro.com"),
                    new Person("Emma", "Jones", "emma.jones@javafxpro.com"),
                    new Person("Russel", "Peters", "russel.peters@javafxpro.com"));


    public static class Person {

        private final SimpleStringProperty firstName;
        private final SimpleStringProperty lastName;
        private final SimpleStringProperty email;

        private Person(String fName, String lName, String email) {
            this.firstName = new SimpleStringProperty(fName);
            this.lastName = new SimpleStringProperty(lName);
            this.email = new SimpleStringProperty(email);
        }

        public String getFirstName() {
            return firstName.get();
        }

        public void setFirstName(String fName) {
            firstName.set(fName);
        }

        public String getLastName() {
            return lastName.get();
        }

        public void setLastName(String fName) {
            lastName.set(fName);
        }

        public String getEmail() {
            return email.get();
        }

        public void setEmail(String fName) {
            email.set(fName);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Table With Delete Row");
        tableView.setEditable(false);

        TableColumn<Person, String> firstName = new TableColumn<>("First Name");
        TableColumn<Person, String> lastName = new TableColumn<>("Last Name");
        TableColumn<Person, String> email = new TableColumn<>("Email");

        firstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        lastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        email.setCellValueFactory(new PropertyValueFactory<>("email"));

        tableView.getColumns().add(firstName);
        tableView.getColumns().add(lastName);
        tableView.getColumns().add(email);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        //Insert Button
        TableColumn col_action = new TableColumn<>("Action");
        tableView.getColumns().add(col_action);

        col_action.setCellValueFactory(
                new Callback<TableColumn.CellDataFeatures<Record, Boolean>,
                        ObservableValue<Boolean>>() {

                    @Override
                    public ObservableValue<Boolean> call(TableColumn.CellDataFeatures<Record, Boolean> p) {
                        return new SimpleBooleanProperty(p.getValue() != null);
                    }
                });

        //Adding the Button to the cell
        col_action.setCellFactory(
                new Callback<TableColumn<Record, Boolean>, TableCell<Record, Boolean>>() {

                    @Override
                    public TableCell<Record, Boolean> call(TableColumn<Record, Boolean> p) {
                        return new ButtonCell();
                    }

                });


        tableView.setItems(data);
        MenuItem deleteItem = new MenuItem("Delete Selected Item");
        deleteItem.setOnAction(this::deleteSelectedTableRow);
        tableView.setContextMenu(new ContextMenu(deleteItem));
        VBox vBox = new VBox();
        vBox.getChildren().addAll(tableView);
        primaryStage.setScene(new Scene(vBox, 440, 400));
        primaryStage.show();
    }

    private void deleteSelectedTableRow(ActionEvent e){
        // get Selected Item
        List<Person> selectedPersons = tableView.getSelectionModel().getSelectedItems();
        //remove selected item from the table list
        data.removeAll(selectedPersons);
    }

    public static void main(String[] args) {
        launch(args);
    }

    //Define the button cell
    private class ButtonCell extends TableCell<Record, Boolean> {
        final Button cellButton = new Button("Delete");

        ButtonCell() {

            //Action when the button is pressed
            cellButton.setOnAction(new EventHandler<ActionEvent>() {

                @Override
                public void handle(ActionEvent t) {
                    // get Selected Item
                    Person currentPerson = (Person) ButtonCell.this.getTableView().getItems().get(ButtonCell.this.getIndex());
                    //remove selected item from the table list
                    data.remove(currentPerson);
                }
            });
        }

        //Display button if the row is not empty
        @Override
        protected void updateItem(Boolean t, boolean empty) {
            super.updateItem(t, empty);
            if (!empty) {
                setGraphic(cellButton);
            } else {
                setGraphic(null);
            }
        }
    }
}