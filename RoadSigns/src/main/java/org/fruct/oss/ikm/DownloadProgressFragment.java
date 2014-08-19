package org.fruct.oss.ikm;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.fruct.oss.ikm.storage.ContentItem;

import java.util.Locale;

public class DownloadProgressFragment extends Fragment implements View.OnClickListener {
	private OnFragmentInteractionListener mListener;

	private ProgressBar progressBar;
	private TextView textView;
	private TextView textView2;

	public DownloadProgressFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_download_progress, container, false);

		this.progressBar = ((ProgressBar) view.findViewById(R.id.progress_bar));
		ImageButton stopButton = ((ImageButton) view.findViewById(R.id.button));

		this.textView = (TextView) view.findViewById(R.id.text);
		this.textView2 = (TextView) view.findViewById(R.id.text2);

		stopButton.setOnClickListener(this);

		return view;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (OnFragmentInteractionListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnFragmentInteractionListener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	public void startDownload() {
		if (isHidden()) {
			getFragmentManager().beginTransaction()
					.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
					.show(this)
					.commit();
		}
	}

	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {
		startDownload();

		progressBar.setMax(max);
		progressBar.setProgress(downloaded);

		float mbMax = (float) max / (1024 * 1024);
		float mbCurrent = (float) downloaded / (1024 * 1024);
		String downloadString = String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax);

		textView.setText(item.getDescription());
		textView2.setText(downloadString);
	}

	public void stopDownload() {
		if (!isHidden()) {
			getFragmentManager().beginTransaction()
					.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
					.hide(this)
					.commit();
		}
	}

	@Override
	public void onClick(View v) {
		mListener.stopButtonPressed();
	}

	public interface OnFragmentInteractionListener {
		public void stopButtonPressed();
	}

}
