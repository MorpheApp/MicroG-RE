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

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.google.android.material.appbar.AppBarLayout

/**
 * Gives every settings page its own scroll position, although they all share one scroll view
 * (the one the collapsing app bar follows).
 *
 * A new page opens at the top with the large title expanded; going back returns a page to
 * where it was left. While the shared scroll view and the app bar move for the incoming page,
 * the outgoing one is held where it is on screen, so it does not jump during its exit animation.
 */
class PageScrollKeeper(
    private val scrollView: NestedScrollView,
    private val appBar: AppBarLayout,
    private val navHost: Fragment,
) : NavController.OnDestinationChangedListener {

    private class PageState(val scrollY: Int, val collapsed: Boolean)

    private val states = HashMap<String, PageState>()
    private var currentId: String? = null
    private var appBarOffset = 0
    private var pending: ViewTreeObserver.OnPreDrawListener? = null

    init {
        appBar.addOnOffsetChangedListener { _, offset -> appBarOffset = offset }
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        val entryId = controller.currentBackStackEntry?.id ?: return
        if (entryId == currentId) return
        currentId?.let {
            states[it] = PageState(scrollView.scrollY, appBarOffset < -appBar.totalScrollRange / 2)
        }
        currentId = entryId
        // Entries are unique per visit, so a page met again is one returned to by going back
        val target = states.remove(entryId) ?: PageState(0, false)

        val container = navHost.view as? ViewGroup
        pending?.let { container?.viewTreeObserver?.removeOnPreDrawListener(it) }
        pending = null
        // Stops a fling still running, which would otherwise carry on into the incoming page
        scrollView.smoothScrollBy(0, 0)
        if (container == null) {
            scrollView.scrollTo(0, target.scrollY)
            appBar.setExpanded(!target.collapsed, false)
            return
        }
        // Taken now: once its fragment is replaced, the container no longer lists the outgoing
        // page among its children, it only keeps drawing it until its exit animation is over
        val outgoing = (0 until container.childCount).map { container.getChildAt(it) }
        val before = screenY(container)
        appBar.setExpanded(!target.collapsed, false)

        val listener = object : ViewTreeObserver.OnPreDrawListener {
            private var frames = 0
            private var held = 0f

            override fun onPreDraw(): Boolean {
                // The page's fragment is tagged with its back stack entry. Its view may be one
                // still on its way out, when going back interrupted its exit: then it is the
                // incoming page as well, not one to hold.
                val incoming = navHost.childFragmentManager.findFragmentByTag(entryId)?.view
                    ?.takeIf { it.parent === container && ViewCompat.isLaidOut(it) }
                val done = incoming != null || ++frames >= MAX_WAIT_FRAMES
                if (done) {
                    container.viewTreeObserver.removeOnPreDrawListener(this)
                    if (pending === this) pending = null
                    // Its height now allows the scroll
                    if (incoming != null) scrollView.scrollTo(0, target.scrollY)
                }
                // Whatever the scroll and the app bar moved the pages by, the outgoing ones are
                // moved back, frame by frame until the incoming page is there
                val shift = (before - screenY(container)) - held
                held += shift
                for (view in outgoing) {
                    if (view !== incoming && shift != 0f) view.translationY += shift
                }
                incoming?.translationY = 0f
                return true
            }
        }
        pending = listener
        container.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun screenY(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1]
    }

    private companion object {
        const val MAX_WAIT_FRAMES = 30
    }
}
