package me.aap.fermata.addon.tv.xtream;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import me.aap.utils.app.App;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
final class XtreamCredentials {
	private static final String PREFS = "xtream_credentials";
	private static final String USERNAME_PREFIX = "username#";
	private static final String PASSWORD_PREFIX = "password#";
	private static volatile Store store;

	private XtreamCredentials() {
	}

	static Credentials load(int sourceId) {
		return load(sourceId, store());
	}

	static void requireAvailable() {
		if (store() == null) {
			throw new IllegalStateException("Encrypted Xtream credential storage is unavailable");
		}
	}

	static Credentials load(int sourceId, Store s) {
		if (s == null) return null;
		String username = s.getString(usernameKey(sourceId));
		String password = s.getString(passwordKey(sourceId));
		return ((username == null) || (password == null)) ? null : new Credentials(username, password);
	}

	static void save(PreferenceStore.Edit edit, int sourceId, String username, String password) {
		save(edit, sourceId, username, password, store());
	}

	static void save(PreferenceStore.Edit edit, int sourceId, String username, String password,
									 Store s) {
		if (s == null) {
			throw new IllegalStateException("Encrypted Xtream credential storage is unavailable");
		}
		s.putString(usernameKey(sourceId), username);
		s.putString(passwordKey(sourceId), password);
		edit.removePref(XtreamAccount.usernamePref(sourceId));
		edit.removePref(XtreamAccount.passwordPref(sourceId));
	}

	static void remove(PreferenceStore.Edit edit, int sourceId) {
		remove(edit, sourceId, store());
	}

	static void remove(PreferenceStore.Edit edit, int sourceId, @Nullable Store s) {
		if (s != null) {
			s.remove(usernameKey(sourceId));
			s.remove(passwordKey(sourceId));
		}

		edit.removePref(XtreamAccount.usernamePref(sourceId));
		edit.removePref(XtreamAccount.passwordPref(sourceId));
	}

	static String usernameKey(int sourceId) {
		return USERNAME_PREFIX + sourceId;
	}

	static String passwordKey(int sourceId) {
		return PASSWORD_PREFIX + sourceId;
	}

	@Nullable
	private static Store store() {
		Store s = store;
		if (s != null) return s;

		synchronized (XtreamCredentials.class) {
			s = store;
			if (s != null) return s;

			try {
				Context ctx = App.get();
				MasterKey key = new MasterKey.Builder(ctx)
						.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
						.build();
				SharedPreferences prefs = EncryptedSharedPreferences.create(
						ctx,
						PREFS,
						key,
						EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
						EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
				return store = new SharedPrefsStore(prefs);
			} catch (GeneralSecurityException | IOException | RuntimeException ex) {
				Log.e(ex, "Failed to open encrypted Xtream credential storage");
				return null;
			}
		}
	}

	interface Store {
		@Nullable
		String getString(String key);

		void putString(String key, String value);

		void remove(String key);
	}

	static final class Credentials {
		final String username;
		final String password;

		Credentials(String username, String password) {
			this.username = username;
			this.password = password;
		}
	}

	private static final class SharedPrefsStore implements Store {
		private final SharedPreferences prefs;

		SharedPrefsStore(SharedPreferences prefs) {
			this.prefs = prefs;
		}

		@Nullable
		@Override
		public String getString(String key) {
			return prefs.getString(key, null);
		}

		@Override
		public void putString(String key, String value) {
			prefs.edit().putString(key, value).apply();
		}

		@Override
		public void remove(String key) {
			prefs.edit().remove(key).apply();
		}
	}
}
