package me.aap.fermata.addon.radio;

import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.fermata.BuildConfig;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

class RadioBrowserApi {
	static final String USER_AGENT = "FermataX/" + BuildConfig.VERSION_NAME + " (" +
			App.get().getPackageName() + ")";
	private static final String[] BASE_URLS = {
			"https://all.api.radio-browser.info/json/",
			"https://de1.api.radio-browser.info/json/"
	};
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 30000;

	FutureSupplier<List<RadioBrowserStation>> getPopularStations(int limit) {
		return getStations("stations/topclick/" + limit + "?hidebroken=true");
	}

	FutureSupplier<List<RadioBrowserStation>> getTopVotedStations(int limit) {
		return getStations("stations/topvote/" + limit + "?hidebroken=true");
	}

	FutureSupplier<List<RadioBrowserStation>> getCountryStations(String countryCode, int limit) {
		return getStations("stations/search?hidebroken=true&order=clickcount&reverse=true&limit=" +
				limit + "&countrycode=" + enc(countryCode));
	}

	FutureSupplier<List<RadioBrowserStation>> getTagStations(String tag, int limit) {
		return getStations("stations/search?hidebroken=true&order=clickcount&reverse=true&limit=" +
				limit + "&tagList=" + enc(tag));
	}

	FutureSupplier<List<DirectoryEntry>> getCountries(int limit) {
		return App.get().execute(() -> request("countries?hidebroken=true&order=stationcount&reverse=true",
				reader -> readDirectories(reader, limit, true)));
	}

	FutureSupplier<List<DirectoryEntry>> getTags(int limit) {
		return App.get().execute(() -> request("tags?hidebroken=true&order=stationcount&reverse=true",
				reader -> readDirectories(reader, limit, false)));
	}

	FutureSupplier<RadioBrowserStation> getStation(String uuid) {
		return App.get().execute(() -> request("stations/byuuid/" + enc(uuid), reader -> {
			reader.beginArray();
			RadioBrowserStation station = reader.hasNext() ? readStation(reader) : null;
			while (reader.hasNext()) reader.skipValue();
			reader.endArray();
			return station;
		}));
	}

	FutureSupplier<Void> click(String uuid) {
		return App.get().execute(() -> {
			request("url/" + enc(uuid), reader -> {
				reader.skipValue();
				return null;
			});
			return null;
		});
	}

	private FutureSupplier<List<RadioBrowserStation>> getStations(String path) {
		return App.get().execute(() -> request(path, this::readStations));
	}

	private <T> T request(String path, Parser<T> parser) throws IOException {
		IOException failure = null;

		for (String baseUrl : BASE_URLS) {
			try {
				return request(baseUrl, path, parser);
			} catch (HttpStatusException ex) {
				if (ex.code < 500) throw ex;
				failure = ex;
			} catch (IOException ex) {
				failure = ex;
			}
		}

		throw failure;
	}

	private <T> T request(String baseUrl, String path, Parser<T> parser) throws IOException {
		HttpURLConnection con = open(baseUrl, path);
		int code = con.getResponseCode();
		if ((code < 200) || (code >= 300)) throw new HttpStatusException(code);

		try (InputStream in = decode(con, con.getInputStream());
				 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
				 JsonReader json = new JsonReader(reader)) {
			return parser.parse(json);
		} finally {
			con.disconnect();
		}
	}

