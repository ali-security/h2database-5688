/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf.context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.h2.command.Parser;
import org.h2.util.New;
import org.h2.util.StringUtils;

/**
 * Keeps meta data information about a database.
 * This class is used by the H2 Console.
 */
public class DbContents {

    private DbSchema[] schemas;
    private DbSchema defaultSchema;
    private boolean isOracle;
    private boolean isH2;
    private boolean isPostgreSQL;
    private boolean isDerby;
    private boolean isSQLite;
    private boolean isH2ModeMySQL;
    private boolean isMySQL;
    private boolean isFirebird;
    private boolean isMSSQLServer;

    /**
     * @return The default schema.
     */
    public DbSchema getDefaultSchema() {
        return defaultSchema;
    }

    /**
     * @return True if this is an Apache Derby database.
     */
    public boolean isDerby() {
        return isDerby;
    }

    /**
     * @return True if this is a Firebird database.
     */
    public boolean isFirebird() {
        return isFirebird;
    }

    /**
     * @return True if this is a H2 database.
     */
    public boolean isH2() {
        return isH2;
    }

    /**
     * @return True if this is a H2 database in MySQL mode.
     */
    public boolean isH2ModeMySQL() {
        return isH2ModeMySQL;
    }

    /**
     * @return True if this is a MS SQL Server database.
     */
    public boolean isMSSQLServer() {
        return isMSSQLServer;
    }

    /**
     * @return True if this is a MySQL database.
     */
    public boolean isMySQL() {
        return isMySQL;
    }

    /**
     * @return True if this is an Oracle database.
     */
    public boolean isOracle() {
        return isOracle;
    }

    /**
     * @return True if this is a PostgreSQL database.
     */
    public boolean isPostgreSQL() {
        return isPostgreSQL;
    }

    /**
     * @return True if this is an SQLite database.
     */
    public boolean isSQLite() {
        return isSQLite;
    }

    /**
     * @return The list of schemas.
     */
    public DbSchema[] getSchemas() {
        return schemas;
    }

    /**
     * Get the column index of a column in a result set. If the column is not
     * found, the default column index is returned.
     * This is a workaround for a JDBC-ODBC bridge problem.
     *
     * @param rs the result set
     * @param columnName the column name
     * @param defaultColumnIndex the default column index
     * @return the column index
     */
    public static int findColumn(ResultSet rs, String columnName, int defaultColumnIndex) {
        try {
            return rs.findColumn(columnName);
        } catch (SQLException e) {
            return defaultColumnIndex;
        }
    }

    /**
     * Read the contents of this database from the database meta data.
     *
     * @param meta the database meta data
     */
    public synchronized void readContents(DatabaseMetaData meta) throws SQLException {
        String prod = StringUtils.toLowerEnglish(meta.getDatabaseProductName());
        isSQLite = prod.indexOf("sqlite") >= 0;
        String url = meta.getURL();
        if (url != null) {
            isH2 = url.startsWith("jdbc:h2:");
            if (isH2) {
                Statement stat = meta.getConnection().createStatement();
                ResultSet rs = stat.executeQuery(
                        "SELECT UPPER(VALUE) FROM INFORMATION_SCHEMA.SETTINGS WHERE NAME='MODE'");
                rs.next();
                if ("MYSQL".equals(rs.getString(1))) {
                    isH2ModeMySQL = true;
                }
                rs.close();
                stat.close();
            }
            isOracle = url.startsWith("jdbc:oracle:");
            isPostgreSQL = url.startsWith("jdbc:postgresql:");
            // isHSQLDB = url.startsWith("jdbc:hsqldb:");
            isMySQL = url.startsWith("jdbc:mysql:");
            isDerby = url.startsWith("jdbc:derby:");
            isFirebird = url.startsWith("jdbc:firebirdsql:");
            isMSSQLServer = url.startsWith("jdbc:sqlserver:");
        }
        String defaultSchemaName = getDefaultSchemaName(meta);
        String[] schemaNames = getSchemaNames(meta);
        schemas = new DbSchema[schemaNames.length];
        for (int i = 0; i < schemaNames.length; i++) {
            String schemaName = schemaNames[i];
            boolean isDefault = defaultSchemaName == null || defaultSchemaName.equals(schemaName);
            DbSchema schema = new DbSchema(this, schemaName, isDefault);
            if (schema.isDefault) {
                defaultSchema = schema;
            }
            schemas[i] = schema;
            String[] tableTypes = { "TABLE", "SYSTEM TABLE", "VIEW", "SYSTEM VIEW",
                    "TABLE LINK", "SYNONYM", "EXTERNAL" };
            schema.readTables(meta, tableTypes);
            schema.readProcedures(meta);
        }
        if (defaultSchema == null) {
            String best = null;
            for (DbSchema schema : schemas) {
                if ("dbo".equals(schema.name)) {
                    // MS SQL Server
                    defaultSchema = schema;
                    break;
                }
                if (defaultSchema == null || best == null || schema.name.length() < best.length()) {
                    best = schema.name;
                    defaultSchema = schema;
                }
            }
        }
    }

