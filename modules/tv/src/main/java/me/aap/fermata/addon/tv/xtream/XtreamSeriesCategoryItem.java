package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSeriesCategoryItem extends BrowsableItemBase implements TvItem {
	public static final String SCHEME = "tvxsc";
	private final XtreamCategory category;

	private XtreamSeriesCategoryItem(String id, XtreamSectionItem parent, XtreamCategory category) {
		super(id, parent, null);
		this.category = category;
	}

	public static XtreamSeriesCategoryItem create(XtreamSectionItem parent, XtreamCategory category) {
		String id = toId(parent.getParent().getSourceId(), category.getId(), category.getName());
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			return (i != null) ? (XtreamSeriesCategoryItem) i :
					new XtreamSeriesCategoryItem(id, parent, category);
		}
	}

	public static FutureSupplier<XtreamSeriesCategoryItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		ParsedId parsed = parseId(id);
		FutureSupplier<? extends Item> f = root.getItem(XtreamSourceItem.SCHEME,
				XtreamSourceItem.toId(parsed.sourceId));
		return (f == null) ? completedNull() : f.then(i -> {
			XtreamSourceItem source = (XtreamSourceItem) i;
			if (source == null) return completedNull();
			return source.getSection(XtreamSectionItem.TYPE_SERIES).map(section ->
					create(section, new XtreamCategory(parsed.categoryId, parsed.name, null)));
		});
	}

	public static String toId(int sourceId, String categoryId, String name) {
		return XtreamItemId.category(SCHEME, sourceId, categoryId, name);
	}

	public FutureSupplier<XtreamSeriesItem> getSeries(int seriesId) {
		return getUnsortedChildren().map(children -> {
			for (Item child : children) {
				if ((child instanceof XtreamSeriesItem) &&
						(((XtreamSeriesItem) child).getSeriesId() == seriesId)) {
					return (XtreamSeriesItem) child;
				}
			}
			return null;
		});
	}

	@NonNull
	@Override
	public XtreamSectionItem getParent() {
		return (XtreamSectionItem) super.getParent();
	}

	public XtreamCategory getCategory() {
		return category;
	}

	@NonNull
	@Override
	public String getName() {
		return category.getName();
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getParent().getParent().getApi().getSeries(category.getId()).map(series -> {
			List<Item> children = new ArrayList<>(series.size());
			for (XtreamSeries s : series) children.add(XtreamSeriesItem.create(this, s));
			return children;
		});
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@Override
	protected String buildSubtitle(List<Item> children) {
		return getLib().getContext().getString(R.string.sub_series, children.size());
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.video;
	}

	static ParsedId parseId(String id) {
		XtreamItemId.Category parsed = XtreamItemId.category(id);
		return new ParsedId(parsed.sourceId, parsed.categoryId, parsed.categoryName);
	}

	static final class ParsedId {
		final int sourceId;
		final String categoryId;
		final String name;

		ParsedId(int sourceId, String categoryId, String name) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.name = name;
		}
	}
}
