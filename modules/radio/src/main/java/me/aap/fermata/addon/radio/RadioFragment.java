package me.aap.fermata.addon.radio;

import static java.util.Objects.requireNonNull;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

public class RadioFragment extends MediaLibFragment {
	private static final long AUTO_RELOAD_INTERVAL = 10 * 60 * 1000L;
	private long autoReloadTime;

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new ListAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_radio);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.radio_fragment;
	}

	@Override
	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) autoReload();
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		if ((h.getItem() instanceof RadioItem) && (h.getItem() instanceof BrowsableItem)) {
			b.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
							me.aap.fermata.R.string.refresh).setData(h.getItem())
					.setHandler(this::contextMenuItemSelected);
		}
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		reloadRadioItem(item.getData(), true);
		return true;
	}

	@Override
	public FutureSupplier<?> refresh() {
		getRootItem().getApi().clearCache();
		return super.refresh();
	}

	public RadioRootItem getRootItem() {
		return requireNonNull(AddonManager.get().getAddon(RadioAddon.class)).getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRootItem().isChildItemId(i.getId());
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	@Override
	public boolean isVideoModeSupported() {
		return false;
	}

	private void autoReload() {
		long now = System.currentTimeMillis();
		if ((now - autoReloadTime) < AUTO_RELOAD_INTERVAL) return;
		autoReloadTime = now;
		getRootItem().getApi().clearCache();

		if (getAdapter() == null) return;
		BrowsableItem parent = getAdapter().getParent();
		if (parent instanceof RadioItem) {
			reloadRadioItem(parent, false);
		} else {
			reload();
		}
	}

	private FutureSupplier<?> reloadRadioItem(BrowsableItem item, boolean showError) {
		getRootItem().getApi().clearCache();
		return item.refresh().main().thenRun(this::reload).ifFail(err -> {
			Log.e(err, "Failed to reload radio item ", item);
			if (showError) {
				String msg = err.getLocalizedMessage();
				UiUtils.showAlert(getContext(), (msg != null) ? msg : err.toString());
			}
			return null;
		});
	}
}
