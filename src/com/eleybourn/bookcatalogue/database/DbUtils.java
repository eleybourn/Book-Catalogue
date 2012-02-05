package com.eleybourn.bookcatalogue.database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbUtils.Synchronizer.LockTypes;
import com.eleybourn.bookcatalogue.database.DbUtils.Synchronizer.SyncLock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/**
 * Utilities and classes to make defining databases a little easier.
 * 
 * TODO: Implement foreign key support. Would need to be FK statements, not on columns. And stored on BOTH tables so can be recreated if table dropped.
 * TODO: Document more!
 * 
 * @author Grunthos
 */
public class DbUtils {
	/**
	 * Class to store domain name and definition.
	 * 
	 * @author Grunthos
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
		public String getDefinition(boolean withConstraints) {
			String s = name + " " + type + " " + extra;
			if (withConstraints)
				s += " " + constraint;
			return s;
		}
	}

	public static class JoinContext {
		TableDefinition currentTable;
		final StringBuilder sql;
		
		public JoinContext(TableDefinition table) {
			currentTable = table;
			sql = new StringBuilder();
		}
		public JoinContext join(TableDefinition to) {
			sql.append(currentTable.join(to));
			sql.append('\n');
			currentTable = to;
			return this;
		}
		public JoinContext join(TableDefinition from, TableDefinition to) {
			sql.append(from.join(to));
			sql.append('\n');
			currentTable = to;
			return this;
		}
		public JoinContext leftOuterJoin(TableDefinition to) {
			sql.append(" left outer ");
			return join(to);
		}
		public JoinContext leftOuterJoin(TableDefinition from, TableDefinition to) {
			sql.append(" left outer ");
			return join(from, to);
		}
		public JoinContext start() {
			sql.append( currentTable.getName() + " " + currentTable.getAlias() );
			return this;
		}
		public JoinContext append(String sql) {
			this.sql.append(sql);
			return this;
		}
		@Override
		public String toString() {
			return sql.toString();
		}
	}

	/**
	 * Class to store table name and a list of domain definitions.
	 * 
	 * @author Grunthos
	 */
	public static class TableDefinition {
		private String mName;
		private String mAlias;
		public ArrayList<DomainDefinition> domains = new ArrayList<DomainDefinition>();
		private HashSet<DomainDefinition> mDomainCheck = new HashSet<DomainDefinition>();
		private Hashtable<String, DomainDefinition> mDomainNameCheck = new Hashtable<String, DomainDefinition>();

		private ArrayList<DomainDefinition> mPrimaryKey = new ArrayList<DomainDefinition>();
		private Hashtable<TableDefinition, FkReference> mParents = new Hashtable<TableDefinition, FkReference>();
		private Hashtable<TableDefinition, FkReference> mChildren = new Hashtable<TableDefinition, FkReference>();

		Hashtable<String, IndexDefinition> mIndexes = new Hashtable<String, IndexDefinition>();
		private boolean mIsTemporary = false;

		public void close() {
			domains.clear();
			mDomainCheck.clear();
			mDomainNameCheck.clear();
			mPrimaryKey.clear();
			mIndexes.clear();

			for(Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
				FkReference fk = fkEntry.getValue();
				removeReference(fk.parent);
			}
			for(Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
				FkReference fk = fkEntry.getValue();
				fk.child.removeReference(this);
			}
		}
		public TableDefinition clone() {
			TableDefinition newTbl = new TableDefinition();
			newTbl.setName(mName);
			newTbl.setAlias(mAlias);
			newTbl.addDomains(domains);
			newTbl.setPrimaryKey(mPrimaryKey);
			newTbl.setIsTemporary(mIsTemporary);

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
				newTbl.addIndex(e.getKey(), i.unique, i.domains);
			}
			return newTbl;
		}
		
		public Collection<IndexDefinition> getIndexes() {
			return mIndexes.values();
		}
		private class FkReference {
			TableDefinition parent;
			TableDefinition child;
			ArrayList<DomainDefinition> domains;

