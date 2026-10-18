package com.onurguner.ogauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.onurguner.ogauto.R
import com.onurguner.ogauto.video.PlaylistStore
import com.onurguner.ogauto.video.VideoPlayback

/** The playlist, paged to whatever list length the host allows. */
class ChannelListScreen(
    carContext: CarContext,
    private val page: Int = 0,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        var channels = VideoPlayback.channels.value
        if (channels.isEmpty()) {
            channels = PlaylistStore.cached(carContext)
            if (channels.isNotEmpty()) VideoPlayback.setChannels(carContext, channels)
        }
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrDefault(6).coerceAtLeast(3)
        val perPage = limit - 2 // room for "previous" and "next" rows
        val start = page * perPage
        val items = ItemList.Builder()
        if (channels.isEmpty()) {
            items.setNoItemsMessage(carContext.getString(R.string.video_no_channels))
        }
        if (page > 0) {
            items.addItem(nav(R.string.video_prev_page) {
                screenManager.pop()
            })
        }
        val current = VideoPlayback.index.value
        channels.drop(start).take(perPage).forEachIndexed { offset, ch ->
            val i = start + offset
            val row = Row.Builder()
                .setTitle((if (i == current) "▶ " else "") + ch.title.ifBlank { "#${i + 1}" })
            ch.group?.let { row.addText(it) }
            row.setOnClickListener {
                VideoPlayback.playIndex(i)
                screenManager.popTo(VIDEO_MARKER)
            }
            items.addItem(row.build())
        }
        if (start + perPage < channels.size) {
            items.addItem(nav(R.string.video_next_page) {
                screenManager.push(ChannelListScreen(carContext, page + 1))
            })
        }
        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle(
                carContext.getString(R.string.video_channels_title) +
                    if (channels.size > perPage) " (${page + 1}/${(channels.size + perPage - 1) / perPage})" else ""
            )
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun nav(titleRes: Int, onClick: () -> Unit): Row =
        Row.Builder().setTitle(carContext.getString(titleRes)).setOnClickListener(onClick).build()

    companion object {
        const val VIDEO_MARKER = "video"
    }
}
