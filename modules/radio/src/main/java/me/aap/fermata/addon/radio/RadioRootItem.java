package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

public class RadioRootItem extends ExtRoot implements RadioItem {
	public static final String ID = "Radio";
	static final String SCHEME = "radio";
	private final RadioBrowserApi api;

	public RadioRootItem(DefaultMediaLib lib) {
		super(ID, lib);
		api = new RadioBrowserApi();
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (!SCHEME.equals(scheme)) return null;
		if (RadioStationItem.isStationId(id)) return RadioStationItem.create(this, id);
		return completed(createFolder(id));
	}

	RadioBrowserApi getApi() {
		return api;
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_radio));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<Item> items = new ArrayList<>(4);
		items.add(RadioStationFolder.popular(this));
		items.add(RadioStationFolder.topVoted(this));
		items.add(RadioDirectoryFolder.countries(this));
		items.add(RadioDirectoryFolder.tags(this));
		return completed(items);
	}

	boolean isChildItemId(String id) {
		return ID.equals(id) || id.startsWith(SCHEME + ':');
	}

	@Nullable
	private MediaLib.Item createFolder(String id) {
		if (RadioStationFolder.ID_POPULAR.equals(id)) return RadioStationFolder.popular(this);
		if (RadioStationFolder.ID_TOP_VOTED.equals(id)) return RadioStationFolder.topVoted(this);
		if (RadioDirectoryFolder.ID_COUNTRIES.equals(id)) return RadioDirectoryFolder.countries(this);
		if (RadioDirectoryFolder.ID_TAGS.equals(id)) return RadioDirectoryFolder.tags(this);
		if (id.startsWith(RadioStationFolder.ID_COUNTRY_PREFIX)) {
			return RadioStationFolder.country(this, RadioStationFolder.decodeCountry(id));
		}
		if (id.startsWith(RadioStationFolder.ID_TAG_PREFIX)) {
			return RadioStationFolder.tag(this, RadioStationFolder.decodeTag(id));
		}
		return null;
	}
}
