/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map.Entry;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;

/**
 * Utilities and classes to make defining databases a little easier and provide synchronization across threads.
 * 
 * @author Philip Warner
 */
public class DbUtils {
	/**
	 * Class to store domain name and definition.
	 * 
	 * @author Philip Warner
	 */
	public static class DomainDefinition {
		public String name;
		public String type;
		public String extra;
		public String constraint;
		public DomainDefinition(String name, String type, String extra, String constraint) {
			this.name = name;
			this.type = type;
			this.extra = extra;
			this.constraint = constraint;
		}
		/** useful for using the DomainDefinition in place of a domain name */
		@Override
		public String toString() {
			return name;
		}
		/** Get the SQL used to define this domain */
		public String getDefinition(boolean withConstraints) {
			String s = name + " " + type + " " + extra;
			if (withConstraints)
				s += " " + constraint;
			return s;
		}
	}

	/**
	 * Class used to build complex joins. Maintaing context and uses foreign keys
	 * to automatically build standard joins.
	 * 
	 * @author Philip Warner
	 */
	public static class JoinContext {
		/** Last table added to join */
		TableDefinition currentTable;
		/** Text of join statement */
		final StringBuilder sql;

		/**
		 * Constructor.
		 * 
		 * @param table		Table that starts join
		 */
		public JoinContext(TableDefinition table) {
			currentTable = table;
			sql = new StringBuilder();
		}
		/**
		 * Add a new table to the join, connecting it to previous table using foreign keys 
		 * 
		 * @param to		New table to add
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext join(TableDefinition to) {
			sql.append(currentTable.join(to));
			sql.append('\n');
			currentTable = to;
			return this;
		}
		/**
		 * Add a new table to the join, connecting it to 'from' using foreign keys 
		 * 
		 * @param from		Parent table in join
		 * @param to		New table to join
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext join(TableDefinition from, TableDefinition to) {
			sql.append(from.join(to));
			sql.append('\n');
			currentTable = to;
			return this;
		}
		/**
		 * Same as 'join', but do a 'left outer' join.
		 * 
		 * @param to		New table to add.
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext leftOuterJoin(TableDefinition to) {
			sql.append(" left outer ");
			return join(to);
		}
		/**
		 * Same as 'join', but do a 'left outer' join.
		 * 
		 * @param from		Parent table in join
		 * @param to		New table to join
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext leftOuterJoin(TableDefinition from, TableDefinition to) {
			sql.append(" left outer ");
			return join(from, to);
		}
		/**
		 * Begin building the join using the current table.
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext start() {
			sql.append( currentTable.getName() + " " + currentTable.getAlias() );
			return this;
		}
		/**
		 * Append arbitrary text to the generated SQL. Useful for adding extra conditions
		 * to a join clause.
		 * 
		 * @param sql		Extra SQL to append
		 * 
		 * @return			Join object (for chaining)
		 */
		public JoinContext append(String sql) {
			this.sql.append(sql);
			return this;
		}
		/**
		 * Get the current SQL
		 */
		@Override
		public String toString() {
			return sql.toString();
		}
	}

	/**
	 * Class to store table name and a list of domain definitions.
	 * 
	 * @author Philip Warner
	 */
	public static class TableDefinition {
		public enum TableTypes { Standard, Temporary, FTS3, FTS4 }

        /** Table name */
		private String mName;
		/** Table alias */
		private String mAlias;
		/** List of domains in this table */
		private ArrayList<DomainDefinition> mDomains = new ArrayList<DomainDefinition>();
		/** Used for checking if a domain has already been added */ 
		private final HashSet<DomainDefinition> mDomainCheck = new HashSet<DomainDefinition>();
		/** Used for checking if a domain NAME has already been added */
		private final Hashtable<String, DomainDefinition> mDomainNameCheck = new Hashtable<String, DomainDefinition>();

		/** List of domains forming primary key */
		private final ArrayList<DomainDefinition> mPrimaryKey = new ArrayList<DomainDefinition>();
		/** List of parent tables (tables referred to by foreign keys on this table) */
		private final Hashtable<TableDefinition, FkReference> mParents = new Hashtable<TableDefinition, FkReference>();
		/** List of child tables (tables referring to by foreign keys to this table) */
		private final Hashtable<TableDefinition, FkReference> mChildren = new Hashtable<TableDefinition, FkReference>();
		/** List of index definitions for this table */
		Hashtable<String, IndexDefinition> mIndexes = new Hashtable<String, IndexDefinition>();
		/** Flag indicating table is temporary */
		private TableTypes mType = TableTypes.Standard;

