package rth;

import com.opencsv.CSVWriter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
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
    public ComboBox<Label> idColumnComboBox;
    public Button runSelectionButton;
    public TextField searchDistinctValuesTextField;
    public TextField searchToBeUpdatedValuesTextField;
    public ListView<Label> leftoverListView;
    public ListView<Label> logListView;
    public Button getLeftoverSelectionButton;

    //Program only contains one data-set thus one SQL table. Variable stores this table and all sql queries in parameters refer to this variable.
    private String table_name = "";
    private String column = "";
    private String selection_column = "";
    private String distinct_value = "";
    private List<String> toBeUpdatedValues = new ArrayList<>();
    private String search_distinct;
    private Map<String, List<String>> search_list = new HashMap<>();
    private List<String> updatedColumns = new ArrayList<>();


    private DatabaseHandler db;

    public Controller() {
        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))
        db = new DatabaseHandler();

        // Escaping column names. H2 requires columns with a space to be double quote escaped.
    }

    @FXML
    public void initialize() {
        idColumnComboBox.getSelectionModel().selectedItemProperty().addListener((observableCheckNewCheckBoxValue, oldValue, newValue) -> {
            selection_column = newValue.getText();
            updateRowCount(db.getUniqueRowCount(table_name, selection_column), 0);
        });

        //region column name listView selection event
        // This code executes when the listView UI element named columnNamesListView has a new item selection.
        columnNamesListView.getSelectionModel().selectedItemProperty().addListener((observableCheckNewCheckBoxValue, oldValue, newValue) -> {
            toBeUpdatedValues.clear();
            column = newValue.getText();
            // Get the value of the selected item from the column listView
            // Clear the other listViews (or new values will just append and constantly expand the size)
            distinctColumnValuesListView.getItems().clear();
            toUpdateDistinctColumnValues.getChildren().clear();

            totalRowCount.setText("x/x");
            valueLabel.setText(column);

            try {
                ResultSet distinctColumnValues = db.distinctColumnValues(column, table_name);
                while (distinctColumnValues.next()) {
                    String distinctColumnValue = distinctColumnValues.getString(1);
                    distinctColumnValuesListView.getItems().add(new Label(distinctColumnValue));
                }

            } catch (SQLException e) {
                //TODO
            }

        });
        //endregion END column names listView event handler

        distinctColumnValuesListView.getSelectionModel().selectedItemProperty().addListener((observableCheckNewCheckBoxValue2, oldValue2, newValue2) -> {

            distinct_value = null;

            if (!distinctColumnValuesListView.getSelectionModel().isEmpty()) {
                toBeUpdatedValues.clear();
                distinct_value = newValue2.getText();
                updateRowCount(db.getUniqueRowCount(table_name, selection_column), db.getSelectionCount(table_name, selection_column, column, distinct_value, new ArrayList<String>() {{
                    add(distinct_value);
                }}));

                toUpdateDistinctColumnValues.getChildren().clear();

                String distinctColumnValueText = newValue2.getText();

                try {
                    ResultSet distinctColumnValues = db.distinctColumnValues(column, table_name);
                    while (distinctColumnValues.next()) {
                        String unselectedValue = distinctColumnValues.getString(1);

                        if (!distinctColumnValueText.equals(unselectedValue)) {
                            CheckBox box = new CheckBox(unselectedValue);
                            toUpdateDistinctColumnValues.getChildren().add(box);

                            //region Checkbox (de)select event handler
                            box.selectedProperty().addListener((observableCheckBox, oldCheckBoxValue, newCheckBoxValue) -> {
                                String toUpdateDistinctColumnValueText = box.getText();

                                if (box.isSelected()) {
                                    // Checkbox is selected
                                    if (!toBeUpdatedValues.contains(toUpdateDistinctColumnValueText)) {
                                        toBeUpdatedValues.add(toUpdateDistinctColumnValueText);
                                    }
                                    //endregion
                                } else {
                                    toBeUpdatedValues.remove(toUpdateDistinctColumnValueText);
                                    // Checkbox is unselected
                                }

                                int total = db.getUniqueRowCount(table_name, selection_column);
                                int selectionCount = db.getSelectionCount(table_name, selection_column, column, distinct_value, toBeUpdatedValues);
                                updateRowCount(total, selectionCount);
                            });
                            //endregion
                        }
                    }
                } catch (SQLException e) {
                    //TODO gracefully handle error
                    e.printStackTrace();
                }
            }
        });

        searchDistinctValuesTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            int listNr = distinctColumnValuesListView.getItems().size();
            for (int i = 0; i < listNr; i++) {
                Label label = distinctColumnValuesListView.getItems().get(i);
                if (!label.getText().toLowerCase().contains(newValue.toLowerCase())) {
                    label.setVisible(false);
                    label.setManaged(false);
                } else {
                    label.setVisible(true);
                    label.setManaged(true);
                }
            }
        });

        searchToBeUpdatedValuesTextField.textProperty().addListener((observable, oldValue, newValue) -> {

            search_distinct = newValue.toLowerCase();

            int listNr = toUpdateDistinctColumnValues.getChildren().size();
            for (int i = 0; i < listNr; i++) {
                CheckBox checkbox = (CheckBox) toUpdateDistinctColumnValues.getChildren().get(i);
                if (!checkbox.getText().toLowerCase().contains(search_distinct)) {
                    checkbox.setVisible(false);
                    checkbox.setManaged(false);
                } else {
                    checkbox.setVisible(true);
                    checkbox.setManaged(true);
                }
            }
        });
    }

    private void updateRowCount(int total, int selection) {
        String count = selection + "/" + total;
        totalRowCount.setText(count);
    }

    @FXML
    private void runSelectionButtonAction() {
        if (!updatedColumns.contains(column)) {
            db.addNewColumn(table_name, column);
            updatedColumns.add(column);
        }
        db.updateColumnValues(table_name, column, distinct_value, distinct_value);

        StringBuilder selectionLog = new StringBuilder("Values ");
        for (String toBeUpdatedValue : toBeUpdatedValues) {
            db.updateColumnValues(table_name, column, distinct_value, toBeUpdatedValue);
            selectionLog.append(toBeUpdatedValue).append(", ");
        }
        selectionLog.append("of column ").append(column).append(" were updated to ").append(distinct_value);

        logListView.getItems().add(new Label(selectionLog.toString()));
    }

    @FXML
    private void getLeftoverSelectionButtonAction() {
        leftoverListView.getItems().clear();

        List<String> rs3 = db.leftoverSelection(column, table_name, selection_column, distinct_value, toBeUpdatedValues);

        if (rs3.size() != 0) {
            for (String update : rs3) {
                leftoverListView.getItems().add(new Label(update));
            }
        } else {
            leftoverListView.getItems().add(new Label());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information Dialog");
            alert.setHeaderText("No more selectable values");
            alert.setContentText("For each unique " + selection_column + " there is no value in column " + column + " which isn't yet selected or contains the search term " + search_distinct);
            alert.showAndWait();
        }
    }

    /**
     * Add dataSet menu item action. This function executes when this item menu is clicked (or "performed")
     * <p>
     * Shows a file explorer where you can select a CSV file.
     * The file path is stripped from extension (.csv) and used as table name.
     * Menu item because disabled as only 1 dataSet can be used in this program.
     */
    @FXML
    public void addDatasetMenuItemAction() {
        // Get the main stage as singleton (the main program window). This way we can say the parent of the file chooser is the main Window.
        Stage stage = Main.getPrimaryStage();

        // Open file explorer chooser and the selected CSV file becomes the selectedFile parameter.
        File selectedFile = fileChooserHandler(stage, "select CSV", "CSV files (*.csv)", "*.csv", "./");

        //Instantiate new database (Not gracefully handled since for each function it instantiates and destroys this object, not a problem but not nice coding (time constraint))

        // Table name is the file name without extension using regex.
        table_name = selectedFile.getName().replaceFirst("[.][^.]+$", "");

        // Load the csv as table int other database by below function call.
        db.loadCsvAsTable(table_name, selectedFile.getPath());

        // Call function that creates all the checkbox column selection on the left side of the main window.
        List<String> selectedColumnNameTexts = db.getColumnNames(table_name);

        columnNamesListView.getItems().clear();
        idColumnComboBox.getItems().clear();

        //region ListView of all columns names
        for (String selectedColumnNameText : selectedColumnNameTexts) {
            columnNamesListView.getItems().add(new Label(selectedColumnNameText));

            Label label = new Label(selectedColumnNameText);
            label.setStyle("-fx-text-fill: #000;");

            idColumnComboBox.getItems().add(label);
        }
        idColumnComboBox.getSelectionModel().select(0);

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
        // Text file writer object
        PrintWriter logWriter = null;
        try {
            logWriter = new PrintWriter("LOG_" + table_name + ".txt", "UTF-8");
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        List<String> log = new ArrayList<>();

        for (int i = 0; i < logListView.getItems().size(); i++) {
            log.add(logListView.getItems().get(i).getText() + "\n");
        }

        if (logWriter != null) {
            //TODO Add file name, table name and date of processing to file and possible author.
            logWriter.println(log);
            logWriter.close();
        }

        try {
            CSVWriter writer = new CSVWriter(new FileWriter("updated_" + table_name + ".csv"));
            ResultSet rs = db.selectAll(table_name, selection_column);
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

    /**
     * Close application action
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
     * @return -
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