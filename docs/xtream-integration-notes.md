# Xtream Integration Notes

Date: 2026-06-28

These notes are the Phase 1 survey for adding Xtream Codes support to Fermata. They describe the actual classes and method boundaries in this checkout, not guesses from the original implementation plan.

## Summary

The current IPTV feature lives in `modules/tv`, not only in the core `fermata` media library. Xtream should be implemented inside the TV addon, most likely under `modules/tv/src/main/java/me/aap/fermata/addon/tv/xtream`, so it can reuse the existing TV root, add-source flow, EPG UI, catchup UI, and `StreamItem` behavior.

The original plan suggests `fermata/src/main/java/me/aap/fermata/media/lib/xtream`. That would miss the current TV addon structure and would duplicate behavior that already exists in `modules/tv`.

## External Preflight

Discussion and reference checks made on 2026-06-28:

- `AndreyPavlenko/Fermata` discussion #434 has prior Xtream interest. There is no `xtream` branch in `origin` and no Xtream code in this cloned `master` checkout.
- `AndreyPavlenko/Fermata` discussion #727 points to a separate Fermata Xtream mod/fork, but it is not merged upstream and its source is not available from that discussion. Treat it as prior art to avoid conflicting claims, not as implementation source.
- `zGrav/xtream-iptv-player` currently has an AGPL-3.0 license. Use it only as a high-level API flow reference unless a deeper license review approves reuse.
- `ahXN00/OwnTV` is MIT licensed. Its streaming JSON technique can be studied, but any direct port should keep the required attribution.
- `zaclimon/xipl` is an Apache-2.0 project with Xtream API documentation. Use the docs as endpoint/spec reference.

## Current M3U Flow

Core M3U parsing is in `fermata/src/main/java/me/aap/fermata/media/lib/M3uItem.java`.

- `M3uItem extends BrowsableItemBase`, with `SCHEME = "m3u"`.
- `M3uItem.data` is a `FutureRef<Data>` whose `create()` calls `App.get().execute(M3uItem.this::parse)`. Because Fermata's application extends `NetApp`, this uses the shared `NetThreadPool` instead of creating ad hoc threads.
- `parse()` reads a `BufferedReader` line by line. It does not buffer the full M3U response as one string, but it does materialize all parsed children into `List<Item>`.
- `parse()` handles `#EXTM3U` attributes including `url-epg`, `url-tvg`, `tvg-url`, `x-tvg-url`, `catchup`, `catchup-days`, and `catchup-source`.
- `parse()` handles per-track attributes including `tvg-logo`, `tvg-id`, `tvg-name`, `group-title`, `catchup`, `catchup-days`, and `catchup-source`.
- Track URLs are resolved through `getLib().getVfsManager().resolve(l, dir)` and then wrapped with `createTrack(...)`.
- `createReader(VirtualFile)` reads from `f.getInputStream().asInputStream()`, applies `GZIPInputStream` or `InflaterInputStream` based on `M3uFile.getContentEncoding()`, and defaults charset to UTF-8.
- `listChildren()` returns `getData().get().map(d -> d.items)`.
- `refresh()` clears the parsed data and removes child items from `DefaultMediaLib` cache.

Important constraint: `BrowsableItem.getChildren()` returns a `FutureSupplier<List<Item>>`. The UI expects a completed list for a folder. Xtream can lazy-load per category, but a single category with tens of thousands of streams will still require either a large list or a deeper UI/storage change.

## TV Addon Flow

The IPTV-specific layer is in `modules/tv/src/main/java/me/aap/fermata/addon/tv`.

### Addon Registration

- `modules/tv/build.gradle` declares the TV addon in `ext.addons` with class `me.aap.fermata.addon.tv.TvAddon`.
- The root `build.gradle` generates `BuildConfig.ADDONS` from addon metadata.
- `TvAddon implements MediaLibAddon`.
- `TvAddon.getRootItem(DefaultMediaLib)` returns a cached `TvRootItem`.
- `TvAddon.getItem(DefaultMediaLib, scheme, id)` delegates scheme lookup to `TvRootItem.getItem(...)`.

### TV Root

`modules/tv/src/main/java/me/aap/fermata/addon/tv/TvRootItem.java`

- `TvRootItem extends ItemContainer<TvM3uItem> implements TvItem`.
- `ID = "TV"`.
- Source persistence is currently M3U-specific:
  - `SOURCE_COUNTER` stores the next source number.
  - `SOURCE_IDS` stores an `int[]` of M3U source ids.
  - per-source `M3UID#<n>` stores the `TvM3uFileSystem` id.
