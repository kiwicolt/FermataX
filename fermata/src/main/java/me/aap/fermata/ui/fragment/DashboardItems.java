package me.aap.fermata.ui.fragment;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

public final class DashboardItems {
	public static final Pref<Supplier<String[]>> PREF =
			Pref.sa("DASHBOARD_ITEMS", (String[]) null);
	public static final String DASHBOARD = "dashboard";
	public static final String FOLDERS = "folders";
	public static final String FAVORITES = "favorites";
	public static final String RECENT = "recent";
	public static final String PLAYLISTS = "playlists";
	public static final String MENU = "menu";

	private DashboardItems() {
	}

	@NonNull
	public static List<Item> getDashboardItems(Context ctx) {
		return getDashboardItems(ctx, getPrefs());
	}

	@NonNull
	public static List<Item> getDashboardItems(Context ctx, PreferenceStore store) {
		List<Item> items = new ArrayList<>();
		for (String name : getLayout(store, false, false, false)) {
			Item item = getItem(ctx, name, false);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static List<Item> getConfigItems(Context ctx) {
		return getConfigItems(ctx, getPrefs());
	}

	@NonNull
	public static List<Item> getConfigItems(Context ctx, PreferenceStore store) {
		List<Item> items = new ArrayList<>();
		for (String name : getLayout(store, false, false, true)) {
			Item item = getItem(ctx, name, true);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static Collection<String> getNavLayout(PreferenceStore store) {
		return getLayout(store, true, true, false);
	}

	@NonNull
	public static List<NavItem> getNavItems(PreferenceStore store) {
		List<NavItem> items = new ArrayList<>();
		for (String name : getNavLayout(store)) {
			NavItem item = getNavItem(name);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static List<String> getConfigLayout(PreferenceStore store) {
		return getLayout(store, false, false, true);
	}

	public static boolean move(PreferenceStore store, String name, int delta) {
		List<String> names = getConfigLayout(store);
		int from = names.indexOf(name);
		if (from == -1) return false;
		int to = from + delta;
		if ((to < 0) || (to >= names.size())) return false;
		names.remove(from);
		names.add(to, name);
		store.applyStringArrayPref(PREF, names.toArray(new String[0]));
		return true;
	}

	public static void setDashboardOrder(PreferenceStore store, List<Item> visibleItems) {
		Set<String> visibleNames = new LinkedHashSet<>(visibleItems.size());
		for (Item item : visibleItems) visibleNames.add(item.name);

		List<String> names = getConfigLayout(store);
		List<String> ordered = new ArrayList<>(names.size());
		Iterator<String> visible = visibleNames.iterator();

		for (String name : names) {
			if (visibleNames.contains(name)) {
				if (visible.hasNext()) ordered.add(visible.next());
			} else {
				ordered.add(name);
			}
		}

		while (visible.hasNext()) ordered.add(visible.next());
		store.applyStringArrayPref(PREF, ordered.toArray(new String[0]));
	}

	public static boolean swap(PreferenceStore store, String name1, String name2) {
		if (!isReorderable(name1) || !isReorderable(name2)) return false;

		List<String> names = getConfigLayout(store);
		int idx1 = names.indexOf(name1);
		int idx2 = names.indexOf(name2);

		if ((idx1 == -1) || (idx2 == -1)) return false;
		names.set(idx1, name2);
		names.set(idx2, name1);
		store.applyStringArrayPref(PREF, names.toArray(new String[0]));
		return true;
	}

	public static void reset(PreferenceStore store) {
		store.removePref(PREF);
	}

	@Nullable
	public static String idToName(@IdRes int id) {
		if (id == R.id.dashboard_fragment) return DASHBOARD;
		if (id == R.id.folders_fragment) return FOLDERS;
		if (id == R.id.favorites_fragment) return FAVORITES;
		if (id == R.id.recent_fragment) return RECENT;
		if (id == R.id.playlists_fragment) return PLAYLISTS;
		if (id == R.id.menu) return MENU;

		AddonManager amgr = AddonManager.get();
		for (AddonInfo ai : BuildConfig.ADDONS) {
			FermataAddon a = amgr.getAddon(ai.className);
			if ((a instanceof FermataFragmentAddon) && (a.getAddonId() == id)) return ai.className;
		}

		Log.e("Unknown DashboardItem id: ", id);
		return null;
	}

	@Nullable
	private static NavItem getNavItem(String name) {
		switch (name) {
			case DASHBOARD:
				return new NavItem(name, R.id.dashboard_fragment, R.drawable.home, R.string.home);
			case FOLDERS:
				return new NavItem(name, R.id.folders_fragment, me.aap.utils.R.drawable.folder,
						R.string.folders);
			case FAVORITES:
				return new NavItem(name, R.id.favorites_fragment, R.drawable.favorite_filled,
						R.string.favorites);
			case RECENT:
				return new NavItem(name, R.id.recent_fragment, R.drawable.timer, R.string.recent);
			case PLAYLISTS:
				return new NavItem(name, R.id.playlists_fragment, R.drawable.playlist,
						R.string.playlists);
			case MENU:
				return new NavItem(name, R.id.menu, me.aap.utils.R.drawable.menu, R.string.menu);
		}

		FermataAddon addon = AddonManager.get().getAddon(name);
		if (addon instanceof FermataFragmentAddon) {
			AddonInfo info = addon.getInfo();
			return new NavItem(name, addon.getAddonId(), info.icon, info.addonName);
		}

		Log.e("Unknown NavBarItem name: ", name);
		return null;
	}

	private static boolean isReorderable(String name) {
		return !DASHBOARD.equals(name) && !MENU.equals(name);
	}

	@NonNull
	private static List<String> getLayout(PreferenceStore store, boolean includeDashboard,
																				boolean includeMenu, boolean includeDisabled) {
		LinkedHashSet<String> names = new LinkedHashSet<>(BuildConfig.ADDONS.length + 5);
		String[] pref = store.getStringArrayPref(PREF);

		if (includeDashboard) names.add(DASHBOARD);

		if (pref != null) {
			for (String name : pref) {
				if (isAllowed(name, includeDashboard, includeMenu, includeDisabled)) names.add(name);
			}
		}

		addMissingItem(names, FOLDERS, null);
		addMissingItem(names, FAVORITES, FOLDERS);
		addMissingItem(names, RECENT, FAVORITES);
		addMissingItem(names, PLAYLISTS, RECENT);

		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (isFragmentAddon(ai, includeDisabled)) names.add(ai.className);
		}

		if (includeMenu) names.add(MENU);
		return new ArrayList<>(names);
	}

	private static void addMissingItem(LinkedHashSet<String> names, String name,
																		 @Nullable String after) {
		if (names.contains(name)) return;
		if ((after == null) || !names.contains(after)) {
			names.add(name);
			return;
		}

		List<String> ordered = new ArrayList<>(names);
		ordered.add(ordered.indexOf(after) + 1, name);
		names.clear();
		names.addAll(ordered);
	}

	private static boolean isAllowed(String name, boolean includeDashboard, boolean includeMenu,
																	boolean includeDisabled) {
		if (DASHBOARD.equals(name)) return includeDashboard;
		if (MENU.equals(name)) return includeMenu;
		if (FOLDERS.equals(name) || FAVORITES.equals(name) || RECENT.equals(name) ||
				PLAYLISTS.equals(name)) return true;

		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (ai.className.equals(name)) return isFragmentAddon(ai, includeDisabled);
		}
		return false;
	}

	private static boolean isFragmentAddon(AddonInfo ai, boolean includeDisabled) {
		if (!BuildConfig.AUTO && ai.isAuto) return false;

		FermataAddon addon = AddonManager.get().getAddon(ai.className);
		if (addon instanceof FermataFragmentAddon) return true;
		if (!includeDisabled) return false;

		try {
			return FermataFragmentAddon.class.isAssignableFrom(Class.forName(ai.className));
		} catch (Exception ignore) {
			return false;
		}
	}

	@Nullable
	private static Item getItem(Context ctx, String name, boolean includeDisabled) {
		switch (name) {
			case DASHBOARD:
				return new Item(name, R.id.dashboard_fragment, R.drawable.home,
						ctx.getString(R.string.home), ctx.getString(R.string.dashboard_home_sub), null);
			case FOLDERS:
				return new Item(name, R.id.folders_fragment, me.aap.utils.R.drawable.folder,
						ctx.getString(R.string.folders), ctx.getString(R.string.dashboard_folders_sub), null);
			case FAVORITES:
				return new Item(name, R.id.favorites_fragment, R.drawable.favorite_filled,
						ctx.getString(R.string.favorites), ctx.getString(R.string.dashboard_favorites_sub), null);
			case RECENT:
				return new Item(name, R.id.recent_fragment, R.drawable.timer,
						ctx.getString(R.string.recent), ctx.getString(R.string.dashboard_recent_sub), null);
			case PLAYLISTS:
				return new Item(name, R.id.playlists_fragment, R.drawable.playlist,
						ctx.getString(R.string.playlists), ctx.getString(R.string.dashboard_playlists_sub), null);
			case MENU:
				return new Item(name, R.id.menu, me.aap.utils.R.drawable.menu,
						ctx.getString(R.string.menu), ctx.getString(R.string.dashboard_menu_sub), null);
		}

		AddonInfo info;
		try {
			info = FermataAddon.findAddonInfo(name);
		} catch (RuntimeException ex) {
			return null;
		}

		FermataAddon addon = AddonManager.get().getAddon(name);
		if (addon instanceof FermataFragmentAddon) {
			return new Item(name, addon.getAddonId(), info.icon, getAddonTitle(ctx, name, info),
					getAddonSubtitle(ctx, name), info);
		}

		if (includeDisabled && isFragmentAddon(info, true)) {
			return new Item(name, ID_NULL, info.icon, ctx.getString(info.addonName),
					ctx.getString(R.string.dashboard_addon_disabled_sub), info);
		}

		return null;
	}

	private static String getAddonSubtitle(Context ctx, String className) {
		if (className.contains(".addon.tv.")) return ctx.getString(R.string.dashboard_tv_sub);
		if (className.contains(".addon.radio.")) return ctx.getString(R.string.dashboard_radio_sub);
		if (className.contains(".addon.web.yt.")) return ctx.getString(R.string.dashboard_youtube_sub);
		if (className.contains(".addon.web.")) return ctx.getString(R.string.dashboard_web_sub);
		if (className.contains(".addon.felex.")) return ctx.getString(R.string.dashboard_felex_sub);
		return ctx.getString(R.string.dashboard_addon_sub);
	}

	private static String getAddonTitle(Context ctx, String className, AddonInfo info) {
		if (className.contains(".addon.web.") && !className.contains(".addon.web.yt.")) {
			return ctx.getString(R.string.module_name_web);
		}
		return ctx.getString(info.addonName);
	}

	private static PreferenceStore getPrefs() {
		return FermataApplication.get().getPreferenceStore();
	}

	public static final class Item {
		public final String name;
		@IdRes
		public final int id;
		@DrawableRes
		public final int icon;
		public final String title;
		public final String subtitle;
		@Nullable
		public final AddonInfo addonInfo;

		private Item(String name, @IdRes int id, @DrawableRes int icon, String title,
								 String subtitle, @Nullable AddonInfo addonInfo) {
			this.name = name;
			this.id = id;
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
			this.addonInfo = addonInfo;
		}
	}

	public static final class NavItem {
		public final String name;
		@IdRes
		public final int id;
		@DrawableRes
		public final int icon;
		@StringRes
		public final int title;

		private NavItem(String name, @IdRes int id, @DrawableRes int icon, @StringRes int title) {
			this.name = name;
			this.id = id;
			this.icon = icon;
			this.title = title;
		}
	}
}
