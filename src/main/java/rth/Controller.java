package rth;

import com.opencsv.CSVWriter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Controller {

    //FXML tag tells Java it's an JavaFX UI element (is required). Using it in a code block indicates all values are FXML, can also be separately typed for each UI variable.
    @FXML
    public MenuItem addDatasetMenuItem;
    public MenuItem exportMenuItem;
    public MenuItem exportAsPivotMenuItem;
    public MenuItem closeMenuItem;
    public ListView<Label> columnNamesListView;
    public AnchorPane selectionSplitAnchor;
    public VBox toUpdateDistinctColumnValues;
    public ListView<Label> distinctColumnValuesListView;
    public Label valueLabel;
    public Label totalRowCount;

    //Program only contains one data-set thus one SQL table. Variable stores this table and all sql queries in parameters refer to this variable.
    private String table_name = "";

    //A multidimensional map.
    //First level contains the column name as key, the value is another map.
    //Second level contains the original data-set value and what the user inputted as new value.
    //Cannot use list as a list doesn't contain Keys, the keys are the column names used to build the queries.
    private HashMap<String, HashMap<String, List<String>>> query_map = new HashMap<>();

    /**
     * Add dataset menu item action. This function executes when this item menu is clicked (or "performed")
     * <p>
     * Shows a file explorere where you can select a CSV file.
     * The file path is stripped from extension (.csv) and used as table name.
     * Menu item because disabled as only 1 dataset can be used in this program.
     */
    @FXML
    public void addDatasetMenuItemAction() {
        // Get the main stage as singleton (the main program window). This way we can say the parent of the file chooser is the main Window.
        Stage stage = Main.getPrimaryStage();

        // Open file explorer chooser and the selected CSV file beceoms the selectedFile parameter.
        File selectedFile = fileChooserHandler(stage, "select CSV", "CSV files (*.csv)", "*.csv", "./");

        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))
        DatabaseHandler db = new DatabaseHandler();

        // Table name is the file name without extension using regex.
        table_name = selectedFile.getName().replaceFirst("[.][^.]+$", "");

        // Load the csv as table int othe database by below function call.
        db.loadCsvAsTable(table_name, selectedFile.getPath());

        // Call function that creates all the checkbox column selection on the left side of the main window.
        createColumnSelection();

        // Disable menu item as only 1 dataset can be used in this program.
        addDatasetMenuItem.setDisable(true);
        exportMenuItem.setDisable(false);
        exportAsPivotMenuItem.setDisable(false);
    }

    /**
     * Executes all stored update values from query_map and saves the table as CSV by using file explorer window.
     */
    @FXML
    private void exportMenuItemAction() {
        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))
        DatabaseHandler db = new DatabaseHandler();

        // Text file writer object
        PrintWriter logWriter = null;
        try {
            logWriter = new PrintWriter("LOG_" + table_name + ".txt", "UTF-8");
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        List<String> log = new ArrayList<>();

//        query_map.forEach((selectedColumnNameText, nestedMap) -> nestedMap.forEach((originalValue, updatingValue) -> {
//            log.add("In column '" + selectedColumnNameText + "' all values '" + originalValue + " were updated to '" + updatingValue + "'\n");
//            db.updateColumnValues(table_name, selectedColumnNameText, updatingValue, originalValue);
//        }));

        if (logWriter != null) {
            //TODO Add file name, table name and date of processing to file and possible author.
            logWriter.println(log);
            logWriter.close();
        }

        try {
            CSVWriter writer = new CSVWriter(new FileWriter("updated_" + table_name + ".csv"));
            ResultSet rs = db.selectAll(table_name);
            writer.writeAll(rs, true);
            writer.close();
        } catch (IOException | SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
        }
        successSavedPopUp();
    }

    @FXML
    private void successSavedPopUp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Success");
        alert.setContentText("New data-set successfully saved to folder");
        alert.showAndWait();
    }

    /**
     * Executes all stored update values from query_map
     * Turns rows into columns by ID (called pivot table)
     * Exports the pivot table as CV by using file explorer window.
     */
    @FXML
    private void exportAsPivotMenuItemAction() {
        //TODO
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Failed");
        alert.setContentText("Feature not yet implemented!");

        alert.showAndWait();
        //successSavedPopUp();
    }

    private void createColumnSelection() {

        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))
        DatabaseHandler db = new DatabaseHandler();

        // Create list with all column names as strings
        List<String> selectedColumnNameTexts = db.getColumnNames(table_name);

        //region ListView of all columns names
        for (String selectedColumnNameText : selectedColumnNameTexts) {
            columnNamesListView.getItems().add(new Label(selectedColumnNameText));
        }

        //region column name listView selection event
        columnNamesListView.getSelectionModel().selectedItemProperty().addListener((observableCheckNewCheckBoxValue, oldValue, newValue) -> {
            String selectedColumnNameText = newValue.getText();
            distinctColumnValuesListView.getItems().clear();
            toUpdateDistinctColumnValues.getChildren().clear();
            totalRowCount.setText("x/x");
            valueLabel.setText(selectedColumnNameText);

            List<String> unselectedValues = new ArrayList<>();

            try {
                ResultSet distinctColumnValues = db.distinctColumnValues(selectedColumnNameText, table_name);
                while (distinctColumnValues.next()) {
                    String distinctColumnValue = distinctColumnValues.getString(1);
                    // distinctColumnValuesListView.getItems().add(new Label(distinctColumnValue));
                    unselectedValues.add(distinctColumnValue);
                }

                for (String unselectedValue : unselectedValues) {
//                    if(!unselectedValue.equals(selectedColumnNameText)){
                        distinctColumnValuesListView.getItems().add(new Label(unselectedValue));
               //     }
                }

                distinctColumnValuesListView.getSelectionModel().selectedItemProperty().addListener((observableCheckNewCheckBoxValue2, oldValue2, newValue2) -> {
                    toUpdateDistinctColumnValues.getChildren().clear();

                    String distinctColumnValueText = newValue2.getText();
                    System.out.println(distinctColumnValueText);

                    for (String unselectedValue : unselectedValues) {
                        CheckBox box = new CheckBox(unselectedValue);
                        toUpdateDistinctColumnValues.getChildren().add(box);

                        //region Checkbox (de)select event handler
                        box.selectedProperty().addListener((observableCheckBox, oldCheckBoxValue, newCheckBoxValue) -> {
                            String toUpdateDistinctColumnValueText = box.getText();
                            if (box.isSelected()) {
                                // Checkbox is selected
                                //region Add selection to query_map
                                if (!query_map.containsKey(selectedColumnNameText)) {

                                    // Not making array inline creation to keep code simple to understand.
                                    List<String> tempArray = new ArrayList<>();
                                    tempArray.add(distinctColumnValueText);

                                    // Not making hashMap creation inline to keep simple to understand.
                                    HashMap<String, List<String>> tempHashMap = new HashMap<>();
                                    tempHashMap.put(toUpdateDistinctColumnValueText, tempArray);

                                    query_map.put(selectedColumnNameText, tempHashMap);
                                } else {
                                    if (!query_map.get(selectedColumnNameText).containsKey(toUpdateDistinctColumnValueText)) {
                                        // Not making array inline creation to keep code simple to understand.
                                        List<String> tempArray = new ArrayList<>();
                                        tempArray.add(distinctColumnValueText);
                                        query_map.get(selectedColumnNameText).put(toUpdateDistinctColumnValueText, tempArray);
                                    }

                                    if (!query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).contains(distinctColumnValueText)) {
                                        query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).add(distinctColumnValueText);
                                    }
                                }
                                //endregion
                            } else {
                                // Checkbox is unselected
                                query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).remove(distinctColumnValueText);
                            }
                            System.out.println(query_map);
                        });
                        //endregion
                    }
                });

            } catch (SQLException e) {
                //TODO
            }

            // Loop over all column names
