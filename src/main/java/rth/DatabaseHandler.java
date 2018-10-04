package rth;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class DatabaseHandler {

    // Datbase name. Is memory database so doesnt matter.
    private static String db_name = "norm";
    // Instantiate database connection for class ref.
    private static Connection connection = null;

    DatabaseHandler() {
        setConnection();
    }

    public Connection getConnection() {
        if (connection == null) {
            setConnection();
        }
        return connection;
    }

    private void setConnection() {
        try {
            // Required to find h2 driver class
            Class.forName("org.h2.Driver");
            // Connection string
            String url = "jdbc:h2:mem:" + db_name;
            // Create database connection
            connection = DriverManager.getConnection(url);
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void loadCsvAsTable(String tableName, String filepath) {
        try {
            Statement statement = connection.createStatement();
            String query = "CREATE TABLE " + tableName + " AS SELECT * FROM csvread('" + filepath + "');";
            statement.execute(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    List<String> getColumnNames(String tableName) {
        List<String> columnNames = new ArrayList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName);
            ResultSetMetaData rsmd = resultSet.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // The column count starts from 1
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(rsmd.getColumnName(i));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return columnNames;
    }


    List<String> leftoverSelection(String columnName, String tableName, String selectedColumn, String ogValue, List<String> selectedValues) {
        List<String> temp = new ArrayList<>();

        try {
            Statement statement = connection.createStatement();
//            statement.setQueryTimeout(5);
            // Escaping column names. H2 requires columns with a space to be double quote escaped.

            String col = "\"" + columnName + "\"";
//            String selCol = "\"" + selectedColumn + "\"";

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("not exists ( select 1 from ").append(tableName).append(" t2 where t2.").append(selectedColumn).append(" = t.").append(selectedColumn).append(" and t2.").append(columnName).append(" = '").append(ogValue).append("')");

            for (String selectedValue : selectedValues) {
                stringBuilder.append(" and not exists ( select 1 from ").append(tableName).append(" t2 where t2.").append(selectedColumn).append(" = t.").append(selectedColumn).append(" and t2.").append(columnName).append(" = '").append(selectedValue).append("')");
            }

            String query = "select distinct " + columnName + " as selected, count(*) as count from " + tableName + " t where " + stringBuilder.toString() + " group by " + columnName + " order by count desc;";
            System.out.println(query);
            ResultSet rs = statement.executeQuery(query);

            while (rs.next()) {
                System.out.println(rs.getString("selected"));
                temp.add(rs.getString("selected"));
            }
            return temp;
            // return statement.executeQuery("select \" + col + \", COUNT(*) from " + tableName + " t where not exists (select 1 from dicom_roi t2 where t2.mrn = t.mrn and t2.name = "+isNotValue+") group by "+isNotValue+" order by count(*) desc;");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    //REDUCE SELECTION OPTIONS BY REMOVING ALL RECORDS WHICH ALREADY CONTAINS THE TARGET UPDATING VALUE.
    //select name, count(*) as count from dicom_roi t where not exists (select 1 from dicom_roi t2 where t2.mrn = t.mrn and t2.name = 'PTV') group by name order by count(*) desc;
    ResultSet distinctColumnValues(String columnName, String tableName) {
        try {
            Statement statement = connection.createStatement();
            // Escaping column names. H2 requires columns with a space to be double quote escaped.
            String col = "\"" + columnName + "\"";
            return statement.executeQuery("SELECT " + col + ", COUNT(*) AS count FROM " + tableName + " GROUP BY " + col + " ORDER BY count DESC;");
            // return statement.executeQuery("select \" + col + \", COUNT(*) from " + tableName + " t where not exists (select 1 from dicom_roi t2 where t2.mrn = t.mrn and t2.name = "+isNotValue+") group by "+isNotValue+" order by count(*) desc;");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    List<String> distinctColumnValuesByColumn(String columnName, String tableName, String selection_column, String selection_value) {
        List<String> temp = new ArrayList<>();

        try {
            Statement statement = connection.createStatement();
            // Escaping column names. H2 requires columns with a space to be double quote escaped.
            String col = "\"" + columnName + "\"";
            System.out.println("SELECT " + col + " as selected FROM " + tableName + " WHERE " + selection_column + " = " + selection_value + ";");
            ResultSet rs = statement.executeQuery("SELECT " + col + " FROM " + tableName + " WHERE " + selection_column + " = " + selection_value + ";");
            // return statement.executeQuery("select \" + col + \", COUNT(*) from " + tableName + " t where not exists (select 1 from dicom_roi t2 where t2.mrn = t.mrn and t2.name = "+isNotValue+") group by "+isNotValue+" order by count(*) desc;");

            while (rs.next()) {
                temp.add(rs.getString(1));
            }
            return temp;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Selects everything from table and returns result as resultset
     *
     * @param tableName name of table to use in query (FROM table_name).
     * @return
     */
    ResultSet selectAll(String tableName, String selectionColumn) {
        try {
            Statement statement = connection.createStatement();
            return statement.executeQuery("SELECT * FROM " + tableName + " order by " + selectionColumn +";");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }


    public void addNewColumn(String tableName, String columnName) {
        String column = "\"" + columnName + "_updated\"";

        String columnQuery = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + "_updated TEXT;";

        System.out.println(columnQuery);

        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(columnQuery);
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
        }



    }

    /**
     * updating column values
     *
     * @param tableName     -
     * @param columnName    -
     * @param updatingValue -
     * @param originalValue -
     */
    public void updateColumnValues(String tableName, String columnName, String updatingValue, String originalValue) {
        // Created query will be similar to "UPDATE table_name SET column_name = value WHERE column_name = value OR column_name = value;
        String columnUpdated = "\"" + columnName + "_updated\"";
        String column = "\"" + columnName + "\"";
        String column2 = "\"" + "NAME_UPDATED" + "\"";

        String query = "UPDATE " + tableName + " SET " + column2 + " = '" + updatingValue + "' WHERE " + column + " = '" + originalValue + "'";

        System.out.println(query);
        // Executing statement with a error handling try-catch.
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
        }
    }

    /**
     * @param tableName              name of table to use in query (FROM table_name).
     * @param columnName             name of table column to reference.
     * @param updatingValue          new user inputted valued that will update original values.
     * @param distinctValuesToUpdate the values that will be updated
     */
    public void updateColumnValuesByList(String tableName, String columnName, String updatingValue, List<String> distinctValuesToUpdate) {
        // String builder is just easier and fancier way of concatenating strings.
        StringBuilder query = new StringBuilder();
        // Append to string with standard SQL dialect and variables.
        query.append("UPDATE ").append(tableName);
        // query SET part (selecting which values to update)
        query.append(" SET ").append(columnName).append(" = ").append(updatingValue);

        // Creating WHERE Clause, which rows will be affected. Looping over distinctValuesToUpdate.
        query.append(" WHERE ");
        // Loop uses .size() -1 because the query cannot end in " OR;", invalid syntax.
        for (int i = 0; i < distinctValuesToUpdate.size() - 1; i++) {
            query.append(columnName).append(" = ").append(distinctValuesToUpdate.get(distinctValuesToUpdate.size() - 1));
            query.append(" OR ");
        }
        // Finishing query correctly (see above comment) without the OR.
        query.append(columnName).append(" = ").append(distinctValuesToUpdate);
        query.append(";");

        // Created query will be similar to "UPDATE table_name SET column_name = value WHERE column_name = value OR column_name = value;

        // Executing statement with a error handling try-catch.
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(query.toString());
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
        }
    }

    int getUniqueRowCount(String tableName, String uniqueColumn) {
        String query = "SELECT COUNT(DISTINCT " + uniqueColumn + ") as count FROM " + tableName;
        int count = 0;
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            count = resultSet.getInt("count");
            return count;
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();

        }
        return count;
    }

    int getSelectionCount(String tableName, String uniqueColumn, String columnName, String postValueSelection, List<String> ogValueSelection) {

        String column = "\"" + columnName + "\"";

        // String builder is just easier and fancier way of concatenating strings.
        StringBuilder query = new StringBuilder();
        // Append to string with standard SQL dialect and variables.
        query.append("SELECT ");
        // query SET part (selecting which values to update)
        query.append("COUNT(DISTINCT ").append(uniqueColumn).append(") ").append("AS count ");

        // Creating WHERE Clause, which rows will be affected. Looping over distinctValuesToUpdate.
        query.append("FROM ").append(tableName).append(" WHERE ").append(column).append(" = '").append(postValueSelection).append("'");
        for (String anOgValueSelection : ogValueSelection) {
            query.append(" OR ");
            query.append(column).append(" = '").append(anOgValueSelection).append("'");
        }
        query.append(";");

        System.out.println(query.toString());
        // Executing statement with a error handling try-catch.
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query.toString());
            resultSet.next();
            int count = resultSet.getInt("count");
            return count;
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
            return 0;
        }

    }

    /**
     * Closing database connection. Currently not used as objects are destroyed (Not great coding but time constraint, sorry))
     */
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
