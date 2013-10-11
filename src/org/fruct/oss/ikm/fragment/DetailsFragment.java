package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DetailsFragment extends Fragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null)
			return null;
		
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.details_fragment, null, false);
		return view;
	
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		PointDesc desc = (PointDesc) getArguments().getSerializable(DetailsActivity.POINT_ARG);
		TextView textView = (TextView) getActivity().findViewById(R.id.details_text);
		
		if (textView == null)
			return;
		
		textView.setText(desc.getName());
	}
}
