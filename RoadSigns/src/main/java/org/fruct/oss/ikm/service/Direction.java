package org.fruct.oss.ikm.service;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.os.Parcel;
import android.os.Parcelable;

public class Direction implements Parcelable {
	public static enum RelativeDirection {
		LEFT(R.drawable.barrow_270, "left"),
		RIGHT(R.drawable.barrow_90, "right"),
		FORWARD(R.drawable.barrow, "forward"),
		BACK(R.drawable.barrow_180, "back");
		
	
		private int iconId;
		private String description;
		private RelativeDirection(int resId, String desc) {
			this.iconId = resId;
			this.description = desc;
		}
		
		public int getIconId() {
			return iconId;
		}

		public CharSequence getDescription() {
			return description;
		}
	}
	
	private ArrayList<PointDesc> points = new ArrayList<PointDesc>();
	
	private GeoPoint center;
	private GeoPoint direction;
	
	public Direction(GeoPoint center, GeoPoint direction) {
		this.center = center;
		this.direction = direction;
	}
	
	public void addPoint(PointDesc point) {
		points.add(point);
	}
	
	public GeoPoint getCenter() {
		return center;
	}
	
	public GeoPoint getDirection() {
		return direction;
	}
	
	public List<PointDesc> getPoints() {
		return points;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeTypedList(points);
		dest.writeParcelable(center, 0);
		dest.writeParcelable(direction, 0);
	}
	
	private Direction(Parcel source) {
		source.readTypedList(points, PointDesc.CREATOR);
		center = source.readParcelable(GeoPoint.class.getClassLoader());
		direction = source.readParcelable(GeoPoint.class.getClassLoader());
	}

	public static Parcelable.Creator<Direction> CREATOR = new Creator<Direction>() {
		@Override
		public Direction[] newArray(int size) {
			return new Direction[size];
		}
		
		@Override
		public Direction createFromParcel(Parcel source) {
			return new Direction(source);
		}
	};
	
	public float getRelativeBearing(float bearing) {
		// Absolute bearing of point of interest
		float pointDir = (float) center.bearingTo(getDirection());
		
		// Absolute bearing of device
		float deviceDir = bearing;
		float relativeBearing = Utils.normalizeAngle(pointDir - deviceDir);
		return relativeBearing;
	}
	
	public RelativeDirection getRelativeDirection(float bearing) {
		float relativeBearing = getRelativeBearing(bearing);
		
		RelativeDirection ret;
		if (relativeBearing > 35 && relativeBearing < 135) {
			ret = RelativeDirection.RIGHT;
		} else if (relativeBearing < -35 && relativeBearing > -135) {
			ret = RelativeDirection.LEFT;
		} else if (Math.abs(relativeBearing) < 35) {
			ret = RelativeDirection.FORWARD;
		} else {
			ret = RelativeDirection.BACK;
		}
		
		return ret;
	}
}