		/**
		 * Accessor. Return list of domains.
		 * 
		 * @return
		 */
		public ArrayList<DomainDefinition> getDomains() {
			return mDomains;
		}

		/**
		 * Remove all references and resources used by this table.
		 */
		public void close() {
			mDomains.clear();
			mDomainCheck.clear();
			mDomainNameCheck.clear();
			mPrimaryKey.clear();
			mIndexes.clear();

			// Need to make local copies to avoid 'collection modified' errors
			ArrayList<TableDefinition> tmpParents = new ArrayList<TableDefinition>(); 
			for(Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
				FkReference fk = fkEntry.getValue();
				tmpParents.add(fk.parent);
			}
			for(TableDefinition parent: tmpParents) {
				removeReference(parent);
			}

			// Need to make local copies to avoid 'collection modified' errors
			ArrayList<TableDefinition> tmpChildren = new ArrayList<TableDefinition>(); 
			for(Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
				FkReference fk = fkEntry.getValue();
				tmpChildren.add(fk.child);
			}
			for(TableDefinition child: tmpChildren) {
				child.removeReference(this);
			}
		}
		/**
		 * Make a copy of this table.
		 */
		public TableDefinition clone() {
			TableDefinition newTbl = new TableDefinition();
			newTbl.setName(mName);
			newTbl.setAlias(mAlias);
			newTbl.addDomains(mDomains);
			newTbl.setPrimaryKey(mPrimaryKey);
			newTbl.setType(mType);

			for(Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
				FkReference fk = fkEntry.getValue();
				newTbl.addReference(fk.parent, fk.domains);
			}
			for(Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
				FkReference fk = fkEntry.getValue();
				fk.child.addReference(newTbl, fk.domains);
			}
			for(Entry<String, IndexDefinition> e: mIndexes.entrySet()) {
				IndexDefinition i = e.getValue();
				newTbl.addIndex(e.getKey(), i.getUnique(), i.getDomains());
			}
			return newTbl;
		}

		/**
		 * Accessor. Get indexes on this table.
		 * 
		 * @return
		 */
		public Collection<IndexDefinition> getIndexes() {
			return mIndexes.values();
		}
		
		/**
		 * Class used to represent a foreign key reference
		 * 
		 * @author Philip Warner
		 */
		private class FkReference {
			/** Owner of primary key in FK reference */
			TableDefinition parent;
			/** Table owning FK */
			TableDefinition child;
			/** Domains in the FK that reference the parent PK */
			ArrayList<DomainDefinition> domains;

			/**
			 * Constructor.
			 * 
			 * @param parent	Parent table (one with PK that FK references)
			 * @param child		Child table (owner of the FK)
			 * @param domains	Domains in child table that reference PK in parent
			 */
			FkReference(TableDefinition parent, TableDefinition child, DomainDefinition...domains) {
				this.domains = new ArrayList<DomainDefinition>();
                Collections.addAll(this.domains, domains);
				this.parent = parent;				
				this.child = child;
			}
			/**
			 * Constructor
			 * 
			 * @param parent	Parent table (one with PK that FK references)
			 * @param child		Child table (owner of the FK)
			 * @param domains	Domains in child table that reference PK in parent
			 */
			FkReference(TableDefinition parent, TableDefinition child, ArrayList<DomainDefinition> domains) {
				this.domains = new ArrayList<DomainDefinition>();
				for(DomainDefinition d: domains)
					this.domains.add(d);			
				this.parent = parent;				
				this.child = child;
			}
			/**
			 * Get an SQL fragment that matches the PK of the parent to the FK of the child.
			 * eg. 'org.id = emp.organization_id' (but handles multi-domain keys)
			 * 
			 * @return	SQL fragment
			 */
			public String getPredicate() {
				ArrayList<DomainDefinition> pk = parent.getPrimaryKey();
				StringBuilder sql = new StringBuilder();
				for(int i = 0; i < pk.size(); i++) {
					if (i > 0)
						sql.append(" and ");
					sql.append(parent.getAlias());
					sql.append(".");
					sql.append(pk.get(i).name);
					sql.append(" = ");
					sql.append(child.getAlias());
					sql.append(".");
					sql.append(domains.get(i).name);
				}
				return sql.toString();
			}
		}

