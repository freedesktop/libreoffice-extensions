/*************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * Copyright 2000, 2010 Oracle and/or its affiliates.
 *
 * OpenOffice.org - a multi-platform office productivity suite
 *
 * This file is part of OpenOffice.org.
 *
 * OpenOffice.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenOffice.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenOffice.org.  If not, see
 * <http://www.openoffice.org/license.html>
 * for a copy of the LGPLv3 License.
 *
 ************************************************************************/
package com.sun.star.report;

import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.IndexOutOfBoundsException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.sdb.XParametersSupplier;
import java.sql.Timestamp;

import com.sun.star.sdbc.DataType;
import com.sun.star.sdbc.SQLException;
import com.sun.star.sdbc.XResultSetMetaData;
import com.sun.star.sdbc.XResultSetMetaDataSupplier;
import com.sun.star.sdbc.XRow;
import com.sun.star.sdbc.XRowSet;
import com.sun.star.sdbcx.XColumnsSupplier;
import com.sun.star.uno.Any;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.DateTime;
import com.sun.star.util.Time;

public class SDBCReportData implements DataSource
{

    private final XRowSet rowSet;
    private final XRow row;
    private int rowCount;
    private XIndexAccess parameters;
    private int firstParameterIndex = -1;
    private int columnCount;
    private final String[] columnNames;
    private final int[] columnTypes;

    public SDBCReportData(final XRowSet rowSet) throws SQLException
    {
        row = (XRow) UnoRuntime.queryInterface(XRow.class, rowSet);
        this.rowSet = rowSet;

        if (rowSet == null)
        {
            rowCount = 0;
            columnCount = 0;
            columnTypes = new int[1];
            columnNames = new String[1];
        }
        else
        {
            final XParametersSupplier xSuppParams = (XParametersSupplier) UnoRuntime.queryInterface(
                    XParametersSupplier.class, rowSet);
            if (xSuppParams != null)
            {
                parameters = xSuppParams.getParameters();
            }

            final XColumnsSupplier columnsSup = (XColumnsSupplier) UnoRuntime.queryInterface(XColumnsSupplier.class, rowSet);
            final XNameAccess columns = columnsSup.getColumns();
            final String[] columnNamesList = columns.getElementNames();
            final XResultSetMetaDataSupplier sup = (XResultSetMetaDataSupplier) UnoRuntime.queryInterface(XResultSetMetaDataSupplier.class, rowSet);
            final XResultSetMetaData resultSetMetaData = sup.getMetaData();

            columnCount = resultSetMetaData.getColumnCount();
            firstParameterIndex = columnCount + 1;
            if (parameters != null)
            {
                columnCount += parameters.getCount();
            }

            columnTypes = new int[columnCount];
            columnNames = new String[columnCount];

            for (int i = 1; i <= columnCount; ++i)
            {
                if (i < firstParameterIndex)
                {
                    columnNames[i - 1] = columnNamesList[i - 1];// resultSetMetaData.getColumnName(i);
                    columnTypes[i - 1] = resultSetMetaData.getColumnType(i);
                }
                else
                {
                    try
                    {
                        final XPropertySet paramColumn = (XPropertySet) UnoRuntime.queryInterface(
                                XPropertySet.class, parameters.getByIndex(i - firstParameterIndex));
                        columnNames[i - 1] = (String) paramColumn.getPropertyValue("Name");
                        columnTypes[i - 1] = (Integer) paramColumn.getPropertyValue("Type");
                    }
                    catch (Exception e)
                    {
                        columnNames[i - 1] = "Error";
                        columnTypes[i - 1] = DataType.CHAR;
                    }
                }
            }

            if (rowSet.last())
            {
                rowCount = rowSet.getRow();
                rowSet.beforeFirst();
            }
            else
            {
                rowCount = 0;
            }
        }
    }

    public int getColumnCount() throws DataSourceException
    {
        return columnCount;
    }

    public int getRowCount()
    {
        return rowCount;
    }

    public String getColumnName(final int column) throws DataSourceException
    {
        return columnNames[column - 1];
    }

    public boolean absolute(final int row) throws DataSourceException
    {
        if (rowSet == null)
        {
            return false;
        }
        try
        {
            if (row == 0)
            {
                rowSet.beforeFirst();
                return true;
            }
            return rowSet.absolute(row);
        }
        catch (SQLException e)
        {
            throw new DataSourceException(e.getMessage(), e);
        }
    }

    public boolean next() throws DataSourceException
    {
        if (rowSet == null)
        {
            return false;
        }
        try
        {
            return rowSet.next();
        }
        catch (SQLException e)
        {
            throw new DataSourceException(e.getMessage(), e);
        }
    }

