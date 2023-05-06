package com.eleybourn.bookcatalogue.compat;

import androidx.fragment.app.Fragment;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer 
 * support Android 2.x), and potentially to make it easier to support two versions.
 * 
 * @author pjw
 */
public class BookCatalogueFragment extends Fragment {

	/**
	 * Utility routine to make sure this fragment's activity is a member of the passed class, and report
	 * an appropriate message if not.
	 */
	public static void checkInstance(Object o, Class<?> c) {
		if (! (c.isInstance(o)) )
			throw new RuntimeException("Class " + o.getClass().getSimpleName() + " must implement " + c.getSimpleName());
	}
}

