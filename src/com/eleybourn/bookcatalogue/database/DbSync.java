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

import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteClosable;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteStatement;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.LockTypes;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * Classes used to help synchronize database access across threads.
 * 
 * @author Philip Warner
 */
public class DbSync {
	/**
	 * Implementation of a Readers/Writer lock that is fully reentrant.
	 * 
	 * Because SQLite throws exception on locking conflicts, this class can be used to serialize WRITE
	 * access while allowing concurrent read access.
	 * 
	 * Each logical database should have its own 'Synchronizer' and before any read, or group or reads, a call
	 * to getSharedLock() should be made. A call to getExclusiveLock() should be made before any update. Multiple
	 * calls can be made as necessary so long as an unlock() is called for all get*() calls by using the 
	 * SyncLock object returned from the get*() call.
	 * 
	 * These can be called in any order and locks in the current thread never block requests.
	 * 
	 * Deadlocks are not possible because the implementation involves a single lock object.
	 * 
	 * NOTE: This lock can cause writer starvation since it does not introduce pending locks.
	 * 
	 * @author Philip Warner
	 */
	public static class Synchronizer {
		/** Main lock for synchronization */
		private final ReentrantLock mLock = new ReentrantLock();
		/** Condition fired when a reader releases a lock */
		private final Condition mReleased = mLock.newCondition();
		/** Collection of threads that have shared locks */
		private final Hashtable<Thread,Integer> mSharedOwners = new Hashtable<Thread,Integer>();
		/** Lock used to pass back to consumers of shared locks */
		private final SharedLock mSharedLock = new SharedLock();
		/** Lock used to pass back to consumers of exclusive locks */
		private final ExclusiveLock mExclusiveLock = new ExclusiveLock();

		/** Enum of lock types supported */
		public enum LockTypes { shared, exclusive };

		/**
		 * Interface common to all lock types.
		 * 
		 * @author Philip Warner
		 */
		public interface SyncLock {
			void unlock();
			LockTypes getType();
		}

		/**
		 * Internal implementation of a Shared Lock.
		 * 
		 * @author Philip Warner
		 */
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
		/**
		 * Internal implementation of an Exclusive Lock.
		 * 
		 * @author Philip Warner
		 */
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

		/**
		 * Routine to purge shared locks held by dead threads. Can only be called 
		 * while mLock is held.
		 */
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
		/**
		 * Release a shared lock. If no more locks in thread, remove from list.
		 */
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
				while (true) {
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
			//final Thread t = Thread.currentThread();
			//System.out.println(t.getName() + " releasing EXCLUSIVE lock");
			if (!mLock.isHeldByCurrentThread())
				throw new RuntimeException("Exclusive Lock is not held by this thread");
			mLock.unlock();
			//System.out.println("Release lock held by " + mLock.getHoldCount());
			//System.out.println(t.getName() + " released EXCLUSIVE lock");
		}
	}
	
	/**
	 * Database wrapper class that performs thread synchronization on all operations.
	 * 
	 * @author Philip Warner
	 */
	public static class SynchronizedDb {
		/** Underlying database */
		final SQLiteDatabase mDb;
		/** Sync object to use */
		final Synchronizer mSync;
		/** Currently held transaction lock, if any */
		private SyncLock mTxLock = null;

		/**
		 * Constructor. Use of this method is not recommended. It is better to use
		 * the methods that take a DBHelper object since opening the database may block
		 * another thread, or vice versa.
		 * 
		 * @param db		Underlying database
		 * @param sync		Synchronizer to use
		 */
		public SynchronizedDb(SQLiteDatabase db, Synchronizer sync) {
			mDb = db;
			mSync = sync;
		}

		/**
		 * Interface to an object that can return an open SQLite database object
		 * 
		 * @author pjw
		 */
		private interface DbOpener {
			SQLiteDatabase open();
		}

