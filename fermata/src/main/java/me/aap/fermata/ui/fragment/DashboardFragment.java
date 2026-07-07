package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_DOWN;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Recent;
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
	private RecyclerView list;
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
		this.list = list;
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
		if (adapter != null) adapter.refreshSmartTopCard();
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
		this.list = null;
		super.onDestroyView();
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		reload();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		DashboardAdapter adapter = this.adapter;
		if (adapter == null) return;
		if (prefs.contains(DashboardItems.PREF) && !adapter.isCallbackCall()) {
			adapter.reload();
		} else {
			adapter.refreshDashboardSummaries();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		refreshSmartTopCard();
	}

	@Override
	public void onPlaybackStateChanged(PlaybackStateCompat state) {
		refreshSmartTopCard();
	}

	@Override
	public void onPlaybackStopped() {
		refreshSmartTopCard();
	}

	private void refreshSmartTopCard() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.refreshSmartTopCard();
	}

	public void reload() {
		DashboardAdapter adapter = this.adapter;
		if (adapter != null) adapter.reload();
	}

	public void showHome() {
		refreshSmartTopCard();
		RecyclerView list = this.list;
		if (list != null) list.post(() -> list.scrollToPosition(0));
	}

	private static int getSpanCount(Context ctx) {
		DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		int minWidth = (int) (240 * dm.density);
		return Math.max(2, dm.widthPixels / Math.max(1, minWidth));
	}

	private static final class DashboardAdapter extends MovableRecyclerViewAdapter<ItemHolder> {
		private static final int VIEW_TYPE_CARD = 0;
		private static final int VIEW_TYPE_SMART_TOP = 1;
		private final Context ctx;
		private final MainActivityDelegate activity;
		private final PreferenceStore store;
		private final DashboardModelBuilder modelBuilder;
		private final List<DashboardCard> cards = new ArrayList<>();
		private long ignoreClicksUntil;
		private int smartRefreshGeneration;
		private boolean closed;

		private DashboardAdapter(MainActivityDelegate activity, Context ctx, PreferenceStore store) {
			this.activity = activity;
			this.ctx = ctx;
			this.store = store;
			modelBuilder = new DashboardModelBuilder(ctx, store);
			reload();
		}

		private void reload() {
			int pos = findSmartTopCardPosition();
			DashboardCard smartTopCard = (pos == -1) ? null : cards.get(pos);
			rebuildCards(smartTopCard);
			notifyDataSetChanged();
			refreshDashboardSummaries();
			refreshSmartTopCard();
		}

		private void rebuildCards(@Nullable DashboardCard smartTopCard) {
			modelBuilder.rebuild(cards, smartTopCard);
		}

		private void close() {
			closed = true;
		}

		@NonNull
		@Override
		public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			int layout = (viewType == VIEW_TYPE_SMART_TOP) ?
					R.layout.dashboard_smart_top_item : R.layout.dashboard_item;
			View v = LayoutInflater.from(parent.getContext())
					.inflate(layout, parent, false);
			return new ItemHolder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
			DashboardCard card = cards.get(position);
			boolean smartTop = card.fixed && card.wide;
			holder.icon.setImageResource(card.icon);
			holder.title.setText(card.title);
			holder.subtitle.setText(card.subtitle);
			holder.subtitle.setVisibility(TextUtils.isEmpty(card.subtitle) ? View.GONE : View.VISIBLE);
			boolean favorite = (card.playable != null) && card.playable.isFavoriteItem();
			holder.actions.setVisibility((card.playable != null) && card.wide ? View.VISIBLE : View.GONE);
			holder.playPause.setImageResource(card.playing ? R.drawable.pause : R.drawable.play);
			holder.playPause.setContentDescription(ctx.getString(card.playing ? R.string.pause : R.string.play));
			holder.favorite.setImageResource(favorite ? R.drawable.favorite_filled : R.drawable.favorite);
			holder.favorite.setContentDescription(ctx.getString(favorite ?
					R.string.favorites_remove : R.string.favorites_add));
			bindSmartTop(holder, card, smartTop);
			holder.itemView.setOnClickListener(v -> {
				if (!acceptClick()) return;
				MainActivityDelegate a = activity;

				if (card.playable != null) {
					PlayableItem current = a.getCurrentPlayable();
					if ((current == null) || !isSamePlayable(current, card.playable)) {
						a.getMediaServiceBinder().playItem(card.playable);
					} else if (!a.getMediaServiceBinder().isPlaying()) {
						a.getMediaServiceBinder().playItem(card.playable);
					}
					goToPlayable(a, card.playable);
					return;
				}

				if (card.targetId == ID_NULL) return;
				a.setActiveNavItemId(R.id.dashboard_fragment);
				a.showFragmentWhenReady(card.targetId);
			});
			holder.playPause.setOnClickListener(v -> {
				if (!acceptClick() || (card.playable == null)) return;
				MainActivityDelegate a = activity;
				FermataServiceUiBinder binder = a.getMediaServiceBinder();
				PlayableItem current = a.getCurrentPlayable();

				if ((current == null) || !isSamePlayable(current, card.playable)) {
					binder.playItem(card.playable);
				} else if (binder.isPlaying()) {
					binder.getMediaSessionCallback().onPause();
				} else {
					binder.playItem(card.playable);
				}
				refreshSmartTopCard();
			});
			if (holder.prev != null) {
				holder.prev.setOnClickListener(v -> {
					if (!acceptClick() || (card.playable == null)) return;
					activity.getMediaSessionCallback().onSkipToPrevious();
					refreshSmartTopCard();
				});
			}
			if (holder.next != null) {
				holder.next.setOnClickListener(v -> {
					if (!acceptClick() || (card.playable == null)) return;
					activity.getMediaSessionCallback().onSkipToNext();
					refreshSmartTopCard();
				});
			}
			holder.favorite.setOnClickListener(v -> {
				if (!acceptClick() || (card.playable == null)) return;
				if (card.playable.isFavoriteItem()) {
					card.playable.getLib().getFavorites().removeItem(card.playable)
							.main().onSuccess(done -> refreshSmartTopCard());
				} else {
					card.playable.getLib().getFavorites().addItem(card.playable)
							.main().onSuccess(done -> refreshSmartTopCard());
				}
			});
			holder.backToList.setOnClickListener(v -> {
				if (!acceptClick() || (card.playable == null)) return;
				goToPlayable(activity, card.playable);
			});
		}

		@Override
		public int getItemViewType(int position) {
			DashboardCard card = cards.get(position);
			return card.fixed && card.wide ? VIEW_TYPE_SMART_TOP : VIEW_TYPE_CARD;
		}

		@Override
		public int getItemCount() {
			return cards.size();
		}

		private boolean isWide(int position) {
			return (position >= 0) && (position < cards.size()) && cards.get(position).wide;
		}

		private boolean acceptClick() {
			long now = SystemClock.uptimeMillis();
			if (now < ignoreClicksUntil) return false;
			ignoreClicksUntil = now + 350;
			return true;
		}

		private void refreshSmartTopCard() {
			if (closed) return;
			int generation = ++smartRefreshGeneration;

			MainActivityDelegate a = activity;
			PlayableItem current = a.getCurrentPlayable();
			a.getLib().getRecent().getChildren().main().onSuccess(items -> {
				if (!isSmartRefreshActive(generation)) return;
				PlayableItem active = activity.getCurrentPlayable();
				if (active != null) {
					setSmartTopCard(DashboardCard.playable(ctx, active,
							a.getMediaServiceBinder().isPlaying(), getRecentPlayables(items, active)), generation);
				} else {
					PlayableItem recent = getFirstPlayable(items);
					if (recent != null) {
						setSmartTopCard(DashboardCard.playable(ctx, recent, false,
								getRecentPlayables(items, recent)), generation);
					} else {
						refreshLastPlayedTopCard(generation);
					}
				}
			}).onFailure(err -> refreshLastPlayedTopCard(generation));
		}

		private void refreshLastPlayedTopCard(int generation) {
			if (!isSmartRefreshActive(generation)) return;
			activity.getLib().getLastPlayedItem().main().onSuccess(item -> {
				if (!isSmartRefreshActive(generation)) return;
				if (activity.getCurrentPlayable() != null) {
					refreshSmartTopCard();
					return;
				}
				if (item != null) setSmartTopCard(DashboardCard.playable(ctx, item, false, null), generation);
				else refreshRecentTopCard(generation);
			}).onFailure(err -> refreshRecentTopCard(generation));
		}

		@Nullable
		private PlayableItem getFirstPlayable(List<Item> items) {
			for (Item item : items) {
				if (item instanceof PlayableItem) return (PlayableItem) item;
			}
			return null;
		}

		private void refreshRecentTopCard(int generation) {
			if (!isSmartRefreshActive(generation)) return;
			activity.getLib().getRecent().getChildren().main().onSuccess(items -> {
				if (!isSmartRefreshActive(generation)) return;
				if (activity.getCurrentPlayable() != null) {
					refreshSmartTopCard();
					return;
				}
				setSmartTopCard(DashboardCard.recent(ctx, items), generation);
			}).onFailure(err -> setSmartTopCard(null, generation));
		}

		private boolean isSmartRefreshActive(int generation) {
			return !closed && (generation == smartRefreshGeneration);
		}

		private int findSmartTopCardPosition() {
			for (int i = 0; i < cards.size(); i++) {
				if (cards.get(i).fixed) return i;
			}
			return -1;
		}

		private void setSmartTopCard(DashboardCard card, int generation) {
			if (!isSmartRefreshActive(generation)) return;
			rebuildCards(card);
			notifyDataSetChanged();
			refreshDashboardSummaries();
		}

		private void refreshDashboardSummaries() {
			if (closed) return;
			activity.getLib().getFavorites().getChildren().main().onSuccess(items ->
					updateCardSubtitle(R.id.favorites_fragment, DashboardCard.itemSummary(items,
							ctx.getString(R.string.dashboard_favorites_sub))));
			activity.getLib().getRecent().getChildren().main().onSuccess(items ->
					updateCardSubtitle(R.id.recent_fragment, DashboardCard.itemSummary(items,
							ctx.getString(R.string.dashboard_recent_sub))));
		}

		private void updateCardSubtitle(int targetId, CharSequence subtitle) {
			if (closed) return;
			for (int i = 0; i < cards.size(); i++) {
				DashboardCard card = cards.get(i);
				if (card.fixed || (card.targetId != targetId) ||
						TextUtils.equals(card.subtitle, subtitle)) continue;
				cards.set(i, card.withSubtitle(subtitle));
				notifyItemChanged(i);
				return;
			}
		}

		private boolean isSamePlayable(PlayableItem a, PlayableItem b) {
			return TextUtils.equals(a.getOrigId(), b.getOrigId()) || TextUtils.equals(a.getId(), b.getId());
		}

		private List<PlayableItem> getRecentPlayables(List<Item> items, @Nullable PlayableItem exclude) {
			List<PlayableItem> recent = new ArrayList<>(3);
			for (Item item : items) {
				if (!(item instanceof PlayableItem playable)) continue;
				if ((exclude != null) && isSamePlayable(exclude, playable)) continue;
				recent.add(playable);
				if (recent.size() == 3) break;
			}
			return recent;
		}

		private void goToPlayable(MainActivityDelegate a, PlayableItem item) {
			String origId = item.getOrigId();
			if (TextUtils.isEmpty(origId) || TextUtils.equals(origId, item.getId())) {
				a.goToItem(item);
				return;
			}

			a.goToItem(origId).onSuccess(i -> {
				if (i == null) a.goToItem(item);
			}).onFailure(err -> a.goToItem(item));
		}

		private void bindSmartTop(ItemHolder holder, DashboardCard card, boolean smartTop) {
			if (holder.recentPanel == null) return;
			holder.recentPanel.setVisibility(smartTop ? View.VISIBLE : View.GONE);
			holder.recentPanel.setOnClickListener(v -> {
				if (!acceptClick()) return;
				activity.setActiveNavItemId(R.id.dashboard_fragment);
				activity.showFragment(R.id.recent_fragment);
			});
			TextView[] views = holder.recentItems;
			List<PlayableItem> items = card.recentItems;

			for (int i = 0; i < views.length; i++) {
				TextView view = views[i];
				if (view == null) continue;
				if ((items == null) || (i >= items.size())) {
					view.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
					view.setText(i == 0 ? ctx.getString(R.string.dashboard_recent_sub) : "");
					view.setOnClickListener(null);
					view.setClickable(false);
					view.setFocusable(false);
					continue;
				}

				PlayableItem item = items.get(i);
				view.setVisibility(View.VISIBLE);
				view.setText(item.getName());
				view.setClickable(true);
				view.setFocusable(true);
				view.setOnClickListener(v -> {
					if (!acceptClick()) return;
					activity.getMediaServiceBinder().playItem(item);
					goToPlayable(activity, item);
				});
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
			return modelBuilder.getDashboardItems(cards);
		}
	}

	private static final class DashboardModelBuilder {
		private final Context ctx;
		private final PreferenceStore store;

		private DashboardModelBuilder(Context ctx, PreferenceStore store) {
			this.ctx = ctx;
			this.store = store;
		}

		private void rebuild(List<DashboardCard> cards, @Nullable DashboardCard smartTopCard) {
			cards.clear();
			if (smartTopCard != null) cards.add(smartTopCard);
			for (DashboardItems.Item item : DashboardItems.getDashboardItems(ctx, store)) {
				if (item.id == R.id.recent_fragment) continue;
				if ((smartTopCard != null) && (smartTopCard.targetId == item.id)) continue;
				cards.add(DashboardCard.item(item));
			}
		}

		private List<DashboardItems.Item> getDashboardItems(List<DashboardCard> cards) {
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
		final int targetId;
		final int icon;
		final CharSequence title;
		final CharSequence subtitle;
		final boolean fixed;
		final boolean wide;
		final boolean playing;
		@Nullable
		final List<PlayableItem> recentItems;

		private DashboardCard(DashboardItems.Item item, PlayableItem playable, int targetId, int icon,
													CharSequence title, CharSequence subtitle, boolean fixed, boolean wide,
													boolean playing, @Nullable List<PlayableItem> recentItems) {
			this.item = item;
			this.playable = playable;
			this.targetId = targetId;
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
			this.fixed = fixed;
			this.wide = wide;
			this.playing = playing;
			this.recentItems = recentItems;
		}

		static DashboardCard item(DashboardItems.Item item) {
			return new DashboardCard(item, null, item.id, item.icon, item.title, item.subtitle, false, false,
					false, null);
		}

		DashboardCard withSubtitle(CharSequence subtitle) {
			return new DashboardCard(item, playable, targetId, icon, title, subtitle, fixed, wide, playing,
					recentItems);
		}

		static DashboardCard playable(Context ctx, PlayableItem playable, boolean playing,
																	@Nullable List<PlayableItem> recentItems) {
			String subtitle = playable.getName();
			if ((playable.getParent() != null) && !(playable.getParent() instanceof Recent)) {
				String parent = playable.getParent().getName();
				if (!TextUtils.isEmpty(parent)) {
					subtitle = ctx.getString(R.string.dashboard_continue_sub, subtitle, parent);
				}
			}

			return new DashboardCard(null, playable, ID_NULL, playable.getIcon(),
					ctx.getString(playing ? R.string.dashboard_now_playing : R.string.dashboard_continue),
					subtitle, true, true, playing, recentItems);
		}

		static DashboardCard recent(Context ctx, List<Item> items) {
			List<PlayableItem> recent = new ArrayList<>(3);
			for (Item item : items) {
				if (item instanceof PlayableItem playable) recent.add(playable);
				if (recent.size() == 3) break;
			}
			return new DashboardCard(null, null, R.id.recent_fragment, R.drawable.timer,
					ctx.getString(R.string.recent),
					itemSummary(items, ctx.getString(R.string.dashboard_recent_sub)), true, true, false, recent);
		}

		static CharSequence itemSummary(List<Item> items, CharSequence fallback) {
			StringBuilder subtitle = new StringBuilder();
			int count = 0;

			for (Item item : items) {
				String name = item.getName();
				if (TextUtils.isEmpty(name)) continue;
				if (count++ != 0) subtitle.append(" - ");
				subtitle.append(name);
				if (count == 3) break;
			}

			return (count == 0) ? fallback : subtitle;
		}
	}

	private static final class ItemHolder extends RecyclerView.ViewHolder {
		final ImageView icon;
		final TextView title;
		final TextView subtitle;
		final View actions;
		final ImageButton playPause;
		final ImageButton favorite;
		final ImageButton backToList;
		final ImageButton prev;
		final ImageButton next;
		final View recentPanel;
		final TextView[] recentItems;

		private ItemHolder(@NonNull View itemView) {
			super(itemView);
			icon = itemView.findViewById(R.id.dashboard_item_icon);
			title = itemView.findViewById(R.id.dashboard_item_title);
			subtitle = itemView.findViewById(R.id.dashboard_item_subtitle);
			actions = itemView.findViewById(R.id.dashboard_item_actions);
			playPause = itemView.findViewById(R.id.dashboard_action_play_pause);
			favorite = itemView.findViewById(R.id.dashboard_action_favorite);
			backToList = itemView.findViewById(R.id.dashboard_action_back_to_list);
			prev = itemView.findViewById(R.id.dashboard_action_prev);
			next = itemView.findViewById(R.id.dashboard_action_next);
			recentPanel = itemView.findViewById(R.id.dashboard_recent_panel);
			recentItems = new TextView[]{
					itemView.findViewById(R.id.dashboard_recent_item_1),
					itemView.findViewById(R.id.dashboard_recent_item_2),
					itemView.findViewById(R.id.dashboard_recent_item_3)
			};
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
