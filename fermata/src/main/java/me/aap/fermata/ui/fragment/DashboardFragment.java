package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_DOWN;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.MovableRecyclerViewAdapter;
import me.aap.utils.ui.view.ToolBarView;

public class DashboardFragment extends MainActivityFragment
		implements AddonManager.Listener, PreferenceStore.Listener, FermataServiceUiBinder.Listener {
	private DashboardAdapter adapter;
	private PreferenceStore prefs;
	private FermataServiceUiBinder binder;

	@Override
	public int getFragmentId() {
		return R.id.dashboard_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getResources().getString(R.string.dashboard);
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return DashboardToolBarMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return DashboardFloatingButtonMediator.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dashboard_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Context ctx = requireContext();
		MainActivityDelegate activity = MainActivityDelegate.get(ctx);
		Context localizedCtx = activity.getLocalizedContext(ctx);
		prefs = activity.getPrefs();
		RecyclerView list = view.findViewById(R.id.dashboard_list);
		DashboardAdapter dashboardAdapter = new DashboardAdapter(activity, localizedCtx, prefs);
		adapter = dashboardAdapter;
		int spanCount = getSpanCount(ctx);
		GridLayoutManager layoutManager = new GridLayoutManager(ctx, spanCount);
		layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return dashboardAdapter.isWide(position) ? spanCount : 1;
			}
		});
		list.setHasFixedSize(true);
		list.setLayoutManager(layoutManager);
		list.setAdapter(dashboardAdapter);
		new ItemTouchHelper(dashboardAdapter.getItemTouchCallback()).attachToRecyclerView(list);

		FermataApplication.get().getAddonManager().addBroadcastListener(this);
		prefs.addBroadcastListener(this);
		binder = activity.getMediaServiceBinder();
		binder.addBroadcastListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.refreshContinueCard();
	}

	@Override
	public void onDestroyView() {
		FermataApplication.get().getAddonManager().removeBroadcastListener(this);
		if (prefs != null) {
			prefs.removeBroadcastListener(this);
			prefs = null;
		}
		if (binder != null) {
			binder.removeBroadcastListener(this);
			binder = null;
		}
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.close();
		this.adapter = null;
		super.onDestroyView();
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		reload();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		DashboardAdapter adapter = this.adapter;
		if ((adapter != null) && prefs.contains(DashboardItems.PREF) && !adapter.isCallbackCall()) {
			reload();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		refreshContinueCard();
	}

	@Override
	public void onPlaybackStopped() {
		refreshContinueCard();
	}

	private void refreshContinueCard() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.refreshContinueCard();
	}

	public void reload() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.reload();
	}

	private static int getSpanCount(Context ctx) {
		DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		int minWidth = (int) (240 * dm.density);
		return Math.max(2, dm.widthPixels / Math.max(1, minWidth));
	}

	private static final class DashboardAdapter extends MovableRecyclerViewAdapter<ItemHolder> {
		private final Context ctx;
		private final MainActivityDelegate activity;
		private final PreferenceStore store;
		private final List<DashboardCard> cards = new ArrayList<>();
		private long ignoreClicksUntil;
		private boolean closed;

		private DashboardAdapter(MainActivityDelegate activity, Context ctx, PreferenceStore store) {
			this.activity = activity;
			this.ctx = ctx;
			this.store = store;
			reload();
		}

		private void reload() {
			int pos = findContinueCardPosition();
			DashboardCard continueCard = (pos == -1) ? null : cards.get(pos);
			cards.clear();
			if (continueCard != null) cards.add(continueCard);
			for (DashboardItems.Item item : DashboardItems.getDashboardItems(ctx, store)) {
				cards.add(DashboardCard.item(item));
			}
			notifyDataSetChanged();
			refreshContinueCard();
		}

		private void close() {
			closed = true;
		}

		@NonNull
		@Override
		public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.dashboard_item, parent, false);
			return new ItemHolder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
			DashboardCard card = cards.get(position);
			holder.icon.setImageResource(card.icon);
			holder.title.setText(card.title);
			holder.subtitle.setText(card.subtitle);
			holder.subtitle.setVisibility(TextUtils.isEmpty(card.subtitle) ? View.GONE : View.VISIBLE);
			holder.itemView.setOnClickListener(v -> {
				if (SystemClock.uptimeMillis() < ignoreClicksUntil) return;
				MainActivityDelegate a = activity;

				if (card.playable != null) {
					PlayableItem current = a.getCurrentPlayable();
					if ((current == null) || !Objects.equals(current.getId(), card.playable.getId())) {
						a.getMediaServiceBinder().playItem(card.playable);
					}
					a.goToItem(card.playable);
					return;
				}

				DashboardItems.Item item = card.item;
				if ((item == null) || (item.id == ID_NULL)) return;
				a.setActiveNavItemId(R.id.dashboard_fragment);
				a.showFragment(item.id);
			});
		}

		@Override
		public int getItemCount() {
			return cards.size();
		}

		private boolean isWide(int position) {
			return (position >= 0) && (position < cards.size()) && cards.get(position).wide;
		}

		private void refreshContinueCard() {
			if (closed) return;

			MainActivityDelegate a = activity;
			PlayableItem current = a.getCurrentPlayable();
			if (current != null) {
				setContinueCard(current, true);
				return;
			}

			a.getLib().getLastPlayedItem().main().onSuccess(item -> setContinueCard(item, false))
					.onFailure(err -> setContinueCard(null, false));
		}

		private int findContinueCardPosition() {
			for (int i = 0; i < cards.size(); i++) {
				if (cards.get(i).playable != null) return i;
			}
			return -1;
		}

		private void setContinueCard(PlayableItem item, boolean current) {
			if (closed) return;
			int pos = findContinueCardPosition();

			if (item == null) {
				if (pos != -1) {
					cards.remove(pos);
					notifyItemRemoved(pos);
				}
				return;
			}

			DashboardCard card = DashboardCard.continueItem(ctx, item, current);
			if (pos == -1) {
				cards.add(0, card);
				notifyItemInserted(0);
			} else {
				cards.set(pos, card);
				notifyItemChanged(pos);
			}
		}

		@Override
		protected void onItemDismiss(int position) {
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			if ((fromPosition < 0) || (toPosition < 0) ||
					(fromPosition >= cards.size()) || (toPosition >= cards.size())) {
				return false;
			}

			if (cards.get(fromPosition).fixed || cards.get(toPosition).fixed) {
				return false;
			}

			move(cards, fromPosition, toPosition);
			ignoreClicksUntil = SystemClock.uptimeMillis() + 500;
			DashboardItems.setDashboardOrder(store, getDashboardItems());
			return true;
		}

		@Override
		protected boolean isItemViewSwipeEnabled() {
			return false;
		}

		private List<DashboardItems.Item> getDashboardItems() {
			List<DashboardItems.Item> items = new ArrayList<>(cards.size());
			for (DashboardCard card : cards) {
				if (card.item != null) items.add(card.item);
			}
			return items;
		}
	}

	private static final class DashboardCard {
		final DashboardItems.Item item;
		final PlayableItem playable;
		final int icon;
		final CharSequence title;
		final CharSequence subtitle;
		final boolean fixed;
		final boolean wide;

		private DashboardCard(DashboardItems.Item item, PlayableItem playable, int icon,
													CharSequence title, CharSequence subtitle, boolean fixed, boolean wide) {
			this.item = item;
			this.playable = playable;
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
			this.fixed = fixed;
			this.wide = wide;
		}

		static DashboardCard item(DashboardItems.Item item) {
			return new DashboardCard(item, null, item.icon, item.title, item.subtitle, false, false);
		}

		static DashboardCard continueItem(Context ctx, PlayableItem playable, boolean current) {
			String subtitle = playable.getName();
			if (playable.getParent() != null) {
				String parent = playable.getParent().getName();
				if (!TextUtils.isEmpty(parent)) {
					subtitle = ctx.getString(R.string.dashboard_continue_sub, subtitle, parent);
				}
			}

			return new DashboardCard(null, playable, playable.getIcon(),
					ctx.getString(current ? R.string.dashboard_now_playing : R.string.dashboard_continue),
					subtitle, true, true);
		}
	}

	private static final class ItemHolder extends RecyclerView.ViewHolder {
		final ImageView icon;
		final TextView title;
		final TextView subtitle;

		private ItemHolder(@NonNull View itemView) {
			super(itemView);
			icon = itemView.findViewById(R.id.dashboard_item_icon);
			title = itemView.findViewById(R.id.dashboard_item_title);
			subtitle = itemView.findViewById(R.id.dashboard_item_subtitle);
		}
	}

	private static final class DashboardToolBarMediator implements ToolBarView.Mediator.BackTitle {
		static final DashboardToolBarMediator instance = new DashboardToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			ToolBarView.Mediator.BackTitle.super.enable(tb, f);
		}

		@Nullable
		@Override
		public View focusSearch(ToolBarView tb, View focused, int direction) {
			View v = ToolBarView.Mediator.BackTitle.super.focusSearch(tb, focused, direction);
			if ((v != null) || (direction != FOCUS_DOWN)) return v;
			ActivityDelegate a = ActivityDelegate.get(tb.getContext());
			View root = a.getActiveFragment() == null ? null : a.getActiveFragment().getView();
			return root instanceof RecyclerView ? ((RecyclerView) root).getChildAt(0) : null;
		}

		@Override
		public int getBackButtonVisibility(ActivityFragment f) {
			return View.GONE;
		}

		@Override
		public int getBackButtonId() {
			return me.aap.utils.R.id.tool_bar_back_button;
		}
	}

	private static final class DashboardFloatingButtonMediator implements FloatingButton.Mediator {
		static final DashboardFloatingButtonMediator instance = new DashboardFloatingButtonMediator();

		@Override
		public void enable(FloatingButton fb, ActivityFragment f) {
			fb.setVisibility(View.GONE);
			FloatingButton.Mediator.super.disable(fb);
		}

		@Override
		public void disable(FloatingButton fb) {
			FloatingButton.Mediator.super.disable(fb);
			fb.setVisibility(View.VISIBLE);
		}
	}
}
