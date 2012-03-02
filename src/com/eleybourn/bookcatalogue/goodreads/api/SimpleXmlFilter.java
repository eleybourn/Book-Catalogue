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

package com.eleybourn.bookcatalogue.goodreads.api;

import java.util.ArrayList;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.goodreads.api.XmlFilter.XmlHandler;

/**
 * Class layered on top of XmlFilter to implement a simple set of XML filters to extract 
 * data from an XML file and return the results in a collection of nested Bundle objects.
 *
 * @author Philip Warner
 */
public class SimpleXmlFilter {
	private final XmlFilter mRootFilter;
	private final ArrayList<BuilderContext> mContexts = new ArrayList<BuilderContext>();
	private final ArrayList<String> mTags = new ArrayList<String>();
	private final DataStore mRootData = new DataStore();

	public interface XmlListener {
		public void onStart(SimpleXmlFilter.BuilderContext bc, ElementContext c);
		public void onFinish(SimpleXmlFilter.BuilderContext bc, ElementContext c);
	}

	public static interface DataStoreProvider {
		public void addArrayItem(Bundle b);		
		public Bundle getData();
	}

	public class DataStore implements DataStoreProvider {
		private final Bundle mData;

		public DataStore() {
			mData = new Bundle();
		}

		@Override
		public void addArrayItem(Bundle b) {
			throw new RuntimeException("Attempt to store array at root");
		}

		@Override
		public Bundle getData() {
			return mData;
		}
		
	}

	public static class BuilderContext implements DataStoreProvider {
		public DataStoreProvider parent;

		String collectField;
		private XmlFilter mFilter;
		ArrayList<AttrFilter> attrs = null;

		XmlListener listener = null;
		XmlHandler finishHandler = null;

		private Bundle mLocalBundle = null;
		private ArrayList<Bundle> mArrayItems = null;

		private boolean mIsArray = false;
		private String mArrayName = null;

		private boolean mIsArrayItem = false;

		BuilderContext(XmlFilter root, DataStoreProvider parent, ArrayList<String> tags) {
			if (parent == null)
				throw new RuntimeException("Parent can not be null");

			mFilter = XmlFilter.buildFilter(root, tags);
			this.parent = parent;
			mFilter.setStartAction(mHandleStart, this);
			mFilter.setEndAction(mHandleFinish, this);
		}

		public void addArrayItem(Bundle b) {
			if (mArrayItems != null)
				mArrayItems.add(b);
			else
				parent.addArrayItem(b);
		}
		public void initArray() {
			mArrayItems = new ArrayList<Bundle>();
		}
		public void saveArray() {
			getData().putParcelableArrayList(mArrayName, mArrayItems);
			mArrayItems = null;
		}

		public XmlFilter getFilter() {
			return mFilter;
		}

		public Bundle getData() {
			if (mLocalBundle != null)
				return mLocalBundle;
			else
				return parent.getData();
		}

		public void pushBundle() {
			if (mLocalBundle != null) 
				throw new RuntimeException("Bundle already pushed!");
			mLocalBundle = new Bundle();
		}
		public Bundle popBundle() {
			if (mLocalBundle == null) 
				throw new RuntimeException("Bundle not pushed!");
			Bundle b = mLocalBundle;
			mLocalBundle = null;
			return b;
		}

		public boolean isArray() {
			return mIsArray;
		}
		public void setArray(String name, boolean isArray) {
			mIsArray = isArray;
			mArrayName = name;
		}

		public boolean isArrayItem() {
			return mIsArrayItem;
		}
		public void setArrayItem(boolean isArrayItem) {
			mIsArrayItem = isArrayItem;
		}
	}

	private abstract class AttrFilter {
		public String name;
		public String key;
		public abstract void put(BuilderContext context, String value);
		AttrFilter(String key, String name) {
			this.name = name;
			this.key = key;
		}
	}
	
	public SimpleXmlFilter isArray(String arrayName) {
		BuilderContext c = mContexts.get(mContexts.size()-1);
		c.setArray(arrayName, true);
		return this;
	}
	public SimpleXmlFilter isArrayItem() {
		BuilderContext c = mContexts.get(mContexts.size()-1);
		c.setArrayItem(true);
		return this;
	}

	SimpleXmlFilter(XmlFilter root) {
		mRootFilter = root;
	}

	public SimpleXmlFilter s(String tag) {
		DataStoreProvider parent;

		mTags.add(tag);
		int size = mContexts.size();
		
		if (size == 0)
			parent = mRootData;
		else
			parent = mContexts.get(size-1);
		
		mContexts.add(new BuilderContext(mRootFilter, parent, mTags));

		return this;
	}

	public SimpleXmlFilter done() {
		mTags.clear();
		mContexts.clear();
		return this;
	}

	public Bundle getData() {
		return mRootData.getData();
	}

	private ArrayList<AttrFilter> getAttrFilters() {
		BuilderContext c = mContexts.get(mContexts.size()-1);
		if (c.attrs == null) {
			c.attrs = new ArrayList<AttrFilter>();
		}
		return c.attrs;
	}

	public SimpleXmlFilter setListener(XmlListener listener) {
		BuilderContext c = mContexts.get(mContexts.size()-1);
		c.listener = listener;
		return this;
	}
	

	public SimpleXmlFilter pop() {
		mContexts.remove(mContexts.size()-1);
		mTags.remove(mTags.size()-1);
		return this;
	}

