package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

public class PointsActivity extends ActionBarActivity {	
	// Actions
	public static final String SHOW_DETAILS = "org.fruct.oss.ikm.SHOW_DETAILS";
	
	// Parameters
	public static final String DETAILS_INDEX = "org.fruct.oss.ikm.DETAILS_INDEX";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setUpActionBar();

		setContentView(R.layout.activity_points);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.directions, menu);
		
		return true;
	}
	
	private void setUpActionBar() {
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// TODO: fix other possible crashes with null getActionBar()
		/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		} else {
		}*/
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