		/**
		 * Call the passed database opener with retries to reduce risks of access conflicts 
		 * causing crashes.
		 * 
		 * @param opener	DbOpener interface
		 * 
		 * @return			The opened database
		 */
		private SQLiteDatabase openWithRetries(DbOpener opener) {
				int wait = 10; // 10ms
				//int retriesLeft = 5; // up to 320ms
				int retriesLeft = 10; // 2^10 * 10ms = 10.24sec (actually 2x that due to total wait time)
				SQLiteDatabase db = null;
				do {
					SyncLock l = mSync.getExclusiveLock();
					try {
						db = opener.open();	
						return db;
					} catch (Exception e) {
						if (l != null) {
							l.unlock();
							l = null;
						}
						if (retriesLeft == 0) {
							throw new RuntimeException("Unable to open database, retries exhausted", e);
						}
						try {
							Thread.sleep(wait);
							// Decrement tries
							retriesLeft--;
							// Wait longer next time
							wait *= 2;
						} catch (InterruptedException e1) {
							throw new RuntimeException("Unable to open database, interrupted", e1);							
						}
					} finally {
						if (l != null) {
							l.unlock();
							l = null;
						}
					}				
				} while (true);
			
		}
		/**
		 * Constructor.
		 * 
		 * @param helper	DBHelper to open underlying database
		 * @param sync		Synchronizer to use
		 */
		public SynchronizedDb(final SQLiteOpenHelper helper, Synchronizer sync) {
			mSync = sync;
			mDb = openWithRetries(new DbOpener() {
				@Override
				public SQLiteDatabase open() {
					return helper.getWritableDatabase();
				}});			
		}

		/**
		 * Constructor.
		 * 
		 * @param helper	DBHelper to open underlying database
		 * @param sync		Synchronizer to use
		 */
		public SynchronizedDb(final GenericOpenHelper helper, Synchronizer sync) {
			mSync = sync;
			mDb = openWithRetries(new DbOpener() {
				@Override
				public SQLiteDatabase open() {
					return helper.getWritableDatabase();
				}});			
		}
		
		/**
		 * Factory for Synchronized Cursor objects. This can be subclassed by other
		 * Cursor implementations.
		 * 
		 * @author Philip Warner
		 */
		public class SynchronizedCursorFactory implements CursorFactory {
			@Override
			public SynchronizedCursor newCursor(SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, String editTable,
					SQLiteQuery query) {
				return new SynchronizedCursor(db, masterQuery, editTable, query, mSync);
			}
		}

		/** Factory object to create the custom cursor. Can not be static because it needs mSync */
		public final SynchronizedCursorFactory mCursorFactory = new SynchronizedCursorFactory();

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param sql
		 * @param selectionArgs
		 * @return
		 */
		public SynchronizedCursor rawQuery(String sql, String [] selectionArgs) {
			return rawQueryWithFactory(mCursorFactory, sql, selectionArgs,"");
		}

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param sql
		 * @return
		 */
		public SynchronizedCursor rawQuery(String sql) {
			return rawQuery(sql, CatalogueDBAdapter.EMPTY_STRING_ARRAY);
		}

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param factory
		 * @param sql
		 * @param selectionArgs
		 * @param editTable
		 * @return
		 */
		public SynchronizedCursor rawQueryWithFactory(SynchronizedCursorFactory factory, String sql, String [] selectionArgs, String editTable) {
			SyncLock l = null;
			if (mTxLock == null)
				l = mSync.getSharedLock();

			try {
				return (SynchronizedCursor)mDb.rawQueryWithFactory(factory, sql, selectionArgs, editTable);				
			} finally {
				if (l != null)
					l.unlock();
			}				
		}

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param sql
		 * @param selectionArgs
		 * @return
		 */
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

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param table
		 * @param columns
		 * @param selection
		 * @param selectionArgs
		 * @param groupBy
		 * @param having
		 * @param orderBy
		 * @return
		 */
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
		
