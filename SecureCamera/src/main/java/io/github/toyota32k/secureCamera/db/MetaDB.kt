package io.github.toyota32k.secureCamera.db

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.room.Room
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.ScDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

object MetaDB {
    private lateinit var db:Database
    private lateinit var application:Application

    fun initialize(context: Context) {
        if(!this::db.isInitialized) {
            application = context.applicationContext as Application
            db = Room.databaseBuilder(this.application, Database::class.java, "meta.db").build()
            CoroutineScope(Dispatchers.IO).launch {
                val s = db.kvTable().getAt("INIT")
                if (s == null) {
                    makeAll()
                    db.kvTable().insert(KeyValueEntry("INIT", "1"))
                }
            }
        }
    }

    private fun filename2date(filename:String): Date? {
        val dateString = when {
            filename.startsWith(ScDef.PHOTO_PREFIX)-> filename.substringAfter(ScDef.PHOTO_PREFIX).substringBefore(ScDef.PHOTO_EXTENSION)
            filename.startsWith(ScDef.VIDEO_PREFIX)-> filename.substringAfter(ScDef.VIDEO_PREFIX).substringBefore(ScDef.VIDEO_EXTENSION)
            else -> return null
        }
        return try { ITcUseCase.dateFormatForFilename.parse(dateString) } catch(e:Throwable) { Date() }
    }
    private fun filename2type(filename:String): Int? {
        return when {
            filename.startsWith(ScDef.PHOTO_PREFIX)-> 0
            filename.startsWith(ScDef.VIDEO_PREFIX)-> 1
            else -> null
        }
    }

    private suspend fun metaDataFromName(id:Int, name:String, group: Int=0, mark:Int=0):MetaData? {
        return withContext(Dispatchers.IO) {
            val type = filename2type(name) ?: return@withContext null
            val file = File(application.filesDir, name)
            val size = file.length()
            val date = filename2date(name)?.time ?: 0L
            val duration = if (type == 1) {
                MediaMetadataRetriever().apply { setDataSource(file.path) }
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: 0L
            } else 0L
            MetaData(id, name, group, mark, type, date, size, duration)
        }
    }

    suspend fun itemOf(name:String):MetaData? {
        return withContext(Dispatchers.IO) {
            db.metaDataTable().getDataOf(name)
        }
    }

    private suspend fun makeAll() {
        withContext(Dispatchers.IO) {
            val meta = db.metaDataTable()
            application.fileList()?.forEach {
                val m = meta.getDataOf(it)
                if (m == null) {
                    val mn = metaDataFromName(0, it)
                    if (mn != null) {
                        meta.insert(mn)
                    }
                }
            }
        }
    }

    suspend fun list(listMode: PlayerActivity.ListMode):List<MetaData> {
        return withContext(Dispatchers.IO) {
            when (listMode) {
                PlayerActivity.ListMode.ALL -> db.metaDataTable().getAll()
                PlayerActivity.ListMode.PHOTO -> db.metaDataTable().getImages()
                PlayerActivity.ListMode.VIDEO -> db.metaDataTable().getVideos()
            }
        }
    }

    suspend fun register(name:String, group:Int?=null, mark:Int?=null):MetaData? {
        return withContext(Dispatchers.IO) {
            val exist = db.metaDataTable().getDataOf(name)
            if (exist != null) {
                updateFile(exist, group, mark)
                exist
            } else {
                metaDataFromName(0, name, group ?: 0, mark ?: 0)?.apply {
                    db.metaDataTable().insert(this)
                }
            }
        }
    }

    suspend fun updateGroup(data:MetaData, group:Int):MetaData {
        return withContext(Dispatchers.IO) {
            MetaData(data.id, data.name, group, data.mark, data.type, data.date, data.size, data.duration).apply {
                db.metaDataTable().update(this)
            }
        }
    }

    suspend fun updateMark(data:MetaData, mark:Int):MetaData {
        return withContext(Dispatchers.IO) {
            MetaData(data.id, data.name, data.group, mark, data.type, data.date, data.size, data.duration).apply {
                db.metaDataTable().update(this)
            }
        }
    }

    suspend fun updateFile(data:MetaData, group:Int?=null, mark:Int?=null):MetaData {
        return withContext(Dispatchers.IO) {
            metaDataFromName(data.id, data.name, group ?: data.group, mark ?: data.mark)?.apply {
                db.metaDataTable().update(this)
            } ?: throw IllegalStateException("no data to update")
        }
    }

    suspend fun deleteFile(data:MetaData) {
        withContext(Dispatchers.IO) {
            try {
                data.file(application).delete()
            } catch (_: Throwable) {
            }
            db.metaDataTable().delete(data)
        }
    }

}