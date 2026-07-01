package me.aap.fermata.addon.tv.xtream;

import android.net.Uri;

import me.aap.utils.text.SharedTextBuilder;

final class XtreamItemId {
	private XtreamItemId() {
	}

	static String category(String scheme, int sourceId, String categoryId, String name) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(name)).releaseString();
	}

	static Category category(String id) {
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int sourceId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryId = Uri.decode(id.substring(start, end));
		String name = Uri.decode(id.substring(end + 1));
		return new Category(sourceId, categoryId, name);
	}

	static String stream(String scheme, int sourceId, String categoryId, String categoryName,
										 int streamId) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(categoryName)).append(':')
				.append(streamId).releaseString();
	}

	static Stream stream(String id) {
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int sourceId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryId = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryName = Uri.decode(id.substring(start, end));
		int streamId = Integer.parseInt(id.substring(end + 1));
		return new Stream(sourceId, categoryId, categoryName, streamId);
	}

	static String series(String scheme, int sourceId, String categoryId, String categoryName,
										 int seriesId, String seriesName) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(categoryName)).append(':')
				.append(seriesId).append(':').append(Uri.encode(seriesName)).releaseString();
	}

	static Series series(String id) {
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int sourceId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryId = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryName = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int seriesId = Integer.parseInt(id.substring(start, end));
		String seriesName = Uri.decode(id.substring(end + 1));
		return new Series(sourceId, categoryId, categoryName, seriesId, seriesName);
	}

	static String season(String scheme, int sourceId, String categoryId, String categoryName,
										 int seriesId, String seriesName, int seasonNumber) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(categoryName)).append(':')
				.append(seriesId).append(':').append(Uri.encode(seriesName)).append(':')
				.append(seasonNumber).releaseString();
	}

	static Season season(String id) {
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int sourceId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryId = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryName = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int seriesId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String seriesName = Uri.decode(id.substring(start, end));
		int seasonNumber = Integer.parseInt(id.substring(end + 1));
		return new Season(sourceId, categoryId, categoryName, seriesId, seriesName, seasonNumber);
	}

	static String episode(String scheme, int sourceId, String categoryId, String categoryName,
											 int seriesId, String seriesName, int seasonNumber, int episodeId) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(categoryName)).append(':')
				.append(seriesId).append(':').append(Uri.encode(seriesName)).append(':')
				.append(seasonNumber).append(':').append(episodeId).releaseString();
	}

	static Episode episode(String id) {
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int sourceId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryId = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String categoryName = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int seriesId = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		String seriesName = Uri.decode(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int seasonNumber = Integer.parseInt(id.substring(start, end));
		int episodeId = Integer.parseInt(id.substring(end + 1));
		return new Episode(sourceId, categoryId, categoryName, seriesId, seriesName,
				seasonNumber, episodeId);
	}

	static String orig(String id, String scheme) {
		int idx = id.indexOf(scheme);
		return (idx < 0) ? id : id.substring(idx);
	}

	static class Category {
		final int sourceId;
		final String categoryId;
		final String categoryName;

		Category(int sourceId, String categoryId, String categoryName) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
		}
	}

	static final class Stream extends Category {
		final int streamId;

		Stream(int sourceId, String categoryId, String categoryName, int streamId) {
			super(sourceId, categoryId, categoryName);
			this.streamId = streamId;
		}
	}

	static class Series extends Category {
		final int seriesId;
		final String seriesName;

		Series(int sourceId, String categoryId, String categoryName, int seriesId,
					 String seriesName) {
			super(sourceId, categoryId, categoryName);
			this.seriesId = seriesId;
			this.seriesName = seriesName;
		}
	}

	static final class Season extends Series {
		final int seasonNumber;

		Season(int sourceId, String categoryId, String categoryName, int seriesId,
					 String seriesName, int seasonNumber) {
			super(sourceId, categoryId, categoryName, seriesId, seriesName);
			this.seasonNumber = seasonNumber;
		}
	}

	static final class Episode extends Series {
		final int seasonNumber;
		final int episodeId;

		Episode(int sourceId, String categoryId, String categoryName, int seriesId,
						String seriesName, int seasonNumber, int episodeId) {
			super(sourceId, categoryId, categoryName, seriesId, seriesName);
			this.seasonNumber = seasonNumber;
			this.episodeId = episodeId;
		}
	}
}
