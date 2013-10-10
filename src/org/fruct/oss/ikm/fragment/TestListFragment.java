package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class TestListFragment extends DialogFragment {
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		
		ListView listView = new ListView(getActivity());
		ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(), R.layout.direction_item_layout, R.id.direction_name, new Integer[] {1, 2, 3});
		listView.setAdapter(adapter);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder
				//.setMessage("Test dialog") 
				.setView(listView)
				
				
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						
					}
				})
				
				;
		
		return builder.create();
	}
}