		/**
		 * Constructor
		 * 
		 * @param name		Table name
		 * @param domains	List of domains in table
		 */
		public TableDefinition(String name, DomainDefinition... domains) {
			this.mName = name;
			this.mAlias = name;
			this.mDomains = new ArrayList<DomainDefinition>();
            Collections.addAll(this.mDomains, domains);
		}

		/**
		 * Constructor (empty table)
		 */
		public TableDefinition() {
			this.mName = "";
			this.mDomains = new ArrayList<DomainDefinition>();
		}

		/**
		 * Accessor. Get the table name.
		 * @return
		 */
		public String getName() {
			return mName;
		}

		/**
		 * Accessor. Get the table alias, or if blank, return the table name.
		 * 
		 * @return		Alias
		 */
		public String getAlias() {
			if (mAlias == null || mAlias.equals("")) 
				return getName();
			else
				return mAlias;
		}

		/**
		 * Utility routine to return <table-alias>.<domain-name>.
		 * 
		 * @param d		Domain
		 * 
		 * @return	SQL fragment
		 */
		public String dot(DomainDefinition d) {
			return getAlias() + "." + d.name;
		}

		/**
		 * Utility routine to return [table-alias].[domain-name] as [domain_name]; this format
		 * is useful in older SQLite installations that add make the alias part of the output
		 * column name.
		 * 
		 * @param d		Domain
		 * 
		 * @return	SQL fragment
		 */
		public String dotAs(DomainDefinition d) {
			return getAlias() + "." + d.name + " as " + d.name;
		}

		/**
		 * Utility routine to return [table-alias].[domain-name] as [asDomain]; this format
		 * is useful when multiple differing versions of a domain are retrieved.
		 * 
		 * @param d		Domain
		 * 
		 * @return	SQL fragment
		 */
		public String dotAs(DomainDefinition d, DomainDefinition asDomain) {
			return getAlias() + "." + d.name + " as " + asDomain.name;
		}

		/**
		 * Utility routine to return <table-alias>.<name>.
		 * 
		 * @param s		Domain name
		 * 
		 * @return	SQL fragment
		 */
		public String dot(String s) {
			return getAlias() + "." + s;
		}

