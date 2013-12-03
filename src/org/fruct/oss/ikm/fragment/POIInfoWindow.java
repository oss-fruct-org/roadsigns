package org.fruct.oss.ikm.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.views.MapView;

public class POIInfoWindow extends DefaultInfoWindow {
	private PointDesc point;
	
	public POIInfoWindow(int res, MapView mapView) {
		super(res, mapView);
		
		ImageButton button = (ImageButton) getView().findViewById(R.id.bubble_moreinfo);
		button.setVisibility(Button.VISIBLE);
		button.setOnClickListener(new Button.OnClickListener() {			
			@Override
			public void onClick(View v) {
				Bundle bundle = new Bundle();
				bundle.putParcelable("pointdesc", point);
				
				Intent intent = new Intent(v.getContext(), PointsActivity.class);
				intent.setAction(PointsActivity.SHOW_DETAILS);
				intent.putExtra(PointsActivity.DETAILS_INDEX, bundle);
				
				v.getContext().startActivity(intent);
			}
		});

	}

	@Override
	public void onOpen(Object itemo) {
		super.onOpen(itemo);
		ExtendedOverlayItem item = (ExtendedOverlayItem) itemo;
		point = (PointDesc) item.getRelatedObject();
	}
}
