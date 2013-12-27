package org.fruct.oss.ikm.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointsManager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;

class Item implements Serializable {
	Item(int idx, boolean state) {
		this.idx = idx;
		this.state = state;
	}

	int idx;
	boolean state;
}

public class FilterDialog extends DialogFragment {
	private String[] filterNames;
	private boolean[] filterChecked;

	// List stores user selection
	private ArrayList<Item> checkedItems;

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putSerializable("checkedItems", checkedItems);
		outState.putBooleanArray("filterChecked", filterChecked);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final List<Filter> filters = PointsManager.getInstance().getFilters();

		if (savedInstanceState != null) {
			checkedItems = (ArrayList<Item>) savedInstanceState.getSerializable("checkedItems");
		} else {
			checkedItems = new ArrayList<Item>();
		}
		// Fill list data
		filterNames = new String[filters.size()];

		if (savedInstanceState != null) {
			filterChecked = savedInstanceState.getBooleanArray("filterChecked");
		} else {
			filterChecked = new boolean[filters.size()];
		}


		for (int i = 0; i < filters.size(); i++) {
			Filter filter = filters.get(i);
			filterNames[i] = filter.getString();
			if (savedInstanceState == null)
				filterChecked[i] = filter.isActive();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Filter")
			   .setMultiChoiceItems(filterNames,
					   filterChecked, new DialogInterface.OnMultiChoiceClickListener() {
				   @Override
				   public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					   filterChecked[which] = isChecked;
					   checkedItems.add(new Item(which, isChecked));
				   }
			   });
		
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				for (Item item : checkedItems) {
					Filter filter = filters.get(item.idx);
					filter.setActive(item.state);
				}

				if (!checkedItems.isEmpty()) {
					PointsManager.getInstance().notifyFiltersUpdated();
				}
			}
		});
		
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});

		return builder.create();
	}
}
