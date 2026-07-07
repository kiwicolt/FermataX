package me.aap.fermata.addon;

import static java.util.Collections.singletonList;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.app.Activity;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class AddonManager extends BasicEventBroadcaster<AddonManager.Listener>
		implements PreferenceStore.Listener {
	private static final String CHANNEL_ID = "fermata.addon.install";
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT", false);
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT_V2 =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT_V2", false);
	private static final AddonRegistry registry = AddonRegistry.get();
	private final Map<Object, FermataAddon> map = new HashMap<>();
	private final List<FermataAddon> addons = new ArrayList<>(registry.size());
	private final Map<String, FutureSupplier<?>> installing = new HashMap<>();
	private final Set<String> failedAddons = new HashSet<>();
	private final AddonLauncher launcher = new AddonLauncher(this);
	private final PreferenceStore store;

	public AddonManager(PreferenceStore store) {
		this.store = store;
		enableAddonsByDefault(store);

		for (AddonInfo i : registry.getAvailable()) {
			if (!store.getBooleanPref(i.enabledPref)) continue;
			if (!loadAddon(i) && !failedAddons.contains(i.className)) install(i);
		}

		store.addBroadcastListener(this);
	}

	private static void enableAddonsByDefault(PreferenceStore store) {
		if (store.getBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2)) return;

		try (PreferenceStore.Edit edit = store.editPreferenceStore(false)) {
			for (AddonInfo i : registry.getAvailable()) {
				if (!i.enableByDefault) continue;
				if (store.hasPref(i.enabledPref, false)) continue;
				edit.setBooleanPref(i.enabledPref, true);
			}

			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT, true);
			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2, true);
		}
	}

	public static AddonManager get() {
		return FermataApplication.get().getAddonManager();
	}

	@Nullable
	public synchronized FermataAddon getAddon(String moduleOrClassName) {
		return map.get(moduleOrClassName);
	}

	public synchronized List<AddonInfo> getAddonInfos() {
		return registry.getAvailable();
	}

	@Nullable
	public synchronized AddonInfo getAddonInfo(Object moduleClassOrId) {
		if (moduleClassOrId == null) return null;
		AddonInfo info = registry.getAvailable(moduleClassOrId);
		if (info != null) return info;

		for (AddonInfo i : registry.getAvailable()) {
			if (moduleClassOrId.equals(i.className)) return i;
			FermataAddon a = map.get(i.className);
			if ((a != null) && moduleClassOrId.equals(a.getAddonId())) return i;
		}

		return null;
	}

	public synchronized AddonState getAddonState(AddonInfo i) {
		if (!registry.isAvailable(i) || !store.getBooleanPref(i.enabledPref)) return AddonState.DISABLED;
		if (isLoaded(i)) return AddonState.LOADED;
		if (failedAddons.contains(i.className)) return AddonState.FAILED;
		if (installing.containsKey(i.className)) return AddonState.LOADING;
		return AddonState.ENABLED_PENDING;
	}

	public <A extends FermataAddon> FutureSupplier<A> getOrInstallAddon(Class<A> c) {
		return getOrInstallAddon(c.getName()).cast();
	}

	public synchronized FutureSupplier<FermataAddon> getOrInstallAddon(String moduleOrClassName) {
		return launcher.getOrInstallAddon(moduleOrClassName);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public synchronized <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) map.get(c.getName());
	}

	public synchronized Collection<FermataAddon> getAddons() {
		return new ArrayList<>(addons);
	}

	/**
	 * @noinspection unchecked
	 */
	public synchronized <A extends FermataAddon> List<A> getAddons(Class<A> c) {
		return (List<A>) CollectionUtils.filter(addons, c::isInstance);
	}

	public synchronized boolean hasAddon(@IdRes int id) {
		return map.containsKey(id);
	}

	@Nullable
	public synchronized ActivityFragment createFragment(@IdRes int id) {
		FermataAddon a = map.get(id);
		if (a instanceof FermataFragmentAddon fa) return fa.createFragment();
		return null;
	}

	@Nullable
	public synchronized FutureSupplier<? extends Item>
	getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		for (FermataAddon a : addons) {
			if (a instanceof MediaLibAddon) {
				FutureSupplier<? extends Item> i = ((MediaLibAddon) a).getItem(lib, scheme, id);
				if (i != null) return i;
			}
		}

		return null;
	}

	@Nullable
	public synchronized MediaLibAddon getMediaLibAddon(Item i) {
		for (FermataAddon a : addons) {
			if (a instanceof MediaLibAddon mla) {
				if (mla.isSupportedItem(i)) return mla;
			}
		}

		return null;
	}

	@IdRes
	public synchronized int getFragmentId(Item i) {
		MediaLibAddon a = getMediaLibAddon(i);
		if (a == null) return 0;
		AddonInfo info = a.getInfo();
		return (info.addonId != 0) ? info.addonId : a.getFragmentId();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		for (AddonInfo i : registry.getAll()) {
			if (prefs.contains(i.enabledPref)) {
				if (store.getBooleanPref(i.enabledPref)) install(i);
				else uninstall(i);
			}
		}
	}

	private synchronized void install(AddonInfo i) {
		if (failedAddons.contains(i.className) || loadAddon(i) || installing.containsKey(i.className)) return;
		for (String dep : i.depends) install(FermataAddon.findAddonInfo(dep));

		var task = ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon, i.moduleName, null,
				MainActivity.class).then(a -> createInstaller(a, i).install(i.moduleName)).onSuccess(v -> {
			Log.i("Module installed: ", i.moduleName);
			if (loadAddon(i)) return;
			if (failedAddons.contains(i.className)) return;
			Log.i("Failed to load addon, retrying: ", i.className);
			var p = new Promise<Void>();
			installing.put(i.className, p);
			p.thenRun(() -> installCompleted(i, p));
			scheduleLoadAddon(i, p, 1);
		}).onFailure(err -> {
			if (!isCancellation(err)) Log.e(err, "Failed to install module: ", i.moduleName);
		});
		installing.put(i.className, task);
		task.thenRun(() -> installCompleted(i, task));
		scheduleLoadAddon(i, task, 1);
	}

	synchronized void installAddon(AddonInfo i) {
		install(i);
	}

	synchronized FutureSupplier<?> getInstallingTask(AddonInfo i) {
		return installing.get(i.className);
	}

	private synchronized void installCompleted(AddonInfo i, FutureSupplier<Void> task) {
		CollectionUtils.remove(installing, i.className, task);
	}

	private synchronized boolean loadAddon(AddonInfo i) {
		if (isLoaded(i)) return true;
		try {
			FermataAddon a =
					(FermataAddon) Class.forName(i.className).getDeclaredConstructor().newInstance();
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			a.install();
			add(a);
			fireBroadcastEvent(c -> c.onAddonChanged(this, i, true));
			prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));
			Log.i("Addon loaded: ", i.className);
			return true;
		} catch (Exception | LinkageError ex) {
			if (ex instanceof ClassNotFoundException) return false;
			if (ex instanceof LinkageError) failedAddons.add(i.className);
			Log.e(ex, "Failed to load addon: ", i.className);
			return false;
		}
	}

	private synchronized void scheduleLoadAddon(AddonInfo i, FutureSupplier<?> task, int counter) {
		App.get().getHandler().postDelayed(() -> {
			if (installing.get(i.className) != task) return;
			if (failedAddons.contains(i.className)) {
				task.cancel();
				return;
			}
			if (loadAddon(i)) {
				task.cancel();
			} else if (counter == 180) {
				Log.e("Failed load addon in 180 seconds: ", i.className);
				task.cancel();
			} else {
				scheduleLoadAddon(i, task, counter + 1);
			}
		}, 1000);
	}

	private synchronized void uninstall(AddonInfo i) {
		failedAddons.remove(i.className);
		var task = installing.get(i.className);
		if (task != null) task.cancel();
		var removed = remove(i);

		if (removed != null) {
			removed.uninstall();
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			fireBroadcastEvent(c -> c.onAddonChanged(this, i, false));
			prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));

			for (AddonInfo ai : registry.getAll()) {
				if (ai.moduleName.equals(i.moduleName)) return;
			}

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon, i.moduleName, null,
					MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = createInstaller(a, i);
				inst.uninstall(i.moduleName).onSuccess(v -> Log.i("Module uninstalled: ", i.moduleName));
			});
		}
	}

	private synchronized void add(FermataAddon a) {
		var info = a.getInfo();
		addons.add(a);
		map.put(a.getAddonId(), a);
		map.put(info.className, a);
		if (registry.isUniqueModule(info)) map.put(info.moduleName, a);
	}

	private synchronized FermataAddon remove(AddonInfo info) {
		FermataAddon a = map.remove(info.className);
		if (a == null) return null;
		addons.remove(a);
		map.remove(a.getAddonId());
		if (registry.isUniqueModule(info)) map.remove(info.moduleName);
		return a;
	}

	private boolean isLoaded(AddonInfo i) {
		return map.containsKey(i.className);
	}

	private static DynamicModuleInstaller createInstaller(Activity a, AddonInfo ai) {
		DynamicModuleInstaller i = new DynamicModuleInstaller(a);
		String name = a.getString(ai.addonName);
		i.setSmallIcon(R.drawable.notification);
		i.setTitle(a.getString(R.string.module_installation, name));
		i.setNotificationChannel(CHANNEL_ID, a.getString(R.string.installing, name));
		i.setPendingMessage(a.getString(R.string.install_pending, name));
		i.setDownloadingMessage(a.getString(R.string.downloading, name));
		i.setInstallingMessage(a.getString(R.string.installing, name));
		return i;
	}

	public interface Listener {
		void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed);
	}
}