- `getItem(scheme, id)` routes these schemes:
  - `TvM3uItem.SCHEME`
  - `TvM3uGroupItem.SCHEME`
  - `TvM3uTrackItem.SCHEME`
  - `TvM3uEpgItem.SCHEME`
- `listChildren()` reconstructs each `TvM3uItem` from `SOURCE_IDS`.
- `addSource(TvM3uFile)` persists a source id and adds a `TvM3uItem`.
- `itemRemoved(TvM3uItem)` removes cached/source files through `TvM3uFileSystemProvider.removeSource(...)`.

Xtream implication: `TvRootItem` must become polymorphic if M3U and Xtream sources should appear as peers. A likely change is to move from `ItemContainer<TvM3uItem>` to a TV-source abstraction or `ItemContainer<TvItem>`, store source type plus source id, and route Xtream schemes from `getItem(...)`.

### TV M3U Items

`modules/tv/src/main/java/me/aap/fermata/addon/tv/m3u/TvM3uItem.java`

- `TvM3uItem extends M3uItem implements TvItem`.
- `SCHEME = "tvm3u"`.
- It starts `xmlTv.get()` in the constructor.
- `createGroup(...)` creates `TvM3uGroupItem` ids.
- `createTrack(...)` creates `TvM3uTrackItem` ids and maps M3U catchup attributes into TV catchup constants.
- `getEpgUrl()` returns explicit source EPG URL or the `tvg-url` parsed from the playlist header.
- `setTvgUrl(String)` stores the playlist EPG URL on the backing `TvM3uFile` if needed.

`modules/tv/src/main/java/me/aap/fermata/addon/tv/m3u/TvM3uTrackItem.java`

- `TvM3uTrackItem extends M3uTrackItem implements StreamItem, StreamItemPrefs, TvItem`.
- `getEpg()` loads XMLTV entries through the parent `TvM3uItem`.
- `buildMeta(...)` merges current EPG data into media metadata.
- `buildExtras()` sets `STREAM_START_TIME` and `STREAM_END_TIME` for stream UI.
- `getLocation(long time, long duration)` builds catchup playback URLs.
- Catchup types already supported: append, default, shift, and flussonic.

Xtream implication: a new `XtreamTrackItem` should probably implement `StreamItem` directly or subclass a small shared TV stream base. Subclassing `TvM3uTrackItem` is awkward because it is tightly coupled to `TvM3uItem`, `M3uTrackItem`, and M3U catchup fields.

## Media Library Contracts

`fermata/src/main/java/me/aap/fermata/media/lib/MediaLib.java`

- `MediaLib.getItem(CharSequence id)` is asynchronous and returns `FutureSupplier<? extends Item>`.
- `MediaLib.Item` is the base UI/media node.
- `MediaLib.BrowsableItem` exposes `getChildren()` and `getUnsortedChildren()` as `FutureSupplier<List<I>>`.
- `MediaLib.PlayableItem` exposes playback through `getLocation()`, metadata through `getMediaData()`, and optional `getUserAgent()`.
- `MediaLib.StreamItem extends PlayableItem, BrowsableItem` and adds EPG/catchup hooks:
  - `getLocation(long time, long duration)`
  - `getEpg()`
  - `getEpg(long time)`

`fermata/src/main/java/me/aap/fermata/media/lib/DefaultMediaLib.java`

- `getItem(...)` handles core schemes in a switch.
- Unknown schemes are delegated to `AddonManager.get().getItem(this, scheme, id)`.
- The media root includes core folders/favorites/playlists plus all `MediaLibAddon` root items.

`fermata/src/main/java/me/aap/fermata/media/lib/BrowsableItemBase.java`

- `listChildren()` is the subclass hook.
- `getUnsortedChildren()` caches the `listChildren()` future.
- `getChildren()` loads metadata and sorts after `listChildren()`.
- `refresh()` clears cached children.

`fermata/src/main/java/me/aap/fermata/media/lib/ItemContainer.java`

- Useful for persisted source lists.
- `addItem`, `removeItem`, `moveItem`, and `saveChildren(...)` maintain a cached child list plus preferences.

## Playback URL Resources

`M3uTrackItem` stores a `VirtualResource` and overrides display metadata such as name/logo.

`MediaLib.PlayableItem.getLocation()` behaves like this:

- If the resource is local or from `GenericFileSystem`, return `file.getRid().toAndroidUri()`.
- If the resource is a non-generic network VFS resource, return Fermata's local HTTP proxy URL from `getVfsManager().getHttpRid(file)`.

`GenericFileSystem` accepts any RID. Therefore Xtream stream URLs can be represented by generic resources created from generated HTTP/HTTPS URLs. A custom Xtream playable item is still recommended for stable IDs, icons, EPG ids, user-agent handling, and catchup behavior.

## Network Layer

`depends/utils/src/main/java/me/aap/utils/net/http/HttpConnection.java`

- Main configuration is `HttpConnection.Opts`.
- Relevant fields:
  - `URL url`
  - `HttpMethod method`
  - `String userAgent`
  - `String acceptEncoding`
  - `boolean keepAlive`
  - `int maxRedirects`
  - `int maxReconnects`
  - `int responseTimeout`
- Main entry points:
  - `HttpConnection.connect(Consumer<Opts> builder, BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer)`
  - `HttpConnection.connect(Opts o, BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer)`
- Redirects and reconnects are built in.
- `HttpMessage.getPayload(...)` buffers a whole payload into a `ByteBuffer`; do not use this for large Xtream arrays.
- `HttpMessage.readPayload()` returns an `AsyncInputStream` backed by an `AsyncPipe`; use this for streaming parse.
- `HttpResponse.getContentEncoding()` exposes response compression.
- `HttpMessage.writePayload(...)` streams the response to an output stream/file.

Recommended Xtream API pattern:

1. Build a redaction-safe URL object.
2. Call `HttpConnection.connect(...)`.
3. On a successful `HttpResponse`, call `resp.readPayload()`.
4. Convert the `AsyncInputStream` to a blocking `InputStream` with `asInputStream()` from a background execution context.
5. Wrap with `GZIPInputStream` or `InflaterInputStream` when `resp.getContentEncoding()` says `gzip` or `deflate`.
6. Feed the resulting stream to `android.util.JsonReader`.
7. Do not log full URLs with passwords.

`depends/utils/src/main/java/me/aap/utils/app/NetApp.java`

- `NetApp.createExecutor()` returns `NetThreadPool`.
- `NetThreadPool` names worker threads `NetThread-<n>`.
- `App.get().execute(...)` is the normal way to run blocking parse work off the main thread.

`depends/utils/src/main/java/me/aap/utils/net/http/HttpFileDownloader.java`

- Good fit for cached files such as M3U and XMLTV.
- It stores ETag, charset, content encoding, timestamps, max age, and response timeout in a `PreferenceStore`.
- It can decode gzip/deflate for downloaded files.
- It is not the best first choice for `player_api.php` lists because it downloads to a cache file before parsing.

## Add Source UI

The TV add-source UI is code-driven, not XML-driven.

`modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFragment.java`

- `addSource()` constructs `TvM3uFileSystemProvider`, calls `select(...)`, and then adds the result through `TvRootItem.addSource(...)`.
- `contributeToContextMenu(...)` adds edit/delete for `TvM3uItem`.
- `isRefreshSupported()` returns true.

`modules/tv/src/main/java/me/aap/fermata/addon/tv/TvFloatingButtonMediator.java`

- The root TV floating button opens `TvFragment.addSource()`.

`modules/tv/src/main/java/me/aap/fermata/addon/tv/m3u/TvM3uFileSystemProvider.java`

- `select(...)` requests preferences and loads a `TvM3uFile`.
- `edit(...)` reuses the same preference form for existing sources.
- `requestPrefs(...)` builds the form with `PreferenceSet`.
- Existing groups: source name/location, EPG, catchup, logo, connection settings.
- `validate(...)` checks required source fields and catchup query shape.
- `setPrefs(...)` writes preferences onto `TvM3uFile`.

Xtream implication: add a source type chooser to `TvFragment.addSource()` or expose separate menu actions for M3U and Xtream. The Xtream form should use `PreferenceSet` with display name, scheme/host/port, username, password, optional output format, and connection settings.

## EPG

`modules/tv/src/main/java/me/aap/fermata/addon/tv/m3u/XmlTv.java`

