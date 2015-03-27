package org.fruct.oss.ikm.points.gets;

import android.os.Parcel;
import android.os.Parcelable;

public class Category implements Parcelable {
	private final long id;
	private final String name;
	private final String description;
	private final String url;

	private boolean isActive;

	public Category(long id, String name, String description, String url) {
		this.url = url;
		this.name = name;
		this.description = description;
		this.id = id;
		this.isActive = true;
	}

	public Category(Parcel parcel) {
		this.id = parcel.readLong();
		this.name = parcel.readString();
		this.description = parcel.readString();
		this.url = parcel.readString();
		this.isActive = parcel.readInt() != 0;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getUrl() {
		return url;
	}

	public boolean isActive() {
		return isActive;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeString(name);
		dest.writeString(description);
		dest.writeString(url);
		dest.writeInt(isActive ? 1 : 0);
	}

	public static final Creator<Category> CREATOR = new Creator<Category>() {
		@Override
		public Category createFromParcel(Parcel source) {
			return new Category(source);
		}

		@Override
		public Category[] newArray(int size) {
			return new Category[size];
		}
	};
}
