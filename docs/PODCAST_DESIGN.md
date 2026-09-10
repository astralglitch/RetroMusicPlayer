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
- **Status:** undecided/not built. Revisit once Music and Podcasts both have real
  content to navigate.

## Multi-queue

The idea: AntennaPod (and most podcast apps) give you exactly one queue. New episodes
from every subscription land in it together, so a burst of low-priority entertainment
episodes can bury a time-sensitive news episode in the middle of the list, and the only
fix is manually dragging things around.

Proposed model:
- `Queue`: `id`, `name`, `is_default`.
- `QueueItem`: `queue_id`, `media_item_id`, `position`, `added_at`.
- Exactly one queue is "active" (what the player actually pulls from next); switching
  which queue is active is a UI action.
- New-episode routing: each subscription has a target queue (defaults to the main
  queue). A later, low-priority refinement: per-subscription ordering rules (e.g.
  always push a given show's new episodes to the front vs. the back of its queue).
- Motivating case: a "News" queue for shows you want to listen to promptly and in
  order, separate from a general queue you work through oldest-first at your own pace.
- **Status:** designed, not built. Needs subscriptions/episodes to exist first — see
  "Build order" below.

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

## Per-tab swipe actions

AntennaPod lets you configure a left-swipe and right-swipe action per screen (e.g.
mark played, add to queue, archive) with sensible per-tab defaults and later
customization. Same idea applies here once there's a real episode-list UI to swipe on
(subscriptions, queue, downloads tabs). Ship reasonable defaults first (e.g. swipe to
mark played/unplayed, swipe to remove from queue), make them configurable later —
same shape as the bottom-nav customization story above.
- **Status:** noted, not designed in detail yet. Depends on the nav-and-tabs decision
  above being settled first, since "per tab" isn't meaningful until tabs exist.

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

Basic loop and the type-aware Now Playing screen (above) are both done. Next real
milestones, roughly in order: nav/tabs, then multi-queue, then per-tab swipe actions.
