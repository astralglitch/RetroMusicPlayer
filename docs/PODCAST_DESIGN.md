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

Right now every playing item — music or podcast episode — shows the same Now Playing
screen: album art, artist, shuffle/repeat transport controls. For a podcast episode
this is actively confusing: nothing on screen says which podcast it's from, there's no
skip ±10/30s, no speed control, and tapping through goes nowhere useful (compare
AntennaPod's player: podcast name + episode title, tap-through to the show, speed/skip
controls sized for spoken-word listening).

The `PlayerControlsStrategy` seam (`fragments/player/controls/PlayerControlsStrategy.kt`)
already exists for exactly this — `PodcastAudioControlsStrategy` is currently a stub
that contributes nothing. Building this out means: podcast-appropriate transport
controls (skip ±10/30s, playback speed, tap-through to the podcast), matching
RetroMusic's existing per-theme visual style rather than a bolted-on separate look.
Those skip/speed increments should themselves eventually be user-configurable, same as
everything else here.
- **Status:** designed (seam exists), controls not built. Bigger job than anything in
  "Build order" below since it touches the shared Now Playing UI instead of living
  in the isolated podcast module — treat as its own milestone once the basic loop
  (below) is solid.

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

Basic loop is done. Next real milestones, roughly in order: type-aware Now Playing
screen (above), then nav/tabs, then multi-queue, then per-tab swipe actions.

### Known limitation: episode artwork

Episodes currently show no cover art (`EpisodeEntity.toSong()` sets `albumId = -1`,
and the local-file-based Glide fallback is intentionally skipped for network URLs —
see `AudioFileCoverFetcher.kt` — since it was making a wasted extra network fetch
competing with the actual episode download for bandwidth). The real fix is wiring
`PodcastEntity.imageUrl` (already fetched from the feed) into whatever Glide model
paints the Now Playing / mini-player art, which naturally belongs in the type-aware
Now Playing work above rather than as a standalone patch.
