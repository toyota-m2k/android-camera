package io.github.toyota32k.secureCamera.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.ScDef
import io.github.toyota32k.secureCamera.utils.VideoUtil
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

data class ItemEx(val data: MetaData, val chapterList: List<IChapter>?) {
    fun file(context: Context):File {
        return data.file(context)
    }
    val id:Int
        get() = data.id
    val name:String
        get() = data.name
    val date:Long
        get() = data.date
    val type:Int
        get() = data.type
    val isVideo:Boolean
        get() = data.isVideo
    val isPhoto:Boolean
        get() = data.isPhoto
    val size:Long
        get() = data.size
    val duration:Long
        get() = data.duration
    val mark:Mark
        get() = Mark.valueOf(data.mark)
    val rating:Rating
        get() = Rating.valueOf(data.flag)
}

object MetaDB {
    private lateinit var db:Database
    lateinit var application:Application
    val logger = UtLog("DB", null, MetaDB::class.java)

    fun initialize(context: Context) {
        if(!this::db.isInitialized) {
            application = context.applicationContext as Application

            db = Room.databaseBuilder(this.application, Database::class.java, "meta.db").build()
            CoroutineScope(Dispatchers.IO).launch {
                val s = db.kvTable().getAt("INIT")
                if (s == null) {
                    db.kvTable().insert(KeyValueEntry("INIT", "1"))
                }
                makeAll()
                deleteTestFile()
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


//    private suspend fun tryGetDuration(file:File, retry:Int):Long {
//
//        for(i in 0..retry) {
//            val fd = openFile(file, if(i==0) retry else 0)
//            if (fd == null) {
//                logger.error("cannot open file: ${file.name}")
//                return 0L
//            }
//            fd.use {
//                try {
//                    MediaMetadataRetriever().use { mmr ->
//                        mmr.setDataSource(fd.fileDescriptor)
//                        val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//                        if (d != null) {
//                            logger.debug("retrieve duration: $d after ${i + 1} trial.")
//                            return d.toLong()
//                        }
//                    }
//                } catch(e:Throwable) {
//                    logger.error(e)
//                    return 0L
//                }
//            }
//            delay(1000)
//        }
//        logger.error("cannot retrieve duration")
//        return 0L
//
//        return try {
//            val mmr = MediaMetadataRetriever()
//            mmr.setDataSource(fd.fileDescriptor)
//            var d:String? = null
//            for(i in 0..60) {
//                d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//                if(d!=null) {
//                    break
//                }
//                delay(1000)
//            }
//            logger.debug("duration = $d")
//            d?.toLongOrNull() ?: 0L
//
//        } catch(e:Throwable) {
//            logger.error(e)
//            0L
//        }
//        return MediaMetadataRetriever().apply { setDataSource(fd.fileDescriptor) }
//            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

//    }

    private suspend fun metaDataFromName(id:Int, name:String, group: Int=0, mark:Int=0, rating:Int=0, allowRetry:Int=0):MetaData? {
        return withContext(Dispatchers.IO) {
            val type = filename2type(name) ?: return@withContext null
            val file = File(application.filesDir, name)
            val size = file.length()
            val date = filename2date(name)?.time ?: 0L
            val duration = if (type == 1) {
                VideoUtil.getDuration(file, allowRetry)
            } else 0L
            MetaData(id, name, group, mark, type, date, size, duration, rating)
        }
    }

    suspend fun itemOf(name:String):MetaData? {
        return withContext(Dispatchers.IO) {
            db.metaDataTable().getDataOf(name)
        }
    }

    suspend fun itemAt(id:Long):MetaData? {
        return withContext(Dispatchers.IO) {
            db.metaDataTable().getDataAt(id)
        }
    }

    private suspend fun makeAll() {
        withContext(Dispatchers.IO) {
            val meta = db.metaDataTable()
            application.fileList()?.forEach {
                logger.debug(it)
                val m = meta.getDataOf(it)
                if (m == null||(m.isVideo&&m.duration==0L)) {
                    val mn = metaDataFromName(0, it, allowRetry = 0)
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

    suspend fun register(name:String, mark:Mark?=null, rating: Rating?=null):MetaData? {
        return withContext(Dispatchers.IO) {
            val exist = db.metaDataTable().getDataOf(name)
            if (exist != null) {
                updateFile(exist, null, mark, rating)
                exist
            } else {
                metaDataFromName(0, name, 0, mark?.v ?: 0, allowRetry = 10)?.apply {
                    db.metaDataTable().insert(this)
                }
            }
        }
    }

//    private suspend fun updateGroup(data:MetaData, group:Int):MetaData {
//        return withContext(Dispatchers.IO) {
//            MetaData(data.id, data.name, group, data.mark, data.type, data.date, data.size, data.duration).apply {
//                db.metaDataTable().update(this)
//            }
//        }
//    }

    private suspend fun updateMarkRating(data:MetaData, mark:Mark?,rating: Rating?):MetaData {
        if(mark==null && rating==null) return data;
        return withContext(Dispatchers.IO) {
            MetaData(data.id, data.name, data.group, mark?.v?:data.mark, data.type, data.date, data.size, data.duration,rating?.v?:data.flag).apply {
                db.metaDataTable().update(this)
            }
        }
    }

//    private suspend fun updateFile(data:MetaData, group:Int?=null, mark:Int?=null):MetaData {
//        return withContext(Dispatchers.IO) {
//            metaDataFromName(data.id, data.name, group ?: data.group, mark ?: data.mark, allowRetry = 10)?.apply {
//                db.metaDataTable().update(this)
//            } ?: throw IllegalStateException("no data to update")
//        }
//    }

    private suspend fun deleteFile(data:MetaData) {
        withContext(Dispatchers.IO) {
            try {
                data.file(application).delete()
            } catch (_: Throwable) {
            }
            db.metaDataTable().delete(data)
        }
    }

    suspend fun getChaptersFor(data:MetaData):List<IChapter> {
        return withContext(Dispatchers.IO) {
            db.chapterDataTable().getByOwner(data.id).map {
                Chapter(it.position, it.label, it.disabled)
            }
        }
    }

    suspend fun setChaptersFor(data: MetaData, chapters:List<IChapter>?) {
        return withContext(Dispatchers.IO) {
            db.chapterDataTable().setForOwner(data.id, chapters?.map {ChapterData(0,data.id, it.position, it.label, it.skip)})
        }
    }

    suspend fun MetaData.toItemEx():ItemEx {
        val chapters = if(isVideo) getChaptersFor(this) else null
        return ItemEx(this, chapters)
    }

    suspend fun listEx(mode:PlayerActivity.ListMode):List<ItemEx> {
        return withContext(Dispatchers.IO) {
            list(mode).map {it.toItemEx() }
        }
    }

    suspend fun itemExOf(name:String):ItemEx? {
        return withContext(Dispatchers.IO) {
            itemOf(name)?.run {toItemEx() }
        }
    }

    suspend fun updateMarkRating(item:ItemEx, mark:Mark?=null, rating: Rating?):ItemEx {
        val newData = updateMarkRating(item.data, mark, rating)
        return ItemEx(newData, item.chapterList)
    }

    suspend fun updateFile(item:ItemEx, chapterList: List<IChapter>?, mark:Mark?=null, rating: Rating?=null):ItemEx {
        if(chapterList!=null) {
            setChaptersFor(item.data, chapterList)
        }
        val newData = updateMarkRating(item.data, mark, rating)
        return ItemEx(newData, chapterList?:item.chapterList)
    }
    suspend fun updateFile(data:MetaData, chapterList: List<IChapter>?, mark:Mark?=null, rating: Rating?=null):MetaData {
        if(chapterList!=null) {
            setChaptersFor(data, chapterList)
        }
        return updateMarkRating(data, mark, rating)
    }

    suspend fun registerEx(name:String, mark:Mark?=null, rating: Rating?=null):ItemEx? {
        val newData = register(name, mark, rating) ?: return null
        return ItemEx(newData, null)
    }


    suspend fun deleteFile(item:ItemEx) {
        setChaptersFor(item.data, emptyList())
        deleteFile(item.data)
    }

    private const val testItemName = "mov-2030.01.01-00:00:00.mp4"

    private suspend fun prepareTestFile():File {
        deleteTestFile()
        return File(application.filesDir, testItemName)
    }

    private suspend fun deleteTestFile() {
        val item = itemOf(testItemName)
        if (item != null) {
            deleteFile(item)
        }
    }

    suspend fun withTestFile(fn:(File)->Unit) {
        val file = prepareTestFile()
        fn(file)
        if (file.exists()) {
            register(testItemName, null, null)
        }
    }
}