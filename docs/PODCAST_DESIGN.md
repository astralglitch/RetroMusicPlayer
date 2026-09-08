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

## Build order (current focus)

Per-item `MediaItemType` schema and the player-controls strategy seam are in place
(see `db/MediaItemEntity.kt`, `fragments/player/controls/PlayerControlsStrategy.kt`).
Nav and multi-queue are deliberately deferred. Immediate focus is the basics:

1. Subscribe to a podcast feed (RSS, given a URL).
2. See its episode list.
3. Stream an episode (play straight from its enclosure URL).
4. Download an episode for offline playback.

Everything above this list stays a design note until that basic loop works end to end.