    public void close() throws DataSourceException
    {
    }

//  public int getCurrentRow() throws DataSourceException
//  {
//    try
//    {
//      return rowSet.getRow();
//    }
//    catch (SQLException e)
//    {
//      throw new DataSourceException(e.getMessage(), e);
//    }
//  }
    static private java.sql.Date getDate(final Object obj)
    {
        final java.sql.Date date;
        if (obj instanceof com.sun.star.util.Date)
        {
            final com.sun.star.util.Date unodate = (com.sun.star.util.Date) obj;
            date = java.sql.Date.valueOf(getDateString(unodate.Year, unodate.Month, unodate.Day).toString());
        }
        else
        {
            date = null;
        }
        return date;
    }

    private static StringBuffer getTimeString(final int hours, final int minutes, final int seconds)
    {
        final StringBuffer timeString = new StringBuffer();
        if (hours < 10)
        {
            timeString.append('0');
        }
        timeString.append(hours);
        timeString.append(':');
        if (minutes < 10)
        {
            timeString.append('0');
        }
        timeString.append(minutes);
        timeString.append(':');
        if (seconds < 10)
        {
            timeString.append('0');
        }
        timeString.append(seconds);
        return timeString;
    }

    static private StringBuffer getDateString(final int years, final int months, final int days)
    {
        final StringBuffer str = new StringBuffer();
        str.append(years);
        final StringBuffer str2 = new StringBuffer("0000");
        str2.delete(0, str.length());
        str.insert(0, str2);
        str.append('-');
        if (months < 10)
        {
            str.append('0');
        }
        str.append(months);
        str.append('-');
        if (days < 10)
        {
            str.append('0');
        }
        str.append(days);
        return str;
    }

    static private java.sql.Time getTime(final Object obj)
    {
        final java.sql.Time time;
        if (obj instanceof Time)
        {
            final Time unoTime = (Time) obj;
            time = java.sql.Time.valueOf(getTimeString(unoTime.Hours, unoTime.Minutes, unoTime.Seconds).toString());
        }
        else
        {
            time = null;
        }
        return time;
    }

    static private Timestamp getTimestamp(final Object obj)
    {
        final Timestamp ts;
        if (obj instanceof DateTime)
        {
            final DateTime unoTs = (DateTime) obj;
            final StringBuffer str = getDateString(unoTs.Year, unoTs.Month, unoTs.Day);
            str.append(' ');
            str.append(getTimeString(unoTs.Hours, unoTs.Minutes, unoTs.Seconds));
            str.append('.');
            str.append(unoTs.HundredthSeconds);
            ts = java.sql.Timestamp.valueOf(str.toString());
        }
        else
        {
            ts = null;
        }
        return ts;
    }

    public Object getObject(final int column) throws DataSourceException
    {
        if (rowSet == null)
        {
            return null;
        }
        try
        {
            final boolean isParameterValue = (parameters != null) && (column >= firstParameterIndex);
            Object obj;
            final boolean wasNull;
            if (isParameterValue)
            {
                final XPropertySet paramCol = (XPropertySet) UnoRuntime.queryInterface(
                        XPropertySet.class, parameters.getByIndex(column - firstParameterIndex));
                obj = paramCol.getPropertyValue("Value");
                wasNull = obj == null;
            }
            else
            {
                obj = row.getObject(column, null);
                wasNull = row.wasNull();
            }

            if (wasNull)
            {
                obj = null;
            }
            else
            {
                obj = convertObject(columnTypes[column - 1], obj);
            }
            return obj;
        }
        catch (SQLException ex)
        {
            throw new DataSourceException(ex.getMessage(), ex);
        }
        catch (UnknownPropertyException ex)
        {
            throw new DataSourceException(ex.getMessage(), ex);
        }
        catch (IndexOutOfBoundsException ex)
        {
            throw new DataSourceException(ex.getMessage(), ex);
        }
        catch (WrappedTargetException ex)
        {
            throw new DataSourceException(ex.getMessage(), ex);
        }
    }

    private Object convertObject(final int type, final Object obj)
    {
        final Object ret;
        switch (type)
        {
            case DataType.DATE:
                ret = getDate(obj);
                break;
            case DataType.TIME:
                ret = getTime(obj);
                break;
            case DataType.TIMESTAMP:
                ret = getTimestamp(obj);
                break;
            case DataType.DECIMAL:
            case DataType.NUMERIC:
                if (!(obj instanceof Any))
                {
                    ret = new java.math.BigDecimal((String) obj);
                }
                else
                {
                    ret = obj;
                }
                break;
            default:
                ret = obj;
                break;
        }
        return ret;
    }
}
