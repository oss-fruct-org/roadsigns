package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;

import org.fruct.oss.ikm.poi.PointDesc;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class PointsFragment extends ListFragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		try {
			@SuppressWarnings("unchecked")
			ArrayList<PointDesc> poiList = (ArrayList<PointDesc>) getActivity()
					.getIntent().getSerializableExtra(MapFragment.POI_LIST_ID);

			ArrayList<String> poiNames = new ArrayList<String>();

			for (PointDesc point : poiList) {
				poiNames.add(point.getName());
			}

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(
					getActivity(), android.R.layout.simple_list_item_1,
					poiNames);
			setListAdapter(adapter);

		} catch (ClassCastException ex) {
			ex.printStackTrace();
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}
}
