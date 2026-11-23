package code.name.monkey.retromusic.views

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import code.name.monkey.retromusic.databinding.CollapsingAppbarLayoutBinding
import code.name.monkey.retromusic.databinding.SimpleAppbarLayoutBinding
import code.name.monkey.retromusic.util.PreferenceUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import dev.chrisbanes.insetter.applyInsetter

class TopAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1,
) : AppBarLayout(context, attrs, defStyleAttr) {
    private var simpleAppbarBinding: SimpleAppbarLayoutBinding? = null
    private var collapsingAppbarBinding: CollapsingAppbarLayoutBinding? = null

    val mode: AppBarMode = PreferenceUtil.appBarMode

    init {
        if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding =
                CollapsingAppbarLayoutBinding.inflate(LayoutInflater.from(context), this, true)
            val isLandscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                fitsSystemWindows = false
            }

        } else {
            simpleAppbarBinding =
                SimpleAppbarLayoutBinding.inflate(LayoutInflater.from(context), this, true)
            simpleAppbarBinding?.root?.applyInsetter {
                type(navigationBars = true) {
                    padding(horizontal = true)
                }
            }
            statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)
        }
    }

    fun pinWhenScrolled() {
        simpleAppbarBinding?.root?.updateLayoutParams<LayoutParams> {
            scrollFlags = SCROLL_FLAG_NO_SCROLL
        }
    }

    val toolbar: MaterialToolbar
        get() = if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding?.toolbar!!
        } else {
            simpleAppbarBinding?.toolbar!!
        }

    var title: CharSequence
        get() = if (mode == AppBarMode.COLLAPSING) {
            collapsingAppbarBinding?.collapsingToolbarLayout?.title.toString()
        } else {
            simpleAppbarBinding?.toolbar?.title.toString()
        }
        set(value) {
            if (mode == AppBarMode.COLLAPSING) {
                collapsingAppbarBinding?.collapsingToolbarLayout?.title = value
            } else if (!worldTitle.isNullOrEmpty()) {
                simpleAppbarBinding?.toolbar?.subtitle = value
            } else {
                simpleAppbarBinding?.toolbar?.title = value
            }
        }

    /**
     * The media world this tab belongs to ("Music" / "Podcasts"), shown next to the hamburger
     * at all times. In collapsing mode it's a label pinned inside the toolbar while [title]
     * (the tab name) is the big collapsing title; in simple mode the world becomes the toolbar
     * title and the tab name moves to the subtitle. Null hides it (non top-level screens).
     */
    var worldTitle: CharSequence? = null
        set(value) {
            field = value
            if (mode == AppBarMode.COLLAPSING) {
                collapsingAppbarBinding?.worldTitle?.apply {
                    text = value
                    isVisible = !value.isNullOrEmpty()
                }
            } else {
                simpleAppbarBinding?.toolbar?.let { toolbar ->
                    if (value.isNullOrEmpty()) {
                        toolbar.subtitle?.let { toolbar.title = it }
                        toolbar.subtitle = null
                    } else {
                        val tabName = toolbar.subtitle ?: toolbar.title
                        toolbar.title = value
                        toolbar.subtitle = tabName
                    }
                }
            }
        }

    enum class AppBarMode {
        COLLAPSING,
        SIMPLE
    }
}