	private HttpURLConnection open(String baseUrl, String path) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(baseUrl + path).openConnection();
		con.setConnectTimeout(CONNECT_TIMEOUT);
		con.setReadTimeout(READ_TIMEOUT);
		con.setRequestProperty("Accept", "application/json");
		con.setRequestProperty("Accept-Encoding", "gzip, deflate");
		con.setRequestProperty("User-Agent", USER_AGENT);
		return con;
	}

	private List<RadioBrowserStation> readStations(JsonReader reader) throws IOException {
		List<RadioBrowserStation> stations = new ArrayList<>();
		reader.beginArray();
		while (reader.hasNext()) {
			RadioBrowserStation station = readStation(reader);
			if ((station != null) && station.hasStreamUrl()) stations.add(station);
		}
		reader.endArray();
		return stations;
	}

	@Nullable
	private RadioBrowserStation readStation(JsonReader reader) throws IOException {
		String uuid = "";
		String name = "";
		String url = "";
		String urlResolved = "";
		String homepage = "";
		String favicon = "";
		String tags = "";
		String country = "";
		String countryCode = "";
		String language = "";
		String codec = "";
		int bitrate = 0;
		int votes = 0;
		int clickCount = 0;

		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "stationuuid" -> uuid = nextString(reader);
				case "name" -> name = nextString(reader);
				case "url" -> url = nextString(reader);
				case "url_resolved" -> urlResolved = nextString(reader);
				case "homepage" -> homepage = nextString(reader);
				case "favicon" -> favicon = nextString(reader);
				case "tags" -> tags = nextString(reader);
				case "country" -> country = nextString(reader);
				case "countrycode" -> countryCode = nextString(reader);
				case "language" -> language = nextString(reader);
				case "codec" -> codec = nextString(reader);
				case "bitrate" -> bitrate = nextInt(reader);
				case "votes" -> votes = nextInt(reader);
				case "clickcount" -> clickCount = nextInt(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();

		if (uuid.isEmpty() || (url.isEmpty() && urlResolved.isEmpty())) return null;
		return new RadioBrowserStation(uuid, name, url, urlResolved, homepage, favicon, tags,
				country, countryCode, language, codec, bitrate, votes, clickCount);
	}

	private List<DirectoryEntry> readDirectories(JsonReader reader, int limit, boolean country)
			throws IOException {
		List<DirectoryEntry> entries = new ArrayList<>(limit);
		reader.beginArray();
		while (reader.hasNext()) {
			String name = "";
			String code = "";
			int stationCount = 0;
			reader.beginObject();
			while (reader.hasNext()) {
				switch (reader.nextName()) {
					case "name" -> name = nextString(reader);
					case "iso_3166_1" -> code = nextString(reader);
					case "stationcount" -> stationCount = nextInt(reader);
					default -> reader.skipValue();
				}
			}
			reader.endObject();

			if (!name.isEmpty() && (stationCount > 0)) {
				entries.add(new DirectoryEntry(name, country && !code.isEmpty() ? code : name,
						stationCount));
			}
			if (entries.size() >= limit) {
				while (reader.hasNext()) reader.skipValue();
				break;
			}
		}
		reader.endArray();
		return entries;
	}

	@NonNull
	private static String nextString(JsonReader reader) throws IOException {
		JsonToken token = reader.peek();
		if (token == JsonToken.NULL) {
			reader.nextNull();
			return "";
		} else if (token == JsonToken.BOOLEAN) {
			return Boolean.toString(reader.nextBoolean());
		} else if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
			return reader.nextString().trim();
		} else {
			reader.skipValue();
			return "";
		}
	}

	private static int nextInt(JsonReader reader) throws IOException {
		JsonToken token = reader.peek();
		if (token == JsonToken.NUMBER) return reader.nextInt();
		if (token == JsonToken.STRING) {
			String s = reader.nextString();
			if (s.isEmpty()) return 0;
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignore) {
				return 0;
			}
		}
		reader.skipValue();
		return 0;
	}

	private static InputStream decode(HttpURLConnection con, InputStream input) throws IOException {
		InputStream in = new BufferedInputStream(input);
		String encoding = con.getContentEncoding();
		if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(in);
		if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(in);
		return in;
	}

	private static String enc(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static final class DirectoryEntry {
		final String name;
		final String code;
		final int stationCount;

		DirectoryEntry(String name, String code, int stationCount) {
			this.name = name;
			this.code = code;
			this.stationCount = stationCount;
		}
	}

	private interface Parser<T> {
		T parse(JsonReader reader) throws IOException;
	}

	private static final class HttpStatusException extends IOException {
		final int code;

		HttpStatusException(int code) {
			super("Radio Browser HTTP " + code);
			this.code = code;
		}
	}
}
