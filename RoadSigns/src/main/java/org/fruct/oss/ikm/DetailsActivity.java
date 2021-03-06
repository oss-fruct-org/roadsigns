package org.fruct.oss.ikm;

import org.fruct.oss.ikm.fragment.DetailsFragment;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

public class DetailsActivity extends ActionBarActivity {
	public static final String POINT_ACTION = "org.fruct.org.ikm.POINT_ACTION";
	public static final String POINT_ARG = "org.fruct.oss.ikm.ARG_POINT";
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
			finish();
			return;
		}

		if (savedInstanceState == null) {
			setContentView(R.layout.activity_details);
			setupActionBar();

			DetailsFragment fragment = new DetailsFragment();
			fragment.setArguments(getIntent().getExtras());

			getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment, "details").commit();
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		} else {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.details, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			//.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