//            for (String selectedColumnNameText : selectedColumnNameTexts) {
//                // Create new checkbox for each column name with the column name as text/label
//                CheckBox box = new CheckBox(selectedColumnNameText);
//                // Create new checkbox event, every time checkbox is checked or unchecked this event is fired.
//                box.selectedProperty().addListener((observableCheckBox, oldCheckBoxValue, newCheckBoxValue) -> {
//                    if (box.isSelected()) {
//                        // If the checkbox state is checked
//                        // Get all distinct values of that column in resultSet, most common values to least common.
//                        ResultSet distinct_column_values = db.distinctColumnValues(selectedColumnNameText, table_name);
//
//                        ListView<Label> listview = new ListView<>();
//                        listview.setPadding(new Insets(10, 10, 10, 10));
//                        try {
//                            while (distinct_column_values.next()) {
//                                listview.getItems().add(new Label(distinct_column_values.getString(1)));
//                            }
//                        } catch (SQLException e) {
//                            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
//                            e.printStackTrace();
//                        }
//
//                        listview.getSelectionModel().selectedItemProperty().addListener((observableChecknewCheckBoxValue2, oldCheckBoxValueColumnValue2, newCheckBoxValueColumnValue2) -> {
//                            System.out.println(newCheckBoxValueColumnValue);
//                        });
//
//
//                        for (String unselected_value : unselectedValues) {
//                            CheckBox unselectedCheckBoxvalue = new CheckBox(unselected_value);
//                            vBoxValues2.getChildren().add(unselectedCheckBoxvalue);
//                            unselectedCheckBoxvalue.selectedProperty().addListener((unselectedBox, oldunselectedBoxValue, newunselectedBoxValue) -> {
//                                if (unselectedCheckBoxvalue.isSelected()) {
//
//                                    //valueTabPane.getTabs().get(tab2-> tab.getText().equals(toUpdateDistinctColumnValueText));
//                                    unselectedValues.remove(distinctColumnValueText);
//
//                                    //If the textfield is empty again (when backspacing)remove the value from the query_map.(Otherwise the new value would be updated as null
//                                    if (!query_map.containsKey(selectedColumnNameText)) {
//
//                                        // Not making array inline creation to keep code simple to understand.
//                                        List<String> tempArray = new ArrayList<>();
//                                        tempArray.add(distinctColumnValueText);
//
//                                        // Not making hashMap creation inline to keep simple to understand.
//                                        HashMap<String, List<String>> tempHashMap = new HashMap<>();
//                                        tempHashMap.put(toUpdateDistinctColumnValueText, tempArray);
//
//                                        query_map.put(selectedColumnNameText, tempHashMap);
//                                    } else {
//                                        if (!query_map.get(selectedColumnNameText).containsKey(toUpdateDistinctColumnValueText)) {
//                                            // Not making array inline creation to keep code simple to understand.
//                                            List<String> tempArray = new ArrayList<>();
//                                            tempArray.add(distinctColumnValueText);
//                                            query_map.get(selectedColumnNameText).put(toUpdateDistinctColumnValueText, tempArray);
//                                        }
//
//                                        if (!query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).contains(distinctColumnValueText)) {
//                                            query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).add(distinctColumnValueText);
//                                        }
//                                    }
//                                } else {
//                                    query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).remove(distinctColumnValueText);
//                                }
//                                System.out.println(query_map);
//                            });
//                        }
//
//
//                        // Create VBox
//                        //VBox vBox = new VBox();
//
//                        // Create anchorPane
//                        AnchorPane valueTabPane = new AnchorPane();
//                        valueTabPane.setPadding(new Insets(10, 10, 10, 10));
//
//
//                        HBox.setHgrow(valueTabPane, Priority.ALWAYS);
//
//                    }
//                });
//            }
        });
        //endregion END column names listView event handler
        //endregion END column names listView
    }

    // Error handling try-catch block