	private static XmlHandler mHandleStart = new XmlHandler() {
		@Override
		public void process(ElementContext context) {
			BuilderContext bc = (BuilderContext)context.userArg;
			
			if (bc.isArray()) {
				bc.initArray();
			}
			if (bc.isArrayItem()) {
				bc.pushBundle();
			}

			if (bc.listener != null)
				bc.listener.onStart(bc, context);

			ArrayList<AttrFilter> attrs = bc.attrs;
			if (attrs != null) {
				for(AttrFilter f: attrs) {
					final String name = f.name;
					final String value = context.attributes.getValue(name);
					if (value != null) {
						try {
							f.put(bc, value);							
						} catch (Exception e) {
							// Could not be parsed....just ignore
						}
					}
				}					
			}

		}
	};
	private static XmlHandler mHandleFinish = new XmlHandler() {
		@Override
		public void process(ElementContext context) {
			BuilderContext bc = (BuilderContext)context.userArg;
			if (bc.finishHandler != null)
				bc.finishHandler.process(context);

			if (bc.listener != null)
				bc.listener.onFinish(bc, context);

			if (bc.isArrayItem()) {
				Bundle b = bc.popBundle();
				bc.addArrayItem(b);
			}

			if (bc.isArray()) {
				bc.saveArray();
			}

		}
	};

	public SimpleXmlFilter booleanAttr(String key, String attrName) {
		ArrayList<AttrFilter> attrs = getAttrFilters();
		attrs.add(new BooleanAttrFilter(key, attrName));
		return this;
	}
	public SimpleXmlFilter doubleAttr(String attrName, String key) {
		ArrayList<AttrFilter> attrs = getAttrFilters();
		attrs.add(new DoubleAttrFilter(key, attrName));
		return this;
	}
	public SimpleXmlFilter longAttr(String attrName, String key) {
		ArrayList<AttrFilter> attrs = getAttrFilters();
		attrs.add(new LongAttrFilter(key, attrName));
		return this;
	}
	public SimpleXmlFilter stringAttr(String attrName, String key) {
		ArrayList<AttrFilter> attrs = getAttrFilters();
		attrs.add(new StringAttrFilter(key, attrName));
		return this;
	}
	
	private void setCollector(String tag, XmlHandler handler, String fieldName) {
		s(tag);
		setCollector(handler, fieldName);
		pop();
	}
	private void setCollector(XmlHandler handler, String fieldName) {
		BuilderContext c = mContexts.get(mContexts.size()-1);
		c.collectField = fieldName;
		c.finishHandler = handler;
	}

	public SimpleXmlFilter booleanBody(String fieldName) {
		setCollector(mBooleanHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter booleanBody(String tag, String fieldName) {
		setCollector(tag, mBooleanHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter doubleBody(String fieldName) {
		setCollector(mDoubleHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter doubleBody(String tag, String fieldName) {
		setCollector(tag, mDoubleHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter longBody(String fieldName) {
		setCollector(mLongHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter longBody(String tag, String fieldName) {
		setCollector(tag, mLongHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter stringBody(String fieldName) {
		setCollector(mTextHandler, fieldName);			
		return this;
	}
	public SimpleXmlFilter stringBody(String tag, String fieldName) {
		setCollector(tag, mTextHandler, fieldName);			
		return this;
	}

	private static XmlHandler mTextHandler = new XmlHandler() {
		@Override
		public void process(ElementContext context) {
			final BuilderContext c = (BuilderContext)context.userArg;
			c.getData().putString(c.collectField, context.body.trim());
		}
	};

	private static XmlHandler mLongHandler = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final BuilderContext c = (BuilderContext)context.userArg;
			final String name = c.collectField;
			try {
				long l = Long.parseLong(context.body.trim());
				c.getData().putLong(name, l);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};
	
	private static XmlHandler mDoubleHandler = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final BuilderContext c = (BuilderContext)context.userArg;
			final String name = c.collectField;
			try {
				double d = Double.parseDouble(context.body.trim());
				c.getData().putDouble(name, d);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};

	private static XmlHandler mBooleanHandler = new XmlHandler() {

		@Override
		public void process(ElementContext context) {
			final BuilderContext c = (BuilderContext)context.userArg;
			final String name = c.collectField;
			try {
				boolean b = textToBoolean(context.body.trim());
				c.getData().putBoolean(name, b);
			} catch (Exception e) {
				// Ignore but dont add
			}
		}
	};
	
	private class StringAttrFilter extends AttrFilter {
		StringAttrFilter(String key, String name) {
			super(key, name);
		}

		public void put(BuilderContext context, String value) {
			context.getData().putString(this.key, value);
		}
	}
	private class LongAttrFilter extends AttrFilter {
		LongAttrFilter(String key, String name) {
			super(key, name);
		}

		public void put(BuilderContext context, String value) {
			context.getData().putLong(this.key, Long.parseLong(value));
		}
	}
	private class DoubleAttrFilter extends AttrFilter {
		DoubleAttrFilter(String key, String name) {
			super(key, name);
		}

		public void put(BuilderContext context, String value) {
			context.getData().putDouble(this.key, Double.parseDouble(value));
		}
	}
	private class BooleanAttrFilter extends AttrFilter {
		BooleanAttrFilter(String key, String name) {
			super(key, name);
		}

		public void put(BuilderContext context, String value) {
			boolean b = textToBoolean(value.trim());
			context.getData().putBoolean(this.key, b);
		}
	}

	private static boolean textToBoolean(final String s) {
		boolean b;
		if (s.length() == 0) {
			b = false;
		} else if (s.equalsIgnoreCase("false")) {
			b = false;
		} else if (s.equalsIgnoreCase("true")) {
			b = true;
		} else if (s.equalsIgnoreCase("f")) {
			b = false;
		} else if (s.equalsIgnoreCase("t")) {
			b = true;
		} else {
			long l = Long.parseLong(s);
			b = (l != 0);
		}
		return b;
	}
}
