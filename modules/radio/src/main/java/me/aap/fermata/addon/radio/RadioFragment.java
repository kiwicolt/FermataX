package me.aap.fermata.addon.radio;

import static java.util.Objects.requireNonNull;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.async.FutureSupplier;

public class RadioFragment extends MediaLibFragment {

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
}
