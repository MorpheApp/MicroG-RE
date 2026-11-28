/*
 * Copyright (C) 2026 Morphe.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.ui

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.google.android.gms.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.listitem.ListItemLayout
import kotlinx.coroutines.launch
import org.microg.gms.ui.MorphePeople.People
import org.microg.gms.ui.MorphePeople.Person
import kotlin.math.max

/** Which of the people behind Morphe a [PeopleSheet] lists. */
enum class PeopleList { CONTRIBUTORS, SUPPORTERS }

/**
 * The contributors or the supporters of Morphe, on a full screen page laid out like the people
 * dialogs of Morphe Manager: a grid of pictures from the Morphe API, the contributors by period
 * and followed by the projects MicroG RE grew out of, the supporters by how many back Morphe.
 */
object PeopleSheet {
    private const val TAG = "PeopleSheet"
    private const val DONATE_URL = "https://morphe.software/donate"
    private const val SUPPORTERS_URL = "https://opencollective.com/morpheapp"

    // Least width a person takes: their picture and enough of a name to tell them by
    private const val PERSON_CELL_WIDTH_DP = 84
    private const val MIN_COLUMNS = 3

    /** A project credited by a link to the people who wrote it, going by its initial. */
    private class Project(val name: String, val organization: String, val url: String)

    private val currentProject = Project("MicroG RE", "MorpheApp", "https://github.com/MorpheApp/MicroG-RE/graphs/contributors")
    private val priorProjects = listOf(
        Project("microG", "microg", "https://github.com/microg/GmsCore/graphs/contributors"),
        Project("ReVanced", "ReVanced", "https://github.com/ReVanced/GmsCore/graphs/contributors"),
    )

    private enum class Period(@StringRes val label: Int, val contributors: (People) -> List<Person>) {
        ALL_TIME(R.string.people_all_time, People::contributors),
        SIX_MONTHS(R.string.people_6_months, People::contributors6Months),
        THREE_MONTHS(R.string.people_3_months, People::contributors3Months),
    }

    fun show(activity: FragmentActivity, list: PeopleList) {
        val context: Context = activity
        val dialog = Dialog(context, R.style.ThemeOverlay_MicroG_PeoplePage)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_people, null)

        val title = view.findViewById<TextView>(R.id.people_title)
        val subtitle = view.findViewById<TextView>(R.id.people_subtitle)
        val periodButton = view.findViewById<View>(R.id.people_period)
        val loading = view.findViewById<View>(R.id.people_loading)
        val recycler = view.findViewById<RecyclerView>(R.id.people_list)

