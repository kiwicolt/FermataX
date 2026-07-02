package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedConsumer;

/**
 * @author Andrey Pavlenko
 */
public class XtreamApi {
	private final XtreamAccount account;
	private final XtreamJsonStreamParser parser = new XtreamJsonStreamParser();
	private final FutureRef<List<XtreamCategory>> liveCategories =
			FutureRef.create(this::loadLiveCategories);
	private final FutureRef<List<XtreamCategory>> vodCategories =
			FutureRef.create(this::loadVodCategories);
	private final FutureRef<List<XtreamCategory>> seriesCategories =
			FutureRef.create(this::loadSeriesCategories);
	private final Map<String, FutureRef<List<XtreamChannel>>> liveStreams =
			new ConcurrentHashMap<>();
	private final Map<String, FutureRef<List<XtreamMovie>>> vodStreams =
			new ConcurrentHashMap<>();
	private final Map<String, FutureRef<List<XtreamSeries>>> series =
			new ConcurrentHashMap<>();
	private final Map<Integer, FutureRef<List<XtreamSeason>>> seriesSeasons =
			new ConcurrentHashMap<>();
	private final Map<Integer, FutureRef<List<XtreamEpgProgram>>> epg =
			new ConcurrentHashMap<>();

	public XtreamApi(XtreamAccount account) {
		this.account = account;
	}

	public FutureSupplier<XtreamStatus> validate() {
		return request(null, null, null, parser::parseStatus);
	}

	public FutureSupplier<XtreamHealth> healthCheck() {
		XtreamHealth health = new XtreamHealth(account);
		return validate().then(status -> {
			health.setStatus(status);
			IOException failure = health.accountFailure();
			return (failure != null) ? failed(failure) : checkCategories(health);
		});
	}

	public FutureSupplier<List<XtreamCategory>> getLiveCategories() {
		return liveCategories.get();
	}

	public FutureSupplier<List<XtreamCategory>> getVodCategories() {
		return vodCategories.get();
	}

	public FutureSupplier<List<XtreamCategory>> getSeriesCategories() {
		return seriesCategories.get();
	}