		/**
		 * Set the table name. Useful for cloned tables.
		 * 
		 * @param newName	New table name
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition setName(String newName) {
			this.mName = newName;
			return this;
		}

		/**
		 * Set the table alias. Useful for cloned tables.
		 * 
		 * @param newAlias	New table alias
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition setAlias(String newAlias) {
			mAlias = newAlias;
			return this;
		}
		
		/**
		 * Set the primary key domains
		 * 
		 * @param domains	List of domains in PK
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition setPrimaryKey(DomainDefinition...domains) {
			mPrimaryKey.clear();
            Collections.addAll(mPrimaryKey, domains);
			return this;
		}

		/**
		 * Set the primary key domains
		 * 
		 * @param domains	List of domains in PK
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition setPrimaryKey(ArrayList<DomainDefinition> domains) {
			mPrimaryKey.clear();
			for(DomainDefinition d: domains)
				mPrimaryKey.add(d);			
			return this;
		}

		/**
		 * Common code to add a foreign key (FK) references to another (parent) table.
		 * 
		 * @param fk			The FK object
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		private TableDefinition addReference(FkReference fk) {
			if (fk.child != this)
				throw new RuntimeException("Foreign key does not include this table as child");
			mParents.put(fk.parent, fk);
			fk.parent.addChild(this, fk);
			return this;
		}

		/**
		 * Add a foreign key (FK) references to another (parent) table.
		 * 
		 * @param parent		The referenced table
		 * @param domains		Domains in this table that reference Primary Key (PK) in parent
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addReference(TableDefinition parent, DomainDefinition...domains) {
			FkReference fk = new FkReference(parent, this, domains);
			return addReference(fk);
		}
		/**
		 * Add a foreign key (FK) references to another (parent) table.
		 * 
		 * @param parent		The referenced table
		 * @param domains		Domains in this table that reference Primary Key (PK) in parent
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addReference(TableDefinition parent, ArrayList<DomainDefinition> domains) {
			FkReference fk = new FkReference(parent, this, domains);
			return addReference(fk);
		}

		/**
		 * Remove FK reference to parent table
		 * 
		 * @param parent	The referenced Table
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition removeReference(TableDefinition parent) {
			mParents.remove(parent);
			parent.removeChild(this);
			return this;
		}

		/**
		 * Add a child table reference to this table.
		 * 
		 * @param child		Child table
		 * @param fk		FK object
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		private TableDefinition addChild(TableDefinition child, FkReference fk) {
			if (!mChildren.containsKey(child))
				mChildren.put(child, fk);
			return this;
		}
		/**
		 * Remove a child FK reference from this table.
		 * 
		 * @param child		Child table
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		private TableDefinition removeChild(TableDefinition child) {
			mChildren.remove(child);
			return this;
		}
		
		/**
		 * Add a domain to this table
		 * 
		 * @param domain	Domain object to add
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addDomain(DomainDefinition domain) {
			// Make sure it's not already in the table
			if (mDomainCheck.contains(domain)) 
				return this;
			// Make sure one with same name is not already in table
			if (mDomainNameCheck.contains(domain.name.toLowerCase()))
				throw new RuntimeException("A domain with that name has already been added");
			// Add it
			mDomains.add(domain);
			mDomainCheck.add(domain);
			mDomainNameCheck.put(domain.name, domain);
			return this;
		}

		/**
		 * Add a list of domains to this table.
		 * 
		 * @param domains	List of domains to add
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addDomains(DomainDefinition... domains) {
			for(DomainDefinition d: domains)
				addDomain(d);			
			return this;
		}
		/**
		 * Add a list of domains to this table.
		 * 
		 * @param domains	List of domains to add
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addDomains(ArrayList<DomainDefinition> domains) {
			for(DomainDefinition d: domains)
				addDomain(d);			
			return this;
		}
		
		/**
		 * Add an index to this table
		 * 
		 * @param localKey	Local name, unique for this table, to give this index. Alphanumeric Only.
		 * @param unique	FLag indicating index is UNIQUE
		 * @param domains	List of domains index
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition addIndex(String localKey, boolean unique, DomainDefinition...domains) {
			// Make sure not already defined
			if (mIndexes.containsKey(localKey))
				throw new RuntimeException("Index with local name '" + localKey + "' already defined");
			// Construct the full index name
			String name = this.mName + "_IX" + (mIndexes.size()+1) + "_" + localKey;
			mIndexes.put(localKey, new IndexDefinition(name, unique, this, domains));
			return this;
		}

		/**
		 * Static method to drop the passed table, if it exists.
		 * 
		 * @param db
		 * @param name
		 */
		public static void drop(SynchronizedDb db, String name) {
			db.execSQL("Drop Table If Exists " + name);
		}

		/**
		 * Drop this table from the passed DB.
		 * 
		 * @param db
		 * @return
		 */
		public TableDefinition drop(SynchronizedDb db) {
			drop(db, mName);
			return this;
		}
		/**
		 * Create this table.
		 * 
		 * @param db					Database in which to create table
		 * @param withConstraints		Indicates if fields should have constraints applied
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition create(SynchronizedDb db, boolean withConstraints) {
			db.execSQL(this.getSql(mName, withConstraints, false));
			return this;
		}
		
		/**
		 * Create this table and related objects (indices).
		 * 
		 * @param db					Database in which to create table
		 * @param withConstraints		Indicates if fields should have constraints applied
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition createAll(SynchronizedDb db, boolean withConstraints) {
			db.execSQL(this.getSql(mName, withConstraints, false));
			createIndices(db);
			return this;
		}
		/**
		 * Create this table if it is not already present.
		 * 
		 * @param db					Database in which to create table
		 * @param withConstraints		Indicates if fields should have constraints applied
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition createIfNecessary(SynchronizedDb db, boolean withConstraints) {
			db.execSQL(this.getSql(mName, withConstraints, true));
			return this;
		}
		
		/**
		 * Get a base INSERT statement for this table using the passed list of domains. Returns partial
		 * SQL of the form: 'INSERT into [table-name] ( [domain-list] )'.
		 * 
		 * @param domains		List of domains to use
		 * 
		 * @return	SQL fragment
		 */
		public String getInsert(DomainDefinition...domains) {
			StringBuilder s = new StringBuilder("Insert Into ");
			s.append(mName);
			s.append(" (\n");

			s.append("	");
			s.append(domains[0]);
			for(int i = 1; i < domains.length; i++) {
				s.append(",\n	");
				s.append(domains[i].toString());
			}
			s.append(")");
			return s.toString();
		}

