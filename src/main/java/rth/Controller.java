package rth;

import com.opencsv.CSVWriter;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
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
    public VBox columnSelectionVBox;
    public TabPane columnTabPane;

    //Program only contains one data-set thus one SQL table. Variable stores this table and all sql queries in parameters refer to this variable.
    private String table_name = "";

    //A multidimensional map.
    //First level contains the column name as key, the value is another map.
    //Second level contains the original data-set value and what the user inputted as new value.
    //Cannot use list as a list doesn't contain Keys, the keys are the column names used to build the queries.
    private HashMap<String, HashMap<String, String>> query_map = new HashMap<>();


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

        query_map.forEach((columnName, nestedMap) -> nestedMap.forEach((originalValue, updatingValue) -> {
            log.add("In column '" + columnName + "' all values '" + originalValue + " were updated to '" + updatingValue + "'\n");
            db.updateColumnValues(table_name, columnName, updatingValue, originalValue);
        }));

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

    private void successSavedPopUp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Success");
        alert.setContentText("New dataset successfully saved to folder");

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

//        successSavedPopUp();
    }


    private void createColumnSelection() {
        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))
        DatabaseHandler db = new DatabaseHandler();

        // Create list with all column names as strings
        List<String> columnNames = db.getColumnNames(table_name);

        // Loop over all column names
        for (String columnName : columnNames) {
            // Create new checkbox for each column name with the column name as text/label
            CheckBox box = new CheckBox(columnName);
            // Create new checkbox event, every time checkbox is checked or unchecked this event is fired.
            box.selectedProperty().addListener((observableCheckBox, oldCheckBoxValue, newCheckBoxValue) -> {
                if (box.isSelected()) {
                    // If the checkbox state is checked
                    // Create new Tab with checkbox text/label as tab title/name.
                    Tab tab = new Tab(box.getText());

                    // Get all distinct values of that column in resultset, most common values to least common.
                    ResultSet distinct_column_values = db.distinctColumnValues(columnName, table_name);

                    // Create scrollpane
                    ScrollPane scrollPane = new ScrollPane();
                    // Create Gridpane
                    GridPane gridPane = new GridPane();
                    // Set horizontal gap between grid columns to 10px
                    gridPane.setHgap(10);
                    // Set gridpane padding to 10px al lsides.
                    gridPane.setPadding(new Insets(10, 10, 10, 10));

                    // Create 2 labels to explain users what both columns are.
                    Label currentValueLabel = new Label("Current dataset value");
                    Label newEnteredValueLabel = new Label("Enter new value to update or leave blank");

                    // Add both above created labels on row 0 of the gridpane.
                    gridPane.add(currentValueLabel, 0, 0);
                    gridPane.add(newEnteredValueLabel, 1, 0);

                    // Error handling try-catch block
                    try {
                        // Keeping count for gridpane rows, starts at 1 since the above labels started at row 0
                        int count = 1;

                        // Loop over all distinct values of that column
                        while (distinct_column_values.next()) {
                            // Get the column name from the resultset (columnIndex 0 is the count column, how many times the value appeared in that column, is already sorted from highest to lowest)
                            String distinct_value = distinct_column_values.getString(1);
                            // Create new label with the distinct value as text
                            Label label = new Label(distinct_value);

                            // Create new textfield where the user can enter the new value for this distinct value
                            TextField textField = new TextField();
                            // Add event handler to the text field. Every time the value changes (thus every keystroke, not when user is done typing)
                            textField.textProperty().addListener((observableTextField, oldTextFieldValue, newTextFieldValue) -> {
                                // If the textfield is empty again (when backspacing) remove the value from the query_map. (Otherwise the new value would be updated as null
                                if (!newTextFieldValue.equals("")) {
                                    if (!query_map.containsKey(columnName)) {
                                        query_map.put(columnName, new HashMap<>());
                                    }
                                    query_map.get(columnName).put(distinct_value, newTextFieldValue);
                                } else {
                                    query_map.get(columnName).remove(distinct_value);
                                }
                                System.out.println(query_map);
                            });

                            // Add original value label to gridPane
                            gridPane.add(label, 0, count);
                            // Set original value label to the right of the grid column
                            GridPane.setHalignment(label, HPos.RIGHT);
                            // Add textfield to gridPane
                            gridPane.add(textField, 1, count);
                            // Increase count so on next loop iteration the new UI controls are added on the right row.
                            count++;
                        }
                    } catch (SQLException e) {
                        //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
                        e.printStackTrace();
                    }

                    // Add gridpane to scrollpane
                    scrollPane.setContent(gridPane);
                    // Add scrllpane to tab
                    tab.setContent(scrollPane);
                    // Add tab to tabpane
                    columnTabPane.getTabs().add(tab);
                } else if (!box.isSelected()) {
                    // If checkbox is unchecked

                    // Remove all references to this column name from the query_map
                    query_map.remove(columnName);
                    // Remove the tab from tabPane
                    columnTabPane.getTabs().removeIf(tab -> tab.getText().equals(box.getText()));
                }
            });
            columnSelectionVBox.getChildren().add(box);
        }
    }

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
    private static File fileChooserHandler(Stage ownerWindow, String explorerTitle, String extensionDescription, String extensionFormat, String initialDir) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(explorerTitle);

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(extensionDescription, extensionFormat);
        fileChooser.getExtensionFilters().add(extFilter);

        File file = new File(initialDir);
        fileChooser.setInitialDirectory(file);

        return fileChooser.showOpenDialog(ownerWindow);
    }

}