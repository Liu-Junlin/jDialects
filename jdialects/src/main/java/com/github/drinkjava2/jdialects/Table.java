/**
 * jDialects, a tiny SQL dialect tool 
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.drinkjava2.jdialects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.drinkjava2.hibernate.DDLFormatter;

/**
 * The platform-independent table model
 * 
 * @author Yong Zhu
 * @since 1.0.2
 */
public class Table {
	private static DialectLogger logger = DialectLogger.getLog(Table.class);

	/** The table name. */
	private String tableName;

	/** check constraint for table */
	private String check;

	/** Columns in this table, key is upper case of column name */
	private Map<String, Column> columns = new LinkedHashMap<>();

	public Table(String tableName) {
		this.tableName = tableName;
	}

	public Table check(String check) {
		this.check = check;
		return this;
	}

	public Column addColumn(String columnName) {
		DialectException.assureNotEmpty(columnName);
		Column column = new Column(columnName);
		columns.put(columnName.toUpperCase(), column);
		return column;
	}

	public Column getColumn(String columnName) {
		DialectException.assureNotEmpty(columnName);
		return columns.get(columnName.toUpperCase());
	}

	public String[] toCreateTableDDL(Dialect dialect, boolean formatDDL) {
		if (formatDDL) {
			String[] ddls = toCreateTableDDL(dialect);
			for (int i = 0; i < ddls.length; i++) {
				ddls[i] = DDLFormatter.format(ddls[i]);
			}
			return ddls;
		}
		return toCreateTableDDL(dialect);
	}

	public String[] toCreateTableDDL(Dialect dialect) {
		DDLFeatures features = dialect.ddlFeatures;

		StringBuilder buf = new StringBuilder();
		boolean hasPkey = false;
		String pkeys = "";

		// Reserved words check
		dialect.check(tableName);
		for (Column col : columns.values()) {
			dialect.check(col.getColumnName());
			dialect.check(col.getPkeyName());
			dialect.check(col.getUniqueConstraintName());
		}

		for (Column col : columns.values()) {
			// check if have PKEY
			if (col.getPkey()) {
				hasPkey = true;
				if (StrUtils.isEmpty(pkeys))
					pkeys = col.getColumnName();
				else
					pkeys += "," + col.getColumnName();
			}
		}
		// create table
		buf.append(hasPkey ? dialect.ddlFeatures.createTableString : dialect.ddlFeatures.createMultisetTableString)
				.append(" ").append(tableName).append(" (");

		for (Column c : columns.values()) {
			// column definition
			buf.append(c.getColumnName()).append(" ");

			// Identity
			if (c.getIdentity()) {
				if (!features.supportsIdentityColumns) {
					DialectException.throwEX("Unsupported identity setting for dialect \"" + dialect + "\" on column \""
							+ c.getColumnName() + "\" in table \"" + tableName);
				}

				if (features.hasDataTypeInIdentityColumn)
					buf.append(dialect.translateToDDLType(c.getColumnType(), c.getLengths()));
				buf.append(' ');
				if (Type.BIGINT.equals(c.getColumnType()))
					buf.append(features.identityColumnStringBigINT);
				else
					buf.append(features.identityColumnString);

			} else {
				buf.append(dialect.translateToDDLType(c.getColumnType(), c.getLengths()));

				// Default
				String defaultValue = c.getDefaultValue();
				if (defaultValue != null) {
					buf.append(" default ").append(defaultValue);
				}

				// Not null
				if (c.getNotNull())
					buf.append(" not null");
				else
					buf.append(features.nullColumnString);
			}

			// Check
			if (!StrUtils.isEmpty(c.getCheck())) {
				if (features.supportsColumnCheck)
					buf.append(" check (").append(c.getCheck()).append(")");
				else
					logger.warn("Ignore unsupported check setting for dialect \"" + dialect + "\" on column \""
							+ c.getColumnName() + "\" in table \"" + tableName + "\" with value: " + c.getCheck());
			}

			// Comments
			if (c.getComment() != null) {
				if (StrUtils.isEmpty(features.columnComment) && !features.supportsCommentOn)
					logger.warn("Ignore unsupported comment setting for dialect \"" + dialect + "\" on column \""
							+ c.getColumnName() + "\" in table \"" + tableName + "\" with value: " + c.getComment());
				else
					buf.append(StrUtils.replace(features.columnComment, "_COMMENT", c.getComment()));
			}

			buf.append(",");
		}
		// PKEY
		if (!StrUtils.isEmpty(pkeys)) {
			buf.append(" primary key (").append(pkeys).append("),");
		}

		// Table Check
		if (!StrUtils.isEmpty(check)) {
			if (features.supportsTableCheck)
				buf.append(" check (").append(check).append("),");
			else
				logger.warn("Ignore unsupported table check setting for dialect \"" + dialect + "\" on table \""
						+ tableName + "\" with value: " + check);
		}

		buf.setLength(buf.length() - 1);
		buf.append(")");

		// type or engine for MariaDB & MySql
		buf.append(dialect.engine());
		buf.append(";");

		List<String> resultList = new ArrayList<>();
		resultList.add(buf.toString());

		// add unique constraint
		for (Column column : columns.values()) {
			String uniqueDDL = DDLUtils.getAddUniqueConstraint(dialect, tableName, column);
			if (!StrUtils.isEmpty(uniqueDDL))
				resultList.add(uniqueDDL);
		}

		// add comment on
		for (Column c : columns.values()) {
			if (features.supportsCommentOn && c.getComment() != null && StrUtils.isEmpty(features.columnComment)) {
				buf.append("comment on column ").append(tableName).append('.').append(c.getColumnName()).append(" is '")
						.append(c.getComment()).append("';");
			}
		}

		return resultList.toArray(new String[resultList.size()]);
	}

//	protected void applyTableCommentOn(DDLFeatures features, Table table) {
//		if (features.supportsCommentOn) {
//			if (table.getComment() != null) {
//				sqlStrings.add("comment on table " + tableName + " is '" + table.getComment() + "'");
//			}
//			 
//	}

	// getter & setter=========================
	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getCheck() {
		return check;
	}

	public void setCheck(String check) {
		this.check = check;
	}

	public Map<String, Column> getColumns() {
		return columns;
	}

	public void setColumns(Map<String, Column> columns) {
		this.columns = columns;
	}
}
