# Podcast feature design notes

Working notes on ideas discussed for the podcast side of the app. Not all of this is
built yet — see "Status" on each section. This is a living doc, not a spec; update it
as decisions change.

## Navigation

- **Side nav** = top-level switch between media *worlds*: Home, Music, Podcasts (Video
  later). Kept small and mostly fixed — this is "what kind of thing am I looking at,"
  not a customization surface.
- **Bottom nav** = per-world tab set, customizable, with sensible per-type defaults:
  - Music: Artists / Albums / Songs / Genres / Folders (RetroMusic's existing set).
  - Podcasts: Queue / Inbox / Subscriptions / Downloads (AntennaPod's set is a good
    starting point).
- Home has no bottom nav of its own — it's a cross-type dashboard ("continue
  listening," "new episodes"), and picking a world from the side nav is what puts a
  bottom nav on screen.
- A "Universal" bottom-nav profile (same tabs regardless of world) can come later if
  wanted; not blocking anything.
- **Status:** side-nav world switcher and customizable per-world bottom nav are built.
  The Universal profile is still undecided/not built.

## Multi-queue

The idea: AntennaPod (and most podcast apps) give you exactly one queue. New episodes
from every subscription land in it together, so a burst of low-priority entertainment
episodes can bury a time-sensitive news episode in the middle of the list, and the only
fix is manually dragging things around.

Model, as built (`db/QueueEntity.kt`, `db/QueueItemEntity.kt`, `podcast/QueueRepository.kt`):
- `QueueEntity`: `id`, `name`, `is_default`, `created_at`. Every install gets exactly
  one `is_default` queue, seeded by `MIGRATION_28_29` (which also backfills it with
  any already-existing not-yet-played episodes, oldest first, so upgraders don't see
  an empty queue).
- `QueueItemEntity`: `queue_id`, `episode_id`, `position` (dense 0-based, fully
  rewritten on reorder), `added_at`.
- "Active" queue (what `QueueFragment` -- the Queue tab -- shows, and what the
  universal long-press "Add to queue" action adds to) is
  `PreferenceUtil.activeQueueId`, resolved through `QueueRepository.activeQueueId()`
  with a fallback to the default queue if unset or since deleted. Switching it is a
  `QueueFragment` overflow-menu action (also: create/rename/delete a queue; the
  default queue can't be deleted).
- New-episode routing: `PodcastEntity.targetQueueId` (null = default queue), set from
  `PodcastDetailsFragment`'s overflow menu ("New episodes go to…").
  `PodcastRepository.persistEpisodes()` auto-adds every genuinely-new episode (not
  just an updated existing row -- detected via `@Upsert`'s per-row inserted-id-or--1
  return) to that podcast's target queue on subscribe/refresh.
- Tapping a `QueueFragment` row plays the whole queue starting there (sequential
  listening being the point of a queue), rather than opening the episode's own
  subpage like every other episode list in the app does.
- Per-subscription ordering rules (front vs. back of queue) remain a later,
  low-priority refinement, same as originally noted.
- **Status:** built.

## Now Playing screen should be type-aware

The `PlayerControlsStrategy` seam (`fragments/player/controls/PlayerControlsStrategy.kt`)
picks `PodcastAudioControlsStrategy` for a playing episode, which hosts
`PodcastExtraControlsFragment` (skip ±10/30s, playback speed cycle) in whichever theme
fragment exposes `AbsPlayerControlsFragment.extraControlsContainerId`. That container
(`podcastControlsContainer`) is now wired into every themed playback-controls layout
where it fits: normal, adaptive, blur, card, cardblur, color, fit, flat, full, md3,
plain, simple, lockscreen, material, peek. Left out on purpose:
- **tiny** — a bare repeat/shuffle icon strip with no room for anything else.
- **circle, gradient, classic** — these themes' player fragments
  (`CirclePlayerFragment`/`GradientPlayerFragment`/`ClassicPlayerFragment`) implement
  transport controls inline rather than delegating to an `AbsPlayerControlsFragment`
  subclass, so they don't have a seam to plug into yet; would need their own
  integration if picked up later.

Tap-through to the podcast now works too: `AbsPlayerFragment.goToAlbum()` and
`goToArtist()` (the shared functions every theme's title/artist tap calls) check
`Song.episodeIdOrNull()` and route to `podcastDetailsFragment` instead of
`albumDetailsFragment`/`artistDetailsFragment` for a podcast episode -- those would
otherwise navigate using bogus ids (`Song.albumId` is -1, `Song.artistId` is actually
the podcast's own id, not a MediaStore artist).

Skip/speed increments are still fixed, not user-configurable yet -- that part of the
original intent remains open.
- **Status:** built (extras panel, per-theme wiring, tap-through). Configurable skip/
  speed increments not done.

### Episode artwork (was: known limitation, now fixed)

`Song` gained an `artworkUrl: String?` field (null for a plain scanned track).
`EpisodeEntity.toSong(podcast)` sets it to `podcast.imageUrl`, and
`RetroGlideExtension.getSongModel()` returns it directly (a plain URL is a valid Glide
model) before falling through to the old AudioFileCover/MediaStore paths -- so Now
Playing, the mini-player, and anywhere else `getSongModel`/`songCoverOptions` is used
now show the podcast's real cover art instead of nothing.

### Podcast video, part 2: resolving external (YouTube-etc.) video links in-app

The section above covers episodes whose feed enclosure *is* a video file
(`EpisodeEntity.videoUrl`). Episodes can also carry a manually-attached video *page*
link instead (`EpisodeEntity.videoSourceUrl`, e.g. a YouTube URL) -- not directly
playable by ExoPlayer. This is resolved to a real playable stream via
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) and played through
**the exact same toggle/inline-surface/fullscreen system** as a direct feed video (an
earlier version of this routed resolved links through a separate, always-fullscreen,
throwaway `ExoPlayer` instance with no aspect-ratio handling at all -- stretched video,
no toggle, no position sync back to audio; that's gone now):

- `podcast/VideoLinkResolver.kt` -- suspend `resolve(sourceUrl)` on `Dispatchers.IO`,
  calls `NewPipe.getServiceByUrl()` then extracts a playable stream (progressive MP4
  preferred, HLS master URL as fallback). Returns `Success`/`UnsupportedSite`/
  `ResolutionFailed`. Resolved stream URLs are signed/time-limited by the source site,
  so they're cached in-memory only (`peekCached()` for a synchronous, non-resolving
  lookup), for the current process, never persisted to Room.
- `podcast/NewPipeOkHttpDownloader.kt` -- the `Downloader` NewPipeExtractor needs to
  actually make HTTP requests, backed by a small dedicated `OkHttpClient`. Initialized
  once via `NewPipe.init(...)` in `App.onCreate()`.
- `Song.videoSourceUrl` (alongside `videoUrl`) carries the unresolved link onto the
  currently-playing song, same reasoning as `videoUrl` -- lets the Now Playing toggle
  button know a video is available at all without a DB round-trip (`EpisodeExtensions.toSong`).
- **Resolution happens in the UI layer, never in the service.** `PodcastExtraControlsFragment`'s
  toggle button: if `videoUrl` is set, or `videoSourceUrl` is already resolved
  (`VideoLinkResolver.peekCached`), the toggle activates immediately, same as before. If
  only an *unresolved* `videoSourceUrl` exists, tapping shows a brief loading spinner on
  the button, resolves in `lifecycleScope`, and only activates
  [`PodcastVideoToggle`](#now-playing-screen-should-be-type-aware) on success --
  `MusicService.toggleEpisodeVideo` itself stays fully synchronous, just picking
  `videoUrl` or the resolver's cached entry for `videoSourceUrl`. This is also how the
  episode details page's "Video link" dialog and "Video available" chip play a
  resolved link now: resolve, then activate the same toggle and expand the sliding
  player panel, instead of opening a standalone dialog.
- `RetroExoPlayer.swapMediaSource` grew an `isHls` parameter (threaded through
  `Playback`/`PlaybackManager`/`MusicService`) so a resolved HLS stream URL -- which
  typically has neither a `.m3u8` extension nor a reliable MIME type for Media3 to
  auto-detect -- gets built as an explicit `HlsMediaSource` instead of a plain
  `MediaItem`.
- Fallback: `UnsupportedSite`/`ResolutionFailed` never touch the toggle -- audio keeps
  playing uninterrupted, with a toast explaining why; the episode-details entry points
  additionally fall back to `ACTION_VIEW` (open externally), same as before.
- Settings: `PodcastSettingsFragment`'s Automation section has a "Play video links
  in-app" toggle (`PreferenceUtil.playVideoLinksInApp`, **on by default** -- safe
  since external-open remains the automatic fallback on any failure). This is a
  *global* app setting, not per-podcast, even though it's surfaced from a per-podcast
  settings screen.

**Known, permanent scope gap: NewPipeExtractor does not support Vimeo** (it supports
YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de), despite `videoSourceUrl`'s
generic name/hint text. A Vimeo link -- or any other site NewPipeExtractor doesn't
recognize -- resolves as `UnsupportedSite` and falls back to external-open. A
Vimeo-specific resolver (e.g. via Vimeo's own oEmbed/config API) would need a separate
implementation and isn't planned.

**Auto-detecting a video link from an episode's show notes** is built: per-podcast
"video link rules" (`db/VideoLinkRuleEntity`, `podcast/VideoLinkMatcher.kt`) scan an
episode's description for URLs and match them against enabled rules (a built-in preset --
YouTube/Vimeo/SoundCloud, see `podcast/VideoLinkPresets.kt` -- or a custom regex), applied
in `PodcastRepository.persistEpisodes()` for newly-fetched episodes only (never touches an
already-set `videoSourceUrl`, whether set manually or by an earlier rule match). If more
than one link matches, the first becomes the primary `videoSourceUrl`; the rest are kept
as `EpisodeEntity.videoLinkCandidates` (JSON-encoded) for a future "switch primary video"
UI -- not surfaced anywhere yet. Rules are managed from a podcast's settings screen
("Video link rules" -> `podcast/VideoLinkRulesFragment.kt`), including a "re-apply to
existing episodes" action for episodes fetched before any rule existed. The episode list
row (`EpisodeAdapter`) now shows a small video-camera icon when either `videoUrl` or
`videoSourceUrl` is set, so a show's video-having episodes are visible without opening
each one.

**Play preference** (`db/PlayPreference`: `AUDIO_FIRST`/`VIDEO_FIRST`) decides whether
starting playback also auto-activates the audio<->video toggle when a video is available
-- global default via `PreferenceUtil.globalPlayPreference`, overridable per podcast via
`PodcastEntity.playPreference` (null = inherit the global default). Wired into every
"start playback" call site (`EpisodeDetailsFragment.play()`, `PodcastDetailsFragment.playEpisode()`,
the cross-podcast episode lists' stream action, `QueueFragment.playFrom()`) via the shared
`EpisodeExtensions.activateVideoIfPreferred()` helper.

**Video download management**: video now downloads as a file fully separate from audio
(`EpisodeEntity.videoDownloadState`/`videoDownloadId`/`videoLocalFilePath`, mirroring the
existing audio trio) -- `EpisodeDownloadManager.enqueueVideo()`/`deleteVideoDownload()`.
This means video is **no longer auto-downloaded** just because a feed's enclosure happens
to be a video file; downloading it is now an explicit action (episode long-press ->
"Download video"), and `PreferenceUtil.globalAutoDownloadVideo` (off by default, per-podcast
override via `PodcastEntity.autoDownloadVideo`) exists as the policy switch for a future
auto-download-on-refresh hook. That hook now exists for both audio and video
(`PodcastRepository.autoDownloadNewEpisodes`, on subscribe/refresh): audio defaults on via
`PreferenceUtil.globalAutoDownloadAudio`, video stays off, and each is overridable per
podcast. `EpisodeDownloadManager` also caps downloads per podcast after each success, oldest
deleted first with favorites exempt (`globalAudioRetentionCount`/`globalVideoRetentionCount`,
-1 for unlimited).
An external (YouTube-etc.) video link downloads by first resolving it via
`VideoLinkResolver` to a real stream URL -- only a progressive (non-HLS) result can be
downloaded this way, since `DownloadManager` can't fetch a segmented stream; an HLS-only
resolution surfaces as "can't download this video" instead.

**Fullscreen aspect ratio + fit/crop**: `PodcastVideoFullscreenDialogFragment` never
wired a video-size listener at all, so video was stretched to fill the screen
regardless of source aspect ratio -- fixed by sharing `podcast/VideoAspectFit.kt`'s
sizing function with the inline surface (`PlayerAlbumCoverFragment`, `Fit`-only) and
adding a `Fit`/`Crop` toggle button to the fullscreen controls (defaults to `Fit`,
doesn't persist across opens).

**Future idea, not built**: per-episode or per-podcast audio<->video offset mapping,
for shows whose video track has a longer/shorter intro than the audio -- so toggling
mid-episode could compensate automatically instead of landing slightly out of sync.
Noted here for later; no design work done yet.

- **Status:** built -- one unified toggle/inline-surface/fullscreen system for both
  feed-direct video and resolved external links (YouTube/SoundCloud/PeerTube/Bandcamp);
  everything else, including Vimeo, falls back to external-open. Minor playback
  stutter on toggle is a known, accepted limitation. Auto-detection rules, the play
  preference, separate video downloads, and auto-download-on-refresh (above) are also built.
  While the screen is off, video drops to audio-only and is restored on screen-on; a
  single-file audio/video enclosure toggles its video track without a source swap, so it
  doesn't stutter.

## Per-tab swipe actions

AntennaPod lets you configure a left-swipe and right-swipe action per screen (e.g.
mark played, add to queue, archive) with sensible per-tab defaults and later
customization. Same idea applies here once there's a real episode-list UI to swipe on
(subscriptions, queue, downloads tabs). Ship reasonable defaults first (e.g. swipe to
mark played/unplayed, swipe to remove from queue), make them configurable later —
same shape as the bottom-nav customization story above.
- **Status:** defaults built for the standalone episode-list tabs
  (`AbsEpisodeListFragment.attachSwipeActions`); configuring them is not built.

## Statistics

The Podcasts-world Statistics tab (`PodcastStatisticsFragment`) shows estimated listening
time, library counts, and the most-listened shows. It's computed from each episode's current
saved state (`EpisodeDao.episodeStats`), with no listening log and no schema change, so:
- a played episode counts in full, even if it was only marked played by hand;
- an unplayed episode counts up to its saved position;
- repeat listens count once, playback speed is ignored, and unsubscribing removes that
  show's history.

The screen labels the total as an estimate because of this. Date-based stats (this week,
streaks) would need a real listening log written from the playback path.
- **Status:** built; the listening log is not.

## Encourage offline listening from the episode subpage

`PreferenceUtil.preferStreaming` (Settings -> Podcasts -> Playback -> "Prefer streaming",
key `prefer_streaming`, **off by default**) controls this. Off (the default): a
not-yet-downloaded episode has no Play button on `EpisodeDetailsFragment` -- only
Download, nudging toward downloading first. On: Play always shows, streaming straight
from the enclosure URL, same as before this preference existed.

Streaming without downloading is still reachable either way, two ways:
- `EpisodeDetailsFragment`'s overflow (⋮) menu -> "Stream episode"
  (`menu_episode_details.xml`, wired to the existing `play()`, which already streams via
  `enclosureUrl` when there's no local file -- see `EpisodeEntity.toSong()`).
- Long-press any episode row anywhere there's an episode list (Podcasts Home's
  Suggestions/Favorites/Inbox/Continue Listening, a podcast's own episode list, the
  Favorites/Downloads/History/Inbox/Episodes tabs) -> "Stream episode" in the popup menu
  (`EpisodeAdapter`'s `onStream` callback, shown only for not-yet-downloaded episodes).
- **Status:** built.

## Build order (current focus)

Per-item `MediaItemType` schema and the player-controls strategy seam are in place
(see `db/MediaItemEntity.kt`, `fragments/player/controls/PlayerControlsStrategy.kt`).
Nav and multi-queue are deliberately deferred. Immediate focus is the basics:

1. Subscribe to a podcast feed (RSS, given a URL). ✅
2. See its episode list. ✅
3. Stream an episode (play straight from its enclosure URL). ✅
4. Download an episode for offline playback, and manage it once downloaded (delete,
   cancel a stuck transfer, see real progress). ✅
5. Persist and resume playback position per episode. ✅

Basic loop, the type-aware Now Playing screen, nav/tabs, multi-queue, and default per-tab
swipe actions (above) are all done. The largest idea still open is the context queue (below).

## Queue modes: New queue / Play next / Play last

Taken from Finamp. A 3-way toggle (`layout_queue_mode_toggle.xml`, driven by
`helper/QueueModeActions.kt`) sits above the Play / Shuffle buttons on the Album, Artist,
Playlist and Genre detail screens:
- **New queue** replaces the queue (the original Play / Shuffle behaviour; the default,
  and reset every time a screen opens).
- **Play next** inserts the songs after the current one (`MusicPlayerRemote.playNext`).
- **Play last** appends them to the end (`MusicPlayerRemote.enqueue`).
Shuffle in Play next / Play last shuffles only the inserted songs and leaves the player's
shuffle mode alone. The existing ⋮ menu items are unchanged.

**Later (low priority, noted 2026-11-23):** Finamp's long-press on any single item (song
row, album/artist/playlist card) opens a bottom-sheet card with the same 3-mode toggle
plus Play / Shuffle and the other item actions. Ours uses popup menus from the ⋮ button
(Play next / Add to playing queue are already there per song), which is fine for now.
Revisit when the long-press menus get reworked: one shared bottom-sheet item menu
reusing `QueueModeActions`.
- **Status:** detail-screen toggle built; per-item long-press sheet not built.

## Playback tracking: what's real vs. what's still a stopgap

Three separate layers currently decide "what plays and where it resumes." Worth
untangling before doing much more here, since they overlap in confusing ways:

1. **The actual playback queue** (`MusicService.playingQueue`) -- one flat, in-memory
   `List<Song>` the player literally advances through with Next/Previous. Whatever last
   called `MusicPlayerRemote.openQueue(...)` replaced it wholesale.
2. **The multi-queue feature** (above) -- persisted, user-curated `QueueEntity`/
   `QueueItemEntity` rows. Tapping a row in `QueueFragment` copies that queue's episodes
   into (1) and starts playing from the tapped position -- so today, (2) is really just
   *one way to populate (1)*, not something the player is continuously aware of.
3. **Per-episode saved position** (`EpisodeEntity.playbackPositionMs`) -- resumed
   centrally now (see `MusicService.autoResumeEpisodePositionIfNeeded()`, added
   2026-09-16) whenever a podcast episode becomes the current track, regardless of how
   it got there: tapping a row, Next/Previous, notification/headset skip, or restoring
   after a crash/relaunch. Skips episodes marked `played` on purpose -- see the code
   comment for why (a played episode's saved position sits near the end of the file;
   seeking there immediately auto-advances, which used to look like "tapping play on a
   finished episode skips to the next one" -- 2026-09-16 bug).

**The "context queue" idea (not built)**: right now, opening any episode list and
hitting play just dumps that screen's current episode list into (1) as a one-off flat
queue -- e.g. `PodcastDetailsFragment.playEpisode()` builds `displayedEpisodes.map {
it.toSong(podcast) }` and calls `openQueue`. That's *closer* to what people expect than
a single global queue, but it's static: built once at tap-time, from whatever sort/
filter was active on screen *then*. It doesn't track "I'm still logically browsing this
podcast's episode list sorted newest-first" as a live, referenceable context distinct
from queues (1) and (2).

Vision: a third notion, a **context queue** -- an ephemeral, screen-derived Next/
Previous ordering that:
- Is created implicitly whenever playback starts from a browsing screen (a podcast's
  episode list, Favorites, History, a search-result list, etc.), capturing that
  screen's *current* sort/filter as the ordering, not a snapshot of episode IDs.
- Stays live: if the user changes that screen's sort (e.g. oldest-first) while still
  playing from it, Next/Previous should reflect the new order without restarting
  playback.
- Is distinct from both (1) (the literal player queue, which the context queue would
  drive under the hood) and (2) (the user's own named, persisted queues) -- adding an
  episode to a named queue from a context-queue session is a deliberate, separate
  action ("save this into my queue"), not something that happens implicitly.
- Needs a way to tell, at any moment, "what's actually driving Next/Previous right
  now" -- a named queue, a context queue (and if so, from which screen/sort), or
  neither (a one-off single-episode play). Not designed yet; probably a small enum/
  sealed-class on `MusicService` alongside `playingQueue` itself.
- **Status:** vision only, not designed in detail or built. Today's flat-queue-per-tap
  behavior is a reasonable stopgap and shouldn't block on this.
