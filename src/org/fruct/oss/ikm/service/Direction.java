package org.fruct.oss.ikm.service;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.os.Parcel;
import android.os.Parcelable;

public class Direction implements Parcelable {
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
		dest.writeSerializable(points);
		dest.writeParcelable(center, 0);
		dest.writeParcelable(direction, 0);
	}
	
	@SuppressWarnings("unchecked")
	private Direction(Parcel source) {
		points = (ArrayList<PointDesc>) source.readSerializable();
		center = source.readParcelable(null);
		direction = source.readParcelable(null);
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
}
