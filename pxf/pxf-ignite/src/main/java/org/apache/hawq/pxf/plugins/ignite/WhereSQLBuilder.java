package org.apache.hawq.pxf.plugins.ignite;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.hawq.pxf.api.LogicalFilter;
import org.apache.hawq.pxf.api.BasicFilter;
import org.apache.hawq.pxf.api.FilterParser;
import org.apache.hawq.pxf.api.io.DataType;
import org.apache.hawq.pxf.api.utilities.ColumnDescriptor;
import org.apache.hawq.pxf.api.utilities.InputData;

/**
 * Parse filter object generated by parent class  {@link org.apache.hawq.pxf.plugins.ignite.IgniteFilterBuilder},
 * and build WHERE statement.
 * For Multiple filters , currently only support HDOP_AND .
 * The unsupported Filter operation and  LogicalOperation ,will return null statement.
 *
 */
public class WhereSQLBuilder extends IgniteFilterBuilder {
    private InputData inputData;

    public WhereSQLBuilder(InputData input) {
        inputData = input;
    }

    /**
     * 1.check for LogicalOperator, Jdbc currently only support HDOP_AND.
     * 2.and convert to BasicFilter List.
     */
    private static List<BasicFilter> convertBasicFilterList(Object filter, List<BasicFilter> returnList) throws UnsupportedFilterException {
        if (returnList == null)
            returnList = new ArrayList<>();
        if (filter instanceof BasicFilter) {
            returnList.add((BasicFilter) filter);
            return returnList;
        }
        LogicalFilter lfilter = (LogicalFilter) filter;
        if (lfilter.getOperator() != FilterParser.LogicalOperation.HDOP_AND)
            throw new UnsupportedFilterException("unsupported LogicalOperation : " + lfilter.getOperator());
        for (Object f : lfilter.getFilterList()) {
            returnList = convertBasicFilterList(f, returnList);
        }
        return returnList;
    }

    public String buildWhereSQL() throws Exception {
        if (!inputData.hasFilter()) {
            return null;
        }
        List<BasicFilter> filters = null;
        try {
            String filterString = inputData.getFilterString();
            Object filterObj = getFilterObject(filterString);

            filters = convertBasicFilterList(filterObj, filters);
            StringBuffer sb = new StringBuffer("1=1");
            for (Object obj : filters) {
                BasicFilter filter = (BasicFilter) obj;
                sb.append(" AND ");

                ColumnDescriptor column = inputData.getColumn(filter.getColumn().index());
                //the column name of filter
                sb.append(column.columnName());

                //the operation of filter
                FilterParser.Operation op = filter.getOperation();
                switch (op) {
                    case HDOP_LT:
                        sb.append("<");
                        break;
                    case HDOP_GT:
                        sb.append(">");
                        break;
                    case HDOP_LE:
                        sb.append("<=");
                        break;
                    case HDOP_GE:
                        sb.append(">=");
                        break;
                    case HDOP_EQ:
                        sb.append("=");
                        break;
                    default:
                        throw new UnsupportedFilterException("unsupported Filter operation : " + op);
                }

                Object val = filter.getConstant().constant();
                switch (DataType.get(column.columnTypeCode())) {
                    case SMALLINT:
                    case INTEGER:
                    case BIGINT:
                    case FLOAT8:
                    case REAL:
                    case BOOLEAN:
                        sb.append(val.toString());
                        break;
                    case TEXT:
                        sb.append("'").append(val.toString()).append("'");
                        break;
                    case DATE:
                        sb.append("date'" + val.toString() + "'");
                        break;
                    default:
                        throw new UnsupportedFilterException("unsupported column type for filtering : " + column.columnTypeCode());
                }

            }
            return sb.toString();
        } catch (UnsupportedFilterException ex) {
            // Do not throw exception, impose no constraints instead
            return null;
        }
    }

    static class UnsupportedFilterException extends Exception {
        UnsupportedFilterException(String message) {
            super(message);
        }
    }
}
