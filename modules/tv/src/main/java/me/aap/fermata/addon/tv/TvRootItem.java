package me.aap.fermata.addon.tv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.m3u.TvM3uEpgItem;
import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uGroupItem;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.addon.tv.m3u.TvM3uTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.xtream.XtreamCatchupFolder;
import me.aap.fermata.addon.tv.xtream.XtreamCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpgItem;
import me.aap.fermata.addon.tv.xtream.XtreamEpisodeItem;
import me.aap.fermata.addon.tv.xtream.XtreamMovieItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeasonItem;
import me.aap.fermata.addon.tv.xtream.XtreamSectionItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamSeriesItem;
import me.aap.fermata.addon.tv.xtream.XtreamSourceItem;
import me.aap.fermata.addon.tv.xtream.XtreamTrackItem;
import me.aap.fermata.addon.tv.xtream.XtreamVodCategoryItem;
import me.aap.fermata.addon.tv.xtream.XtreamWatchFromBeginningItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

import static me.aap.utils.async.Async.forEach;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.contains;

/**
 * @author Andrey Pavlenko
 */
public class TvRootItem extends ItemContainer<TvSourceItem> implements TvItem {
	public static final String ID = "TV";
	private static final Pref<IntSupplier> SOURCE_COUNTER = Pref.i("SOURCE_COUNTER", 0).withInheritance(false);
	private static final Pref<Supplier<int[]>> SOURCE_IDS = Pref.ia("SOURCE_IDS", () -> new int[0]).withInheritance(false);
	private static final String SOURCE_TYPE_PREFIX = "SOURCE_TYPE#";
	private final DefaultMediaLib lib;

