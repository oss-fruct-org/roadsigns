package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.points.Point;
import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.views.MapView;

public class POIInfoWindow extends DefaultInfoWindow implements View.OnTouchListener {
	private Point point;

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

		Intent intent = new Intent(context, DrawerActivity.class);
		intent.setAction(PointsFragment.ACTION_SHOW_DETAILS);
		intent.putExtra(DetailsFragment.ARG_POINT, (android.os.Parcelable) point);

		context.startActivity(intent);
	}

	@Override
	public void onOpen(Object itemo) {
		super.onOpen(itemo);
		ExtendedOverlayItem item = (ExtendedOverlayItem) itemo;
		point = (Point) item.getRelatedObject();
	}

	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		return true;
	}
}
