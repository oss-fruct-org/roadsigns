package org.fruct.oss.ikm.poi;

import java.io.Serializable;

import org.osmdroid.util.GeoPoint;

import android.os.Parcel;
import android.os.Parcelable;

public class PointDesc implements Serializable, Parcelable {
	private static final long serialVersionUID = 1L;
	
	private int latE6, lonE6;
	private String name;
	
	private GeoPoint geoPoint;
	
	private String category;
	private String desc;
	
	private static int nextId = 0;
	private int internalId;
	
	public PointDesc(String name, int latE6, int lonE6) {
		this.name = name;
		this.latE6 = latE6;
		this.lonE6 = lonE6;
		
		internalId = nextId++;
	}
	
	public PointDesc setCategory(String cat) {
		this.category = cat;
		return this;
	}
	
	public PointDesc setDescription(String d) {
		this.desc = d;
		return this;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return desc;
	}
	
	public String getCategory() {
		return category;
	}
	
	public GeoPoint toPoint() {
		return geoPoint == null ? geoPoint = new GeoPoint(latE6, lonE6)
								: geoPoint;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + internalId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PointDesc other = (PointDesc) obj;
		if (internalId != other.internalId)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "PointDesc [name=" + name + "]";
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
		dest.writeString(category);
		dest.writeInt(internalId);
	}
	
	public static final Parcelable.Creator<PointDesc> CREATOR = new Parcelable.Creator<PointDesc>() {
		@Override
		public PointDesc[] newArray(int size) {
			return new PointDesc[size];
		}
	
		@Override
		public PointDesc createFromParcel(Parcel source) {
			int lat = source.readInt();
			int lon = source.readInt();
			String name = source.readString();
			
			PointDesc ret = new PointDesc(name, lat, lon)
				.setDescription(source.readString())
				.setCategory(source.readString());
			
			ret.internalId = source.readInt();
			
			return ret;
		}
	};
}