        view.findViewById<ImageView>(R.id.people_icon).setImageResource(
            if (list == PeopleList.CONTRIBUTORS) org.microg.tools.ui.R.drawable.ic_contributors else org.microg.tools.ui.R.drawable.ic_volunteer
        )
        title.setText(
            if (list == PeopleList.CONTRIBUTORS) org.microg.tools.ui.R.string.about_morphe_contributors
            else org.microg.tools.ui.R.string.about_morphe_supporters
        )
        view.findViewById<View>(R.id.people_become_supporter).apply {
            visibility = if (list == PeopleList.SUPPORTERS) View.VISIBLE else View.GONE
            setOnClickListener { openUrl(context, DONATE_URL) }
        }
        view.findViewById<View>(R.id.people_close).setOnClickListener { dialog.dismiss() }

        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density - 32
        val columns = max(MIN_COLUMNS, (widthDp / PERSON_CELL_WIDTH_DP).toInt())
        val adapter = PeopleAdapter { openUrl(context, it) }
        recycler.layoutManager = GridLayoutManager(context, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.currentList.getOrNull(position) is Item.PersonItem) 1 else columns
            }
        }
        recycler.adapter = adapter

        var people: People? = null
        var unavailable = false
        var period = Period.ALL_TIME

        fun render() {
            val loaded = people
            loading.visibility = if (loaded == null && !unavailable) View.VISIBLE else View.GONE
            val periods = loaded?.takeIf { list == PeopleList.CONTRIBUTORS }
                ?.let { p -> Period.entries.filter { it.contributors(p).isNotEmpty() } }.orEmpty()
            if (period !in periods) period = Period.ALL_TIME
            periodButton.visibility = if (periods.size > 1) View.VISIBLE else View.GONE
            periodButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    periods.forEachIndexed { index, option ->
                        menu.add(Menu.NONE, index, index, option.label).isChecked = option == period
                    }
                    menu.setGroupCheckable(Menu.NONE, true, true)
                    setOnMenuItemClickListener { item ->
                        period = periods[item.itemId]
                        render()
                        true
                    }
                }.show()
            }

            val persons = when (list) {
                PeopleList.CONTRIBUTORS -> loaded?.let(period.contributors)
                PeopleList.SUPPORTERS -> loaded?.supporters
            }.orEmpty()
            val res = context.resources
            subtitle.text = when (list) {
                PeopleList.CONTRIBUTORS -> persons.size.takeIf { it > 0 }
                    ?.let { res.getQuantityString(R.plurals.people_contributor_count, it, it.toString()) + " · " + res.getString(period.label) }
                    ?: if (unavailable) res.getString(R.string.people_unavailable) else ""
                PeopleList.SUPPORTERS -> listOfNotNull(
                    res.getString(R.string.people_supporters_description),
                    loaded?.supportersTotal?.takeIf { it > 0 }?.toString()
                ).joinToString(" · ")
            }

            val items = mutableListOf<Item>()
            if (persons.isEmpty() && unavailable) {
                items += Item.Row(
                    key = "unavailable", title = res.getString(R.string.people_unavailable),
                    icon = R.drawable.ic_cloud_off, url = null
                )
            }
            persons.forEachIndexed { index, person ->
                // Keyed by place for the supporters: two of them may link to the same page
                val key = if (list == PeopleList.CONTRIBUTORS) "person:" + person.url else "person:$index"
                items += Item.PersonItem(key, person)
            }
            when (list) {
                PeopleList.CONTRIBUTORS -> if (loaded != null || unavailable) {
                    // Not ahead of the list: people arriving above would push it out of view
                    if (persons.isEmpty()) items += currentProject.toRow(0, 1)
                    items += Item.Section("prior", R.string.people_prior_development, R.drawable.ic_history)
                    priorProjects.forEachIndexed { index, project -> items += project.toRow(index, priorProjects.size) }
                }
                PeopleList.SUPPORTERS -> {
                    val unlisted = (loaded?.supportersTotal ?: 0) - persons.size
                    if (persons.isNotEmpty() && unlisted > 0) {
                        items += Item.Row(
                            key = "more", title = res.getString(R.string.people_more, unlisted.toString()),
                            icon = org.microg.tools.ui.R.drawable.ic_volunteer, url = SUPPORTERS_URL
                        )
                    }
                }
            }
            adapter.submitList(items)
        }

        render()
        val job = activity.lifecycleScope.launch {
            // Cached lists show at once, then the network brings them up to date where they aged
            MorphePeople.cached(context)?.let { people = it; render() }
            val fresh = MorphePeople.refresh(context)
            people = fresh
            unavailable = fresh == null
            render()
        }
        // A plain dialog does not survive the activity being recreated: close it with the
        // activity rather than leak its window.
        val closeWithActivity = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        activity.lifecycle.addObserver(closeWithActivity)
        dialog.setOnDismissListener {
            job.cancel()
            activity.lifecycle.removeObserver(closeWithActivity)
        }

        dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.window?.let { layOutFullScreen(it, view, recycler) }
        dialog.show()
    }

    /**
     * Spreads the page under the system bars, as the About page under it is: the header clears
     * the status bar, the buttons the navigation bar, and the grid scrolls on under the actions,
     * fading out above them, with room at its end to scroll its last row out from under them.
     */
    private fun layOutFullScreen(window: Window, view: View, recycler: RecyclerView) {
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val night = (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val header = view.findViewById<View>(R.id.people_header)
        val actions = view.findViewById<View>(R.id.people_actions)
        val buttons = view.findViewById<View>(R.id.people_buttons)
        val headerTop = header.paddingTop
        val buttonsBottom = buttons.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, right = bars.right)
            header.updatePadding(top = headerTop + bars.top)
            buttons.updatePadding(bottom = buttonsBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Solid behind the buttons, so names do not read through the outlined one, and fading out
        // over a fixed strip above them, the same however many buttons show; Manager blurs what
        // passes under its buttons instead
        val background = MaterialColors.getColor(view, android.R.attr.colorBackground)
        buttons.setBackgroundColor(background)
        view.findViewById<View>(R.id.people_actions_fade).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(background and 0x00FFFFFF, background)
        )
        actions.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) recycler.updatePadding(bottom = bottom - top)
        }
    }

    private fun Project.toRow(position: Int, count: Int) = Item.Row(
        key = "project:$name", title = name, subtitle = "github.com/$organization",
        initial = name.take(1).uppercase(), url = url, position = position, count = count
    )

    private fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open $url", e)
        }
    }

    private sealed class Item(val key: String) {
        class PersonItem(key: String, val person: Person) : Item(key)
        class Section(key: String, @StringRes val title: Int, @DrawableRes val icon: Int) : Item(key)
        class Row(
            key: String,
            val title: String,
            val subtitle: String? = null,
            val initial: String? = null,
            @DrawableRes val icon: Int = 0,
            val url: String?,
            val position: Int = 0,
            val count: Int = 1,
        ) : Item(key)
    }

    private class PeopleAdapter(private val onOpen: (String) -> Unit) :
        ListAdapter<Item, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: Item, newItem: Item) = when {
                oldItem is Item.PersonItem && newItem is Item.PersonItem -> oldItem.person == newItem.person
                oldItem is Item.Row && newItem is Item.Row -> oldItem.title == newItem.title &&
                        oldItem.position == newItem.position && oldItem.count == newItem.count
                oldItem is Item.Section && newItem is Item.Section -> oldItem.title == newItem.title &&
                        oldItem.icon == newItem.icon
                else -> false
            }
        }) {

        // People whose picture already came in, so one scrolled away and back does not pop again
        private val entered = HashSet<String>()

        override fun getItemViewType(position: Int) = when (getItem(position)) {
            is Item.PersonItem -> TYPE_PERSON
            is Item.Section -> TYPE_SECTION
            is Item.Row -> TYPE_ROW
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout = when (viewType) {
                TYPE_PERSON -> R.layout.item_people_person
                TYPE_SECTION -> R.layout.item_people_section
                else -> R.layout.item_people_row
            }
            return object : RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView
            when (val item = getItem(position)) {
                is Item.PersonItem -> bindPerson(view, item, position)
                is Item.Section -> {
                    view.findViewById<ImageView>(R.id.section_icon).setImageResource(item.icon)
                    view.findViewById<TextView>(R.id.section_title).setText(item.title)
                }
                is Item.Row -> bindRow(view, item)
            }
        }

        private fun bindPerson(view: View, item: Item.PersonItem, position: Int) {
            val person = item.person
            val context = view.context
            val density = context.resources.displayMetrics.density
            val ring = view.findViewById<PulsingRingView>(R.id.person_ring)
            val initial = view.findViewById<TextView>(R.id.person_initial)
            val avatar = view.findViewById<ImageView>(R.id.person_avatar)
            view.findViewById<TextView>(R.id.person_name).text = person.name
            view.contentDescription = person.name
            view.setOnClickListener { person.url.takeIf { it.isNotEmpty() }?.let(onOpen) }

            // A Mega Supporter's picture sits inside a ring, smaller by the ring and its gap
            ring.visibility = if (person.mega) View.VISIBLE else View.GONE
            ring.color = ContextCompat.getColor(context, R.color.people_mega_ring)
            val size = ((if (person.mega) 48 else 56) * density).toInt()
            for (target in listOf(initial, avatar)) {
                val params = target.layoutParams
                if (params.width != size) {
                    params.width = size
                    params.height = size
                    target.layoutParams = params
                }
            }

            initial.text = person.name.take(1).uppercase()
            initial.visibility = View.VISIBLE
            avatar.dispose()
            avatar.setImageDrawable(null)
            if (person.avatar != null) {
                avatar.load(person.avatar) {
                    crossfade(true)
                    // Gone once the picture is there, so it does not show through a clear one
                    listener(onSuccess = { _, _ -> initial.visibility = View.INVISIBLE })
                }
            }

            // Each picture pops in a moment after the one before it, corner to corner across
            // the first screenful; later ones come in as they are scrolled to
            val frame = view.findViewById<View>(R.id.person_frame)
            frame.animate().cancel()
            if (entered.add(item.key)) {
                val columns = ((view.parent as? RecyclerView)?.layoutManager as? GridLayoutManager)?.spanCount ?: 1
                val row = position / columns
                val column = position % columns
                val order = if (row < STAGGERED_ROWS) row + column else column
                frame.scaleX = ENTRANCE_SCALE
                frame.scaleY = ENTRANCE_SCALE
                frame.alpha = 0f
                frame.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setStartDelay(order * STAGGER_MILLIS)
                    .setDuration(ENTRANCE_MILLIS)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            } else {
                frame.scaleX = 1f
                frame.scaleY = 1f
                frame.alpha = 1f
            }
        }

        private fun bindRow(view: View, item: Item.Row) {
            view.findViewById<ListItemLayout>(R.id.people_row_layout).updateAppearance(item.position, item.count)
            val initial = view.findViewById<TextView>(R.id.row_initial)
            val icon = view.findViewById<ImageView>(R.id.row_icon)
            if (item.initial != null) {
                initial.visibility = View.VISIBLE
                initial.text = item.initial
                icon.visibility = View.GONE
            } else {
                initial.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(item.icon)
            }
            view.findViewById<TextView>(R.id.row_title).text = item.title
            view.findViewById<TextView>(R.id.row_subtitle).apply {
                text = item.subtitle
                visibility = if (item.subtitle == null) View.GONE else View.VISIBLE
            }
            // Rows that leave for the browser say so
            view.findViewById<View>(R.id.row_open).visibility = if (item.url != null) View.VISIBLE else View.GONE
            val card = view.findViewById<View>(R.id.people_row_card)
            card.isClickable = item.url != null
            card.setOnClickListener(item.url?.let { url -> View.OnClickListener { onOpen(url) } })
        }

        companion object {
            const val TYPE_PERSON = 0
            const val TYPE_SECTION = 1
            const val TYPE_ROW = 2

            const val STAGGER_MILLIS = 30L
            const val STAGGERED_ROWS = 8
            const val ENTRANCE_MILLIS = 300L
            const val ENTRANCE_SCALE = 0.6f
        }
    }
}

/**
 * A ring around a round picture, with softer ones forever widening out of it and fading, so a
 * Mega Supporter draws the eye without a label saying why (as in Morphe Manager).
 */
class PulsingRingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val ringWidth = 2 * density
    private val spread = 8 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    private var pulse = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    var color: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        // The ring is as wide as a plain picture (56dp); the waves spread out from it
        val radius = (56 * density - ringWidth) / 2
        paint.color = color
        repeat(WAVES) { wave ->
            val progress = (pulse + wave.toFloat() / WAVES) % 1f
            paint.alpha = (255 * 0.8f * (1f - progress)).toInt()
            canvas.drawCircle(cx, cy, radius + spread * progress, paint)
        }
        paint.alpha = 255
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        if (isAttachedToWindow && isShown) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    private companion object {
        const val WAVES = 2
    }
}
