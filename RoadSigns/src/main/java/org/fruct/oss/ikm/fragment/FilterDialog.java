package org.fruct.oss.ikm.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.events.PointsUpdatedEvent;
import org.fruct.oss.ikm.points.PointsAccess;
import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.ikm.utils.StaticTranslations;

import de.greenrobot.event.EventBus;

class Item implements Serializable {
	Item(int idx, boolean state) {
		this.idx = idx;
		this.state = state;
	}

	int idx;
	boolean state;
}

public class FilterDialog extends DialogFragment {
	private boolean[] filterChecked;

	// List stores user selection
	private ArrayList<Item> checkedItems;
	private StaticTranslations translations;

	private PointsAccess pointsAccess;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		pointsAccess = App.getInstance().getPointsAccess();
		translations = StaticTranslations.createDefault(getResources());
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putSerializable("checkedItems", checkedItems);
		outState.putBooleanArray("filterChecked", filterChecked);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final List<Category> categories = pointsAccess.loadCategories();

		if (savedInstanceState != null) {
			checkedItems = (ArrayList<Item>) savedInstanceState.getSerializable("checkedItems");
		} else {
			checkedItems = new ArrayList<>();
		}

		// Fill list data
		String[] filterNames = new String[categories.size()];

		if (savedInstanceState != null) {
			filterChecked = savedInstanceState.getBooleanArray("filterChecked");
		} else {
			filterChecked = new boolean[categories.size()];
		}


		for (int i = 0; i < categories.size(); i++) {
			Category category = categories.get(i);
			filterNames[i] = translations.getString(category.getName());
			if (savedInstanceState == null)
				filterChecked[i] = category.isActive();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.filter)
				.setMultiChoiceItems(filterNames,
						filterChecked, new DialogInterface.OnMultiChoiceClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which, boolean isChecked) {
								filterChecked[which] = isChecked;
								checkedItems.add(new Item(which, isChecked));
							}
						})

				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						for (Item item : checkedItems) {
							Category category = categories.get(item.idx);
							category.setActive(item.state);
							pointsAccess.setCategoryState(category, item.state);
						}

						EventBus.getDefault().post(new PointsUpdatedEvent(true));
					}
				})

				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});

		return builder.create();
	}
}
