package org.fruct.oss.ikm.drawer;

import android.support.v4.app.Fragment;

public interface MultiPanel {
	void pushFragment(Fragment fragment);
	void popFragment();
}
