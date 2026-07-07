package me.aap.fermata.ui.policy;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.VideoView;

public final class PlaybackLayoutPolicy {
	private PlaybackLayoutPolicy() {
	}

	public static boolean shouldShowSplit(MediaLibFragment f, MediaEngine eng,
																				MediaSessionCallback cb, VideoView vv) {
		if ((f == null) || (eng == null)) return false;

		MediaLib.PlayableItem i = eng.getSource();
		return (i != null) && i.isVideo() && eng.isSplitModeSupported() &&
				(cb.getVideoView() == vv) && isSameRoot(f, i);
	}

	public static BodyLayout.Mode getModeOnPlayableChanged(BodyLayout.Mode currentMode,
																												 MediaLib.PlayableItem newItem,
																												 MediaEngine eng) {
		if ((newItem == null) || !newItem.isVideo() || (eng == null) || !eng.isSplitModeSupported()) {
			return BodyLayout.Mode.FRAME;
		}

		if (!eng.isVideoModeRequired()) return BodyLayout.Mode.FRAME;
		return currentMode == BodyLayout.Mode.FRAME ? BodyLayout.Mode.VIDEO : currentMode;
	}

	public static boolean shouldRefreshVideoInCurrentMode(BodyLayout.Mode currentMode,
																												MediaLib.PlayableItem newItem,
																												MediaEngine eng) {
		return (currentMode != BodyLayout.Mode.FRAME) && (newItem != null) && newItem.isVideo() &&
				(eng != null) && eng.isSplitModeSupported() && eng.isVideoModeRequired();
	}

	public static BodyLayout.Mode getModeAfterLeavingVideo(boolean carActivity) {
		return carActivity ? BodyLayout.Mode.FRAME : BodyLayout.Mode.BOTH;
	}

	private static boolean isSameRoot(MediaLibFragment f, MediaLib.PlayableItem i) {
		var adapter = f.getAdapter();
		if (adapter == null) return false;
		var parent = adapter.getParent();
		return (parent != null) && parent.getRoot().equals(i.getRoot());
	}
}