	public TvRootItem(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;

		switch (scheme) {
			case TvM3uItem.SCHEME:
				return create(toSourceId(id));
			case TvM3uGroupItem.SCHEME:
				return TvM3uGroupItem.create(this, id);
			case TvM3uTrackItem.SCHEME:
				return TvM3uTrackItem.create(this, id);
			case TvM3uEpgItem.SCHEME:
				return TvM3uEpgItem.create(this, id);
			case XtreamSourceItem.SCHEME:
				return XtreamSourceItem.create(this, toXtreamSourceId(id));
			case XtreamSectionItem.SCHEME:
				return XtreamSectionItem.create(this, id);
			case XtreamCategoryItem.SCHEME:
				return XtreamCategoryItem.create(this, id);
			case XtreamTrackItem.SCHEME:
				return XtreamTrackItem.create(this, id);
			case XtreamEpgItem.SCHEME:
				return XtreamEpgItem.create(this, id);
			case XtreamCatchupFolder.SCHEME:
				return XtreamCatchupFolder.create(this, id);
			case XtreamWatchFromBeginningItem.SCHEME:
				return XtreamWatchFromBeginningItem.create(this, id);
			case XtreamVodCategoryItem.SCHEME:
				return XtreamVodCategoryItem.create(this, id);
			case XtreamMovieItem.SCHEME:
				return XtreamMovieItem.create(this, id);
			case XtreamSeriesCategoryItem.SCHEME:
				return XtreamSeriesCategoryItem.create(this, id);
			case XtreamSeriesItem.SCHEME:
				return XtreamSeriesItem.create(this, id);
			case XtreamSeasonItem.SCHEME:
				return XtreamSeasonItem.create(this, id);
			case XtreamEpisodeItem.SCHEME:
				return XtreamEpisodeItem.create(this, id);
			default:
				return null;
		}
	}


	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_tv));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public MediaLib.BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
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
		int[] ids = getIntArrayPref(SOURCE_IDS);
		List<Integer> idList = new ArrayList<>(ids.length);
		for (int i : ids) idList.add(i);
		List<Item> children = new ArrayList<>(ids.length);
		return forEach(id -> {
			FutureSupplier<? extends TvSourceItem> f = create(id);
			if (f != null) return f.onSuccess(i -> {
				if (i != null) children.add(i);
			});
			return completedVoid();
		}, idList).map(v -> children);
	}

	@Override
	protected String getScheme() {
		return TvM3uItem.SCHEME;
	}

	@Override
	protected void saveChildren(List<TvSourceItem> children) {
		applyIntArrayPref(SOURCE_IDS, CollectionUtils.map(children,
				(i, t, a) -> a[i] = t.getSourceId(), int[]::new));
	}

	@Override
	public boolean isChildItemId(String id) {
		return id.startsWith(TvM3uTrackItem.SCHEME)
				|| id.startsWith(TvM3uGroupItem.SCHEME)
				|| id.startsWith(TvM3uEpgItem.SCHEME)
				|| id.startsWith(TvM3uItem.SCHEME)
				|| id.startsWith(XtreamTrackItem.SCHEME)
				|| id.startsWith(XtreamEpgItem.SCHEME)
				|| id.startsWith(XtreamCatchupFolder.SCHEME)
				|| id.startsWith(XtreamWatchFromBeginningItem.SCHEME)
				|| id.startsWith(XtreamMovieItem.SCHEME)
				|| id.startsWith(XtreamEpisodeItem.SCHEME)
				|| id.startsWith(XtreamSeasonItem.SCHEME)
				|| id.startsWith(XtreamSeriesItem.SCHEME)
				|| id.startsWith(XtreamSeriesCategoryItem.SCHEME)
				|| id.startsWith(XtreamVodCategoryItem.SCHEME)
				|| id.startsWith(XtreamCategoryItem.SCHEME)
				|| id.startsWith(XtreamSectionItem.SCHEME)
				|| id.startsWith(XtreamSourceItem.SCHEME);
	}

	public void addSource(TvM3uFile m3u) {
		int counter = getIntPref(SOURCE_COUNTER) + 1;
		Pref<Supplier<String>> id = Pref.s("M3UID#" + counter);

		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setIntPref(SOURCE_COUNTER, counter);
			e.setStringPref(sourceTypePref(counter), TvSourceItem.TYPE_M3U);
			e.setStringPref(id, TvM3uFileSystem.getInstance().toId(m3u.getRid()));
		}

		addItem(TvM3uItem.create(this, m3u, counter));
	}

	@Override
	protected void itemRemoved(TvSourceItem i) {
		super.itemRemoved(i);
		int sourceId = i.getSourceId();

		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.removePref(sourceTypePref(sourceId));
			if (i instanceof TvM3uItem) {
				e.removePref(Pref.s("M3UID#" + sourceId));
			} else if (i instanceof XtreamSourceItem) {
				XtreamAccount.remove(e, sourceId);
			}
		}

		if (i instanceof TvM3uItem) TvM3uFileSystemProvider.removeSource(((TvM3uItem) i).getResource());
	}

	public void addSource(XtreamAccount account) {
		XtreamAccount.requireCredentialStorage();
		int counter = getIntPref(SOURCE_COUNTER) + 1;
		XtreamAccount source = account.withSourceId(counter);

		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setIntPref(SOURCE_COUNTER, counter);
			e.setStringPref(sourceTypePref(counter), TvSourceItem.TYPE_XTREAM);
			XtreamAccount.save(e, counter, source);
		}

		XtreamSourceItem item = XtreamSourceItem.create(this, source);
		item.warmUp();
		addItem(item);
	}

	public void updateSource(XtreamAccount account) {
		XtreamAccount.requireCredentialStorage();
		int sourceId = account.getSourceId();

		try (PreferenceStore.Edit e = editPreferenceStore()) {
			e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_XTREAM);
			XtreamAccount.save(e, sourceId, account);
		}
	}

	private FutureSupplier<? extends TvSourceItem> create(int srcId) {
		if (!contains(getIntArrayPref(SOURCE_IDS), srcId)) return null;
		if (TvSourceItem.TYPE_XTREAM.equals(getSourceType(this, srcId))) {
			return XtreamSourceItem.create(this, srcId);
		}
		return createM3uSource(srcId);
	}

	private FutureSupplier<TvM3uItem> createM3uSource(int srcId) {
		String m3uId = getStringPref(Pref.s("M3UID#" + srcId));
		if (m3uId == null) return null;
		return TvM3uItem.create(this, srcId, m3uId).onFailure(err -> {
			Log.e(err, "Failed to load source: ", m3uId);
			if (err instanceof MalformedURLException) removeSource(srcId);
		}).ifNull(() -> {
			Log.e("Failed to load source: ", m3uId);
			removeSource(srcId);
			return null;
		});
	}

	private void removeSource(int srcId) {
		int[] ids = getIntArrayPref(SOURCE_IDS);
		if (ids.length == 0) return;

		int[] newIds = new int[ids.length - 1];
		boolean removed = false;

		for (int i = 0, j = 0; i < ids.length; i++) {
			if (ids[i] == srcId) removed = true;
			else if (j < newIds.length) newIds[j++] = ids[i];
			else return;
		}

		if (removed) {
			Log.i("Removing source: ", srcId);
			try (PreferenceStore.Edit e = editPreferenceStore()) {
				e.setIntArrayPref(SOURCE_IDS, newIds);
				e.removePref(sourceTypePref(srcId));
				e.removePref(Pref.s("M3UID#" + srcId));
				XtreamAccount.remove(e, srcId);
			}
		}
	}

	private int toSourceId(String id) {
		return Integer.parseInt(id.substring(TvM3uItem.SCHEME.length() + 1));
	}

	private int toXtreamSourceId(String id) {
		return Integer.parseInt(id.substring(XtreamSourceItem.SCHEME.length() + 1));
	}

	static String getSourceType(PreferenceStore ps, int sourceId) {
		String type = ps.getStringPref(sourceTypePref(sourceId));
		return TvSourceItem.TYPE_XTREAM.equals(type) ? TvSourceItem.TYPE_XTREAM : TvSourceItem.TYPE_M3U;
	}

	static Pref<Supplier<String>> sourceTypePref(int sourceId) {
		return Pref.s(SOURCE_TYPE_PREFIX + sourceId);
	}
}