			FkReference(TableDefinition parent, TableDefinition child, DomainDefinition...domains) {
				this.domains = new ArrayList<DomainDefinition>();
				for(DomainDefinition d: domains)
					this.domains.add(d);			
				this.parent = parent;				
				this.child = child;
			}
			FkReference(TableDefinition parent, TableDefinition child, ArrayList<DomainDefinition> domains) {
				this.domains = new ArrayList<DomainDefinition>();
				for(DomainDefinition d: domains)
					this.domains.add(d);			
				this.parent = parent;				
				this.child = child;
			}
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

		public TableDefinition(String name, DomainDefinition... domains) {
			this.mName = name;
			this.mAlias = name;
			this.domains = new ArrayList<DomainDefinition>();
			for(DomainDefinition d: domains)
				this.domains.add(d);
		}
		public String getName() {
			return mName;
		}
		public String getAlias() {
			if (mAlias == null || mAlias.equals("")) 
				return getName();
			else
				return mAlias;
		}
		public String dot(DomainDefinition d) {
			return getAlias() + "." + d.name;
		}
		public String dot(String s) {
			return getAlias() + "." + s;
		}
		public TableDefinition() {
			this.mName = "";
			this.domains = new ArrayList<DomainDefinition>();
		}
		public TableDefinition setName(String newName) {
			this.mName = newName;
			return this;
		}
		public TableDefinition setAlias(String newAlias) {
			mAlias = newAlias;
			return this;
		}
		public TableDefinition setPrimaryKey(DomainDefinition...domains) {
			mPrimaryKey.clear();
			for(DomainDefinition d: domains)
				mPrimaryKey.add(d);			
			return this;
		}
		public TableDefinition setPrimaryKey(ArrayList<DomainDefinition> domains) {
			mPrimaryKey.clear();
			for(DomainDefinition d: domains)
				mPrimaryKey.add(d);			
			return this;
		}
		public TableDefinition removeReference(TableDefinition parent) {
			mParents.remove(parent);
			parent.removeChild(this);
			return this;
		}
		public TableDefinition addReference(TableDefinition parent, DomainDefinition...domains) {
			FkReference fk = new FkReference(parent, this, domains);
			mParents.put(fk.parent, fk);
			parent.addChild(this, fk);
			return this;
		}
		public TableDefinition addReference(TableDefinition parent, ArrayList<DomainDefinition> domains) {
			FkReference fk = new FkReference(parent, this, domains);
			mParents.put(fk.parent, fk);
			parent.addChild(this, fk);
			return this;
		}
		public TableDefinition addChild(TableDefinition child, FkReference fk) {
			if (!mChildren.containsKey(child))
				mChildren.put(child, fk);
			return this;
		}
		public TableDefinition removeChild(TableDefinition child) {
			mChildren.remove(child);
			return this;
		}
		public TableDefinition addDomain(DomainDefinition domain) {
			if (mDomainCheck.contains(domain)) 
				return this;
			if (mDomainNameCheck.contains(domain.name.toLowerCase()))
				throw new RuntimeException("A domain with that name has already been added");
			domains.add(domain);
			mDomainCheck.add(domain);
			mDomainNameCheck.put(domain.name, domain);
			return this;
		}
		public TableDefinition addDomains(DomainDefinition... domains) {
			for(DomainDefinition d: domains)
				addDomain(d);			
			return this;
		}
		public TableDefinition addDomains(ArrayList<DomainDefinition> domains) {
			for(DomainDefinition d: domains)
				addDomain(d);			
			return this;
		}
		public TableDefinition addIndex(String localKey, boolean unique, DomainDefinition...domains) {
			if (mIndexes.containsKey(localKey))
				throw new RuntimeException("Index with local name '" + localKey + "' already defined");
			String name = this.mName + "_IX" + (mIndexes.size()+1);
			mIndexes.put(localKey, new IndexDefinition(name, unique, this, domains));
			return this;
		}
		public static void drop(SynchronizedDb db, String name) {
			db.execSQL("Drop Table If Exists " + name);
		}
		public TableDefinition drop(SynchronizedDb db) {
			drop(db, mName);
			return this;
		}
		public TableDefinition create(SynchronizedDb db, boolean withConstraints) {
			db.execSQL(this.getSql(mName, withConstraints, false));
			return this;
		}
		public TableDefinition createIfNecessary(SynchronizedDb db, boolean withConstraints) {
			db.execSQL(this.getSql(mName, withConstraints, true));
			return this;
		}
		public TableDefinition create(SynchronizedDb db, String name, boolean withConstraints) {
			db.execSQL(this.getSql(name, withConstraints, false));
			return this;
		}
		public String getInsert(DomainDefinition...domains) {
			StringBuilder s = new StringBuilder("Insert Into ");
			s.append(mName);
			s.append(" (");

			s.append(domains[0]);
			for(int i = 1; i < domains.length; i++) {
				s.append(",");
				s.append(domains[i].toString());
			}
			s.append(")");
			return s.toString();
		}
		public TableDefinition setIsTemporary(boolean flag) {
			mIsTemporary = flag;
			return this;
		}
		/** useful for using the TableDefinition in place of a table name */
		@Override
		public String toString() {
			return mName;
		}
		private String getSql(String name, boolean withConstraints, boolean ifNecessary) {
			StringBuilder sql = new StringBuilder("Create ");
			if (mIsTemporary)
				sql.append("Temporary ");

			sql.append("Table ");
			if (ifNecessary)
				sql.append("if not exists ");

			sql.append(name + " (\n");
			boolean first = true;
			for(DomainDefinition d : domains) {
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
		public String join(TableDefinition to) {
			FkReference fk;
			if (mChildren.containsKey(to)) {
				fk = mChildren.get(to);
			} else {
				fk = mParents.get(to);
			}
			if (fk == null)
				throw new RuntimeException("No foreign key between '" + mName + "' and '" + to.getName() + "'");

			return " join " + to.ref() + " On (" + fk.getPredicate() + ")";			
		}
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
		
		public ArrayList<DomainDefinition> getPrimaryKey() {
			return mPrimaryKey;
		}
		public String ref() {
			return mName + " " + getAlias();
		}
		public void createIndices(SynchronizedDb db) {
			for (IndexDefinition i : getIndexes()) {
				db.execSQL(i.getSql());
			}
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
		public IndexDefinition drop(SynchronizedDb db) {
			db.execSQL("Drop Index If Exists " + name);
			return this;
		}
		public IndexDefinition create(SynchronizedDb db) {
			db.execSQL(this.getSql());
			return this;
		}
		public String getSql() {
			int count;

			StringBuilder sql = new StringBuilder("Create ");
			if (unique)
				sql.append(" Unique");
			sql.append(" Index ");
			sql.append(this.name);
			sql.append(" on " + table.getName() + "(\n");
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
	public static void createTables(SynchronizedDb db, TableDefinition[] tables, boolean withConstraints) {
		for (TableDefinition t : tables) {
			t.create(db, withConstraints);
			for (IndexDefinition i : t.getIndexes()) {
				db.execSQL(i.getSql());
			}
		}
	}

	/**
	 * Crude implementation of a Readers/Writer lock that is fully reentrant.
	 * 
	 * Because SQLite throws exception on locking conflicts, this class can be used to serialize WRITE
	 * access while allowing concurrent read access.
	 * 
	 * Each logical database should have its own 'Synchronizer' and before any read, or group or reads, a call
	 * to getSharedLock() should be made. A call to getExclusiveLock() should be made before any update. Multiple
	 * calls can be made as necessary so long as a release() is called for all get*() calls by using the 
	 * SyncLock object returned from the get*() call.
	 * 
	 * These can be called in any order and locks in the current thread never block requests.
	 * 
	 * Deadlocks are not possible because the implementation involves a single lock object.
	 * 
	 * @author Grunthos
	 *
	 */
	public static class Synchronizer {
		private final ReentrantLock mLock = new ReentrantLock();
		private final Condition mReleased = mLock.newCondition();
		private final Hashtable<Thread,Integer> mSharedOwners = new Hashtable<Thread,Integer>();
		private final SharedLock mSharedLock = new SharedLock();
		private final ExclusiveLock mExclusiveLock = new ExclusiveLock();
	
		public enum LockTypes { shared, exclusive };

		public interface SyncLock {
			void unlock();
			LockTypes getType();
		}

		private class SharedLock implements SyncLock {
			@Override
			public void unlock() {
				releaseSharedLock();
			}
			@Override
			public LockTypes getType() {
				return LockTypes.shared;
			}
		}
		private class ExclusiveLock implements SyncLock {
			@Override
			public void unlock() {
				releaseExclusiveLock();
			}
			@Override
			public LockTypes getType() {
				return LockTypes.exclusive;
			}
		}

		private void purgeOldLocks() {
			if (!mLock.isHeldByCurrentThread())
				throw new RuntimeException("Can not cleanup old locks if not locked");

			Enumeration<Thread> it = mSharedOwners.keys();
			while( it.hasMoreElements() ) {
				Thread t = it.nextElement();
				if (!t.isAlive())
					mSharedOwners.remove(t);
			}
			
		}
		/**
		 * Add a new SharedLock to the collection and return it.
		 * 
		 * @return
		 */
		public SyncLock getSharedLock() {
			final Thread t = Thread.currentThread();
			//System.out.println(t.getName() + " requesting SHARED lock");
			mLock.lock();
			//System.out.println(t.getName() + " locked lock held by " + mLock.getHoldCount());
			purgeOldLocks();
			try {
				Integer count;
				if (mSharedOwners.containsKey(t)) {
					count = mSharedOwners.get(t) + 1;
				} else {
					count = 1;
				}
				mSharedOwners.put(t,count);
				//System.out.println(t.getName() + " " + count + " SHARED threads");
				return mSharedLock;
			} finally {
				mLock.unlock();
				//System.out.println(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
			}
		}
		public void releaseSharedLock() {
			final Thread t = Thread.currentThread();
			//System.out.println(t.getName() + " releasing SHARED lock");
			mLock.lock();
			//System.out.println(t.getName() + " locked lock held by " + mLock.getHoldCount());
			try {
				if (mSharedOwners.containsKey(t)) {
					Integer count = mSharedOwners.get(t) - 1;
					//System.out.println(t.getName() + " now has " + count + " SHARED locks");
					if (count < 0)
						throw new RuntimeException("Release a lock count already zero");
					if (count != 0) {
						mSharedOwners.put(t,count);
					} else {
						mSharedOwners.remove(t);
						mReleased.signal();
					}
				} else {
					throw new RuntimeException("Release a lock when not held");
				}
			} finally {
				mLock.unlock();
				//System.out.println(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
			}
		}

		/**
		 * Return when exclusive access is available.
		 * 
		 * - take a lock on the collection
		 * - see if there are any other locks
		 * - if not, return with the lock still held -- this prevents more EX or SH locks.
		 * - if there are other SH locks, wait for one to be release and loop.
		 * 
		 * @return
		 */
		public SyncLock getExclusiveLock() {
			final Thread t = Thread.currentThread();
			//long t0 = System.currentTimeMillis();
			// Synchronize with other code
			mLock.lock();
			try {
				int i = 0;
				while (true) {
					i++;
					// Cleanup any old threads that are dead.
					purgeOldLocks();
					//System.out.println(t.getName() + " requesting EXCLUSIVE lock with " + mSharedOwners.size() + " shared locks (attempt #" + i + ")");
					//System.out.println("Lock held by " + mLock.getHoldCount());
					try {
						// Simple case -- no locks held, just return and keep the lock
						if (mSharedOwners.size() == 0)
							return mExclusiveLock;
						// Check for one lock, and it being this thread.
						if (mSharedOwners.size() == 1 && mSharedOwners.containsValue(t)) {
							// One locker, and it is us...so upgrade is OK.
							return mExclusiveLock;
						}
						// Someone else has it. Wait.
						//System.out.println("Thread " + t.getName() + " waiting for DB access");
						mReleased.await();
					} catch (Exception e) {
						// Probably happens because thread was interrupted. Just die.
						try { mLock.unlock(); } catch(Exception e2) {};
						throw new RuntimeException("Unable to get exclusive lock", e);
					}
				}				
			} finally {
				//long t1 = System.currentTimeMillis();
				//if (mLock.isHeldByCurrentThread())
				//	System.out.println(t.getName() + " waited " + (t1 - t0) + "ms for EXCLUSIVE access");					
				//else
				//	System.out.println(t.getName() + " waited " + (t1 - t0) + "ms AND FAILED TO GET EXCLUSIVE access");				
			}
		}
		/**
		 * Release the lock previously taken
		 */
		public void releaseExclusiveLock() {
			final Thread t = Thread.currentThread();
			//System.out.println(t.getName() + " releasing EXCLUSIVE lock");
			if (!mLock.isHeldByCurrentThread())
				throw new RuntimeException("Exclusive Lock is not held by this thread");
			mLock.unlock();
			//System.out.println("Release lock held by " + mLock.getHoldCount());
			//System.out.println(t.getName() + " released EXCLUSIVE lock");
		}
	}
	
	public static class SynchronizedDb {
		final SQLiteDatabase mDb;
		final Synchronizer mSync;
		private SyncLock mTxLock = null;

		public SynchronizedDb(SQLiteDatabase db, Synchronizer sync) {
			mDb = db;
			mSync = sync;
		}

		public SynchronizedDb(SQLiteOpenHelper helper, Synchronizer sync) {
			mSync = sync;
			SyncLock l = mSync.getExclusiveLock();
			try {
				mDb = helper.getWritableDatabase();
			} finally {
				l.unlock();
			}				
		}

		public SynchronizedDb(GenericOpenHelper helper, Synchronizer sync) {
			mSync = sync;
			SyncLock l = mSync.getExclusiveLock();
			try {
				mDb = helper.getWritableDatabase();
			} finally {
				l.unlock();
			}				
		}
		
		public Cursor rawQuery(String sql, String [] selectionArgs) {
			SyncLock l = null;
			if (mTxLock == null)
				l = mSync.getSharedLock();

			try {
				return mDb.rawQuery(sql, selectionArgs);				
			} finally {
				if (l != null)
					l.unlock();
			}				
		}

		public Cursor rawQuery(String sql) {
			return rawQuery(sql, CatalogueDBAdapter.EMPTY_STRING_ARRAY);
		}

		public void execSQL(String sql) {
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Update inside shared TX");
				mDb.execSQL(sql);
			} else {
				SyncLock l = mSync.getExclusiveLock();
				try {
					mDb.execSQL(sql);
				} finally {
					l.unlock();
				}				
			}
		}

		public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
			SyncLock l = null;
			if (mTxLock == null)
				l = mSync.getSharedLock();

			try {
				return mDb.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);				
			} finally {
				if (l != null)
					l.unlock();
			}			
		}
		
		public long insert(String table, String nullColumnHack, ContentValues values) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Update inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return mDb.insert(table, nullColumnHack, values);				
			} finally {
				if (l != null)
					l.unlock();
			}
		}