	public FutureSupplier<List<XtreamChannel>> getLiveStreams(String categoryId) {
		return liveStreams.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadLiveStreams(categoryId))).get();
	}

	public FutureSupplier<List<XtreamMovie>> getVodStreams(String categoryId) {
		return vodStreams.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadVodStreams(categoryId))).get();
	}

	public FutureSupplier<List<XtreamSeries>> getSeries(String categoryId) {
		return series.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadSeries(categoryId))).get();
	}

	public FutureSupplier<List<XtreamSeason>> getSeriesSeasons(int seriesId) {
		return seriesSeasons.computeIfAbsent(seriesId,
				key -> FutureRef.create(() -> loadSeriesSeasons(seriesId))).get();
	}

	public FutureSupplier<List<XtreamEpgProgram>> getEpg(int streamId) {
		return epg.computeIfAbsent(streamId, key -> FutureRef.create(() -> loadEpg(streamId))).get();
	}

	public void warmUp() {
		getLiveCategories();
		getVodCategories();
		getSeriesCategories();
	}

	public void clearCache() {
		liveCategories.clear();
		vodCategories.clear();
		seriesCategories.clear();
		liveStreams.clear();
		vodStreams.clear();
		series.clear();
		seriesSeasons.clear();
		epg.clear();
	}

	private FutureSupplier<List<XtreamCategory>> loadLiveCategories() {
		return requestList("get_live_categories", null, null, parser::parseLiveCategories);
	}

	private FutureSupplier<List<XtreamCategory>> loadVodCategories() {
		return requestList("get_vod_categories", null, null, parser::parseVodCategories);
	}

	private FutureSupplier<List<XtreamCategory>> loadSeriesCategories() {
		return requestList("get_series_categories", null, null, parser::parseSeriesCategories);
	}

	private FutureSupplier<XtreamHealth> checkCategories(XtreamHealth health) {
		return getLiveCategories().then(live -> getVodCategories().then(vod ->
				getSeriesCategories().then(series -> {
					health.setCategoryCounts(live.size(), vod.size(), series.size());
					if (health.getTotalCategories() == 0) return failed(health.noCategoriesFailure());
					return live.isEmpty() ? completed(health) : probeFirstLiveStream(health);
				})));
	}

	private FutureSupplier<XtreamHealth> probeFirstLiveStream(XtreamHealth health) {
		return getFirstLiveStream().then(channel -> {
			if (channel == null) return failed(health.noLiveStreamsFailure());

			health.setTestedStream(channel);
			return probeStream(health, account.buildLiveStreamUrl(channel.getStreamId()));
		});
	}

	private FutureSupplier<List<XtreamChannel>> loadLiveStreams(String categoryId) {
		return requestList("get_live_streams", "category_id", categoryId, parser::parseLiveStreams);
	}

	private FutureSupplier<XtreamChannel> getFirstLiveStream() {
		return request("get_live_streams", null, null, parser::parseFirstLiveStream);
	}

	private FutureSupplier<List<XtreamMovie>> loadVodStreams(String categoryId) {
		return requestList("get_vod_streams", "category_id", categoryId, parser::parseVodStreams);
	}

	private FutureSupplier<List<XtreamSeries>> loadSeries(String categoryId) {
		return requestList("get_series", "category_id", categoryId, parser::parseSeries);
	}

	private FutureSupplier<List<XtreamSeason>> loadSeriesSeasons(int seriesId) {
		return request("get_series_info", "series_id", String.valueOf(seriesId),
				parser::parseSeriesInfo);
	}

	private FutureSupplier<List<XtreamEpgProgram>> loadEpg(int streamId) {
		Map<String, String> params = new LinkedHashMap<>(1);
		params.put("stream_id", String.valueOf(streamId));
		return request("get_simple_data_table", params, parser::parseEpg).then(v ->
						v.isEmpty() ? request("get_short_epg", params, parser::parseEpg) : completed(v),
				err -> request("get_short_epg", params, parser::parseEpg));
	}

	private String cacheKey(@Nullable String value) {
		return (value == null) ? "" : value;
	}

	private <T> FutureSupplier<T> request(@Nullable String action, @Nullable String extraKey,
																				@Nullable String extraValue, ResponseParser<T> parser) {
		Map<String, String> params = null;
		if ((extraKey != null) && (extraValue != null)) {
			params = new LinkedHashMap<>(1);
			params.put(extraKey, extraValue);
		}
		return request(action, params, parser);
	}

	private <T> FutureSupplier<List<T>> requestList(@Nullable String action,
																									@Nullable String extraKey,
																									@Nullable String extraValue,
																									ListResponseParser<T> parser) {
		return request(action, extraKey, extraValue, in -> collect(in, parser));
	}

	private <T> List<T> collect(InputStream in, ListResponseParser<T> parser) throws IOException {
		List<T> list = new ArrayList<>();
		parser.parse(in, list::add);
		return list;
	}

	private <T> FutureSupplier<T> request(@Nullable String action,
																				@Nullable Map<String, String> extraParams,
																				ResponseParser<T> parser) {
		String url = account.buildPlayerApiUrl(action, extraParams);
		URL requestUrl;
		String ua = account.getUserAgent();

		try {
			requestUrl = new URL(url);
		} catch (IOException ex) {
			return failed(redact(ex));
		}

		return App.get().getExecutor().submitTask(() -> {
			HttpURLConnection conn = null;

			try {
				conn = openConnection(requestUrl, "gzip, deflate", ua);

				int status = conn.getResponseCode();
				if (status != HttpURLConnection.HTTP_OK) {
					close(conn.getErrorStream());
					throw new HttpStatusException(status, conn.getResponseMessage());
				}

				InputStream payload = conn.getInputStream();
				if (payload == null) throw new IOException("Xtream response is empty");

				try (InputStream in = decode(payload, conn.getContentEncoding())) {
					return parser.parse(in);
				}
			} catch (Throwable ex) {
				throw redact(ex);
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	private FutureSupplier<XtreamHealth> probeStream(XtreamHealth health, String url) {
		URL requestUrl;
		String ua = account.getUserAgent();

		try {
			requestUrl = new URL(url);
		} catch (IOException ex) {
			return failed(redact(ex));
		}

		return App.get().getExecutor().submitTask(() -> {
			HttpURLConnection conn = null;

			try {
				conn = openConnection(requestUrl, "identity", ua);
				conn.setRequestProperty("Range", "bytes=0-0");

				int status = conn.getResponseCode();
				health.setStreamStatusCode(status);
				close((status >= HttpURLConnection.HTTP_BAD_REQUEST) ? conn.getErrorStream() :
						conn.getInputStream());
				if ((status == HttpURLConnection.HTTP_OK) ||
						(status == HttpURLConnection.HTTP_PARTIAL)) {
					return health;
				}

				throw health.streamFailure(status, conn.getResponseMessage());
			} catch (Throwable ex) {
				throw redact(ex);
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	private HttpURLConnection openConnection(URL requestUrl, String acceptEncoding,
																					 @Nullable String userAgent) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();
		conn.setRequestMethod("GET");
		conn.setInstanceFollowRedirects(true);
		conn.setRequestProperty("Accept-Encoding", acceptEncoding);
		if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent);

		int timeout = account.getResponseTimeout();
		if (timeout > 0) {
			int millis = (int) Math.min(Integer.MAX_VALUE, timeout * 1000L);
			conn.setConnectTimeout(millis);
			conn.setReadTimeout(millis);
		}

		return conn;
	}

	private void close(@Nullable InputStream in) throws IOException {
		if (in != null) in.close();
	}

	private InputStream decode(InputStream in, CharSequence enc) throws IOException {
		if (enc == null) return in;

		String e = enc.toString().toLowerCase(Locale.ROOT);
		if (e.contains("gzip")) return new GZIPInputStream(in);
		if (e.contains("deflate")) return new InflaterInputStream(in);
		throw new IOException("Unsupported Xtream content encoding: " + enc);
	}

	private Throwable redact(Throwable err) {
		Throwable root = rootCause(err);
		String message = describe(root);
		if (isNullOrBlank(message)) message = account.redact(err.getLocalizedMessage());
		if (isNullOrBlank(message)) message = account.redact(root.getLocalizedMessage());
		return new IOException(message, err);
	}

	private Throwable rootCause(Throwable err) {
		Throwable root = err;

		while ((root.getCause() != null) && (root.getCause() != root)) {
			root = root.getCause();
		}

		return root;
	}

	private String describe(Throwable err) {
		String host = account.getHost();

		if ((err instanceof UnresolvedAddressException) || (err instanceof UnknownHostException)) {
			return "Unable to find Xtream server " + host + ". Check the host and port.";
		} else if (err instanceof ConnectException) {
			return "Unable to connect to Xtream server " + host + ". Check the host, port and network.";
		} else if ((err instanceof TimeoutException) || (err instanceof SocketTimeoutException)) {
			return "Xtream server did not respond in time: " + host + ". Try again or increase the timeout.";
		} else if (err instanceof HttpStatusException) {
			return describeHttpStatus((HttpStatusException) err);
		}

		String msg = err.getLocalizedMessage();
		if (!isNullOrBlank(msg) && msg.contains("expected JSON, got HTML")) {
			return "Xtream server returned an HTML error page instead of JSON. Check the portal URL and account.";
		} else if (!isNullOrBlank(msg) && msg.contains("expected JSON")) {
			return "Xtream server returned an invalid response. Check the portal URL and account.";
		}

		return null;
	}

	private String describeHttpStatus(HttpStatusException err) {
		int status = err.status;
		String reason = isNullOrBlank(err.reason) ? "" : " " + err.reason;

		if ((status == HttpURLConnection.HTTP_UNAUTHORIZED) ||
				(status == HttpURLConnection.HTTP_FORBIDDEN)) {
			return "Xtream server rejected the request (HTTP " + status +
					"). Check username, password, expiry, or connection slots.";
		} else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
			return "Xtream API was not found on this server (HTTP 404). Check the portal URL, host and port.";
		} else if (status >= 500) {
			return "Xtream server error (HTTP " + status + reason + "). Try again later.";
		}

		return "Xtream request failed (HTTP " + status + reason + ").";
	}

	private interface ResponseParser<T> {
		T parse(InputStream in) throws IOException;
	}

	private interface ListResponseParser<T> {
		void parse(InputStream in, CheckedConsumer<T, IOException> consumer) throws IOException;
	}

	private static final class HttpStatusException extends IOException {
		final int status;
		final String reason;

		HttpStatusException(int status, String reason) {
			super("HTTP " + status + (isNullOrBlank(reason) ? "" : " " + reason));
			this.status = status;
			this.reason = reason;
		}
	}
}