- `XmlTv.create(TvM3uItem)` opens a SQLite DB stored next to the TV source cache.
- `load(...)` downloads EPG through `TvM3uFile.downloadEpg()`.
- `loadXml(...)` streams XMLTV with SAX and writes channel/program rows to SQLite.
- `loadChannels(...)` builds maps from current TV tracks:
  - `tvg-id` to tracks
  - normalized `tvg-name` to tracks
  - normalized display name to tracks
- XMLTV channel ids are matched to `TvM3uTrackItem.getTvgId()`.
- Current-program data is pushed back into each `TvM3uTrackItem.update(...)`.

Xtream implication: map Xtream `epg_channel_id` into the same conceptual slot as M3U `tvg-id`. A future Xtream stream item can reuse the existing `StreamItem` EPG UI if it provides compatible `getEpg()` data or participates in XMLTV matching.

## Storage And Cache Reality Check

The plan says to reuse Fermata's DB for M3U metadata. In this checkout:

- M3U source metadata is mostly preferences plus a cached `.m3u` file.
- XMLTV data has a source-specific SQLite DB.
- `MetadataRetriever` has a metadata DB, but it is for media metadata/durations and is not a general IPTV source cache.

Xtream category/stream cache should therefore be explicit:

- Store small account/source metadata in the TV source preference layer.
- Store large category/stream snapshots in a source-specific SQLite DB or cache file under the TV module cache directory.
- Sync categories immediately when adding an Xtream source because category lists are small.
- Fetch and cache streams/movies/series only when the user opens a category for the first time.

Avoid adding all Xtream streams into global preferences.

## Decisions From Updated Plan

The updated implementation plan from 2026-06-28 resolves the open design questions from the first survey:

- Make `TvRootItem` polymorphic. Replace the current M3U-only source list with a TV source abstraction such as `TvSourceItem`, store source type beside source id, and route M3U/Xtream schemes from `TvRootItem.getItem(...)`.
- Do not add hard "M3U" and "Xtream" folders under TV. Both should appear as peer IPTV sources because the existing TV UX already treats sources that way.
- Use hybrid cache. Categories are synced at add time; stream/movie/series entries are lazy-fetched and cached per category.
- Do not change shared `BrowsableItem` pagination in v1. A category still returns a full `List<Item>`, but parsing must be streaming and item objects must stay lightweight. Revisit paging only if profiling shows the UI list itself is the bottleneck.
- Use both EPG paths. Prefer existing XMLTV matching when a provider exposes an XMLTV URL, mapping Xtream `epg_channel_id` to the M3U `tvg-id` concept. Fall back to `get_short_epg&stream_id=` only for focused or visible channels, not for every channel in a large category.
- Store Xtream passwords with encrypted app storage if practical, rather than plain preferences. Never log the raw password or full credential URL.

## Recommended First Implementation Slice

1. Add `modules/tv/src/main/java/me/aap/fermata/addon/tv/xtream/model` POJOs:
   - `XtreamAccount`
   - `XtreamCategory`
   - `XtreamChannel`
   - `XtreamMovie`
   - `XtreamSeries`
   - `XtreamSeason`
   - `XtreamEpisode`
2. Add a clean-room `XtreamApi` that builds URLs and streams responses through `HttpConnection`.
3. Add `XtreamJsonStreamParser` using `android.util.JsonReader`.
4. Add `XtreamSourceItem`, `XtreamSectionItem`, `XtreamCategoryItem`, and `XtreamTrackItem`.
5. Add a TV source abstraction and migrate `TvRootItem` from `ItemContainer<TvM3uItem>` toward polymorphic source handling.
6. Add `XtreamFileSystemProvider` or equivalent `PreferenceSet`-based add/edit flow for Xtream credentials. Do not add an XML dialog layout for this.
7. Immediately after Live-only is playable, move Xtream credentials from plain `PreferenceStore` to encrypted storage if `androidx.security:security-crypto` is added or otherwise available. Do not defer this into the later catchup/EPG milestone.
8. Start with lazy Live categories only, then add VOD, then Series, then catchup/short EPG.

## Planned New Files

```text
modules/tv/src/main/java/me/aap/fermata/addon/tv/xtream/
  model/
    XtreamAccount.java
    XtreamCategory.java
    XtreamChannel.java
    XtreamMovie.java
    XtreamSeries.java
    XtreamSeason.java
    XtreamEpisode.java
  XtreamApi.java
  XtreamJsonStreamParser.java
  XtreamSourceItem.java
  XtreamSectionItem.java
  XtreamCategoryItem.java
  XtreamTrackItem.java
  XtreamFileSystemProvider.java
```
