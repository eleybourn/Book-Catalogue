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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 * Collection of methods to wrap common serialization routines.
 * 
 * @author Philip Warner
 */
public class SerializationUtils {

	/**
	 * Utility routine to convert a Serializable object to a byte array.
	 * 
	 * @param o		Object to convert
	 * @return		Resulting byte array. NULL on failure.
	 */
	public static byte[] serializeObject(Serializable o) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(o);
			out.close();
		} catch (Exception e) {
			out = null;
		}

		// Get the bytes of the serialized object
		byte[] buf;
		if (out != null) {
			buf = bos.toByteArray();
		} else {
			buf = null; //new byte[]{};
		}
		return buf;
	}

	/**
	 * Catchall class for errors in serialization
	 * 
	 * @author Philip Warner
	 */
	public static class DeserializationException extends Exception {
		private static final long serialVersionUID = -2040548134317746620L;
		public final Exception inner;
		DeserializationException(Exception e) {
			super();
			inner = e;
		}
	}

	/**
	 * Deserialize the passed byte array
	 */
	@SuppressWarnings("unchecked")
	public static <T> T deserializeObject(byte[] blob) throws DeserializationException {
		try {
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(blob));
			Object o = in.readObject();
		    return (T)o;
		} catch (ClassCastException | ClassNotFoundException | IOException e) {
			throw new DeserializationException(e);
		}
    }

	/**
	 * Serialize then de-serialize to create a deep clone.
	 */
	public static <T extends Serializable> T cloneObject(T o) throws DeserializationException {
		return deserializeObject(serializeObject(o));
	}
}
