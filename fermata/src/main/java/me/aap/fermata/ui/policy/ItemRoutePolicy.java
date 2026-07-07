package me.aap.fermata.ui.policy;

import androidx.annotation.IdRes;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;

public final class ItemRoutePolicy {
	private ItemRoutePolicy() {
	}

	@IdRes
	public static int getFragmentId(Item i) {
		MediaLib.BrowsableItem root = i.getRoot();

		if (root instanceof MediaLib.Folders) return R.id.folders_fragment;
		if (root instanceof MediaLib.Favorites) return R.id.favorites_fragment;
		if (root instanceof MediaLib.Recent) return R.id.recent_fragment;
		if (root instanceof MediaLib.Playlists) return R.id.playlists_fragment;
		if ((root instanceof ExtRoot) && "youtube".equals(root.getId())) return R.id.youtube_fragment;

		return AddonManager.get().getFragmentId(root);
	}
}
