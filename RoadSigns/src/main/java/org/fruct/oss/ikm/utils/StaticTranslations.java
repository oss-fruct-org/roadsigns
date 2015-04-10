package org.fruct.oss.ikm.utils;

import android.content.res.Resources;
import android.support.annotation.StringRes;

import org.fruct.oss.ikm.R;

import java.util.HashMap;
import java.util.Map;

public class StaticTranslations {
	private final Resources resources;
	private final Map<String, Integer> translationsMap = new HashMap<>();

	public StaticTranslations(Resources resources) {
		this.resources = resources;
	}

	public StaticTranslations addTranslation(String key, @StringRes int valueId) {
		translationsMap.put(key.toLowerCase().trim(), valueId);
		return this;
	}

	public String getString(String key) {
		Integer resId = translationsMap.get(key.toLowerCase().trim());

		if (resId == null)
			return key;

		try {
			return resources.getString(resId);
		} catch (Resources.NotFoundException e) {
			return key;
		}
	}

	public static StaticTranslations createDefault(Resources resources) {
		StaticTranslations trans = new StaticTranslations(resources);

		trans.addTranslation("shops", R.string.trans_shops);
		trans.addTranslation("hostels", R.string.trans_hostels);
		trans.addTranslation("sights", R.string.trans_sights);
		trans.addTranslation("city.large", R.string.trans_large);
		trans.addTranslation("city.medium", R.string.trans_medium);
		trans.addTranslation("city.small", R.string.trans_small);

		trans.addTranslation("automobile tourism", R.string.trans_automobile);
		trans.addTranslation("apartment", R.string.trans_apartment);
		trans.addTranslation("hotels", R.string.trans_hotels);

		trans.addTranslation("sanatoriums", R.string.trans_sanatoriums);
		trans.addTranslation("monuments", R.string.trans_monuments);
		trans.addTranslation("museums", R.string.trans_museums);

		trans.addTranslation("historical cities", R.string.trans_historical);
		trans.addTranslation("monasteries", R.string.trans_monasteries);
		trans.addTranslation("natural monuments", R.string.trans_natural_monuments);

		trans.addTranslation("audio.other", R.string.trans_audio_other);
		trans.addTranslation("audio.shops", R.string.trans_audio_other);
		trans.addTranslation("audio.guide", R.string.trans_audio_other);

		return trans;
	}
}