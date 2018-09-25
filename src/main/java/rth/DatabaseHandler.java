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

    public DatabaseHandler() {
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

    public ResultSet distinctColumnValues(String columnName, String tableName) {
        try {
            Statement statement = connection.createStatement();
            // Escaping column names. H2 requires columns with a space to be double quote escaped.
            String col = "\"" + columnName + "\"";
            return statement.executeQuery("SELECT " + col + ", COUNT(*) AS count FROM " + tableName + " GROUP BY " + col + " ORDER BY count DESC;");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Selects everything from table and returns result as resultset
     *
     * @param tableName name of table to use in query (FROM table_name).
     * @return
     */
    public ResultSet selectAll(String tableName) {
        try {
            Statement statement = connection.createStatement();
            return statement.executeQuery("SELECT * FROM " + tableName + ";");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * updating column values
     *
     * @param tableName
     * @param columnName
     * @param updatingValue
     * @param originalValue
     */
    public void updateColumnValues(String tableName, String columnName, String updatingValue, String originalValue) {
        // Created query will be similar to "UPDATE table_name SET column_name = value WHERE column_name = value OR column_name = value;

        String column = "\"" + columnName + "\"";

        String query = "UPDATE " + tableName + " SET " + column + " = '" + updatingValue + "' WHERE " + column + " = '" + originalValue + "'";
        // String query = "UPDATE subset SET SEX = 6 WHERE SEX = '2'";

        System.out.println(query);
        // Executing statement with a error handling try-catch.
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
            statement.close();
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
            statement.close();
        } catch (SQLException e) {
            //TODO Error not logged or shown to user. printStackTrace only prints in debugging console in IDE.
            e.printStackTrace();
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
