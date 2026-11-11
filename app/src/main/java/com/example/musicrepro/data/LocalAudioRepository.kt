package com.example.musicrepro.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioRepository(private val contentResolver: ContentResolver) {
    suspend fun loadTracks(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        ) ?: throw IllegalStateException("The device did not return an audio library.")

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            buildList {
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    add(
                        LocalAudioTrack(
                            id = id,
                            title = it.getString(titleColumn)?.takeIf(String::isNotBlank)
                                ?: "Unknown title",
                            artist = it.getString(artistColumn)?.takeIf(String::isNotBlank)
                                ?: "Unknown artist",
                            album = it.getString(albumColumn)?.takeIf(String::isNotBlank)
                                ?: "Unknown album",
                            durationMs = it.getLong(durationColumn),
                            uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                        )
                    )
                }
            }
        }
    }
}
