package org.fruct.oss.ikm.points;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Patterns;
import android.util.SparseLongArray;

import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.ikm.service.Direction;
import org.osmdroid.util.GeoPoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;

public class Point implements Parcelable, Serializable {
	private static final long serialVersionUID = 1L;

	private int latE6, lonE6;
	private String name;

	private Category category;
	private String desc;
	private String uuid;

	private List<String> photos = new ArrayList<>();

	private boolean isDescriptionUrl = false;
	private transient GeoPoint geoPoint;

	private long dbId;

	public Point(Parcel source) {
		latE6 = source.readInt();
		lonE6 = source.readInt();
		name = source.readString();

		desc = source.readString();
		category = source.readParcelable(Category.class.getClassLoader());
		source.readStringList(photos);

		dbId = source.readLong();
	}

	public Point(String name, int latE6, int lonE6) {
		this(name, latE6, lonE6, Collections.<String>emptyList());
	}

	public Point(String name, int latE6, int lonE6, List<String> photos) {
		this.name = name;
		this.latE6 = latE6;
		this.lonE6 = lonE6;
		this.photos.addAll(photos);
	}

	public Point setDbId(long dbId) {
		this.dbId = dbId;
		return this;
	}

	public Point setUuid(String uuid) {
		this.uuid = uuid;
		return this;
	}

	public Point setCategory(Category category) {
		this.category = category;
		return this;
	}

	public Point setDescription(String d) {
		if (d == null)
			d = "";

		this.desc = d;

		Matcher match = Patterns.WEB_URL.matcher(desc);

		if (match.find()) {
			desc = match.group(0);
			isDescriptionUrl = true;
		}

		return this;
	}

	public boolean isDescriptionUrl() {
		return isDescriptionUrl;
	}


	public String getName() {
		return name;
	}

	public String getDescription() {
		return desc;
	}

	public Category getCategory() {
		return category;
	}

	public String getUuid() {
		return uuid;
	}

	public List<String> getPhotos() {
		return photos;
	}

	public Point setPhotos(List<String> photos) {
		this.photos.addAll(photos);
		return this;
	}

	public boolean hasPhoto() {
		return !photos.isEmpty();
	}

	public String getPhoto() {
		return photos.get(0);
	}

	public GeoPoint toPoint() {
		return geoPoint == null ? geoPoint = new GeoPoint(latE6, lonE6)
				: geoPoint;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Point point = (Point) o;

		if (!uuid.equals(point.uuid)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		return uuid.hashCode();
	}


	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(latE6);
		dest.writeInt(lonE6);
		dest.writeString(name);
		dest.writeString(desc);
		dest.writeParcelable(category, flags);
		dest.writeStringList(photos);

		dest.writeLong(dbId);
	}

	public static final Parcelable.Creator<Point> CREATOR = new Parcelable.Creator<Point>() {
		@Override
		public Point[] newArray(int size) {
			return new Point[size];
		}

		@Override
		public Point createFromParcel(Parcel source) {
			return new Point(source);
		}
	};

	private static long dataInvalidatedTime = 0;
	private static HashMap<Long, RoutingData> routingDataMap = new HashMap<>();

	private transient RoutingData routingData;
	private transient long dataExtractTime = -1;

	public static void invalidateData() {
		routingDataMap.clear();
		dataInvalidatedTime = System.currentTimeMillis();
	}

	public int getDistance() {
		extractData();
		if (routingData != null) {
			return routingData.distance;
		} else {
			return 0;
		}
	}

	public Direction.RelativeDirection getRelativeDirection() {
		extractData();
		if (routingData != null) {
			return routingData.direction;
		} else {
			return null;
		}
	}

	public void setRelativeDirection(Direction.RelativeDirection relativeDirection) {
		extractData();
		if (routingData == null) {
			createData();
		}

		routingData.direction = relativeDirection;
	}

	public void setDistance(int dist) {
		extractData();
		if (routingData == null) {
			createData();
		}

		routingData.distance = dist;
	}

	private void extractData() {
		if (routingData == null) {
			routingData = routingDataMap.get(dbId);
			dataExtractTime = dataInvalidatedTime;
		} else if (dataInvalidatedTime != dataExtractTime) {
			routingData = null;
		}
	}

	private void createData() {
		routingData = new RoutingData(0, null);
		routingDataMap.put(dbId, routingData);
		dataExtractTime = dataInvalidatedTime;
	}

	private static class RoutingData {
		public RoutingData(int distance, Direction.RelativeDirection direction) {
			this.distance = distance;
			this.direction = direction;
		}

		int distance;
		Direction.RelativeDirection direction;
	}
}
