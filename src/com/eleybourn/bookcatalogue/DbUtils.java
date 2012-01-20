package com.eleybourn.bookcatalogue;

import java.util.Hashtable;

import android.database.sqlite.SQLiteDatabase;

/**
 * Utilities and classes to make defining databases a little easier.
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
	}

	/**
	 * Class to store table name and a list of domain definitions.
	 * 
	 * @author Grunthos
	 */
	public static class TableDefinition {
		String name;
		DomainDefinition[] domains;
		TableDefinition(String name, DomainDefinition...domains) {
			this.name = name;
			this.domains = domains;
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
		TableDefinition table;
		DomainDefinition[] domains;
		boolean unique;
		IndexDefinition(boolean unique, TableDefinition table, DomainDefinition...domains) {
			this.unique = unique;
			this.table = table;
			this.domains = domains;
		}
		public String getSql(Hashtable<String,Integer> names) {
			int count;
			if (names.containsKey(table.name)) {
				count = names.get(table.name) + 1;
			} else {
				count = 1;
			}
			names.put(table.name, count);

			StringBuilder sql = new StringBuilder("Create ");
			if (unique)
				sql.append(" Unique");
			sql.append(" Index ");
			sql.append(table.name);
			sql.append("_IX");
			sql.append(count);
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
	public static void createDatabase(SQLiteDatabase db, TableDefinition[] tables, IndexDefinition[] indexes) {
		for (TableDefinition t : tables) {
			db.execSQL(t.getSql());
		}
		Hashtable<String, Integer> names = new Hashtable<String, Integer>();
		for (IndexDefinition i : indexes) {
			db.execSQL(i.getSql(names));
		}
	}

}