//                    try {
//                        // Keeping count for gridPane rows, starts at 1 since the above labels started at row 0
//                        List<String> unselectedValues = new ArrayList<>();
//                        // Loop over all distinct values of that column
//                        while (distinct_column_values.next()) {
//
//                            String distinct_column_value = distinct_column_values.getString(1);
//                            unselectedValues.add(distinct_column_value);
//                            CheckBox newCheckBoxValue = new CheckBox(distinct_column_value);
//                            listview.getItems().add(new Label(distinct_column_value));
//
//                            // Create new checkbox event, every time checkbox is checked or unchecked this event is fired.
//                            newCheckBoxValue.selectedProperty().addListener((observableChecknewCheckBoxValue, oldCheckBoxValueColumnValue, newCheckBoxValueColumnValue) -> {
//                                if (newCheckBoxValue.isSelected()) {
//                                    unselectedValues.remove(toUpdateDistinctColumnValueText);
//
//                                    Tab tab = new Tab(toUpdateDistinctColumnValueText);
//                                    tab.setClosable(false);
//
//                                    GridPane gridPane = new GridPane();
//                                    gridPane.setHgap(10);
//
//                                    ScrollPane scrollPaneVbox2 = new ScrollPane();
//                                    VBox vBoxValues2 = new VBox();
//                                    vBoxValues2.setPadding(new Insets(10, 10, 10, 10));
//                                    scrollPaneVbox2.setContent(vBoxValues2);
//
//                                    Label valueLabel = new Label(toUpdateDistinctColumnValueText);
//                                    Label patientCount = new Label("x/x");
//
//                                    gridPane.add(valueLabel, 0, 0);
//                                    gridPane.add(patientCount, 1, 0);
//                                    gridPane.add(scrollPaneVbox2, 0, 2);
//
//                                    tab.setContent(gridPane);
//                                    valueTabPane.getTabs().add(tab);
//
//                                    for (String unselected_value : unselectedValues) {
//                                        CheckBox unselectedCheckBoxvalue = new CheckBox(unselected_value);
//                                        vBoxValues2.getChildren().add(unselectedCheckBoxvalue);
//                                        unselectedCheckBoxvalue.selectedProperty().addListener((unselectedBox, oldunselectedBoxValue, newunselectedBoxValue) -> {
//                                            if (unselectedCheckBoxvalue.isSelected()) {
//
//                                                //valueTabPane.getTabs().get(tab2-> tab.getText().equals(toUpdateDistinctColumnValueText));
//                                                unselectedValues.remove(distinctColumnValueText);
//
//                                                //If the textfield is empty again (when backspacing)remove the value from the query_map.(Otherwise the new value would be updated as null
//                                                if (!query_map.containsKey(selectedColumnNameText)) {
//
//                                                    // Not making array inline creation to keep code simple to understand.
//                                                    List<String> tempArray = new ArrayList<>();
//                                                    tempArray.add(distinctColumnValueText);
//
//                                                    // Not making hashMap creation inline to keep simple to understand.
//                                                    HashMap<String, List<String>> tempHashMap = new HashMap<>();
//                                                    tempHashMap.put(toUpdateDistinctColumnValueText, tempArray);
//
//                                                    query_map.put(selectedColumnNameText, tempHashMap);
//                                                } else {
//                                                    if (!query_map.get(selectedColumnNameText).containsKey(toUpdateDistinctColumnValueText)) {
//                                                        // Not making array inline creation to keep code simple to understand.
//                                                        List<String> tempArray = new ArrayList<>();
//                                                        tempArray.add(distinctColumnValueText);
//                                                        query_map.get(selectedColumnNameText).put(toUpdateDistinctColumnValueText, tempArray);
//                                                    }
//
//                                                    if (!query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).contains(distinctColumnValueText)) {
//                                                        query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).add(distinctColumnValueText);
//                                                    }
//                                                }
//                                            } else {
//                                                query_map.get(selectedColumnNameText).get(toUpdateDistinctColumnValueText).remove(distinctColumnValueText);
//                                            }
//                                            System.out.println(query_map);
//                                        });
//                                    }
//                                } else if (!newCheckBoxValue.isSelected()) {
//                                    query_map.get(selectedColumnNameText).remove(toUpdateDistinctColumnValueText);
//                                    // Remove the tab from tabPane
//                                    valueTabPane.getTabs().removeIf(tab -> tab.getText().equals(toUpdateDistinctColumnValueText));
//                                }
//                            });
//                        }
//                    } catch (SQLException e) {
//                        //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
//                        e.printStackTrace();
//                    }
//                    selectScrollPane.setContent(vBox);
//                    hBox.getChildren().add(selectScrollPane);
//                    hBox.getChildren().add(valueTabPane);
//
//                    // Add scrollPane to tab
//                    columnPane.setContent(hBox);
//                    // Add tab to tabPane
//                    columnTabPane.getTabs().add(columnPane);
//                } else if (!box.isSelected()) {
//                    // If checkbox is unchecked
//                    // Remove all references to this column name from the query_map
//                    query_map.remove(selectedColumnNameText);
//
//                    // Remove the tab from tabPane
//                    columnTabPane.getTabs().removeIf(tab -> tab.getText().equals(box.getText()));
//                }
//            });
//            columnSelectionVBox.getChildren().add(box);
//        }
//    }

    /**
     * Close applicaiton action
     */
    @FXML
    public void closeMenuItemAction() {
        System.exit(1);
    }

    /**
     * Generic function to open a file explorer window to open a file.
     *
     * @param ownerWindow          Which window owns this file explorer window (not important here, can be null as standalone window, nothing will break)
     * @param explorerTitle        Name of the file explorere window
     * @param extensionDescription description in file explorer window which extensions options are available
     * @param extensionFormat      which extensions to show only
     * @param initialDir           Which directory to open first
     * @return
     */
    private static File fileChooserHandler(Stage ownerWindow, String explorerTitle, String
            extensionDescription, String extensionFormat, String initialDir) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(explorerTitle);

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(extensionDescription, extensionFormat);
        fileChooser.getExtensionFilters().add(extFilter);

        File file = new File(initialDir);
        fileChooser.setInitialDirectory(file);

        return fileChooser.showOpenDialog(ownerWindow);
    }

}