    private String[] getSchemaNames(DatabaseMetaData meta) throws SQLException {
        if (isMySQL || isSQLite) {
            return new String[] { "" };
        } else if (isFirebird) {
            return new String[] { null };
        }
        ResultSet rs = meta.getSchemas();
        ArrayList<String> schemaList = New.arrayList();
        while (rs.next()) {
            String schema = rs.getString(findColumn(rs, "TABLE_SCHEM", 1));
            if (isOracle) {
                for (String ignore : new String[] {
                        "CTXSYS", "DIP", "DBSNMP", "DMSYS", "EXFSYS", "FLOWS_020100", "FLOWS_FILES", "MDDATA", "MDSYS",
                        "MGMT_VIEW", "OLAPSYS", "ORDSYS", "ORDPLUGINS", "OUTLN", "SI_INFORMTN_SCHEMA", "SYS", "SYSMAN",
                        "SYSTEM", "TSMSYS", "WMSYS", "XDB"
                }) {
                    if (ignore.equals(schema)) {
                        schema = null;
                        break;
                    }
                }
            } else if (isMSSQLServer) {
                for (String ignore : new String[] {
                        "sys", "db_accessadmin", "db_backupoperator", "db_datareader", "db_datawriter", "db_ddladmin",
                        "db_denydatareader", "db_denydatawriter", "db_owner", "db_securityadmin"
                }) {
                    if (ignore.equals(schema)) {
                        schema = null;
                        break;
                    }
                }
            }
            if (schema == null) {
                continue;
            }
            schemaList.add(schema);
        }
        rs.close();
        String[] list = new String[schemaList.size()];
        schemaList.toArray(list);
        return list;
    }

    private String getDefaultSchemaName(DatabaseMetaData meta) {
        String defaultSchemaName = "";
        try {
            if (isOracle) {
                return meta.getUserName();
            } else if (isPostgreSQL) {
                return "public";
            } else if (isMySQL) {
                return "";
            } else if (isDerby) {
                return StringUtils.toUpperEnglish(meta.getUserName());
            } else if (isFirebird) {
                return null;
            }
            ResultSet rs = meta.getSchemas();
            int index = rs.findColumn("IS_DEFAULT");
            while (rs.next()) {
                if (rs.getBoolean(index)) {
                    defaultSchemaName = rs.getString("TABLE_SCHEM");
                }
            }
        } catch (SQLException e) {
            // IS_DEFAULT not found
        }
        return defaultSchemaName;
    }

    /**
     * Add double quotes around an identifier if required.
     * For the H2 database, all identifiers are quoted.
     *
     * @param identifier the identifier
     * @return the quoted identifier
     */
    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        if (isH2 && !isH2ModeMySQL) {
            return Parser.quoteIdentifier(identifier);
        }
        return StringUtils.toUpperEnglish(identifier);
    }

}