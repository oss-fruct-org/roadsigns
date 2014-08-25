package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.views.MapView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class POIInfoWindow extends DefaultInfoWindow implements View.OnTouchListener {
	private PointDesc point;

	public POIInfoWindow(int res, MapView mapView) {
		super(res, mapView);

		getView().setOnTouchListener(this);

		ImageButton button = (ImageButton) getView().findViewById(R.id.bubble_moreinfo);
		button.setVisibility(Button.VISIBLE);
		button.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				activate(getView().getContext());
			}
		});

		getView().setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
					activate(getView().getContext());
				}
				return true;
			}
		});
	}

	private void activate(Context context) {
		/*Pattern pattern = Pattern.compile("(https?://.+)");

		if (point.getDescription() == null)
			return;

		Matcher match = pattern.matcher(point.getDescription());

		if (match.find()) {
			String str = match.group(1);
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(str));
			context.startActivity(intent);
		} else {*/

		Bundle bundle = new Bundle();
		bundle.putParcelable("pointdesc", point);

		Intent intent = new Intent(context, PointsActivity.class);
		intent.setAction(PointsActivity.SHOW_DETAILS);
		intent.putExtra(PointsActivity.DETAILS_INDEX, bundle);

		context.startActivity(intent);

		//}
	}

	@Override
	public void onOpen(Object itemo) {
		super.onOpen(itemo);
		ExtendedOverlayItem item = (ExtendedOverlayItem) itemo;
		point = (PointDesc) item.getRelatedObject();
	}

	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		return true;
	}
}