		/**
		 * Locking-aware wrapper for underlying database method; actually
		 * calls insertOrThrow since this method also throws exceptions
		 * 
		 * @param sql
		 * @param selectionArgs
		 * @return
		 */
		public long insert(String table, String nullColumnHack, ContentValues values) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Update inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return mDb.insertOrThrow(table, nullColumnHack, values);				
			} finally {
				if (l != null)
					l.unlock();
			}
		}

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param table
		 * @param values
		 * @param whereClause
		 * @param whereArgs
		 * @return
		 */
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

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param sql
		 * @param selectionArgs
		 * @return
		 */
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

		/**
		 * Wrapper for underlying database method. It is recommended that custom cursors subclass SynchronizedCursor.
		 * 
		 * @param cursorFactory
		 * @param sql
		 * @param selectionArgs
		 * @param editTable
		 * @return
		 */
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

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param sql
		 * @return
		 */
		public SynchronizedStatement compileStatement(String sql) {
			SyncLock l = null;
			if (mTxLock != null) {
				if (mTxLock.getType() != LockTypes.exclusive)
					throw new RuntimeException("Compile inside shared TX");
			} else
				l = mSync.getExclusiveLock();

			try {
				return new SynchronizedStatement(this, sql);				
			} finally {
				if (l != null)
					l.unlock();
			}			
		}

		/**
		 * Return the underlying SQLiteDatabase object.
		 * 
		 * @return
		 */
		public SQLiteDatabase getUnderlyingDatabase() {
			return mDb;
		}

		/**
		 * Wrapper.
		 * 
		 * @return
		 */
		public boolean inTransaction() {
			return mDb.inTransaction();
		}

		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param isUpdate	Indicates if updates will be done in TX
		 *
		 * @return
		 */
		public SyncLock beginTransaction(boolean isUpdate) {
			SyncLock l;
			if (isUpdate) {
				l = mSync.getExclusiveLock();
			} else {
				l = mSync.getSharedLock();
			}
			// We have the lock, but if the real beginTransaction() throws an exception, we need to release the lock
			try {
				// If we have a lock, and there is currently a TX active...die
				// Note: because we get a lock, two 'isUpdate' transactions will
				// block, this is only likely to happen with two TXs on the current thread
				// or two non-update TXs on different thread.
				// ENHANCE: Consider allowing nested TXs
				// ENHANCE: Consider returning NULL if TX active and handle null locks...
				if (mTxLock != null)
					throw new RuntimeException("Starting a transaction when one is already started");

				mDb.beginTransaction();
			} catch (Exception e) {
				l.unlock();
				throw new RuntimeException("Unable to start database transaction: " + e.getMessage(), e);
			}
			mTxLock = l;
			return l;				
		}
		/**
		 * Locking-aware wrapper for underlying database method.
		 * 
		 * @param l		Lock returned from BeginTransaction().
		 */
		public void endTransaction(SyncLock l) {
			if (mTxLock == null)
				throw new RuntimeException("Ending a transaction when none is started");
			if (!mTxLock.equals(l))
				throw new RuntimeException("Ending a transaction with wrong transaction lock");
				
			try {
				mDb.endTransaction();			
			} finally {
				// Clear mTxLock before unlocking so another thread does not
				// see the old lock when it gets the lock
				mTxLock = null;
				l.unlock();
			}
		}
		/**
		 * Wrapper for underlying database method.
		 * 
		 */
		public void setTransactionSuccessful() {
			mDb.setTransactionSuccessful();
		}

		/**
		 * Wrapper for underlying database method.
		 * 
		 */
		public boolean isOpen() {
			return mDb.isOpen();
		}

		/**
		 * Return the underlying synchronizer object.
		 * 
		 * @return
		 */
		public Synchronizer getSynchronizer() {
			return mSync;
		}

		/**
		 * Utility routine, purely for debugging ref count issues (mainly Android 2.1)
		 * 
		 * @param msg	Message to display (relating to context)
		 * @param db	Database object
		 * 
		 * @return		Number of current references
		 */
		public static int printRefCount(String msg, SQLiteDatabase db) {
			System.gc();
			Field f;
			try {
				f = SQLiteClosable.class.getDeclaredField("mReferenceCount");
				f.setAccessible(true);
				int refs = (Integer) f.get(db); //IllegalAccessException
				if (msg != null) {
					System.out.println("DBRefs (" + msg + "): " + refs);
					//if (refs < 100) {
					//	System.out.println("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 100)!");					
					//} else if (refs < 1001) {
					//	System.out.println("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 1000)!");					
					//} else {
					//	System.out.println("DBRefs (" + msg + "): " + refs);
					//}					
				}
				return refs;
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return 0;			
		}
	}
	
	/**
	 * Wrapper for statements that ensures locking is used.
	 *
	 * @author Philip Warner
	 */
	public static class SynchronizedStatement {
		/** Synchronizer from database */
		final Synchronizer mSync;
		/** Underlying statement */
		final SQLiteStatement mStatement;
		/** Indicates this is a 'read-only' statement */
		final boolean mIsReadOnly;
		/** Indicates close() has been called */
		private boolean mIsClosed = false;
		/** Copy of SQL used for debugging */
		private final String mSql;

		private SynchronizedStatement (final SynchronizedDb db, final String sql) {
			mSync = db.getSynchronizer();
			mSql = sql;
			if (sql.trim().toLowerCase().startsWith("select"))
				mIsReadOnly = true;
			else
				mIsReadOnly = false;
			mStatement = db.getUnderlyingDatabase().compileStatement(sql);
		}
		
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void bindDouble(final int index, final double value) {
			mStatement.bindDouble(index, value);
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void bindLong(final int index, final long value) {
			mStatement.bindLong(index, value);
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void bindString(final int index, final String value) {
			mStatement.bindString(index, value);
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void bindBlob(final int index, final byte[] value) {
			mStatement.bindBlob(index, value);
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void bindNull(final int index) {
			mStatement.bindNull(index);
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void clearBindings() {
			mStatement.clearBindings();
		}
		/**
		 * Wrapper for underlying method on SQLiteStatement.
		 */
		public void close() {
			mIsClosed = true;
			mStatement.close();
		}

		/**
		 * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
		 */
		public long simpleQueryForLong() {
			SyncLock l = mSync.getSharedLock();
			try {
				return mStatement.simpleQueryForLong();				
			} finally {
				l.unlock();
			}
		}
		/**
		 * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
		 */
		public String simpleQueryForString() {
			SyncLock l = mSync.getSharedLock();
			try {
				return mStatement.simpleQueryForString();				
			} finally {
				l.unlock();
			}
		}

		/**
		 * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
		 */
		public void execute() {
			SyncLock l;
			if (mIsReadOnly)
				l = mSync.getSharedLock();
			else
				l = mSync.getExclusiveLock();
			try {
				mStatement.execute();				
			} finally {
				l.unlock();
			}
		}
		
		/**
		 * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
		 */
		public long executeInsert() {
			SyncLock l = mSync.getExclusiveLock();
			try {
				return mStatement.executeInsert();				
			} finally {
				l.unlock();
			}
		}
		
		public void finalize() {
			if (!mIsClosed)
				Logger.logError(new RuntimeException("Finalizing non-closed statement")); // + mSql));
			// Try to close the underlying statement.
			try {
				mStatement.close();
			} catch (Exception e) {
				// Ignore; may have been finalized
			}
		}
	}
	
	/**
	 * Cursor wrapper that tries to apply locks as necessary. Unfortunately, most cursor
	 * movement methods are final and, if they involve any database locking, could theoretically
	 * still result in 'database is locked' exceptions. So far in testing, none have occurred.
	 */
	public static class SynchronizedCursor extends SQLiteCursor {
		private final Synchronizer mSync;

		public SynchronizedCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
				String editTable, SQLiteQuery query, Synchronizer sync) {
			super(db, driver, editTable, query);
			mSync = sync;
		}

		private int mCount = -1;
		/**
		 * Wrapper that uses a lock before calling underlying method.
		 */
		@Override
		public int getCount() {
			// Cache the count (it's what SQLiteCursor does), and we avoid locking 
			if (mCount == -1) {
				SyncLock l = mSync.getSharedLock();
				try {
					mCount = super.getCount();
				} finally {
					l.unlock();
				}				
			}
			return mCount;
		}

		/**
		 * Wrapper that uses a lock before calling underlying method.
		 */
		@Override
		public boolean requery() {
			SyncLock l = mSync.getSharedLock();
			try {
				return super.requery();
			} finally {
				l.unlock();
			}
		}
	}
}