		/**
		 * Get a base list of fields for this table using the passed list of domains. Returns partial
		 * SQL of the form: '[alias].[domain-1], ..., [alias].[domain-n]'.
		 * 
		 * @param domains		List of domains to use
		 * 
		 * @return	SQL fragment
		 */
		public String ref(DomainDefinition...domains) {
			if (domains == null || domains.length == 0)
				return "";

			final String aliasDot = getAlias() + ".";
			final StringBuilder s = new StringBuilder(aliasDot);
			s.append(domains[0].name);

			for(int i = 1; i < domains.length; i++) {
				s.append(",\n");
				s.append(aliasDot);
				s.append(domains[i].name);
			}
			return s.toString();
		}

		/**
		 * Get a base UPDATE statement for this table using the passed list of domains. Returns partial
		 * SQL of the form: 'UPDATE [table-name] Set [domain-1] = ?, ..., [domain-n] = ?'.
		 * 
		 * @param domains		List of domains to use
		 * 
		 * @return	SQL fragment
		 */
		public String getUpdate(DomainDefinition...domains) {
			StringBuilder s = new StringBuilder("Update ");
			s.append(mName);
			s.append(" Set\n");

			s.append("	");
			s.append(domains[0]);
			s.append(" = ?");
			for(int i = 1; i < domains.length; i++) {
				s.append(",\n	");
				s.append(domains[i].toString());
				s.append(" = ?");
			}
			s.append("\n");
			return s.toString();
		}

		/**
		 * Get a base 'INSERT or REPLACE' statement for this table using the passed list of domains. Returns partial
		 * SQL of the form: 'INSERT or REPLACE INTO [table-name] ( [domain-1] ) Values (?, ..., ?)'.
		 * 
		 * @param domains		List of domains to use
		 * 
		 * @return	SQL fragment
		 */
		public String getInsertOrReplaceValues(DomainDefinition...domains) {
			StringBuilder s = new StringBuilder("Insert or Replace Into ");
			StringBuilder sPlaceholders = new StringBuilder("?");
			s.append(mName);
			s.append(" ( ");
			s.append(domains[0]);

			for(int i = 1; i < domains.length; i++) {
				s.append(", ");
				s.append(domains[i].toString());
				
				sPlaceholders.append(", ?");
			}
			s.append(")\n	values (");
			s.append(sPlaceholders);
			s.append(")\n");
			return s.toString();
		}

		/**
		 * Setter. Set flag indicating table is a TEMPORARY table.
		 * 
		 * @param flag		Flag
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition setType(TableTypes type) {
			mType = type;
			return this;
		}

		/** useful for using the TableDefinition in place of a table name */
		@Override
		public String toString() {
			return mName;
		}
		
		/**
		 * Return the SQL that can be used to define this table.
		 * 
		 * @param name				Name to use for table
		 * @param withConstraints	Flag indicating domian constraints should be applied
		 * @param ifNecessary		Flag indicating if creation should not be done if table exists
		 * 
		 * @return	SQL to create table
		 */
		private String getSql(String name, boolean withConstraints, boolean ifNecessary) {
			StringBuilder sql = new StringBuilder("Create ");
			switch(mType) {
			case Standard:
				break;
			case FTS3:
			case FTS4:
				sql.append("Virtual ");
				break;
			case Temporary:
				sql.append("Temporary ");
				break;
			}

			sql.append("Table ");
			if (ifNecessary) {
				if (mType == TableTypes.FTS3 || mType == TableTypes.FTS4)
					throw new RuntimeException("'if not exists' can not be used when creating virtual tables");
				sql.append("if not exists ");
			}

			sql.append(name);

			if (mType == TableTypes.FTS3) {
				sql.append(" USING fts3");
			} else if (mType == TableTypes.FTS3) {
				sql.append(" USING fts4");				
			}
			
			sql.append(" (\n");
			boolean first = true;
			for(DomainDefinition d : mDomains) {
				if (first) {
					first = false;
				} else {
					sql.append(",\n");
				}
				sql.append("    ");
				sql.append(d.getDefinition(withConstraints));
			}
			sql.append(")\n");
			return sql.toString();
		}
		
		/**
		 * Utility code to return a join and condition from this table to another using foreign keys.
		 * 
		 * @param to	Table this table will be joined with
		 * 
		 * @return	SQL fragment (eg. 'join [to-name] [to-alias] On [pk/fk match]')
		 */
		public String join(TableDefinition to) {
			return " join " + to.ref() + " On (" + fkMatch(to) + ")";			
		}
		