		public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Update inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return mDb.update(table, values, whereClause, whereArgs);				
			} finally {
				if (l != null)
					l.unlock();
			}
		}

		public int delete(String table, String whereClause, String[] whereArgs) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Update inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return mDb.delete(table, whereClause, whereArgs);				
			} finally {
				if (l != null)
					l.unlock();
			}
		}

		public Cursor rawQueryWithFactory(SQLiteDatabase.CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable) {
			SyncLock l = null;
			if (mTxLock == null)
				l = mSync.getSharedLock();
			try {
				return mDb.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable);				
			} finally {
				if (l != null)
					l.unlock();
			}
		}

		public SQLiteStatement compileStatement(String sql) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Compile inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return mDb.compileStatement(sql);				
			} finally {
				if (l != null)
					l.unlock();
			}			
		}

		public SQLiteDatabase getUnderlyingDatabase() {
			return mDb;
		}
		
		public SyncLock beginTransaction(boolean isUpdate) {
			SyncLock l;
			if (isUpdate) {
				l = mSync.getExclusiveLock();
			} else {
				l = mSync.getSharedLock();
			}
			mDb.beginTransaction();
			mTxLock = l;
			return l;
		}
		public void endTransaction(SyncLock l) {
			if (mTxLock == null)
				throw new RuntimeException("Ending a transaction when none is started");
			if (!mTxLock.equals(l))
				throw new RuntimeException("Ending a transaction with wrong transaction lock");
				
			try {
				mDb.endTransaction();				
				mTxLock = null;
			} finally {
				l.unlock();
			}
		}
		public void setTransactionSuccessful() {
			mDb.setTransactionSuccessful();
		}

		public boolean isOpen() {
			return mDb.isOpen();
		}
		
		// TODO: Add SynchronzedSQLiteStatement
		// TODO: Add 'sync' calls to cursor movement...perhaps in TrackedCursor'
	}
}
