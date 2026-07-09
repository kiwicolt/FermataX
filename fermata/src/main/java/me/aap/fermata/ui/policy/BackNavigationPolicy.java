package me.aap.fermata.ui.policy;

import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.ToolBarView;

public final class BackNavigationPolicy {
	private BackNavigationPolicy() {
	}

	public static void handlePlayerBack(MainActivityDelegate a) {
		ActivityFragment f = a.getActiveFragment();
		BodyLayout b = a.getBody();

		if (b.isBothMode()) {
			if ((f != null) && f.onBackPressed()) {
				ChromePolicy.refreshAutoTopBackButton(a);
				return;
			}

			a.showDashboard();
			return;
		}

		if ((f != null) && !(f instanceof MediaLibFragment) && f.onBackPressed()) return;

		if (leaveVideoMode(a)) return;

		if (goToAudioSource(a)) return;

		a.onBackPressed();
	}

	private static boolean goToAudioSource(MainActivityDelegate a) {
		MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
		PlayableItem pi = (eng == null) ? null : eng.getSource();
		if ((pi != null) && !pi.isVideo() && a.goToItem(pi)) return true;

		pi = a.getMediaServiceBinder().getCurrentItem();
		return (pi != null) && !pi.isVideo() && a.goToItem(pi);
	}

	public static boolean leaveVideoMode(MainActivityDelegate a) {
		BodyLayout b = a.getBody();
		if (!b.isVideoMode()) return false;

		b.setMode(BodyLayout.Mode.BOTH);
		if (AUTO) a.setBarsHidden(false);
		if (a.isCarActivity()) {
			a.post(() -> {
				MediaItemListView.focusActive(a.getContext(), null);
				ChromePolicy.refreshAutoTopBackButton(a);
			});
		} else {
			ChromePolicy.refreshAutoTopBackButton(a);
		}
		return true;
	}

	public static void handleAutoActivityBack(MainActivityDelegate a) {
		OverlayMenu menu = a.getActiveMenu();
		if (menu != null) {
			if (menu.back()) return;
			else if (a.hideActiveMenu()) return;
		}

		ToolBarView tb = a.getToolBar();
		if ((tb != null) && tb.onBackPressed()) return;

		ActivityFragment f = a.getActiveFragment();
		if (f != null) {
			if (f.onBackPressed()) return;

			int navId = a.getActiveNavItemId();
			if ((f.getFragmentId() != navId) && (navId != ID_NULL)) {
				a.showFragment(navId);
				return;
			}

			if (!(f instanceof DashboardFragment) || (a.getActiveNavItemId() != R.id.dashboard_fragment) ||
					!f.isRootPage()) {
				a.showDashboard();
				return;
			}
		}

		a.finish();
	}
}