		/**
		 * Return the FK condition that applies between this table and the 'to' table
		 * 
		 * @param to	Table that is other part of FK/PK
		 * 
		 * @return	SQL fragment (eg. <to-alias>.<to-pk> = <from-alias>.<from-pk>').
		 */
		public String fkMatch(TableDefinition to) {
			FkReference fk;
			if (mChildren.containsKey(to)) {
				fk = mChildren.get(to);
			} else {
				fk = mParents.get(to);
			}
			if (fk == null)
				throw new RuntimeException("No foreign key between '" + this.getName() + "' and '" + to.getName() + "'");

			return fk.getPredicate();			
		}

		/**
		 * Accessor. Get the domains forming the PK of this table.
		 * 
		 * @return	Domain List
		 */
		public ArrayList<DomainDefinition> getPrimaryKey() {
			return mPrimaryKey;
		}
		
		/**
		 * Utility routine to return an SQL fragment of the form '<table-name> <table-alias>', eg. 'employees e'.
		 * 
		 * @return	SQL Fragment
		 */
		public String ref() {
			return mName + " " + getAlias();
		}
		
		/**
		 * Create all indices defined for this table.
		 * 
		 * @param db	Database to use
		 * 
		 * @return	TableDefinition (for chaining)
		 */
		public TableDefinition createIndices(SynchronizedDb db) {
			for (IndexDefinition i : getIndexes()) {
				db.execSQL(i.getSql());
			}
			return this;
		}

		final static String mExistsSql = "Select (SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?) + "
										+ "(SELECT count(*) FROM sqlite_temp_master WHERE type='table' AND name=?)";
		/**
		 * Check if the table exists within the passed DB
		 */
		public boolean exists(SynchronizedDb db) {
			SynchronizedStatement stmt = db.compileStatement(mExistsSql);
			try {
				stmt.bindString(1, getName());
				stmt.bindString(2, getName());
				return (stmt.simpleQueryForLong() > 0);				
			} finally {
				stmt.close();
			}
		}

	}

	/**
	 * Class to store an index using a table name and a list of domian definitions.
	 * 
	 * @author Philip Warner
	 */
	public static class IndexDefinition {
		/** Full name of index */
		private final String mName;
		/** Table to which index applies */
		private final TableDefinition mTable;
		/** Domains in index */
		private final DomainDefinition[] mDomains;
		/** Flag indicating index is unique */
		private final boolean mIsUnique;

		/**
		 * Constructor.
		 * 
		 * @param name		name of index
		 * @param unique	Flag indicating index is unique
		 * @param table		Table to which index applies
		 * @param domains	Domains in index
		 */
		IndexDefinition(String name, boolean unique, TableDefinition table, DomainDefinition...domains) {
			this.mName = name;
			this.mIsUnique = unique;
			this.mTable = table;
			this.mDomains = domains;
		}
		/**
		 * Accessor. Get UNIQUE flag.
		 * 
		 * @return
		 */
		public boolean getUnique() {
			return mIsUnique;
		}
		/**
		 * Accessor. Get list of domains in index.
		 *
		 * @return
		 */
		public DomainDefinition[] getDomains() {
			return mDomains;
		}
		/**
		 * Drop the index, if it exists.
		 * 
		 * @param db	Database to use.
		 * 
		 * @return	IndexDefinition (for chaining)
		 */
		public IndexDefinition drop(SynchronizedDb db) {
			db.execSQL("Drop Index If Exists " + mName);
			return this;
		}
		/**
		 * Create the index.
		 * 
		 * @param db	Database to use.
		 * 
		 * @return	IndexDefinition (for chaining)
		 */
		public IndexDefinition create(SynchronizedDb db) {
			db.execSQL(this.getSql());
			return this;
		}
		/**
		 * Return the SQL used to define the index.
		 * 
		 * @return	SQL Fragment
		 */
		private String getSql() {
			StringBuilder sql = new StringBuilder("Create ");
			if (mIsUnique)
				sql.append(" Unique");
			sql.append(" Index ");
			sql.append(mName);
			sql.append(" on " + mTable.getName() + "(\n");
			boolean first = true;
			for(DomainDefinition d : mDomains) {
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
	public static void createTables(SynchronizedDb db, TableDefinition[] tables, boolean withConstraints) {
		for (TableDefinition t : tables) {
			t.create(db, withConstraints);
			for (IndexDefinition i : t.getIndexes()) {
				db.execSQL(i.getSql());
			}
		}
	}

}
