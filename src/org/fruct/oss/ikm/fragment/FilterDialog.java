package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointsManager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;

public class FilterDialog extends DialogFragment {
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final List<Filter> filters = PointsManager.getInstance().getFilters();
		
		// List stores user selection
		final List<Pair<Integer, Boolean>> checkedItems = new ArrayList<Pair<Integer,Boolean>>();
		
		// Fill list data
		String[] filterNames = new String[filters.size()];
		boolean[] filterChecked = new boolean[filters.size()];

		for (int i = 0; i < filters.size(); i++) {
			Filter filter = filters.get(i);
			filterNames[i] = filter.getString();
			filterChecked[i] = filter.isActive();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Filter").setMultiChoiceItems(filterNames,
				filterChecked, new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						checkedItems.add(Pair.create(which, isChecked));
					}
				});
		
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				for (Pair<Integer, Boolean> item : checkedItems) {
					Filter filter = filters.get(item.first);
					filter.setActive(item.second);
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
