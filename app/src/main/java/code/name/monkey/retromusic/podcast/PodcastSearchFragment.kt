package code.name.monkey.retromusic.podcast

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentPodcastSearchBinding
import code.name.monkey.retromusic.databinding.ItemListBinding
import code.name.monkey.retromusic.databinding.SubHeaderBinding
import code.name.monkey.retromusic.db.EpisodeEntity
import code.name.monkey.retromusic.db.PodcastEntity
import code.name.monkey.retromusic.extensions.focusAndShowKeyboard
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Podcasts-world search: matches subscribed podcasts and their episodes by title or
 * description, from the local database only (no online directory). Episode rows reuse
 * [EpisodeAdapter] and [EpisodeListViewModel] (kind [EpisodeListKind.SEARCH]), so they behave
 * like every other episode list in the app.
 */
class PodcastSearchFragment : AbsMainActivityFragment(R.layout.fragment_podcast_search) {

    private var _binding: FragmentPodcastSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModel<EpisodeListViewModel> { parametersOf(EpisodeListKind.SEARCH) }
    private val resumeSeeker = EpisodeResumeSeeker()
    private var episodeAdapter: EpisodeAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPodcastSearchBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.searchView.apply {
            doAfterTextChanged { text ->
                binding.clearText.isVisible = !text.isNullOrEmpty()
                viewModel.setSearchQuery(text?.toString().orEmpty())
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard()
                actionId == EditorInfo.IME_ACTION_SEARCH
            }
            if (savedInstanceState == null) focusAndShowKeyboard()
        }
        binding.clearText.setOnClickListener {
            binding.searchView.text?.clear()
            binding.searchView.focusAndShowKeyboard()
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        viewModel.episodes.observe(viewLifecycleOwner) { updateResults() }
        viewModel.podcastResults.observe(viewLifecycleOwner) { updateResults() }
        // Lookup for each episode row's thumbnail, same as AbsEpisodeListFragment.
        viewModel.podcasts.observe(viewLifecycleOwner) { updateResults() }
    }

    /** Lists here are capped (200 episodes) and short-lived, so rebuilding the adapters on each
     * change is simpler than diffing, matching AbsEpisodeListFragment. */
    private fun updateResults() {
        if (_binding == null) return
        val podcasts = viewModel.podcastResults.value.orEmpty()
        val episodes = viewModel.episodes.value.orEmpty()
        val podcastsById = viewModel.podcasts.value.orEmpty().associateBy { it.id }

        episodeAdapter = EpisodeAdapter(
            onPlay = ::openEpisode,
            onPlayPauseButton = ::onEpisodePlayPauseButton,
            onDownload = { viewModel.download(it) },
            onDeleteDownload = { viewModel.deleteDownload(it) },
            onTogglePlayed = { episode, played -> viewModel.setPlayed(episode, played) },
            onToggleFavorited = { episode, favorited -> viewModel.setFavorited(episode, favorited) },
            onStream = ::streamEpisode,
            onAddToQueue = { viewModel.addToActiveQueue(it) },
            podcastsById = podcastsById
        ).apply {
            submitList(episodes)
            setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
        }

        val sections = mutableListOf<RecyclerView.Adapter<*>>()
        if (podcasts.isNotEmpty()) {
            sections += HeaderAdapter(getString(R.string.podcast_search_podcasts))
            sections += PodcastResultAdapter(podcasts) { openPodcast(it.id) }
        }
        if (episodes.isNotEmpty()) {
            sections += HeaderAdapter(getString(R.string.podcast_tab_episodes))
            sections += episodeAdapter!!
        }
        binding.recyclerView.adapter = ConcatAdapter(sections)

        val queryBlank = binding.searchView.text.isNullOrBlank()
        binding.emptyText.isVisible = sections.isEmpty()
        binding.emptyText.setText(
            if (queryBlank) R.string.podcast_search_prompt else R.string.no_results
        )
    }

    private fun openPodcast(podcastId: Long) {
        hideKeyboard()
        findNavController().navigate(
            R.id.podcastDetailsFragment,
            bundleOf("extra_podcast_id" to podcastId)
        )
    }

    private fun openEpisode(episode: EpisodeEntity) {
        hideKeyboard()
        findNavController().navigate(
            R.id.episodeDetailsFragment,
            bundleOf("extra_episode_id" to episode.id)
        )
    }

    /** Same "Stream episode" path as AbsEpisodeListFragment: plays from the enclosure URL,
     * resuming to the saved position. */
    private fun streamEpisode(episode: EpisodeEntity) {
        val podcast = viewModel.podcasts.value?.firstOrNull { it.id == episode.podcastId } ?: return
        resumeSeeker.arm(episode)
        MusicPlayerRemote.openQueue(listOf(episode.toSong(podcast)), 0, true)
        viewLifecycleOwner.lifecycleScope.activateVideoIfPreferred(episode, podcast)
    }

    private fun onEpisodePlayPauseButton(episode: EpisodeEntity) {
        if (MusicPlayerRemote.currentSong.episodeIdOrNull() == episode.id) {
            if (MusicPlayerRemote.isPlaying) MusicPlayerRemote.pauseSong() else MusicPlayerRemote.resumePlaying()
        } else {
            streamEpisode(episode)
        }
    }

    private fun hideKeyboard() {
        val view = _binding?.searchView ?: return
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onPlayingMetaChanged() {
        episodeAdapter?.setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
        resumeSeeker.onPlayingMetaChanged()
    }

    override fun onPlayStateChanged() {
        episodeAdapter?.setPlaybackState(MusicPlayerRemote.currentSong.episodeIdOrNull(), MusicPlayerRemote.isPlaying)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onMenuItemSelected(menuItem: MenuItem) = false

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        _binding = null
    }

    /** One section title ("Podcasts" / "Episodes"), styled like the music search's headers. */
    private class HeaderAdapter(private val title: String) :
        RecyclerView.Adapter<HeaderAdapter.ViewHolder>() {

        class ViewHolder(val binding: SubHeaderBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            SubHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.title.text = title
        }

        override fun getItemCount() = 1
    }

    /** Matching subscriptions as plain list rows (artwork, title, description). */
    private class PodcastResultAdapter(
        private val podcasts: List<PodcastEntity>,
        private val onClick: (PodcastEntity) -> Unit,
    ) : RecyclerView.Adapter<PodcastResultAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemListBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val podcast = podcasts[position]
            with(holder.binding) {
                title.text = podcast.title
                val description = podcast.description?.let {
                    HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                }
                text.text = description
                text.isVisible = !description.isNullOrEmpty()
                menu.isVisible = false
                imageText.isVisible = false
                (image as? ImageView)?.let { artwork ->
                    Glide.with(artwork)
                        .load(podcast.imageUrl)
                        .apply(
                            RequestOptions()
                                .placeholder(R.drawable.default_audio_art)
                                .error(R.drawable.default_audio_art)
                        )
                        .into(artwork)
                }
                root.setOnClickListener { onClick(podcast) }
            }
        }

        override fun getItemCount() = podcasts.size
    }
}
