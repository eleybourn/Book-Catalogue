package com.eleybourn.bookcatalogue;

import java.util.ArrayList;
import java.util.Hashtable;

import android.database.sqlite.SQLiteDatabase;

/**
 * Utilities and classes to make defining databases a little easier.
 * 
 * TODO: Implement foreign key support. Would need to be FK statements, not on columns. And stored on BOTH tables so can be recreated if table dropped.
 * 
 * @author Grunthos
 *
 */
public class DbUtils {
	/**
	 * Class to store domain name and definition.
	 * 
	 * @author Grunthos
	 */
	public static class DomainDefinition {
		String name;
		String definition;
		DomainDefinition(String name, String definition) {
			this.name = name;
			this.definition = definition;
		}
		/** useful for using the DomainDefinition in place of a domain name */
		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * Class to store table name and a list of domain definitions.
	 * 
	 * @author Grunthos
	 */
	public static class TableDefinition {
		String name;
		DomainDefinition[] domains;
		ArrayList<IndexDefinition> indexes = new ArrayList<IndexDefinition>();
		
		TableDefinition(String name, DomainDefinition... domains) {
			this.name = name;
			this.domains = domains;
		}
		public TableDefinition addIndex(boolean unique, DomainDefinition...domains) {
			String name = this.name + "_IX" + (indexes.size()+1);
			indexes.add(new IndexDefinition(name, unique, this, domains));
			return this;
		}
		/** useful for using the TableDefinition in place of a table name */
		@Override
		public String toString() {
			return name;
		}
		public String getSql() {
			StringBuilder sql = new StringBuilder("Create Table " + name + " (\n");
			boolean first = true;
			for(DomainDefinition d : domains) {
				if (first) {
					first = false;
				} else {
					sql.append(",\n");
				}
				sql.append("    ");
				sql.append(d.name);
				sql.append(" ");
				sql.append(d.definition);
			}
			sql.append(")\n");
			return sql.toString();
		}
	}

	/**
	 * Class to store an index using a table name and a list of domian definitions.
	 * 
	 * @author Grunthos
	 */
	public static class IndexDefinition {
		String name;
		TableDefinition table;
		DomainDefinition[] domains;
		boolean unique;
		IndexDefinition(String name, boolean unique, TableDefinition table, DomainDefinition...domains) {
			this.name = name;
			this.unique = unique;
			this.table = table;
			this.domains = domains;
		}
		public String getSql() {
			int count;

			StringBuilder sql = new StringBuilder("Create ");
			if (unique)
				sql.append(" Unique");
			sql.append(" Index ");
			sql.append(this.name);
			sql.append(" on " + table.name + "(\n");
			boolean first = true;
			for(DomainDefinition d : domains) {
				if (first) {
					first = false;
				} else {
					sql.append(",\n");
				}
				sql.append("    ");
				sql.append(d.name);
			}
			sql.append(")\n");
			return sql.toString();
		}
	}

	/**
	 * Given arrays of table and index definitions, create the database.
	 * 
	 * @param db		Blank database
	 * @param tables	Table list
	 * @param indexes	Index list
	 */
	public static void createTables(SQLiteDatabase db, TableDefinition[] tables) {
		for (TableDefinition t : tables) {
			db.execSQL(t.getSql());
			for (IndexDefinition i : t.indexes) {
				db.execSQL(i.getSql());
			}
		}
	}

}
