package io.github.toyota32k.secureCamera.db

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.HttpInputFile
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.ScDef
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.TcClient.RepairingItem
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.db.ItemEx.Companion.decodeChaptersString
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.utils.VideoUtil
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

data class ItemEx(val data: MetaData, val chapterList: List<IChapter>?) {
//    fun file(context: Context):File {
//        return data.file(context)
//    }
    val file:File
        get() = data.file
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
        get() = Rating.valueOf(data.rating)
    val cloud:CloudStatus
        get() = CloudStatus.valueOf(data.cloud)
    val nameForDisplay:String
        get() = name.substringAfter("-").substringBeforeLast(".").replace("-", "  ")

    val creationDate:Long by lazy {
        filename2date(name)?.time ?: 0L
    }

    val dpDate: DPDate by lazy {
//        val dp = filename2dpDate(name) ?: DPDate.Invalid
//        MetaDB.logger.debug("$name - $dp")
//        dp
        filename2dpDate(name) ?: DPDate.Invalid
    }

    val serverUri:String
        get() = "http://${Authentication.activeHostAddress}/${if(isVideo) "video" else "photo"}?auth=${Authentication.authToken}&o=${Settings.SecureArchive.clientId}&c=${id}"

    val uri:String
        get() {
            return if(cloud.loadFromCloud) {
                serverUri
            } else {
                file.toUri().toString()
            }
        }

    private val encodedChapters:String
        get() {
            return if(isVideo) {
                chapterList?.fold(JSONArray()) { acc, chapter ->
                    acc.apply {
                        put(JSONObject().apply {
                            put("position", chapter.position)
                            put("label", chapter.label)
                            put("skip", chapter.skip)
                        })
                    }
                }?.toString() ?: ""
            } else ""
        }

    val attrDataJson : JSONObject
        get() = JSONObject()
                .put("cmd", "extension")
                .put("id", "$id")
                .put("attrDate", "${data.attr_date}")
                .put("rating", "${rating.v}")
                .put("mark", "${mark.v}")
                .put("label", data.label?:"")
                .put("category", data.category?:"")
                .put("chapters", encodedChapters)

    companion object {
        fun filename2date(filename:String): Date? {
            val dateString = when {
                filename.startsWith(ScDef.PHOTO_PREFIX)-> filename.substringAfter(ScDef.PHOTO_PREFIX).substringBefore(ScDef.PHOTO_EXTENSION)
                filename.startsWith(ScDef.VIDEO_PREFIX)-> filename.substringAfter(ScDef.VIDEO_PREFIX).substringBefore(ScDef.VIDEO_EXTENSION)
                else -> return null
            }
            return try { ITcUseCase.dateFormatForFilename.parse(dateString) } catch(e:Throwable) { Date() }
        }

        // "yyyy.MM.dd-HH:mm:ss"
        private val regex4dpDate = Regex("""(\d{4})\.(\d{2})\.(\d{2})-(\d{2}):(\d{2}):(\d{2})""")
        fun filename2dpDate(filename:String): DPDate? {
            val dateString = when {
                filename.startsWith(ScDef.PHOTO_PREFIX)-> filename.substringAfter(ScDef.PHOTO_PREFIX).substringBefore(ScDef.PHOTO_EXTENSION)
                filename.startsWith(ScDef.VIDEO_PREFIX)-> filename.substringAfter(ScDef.VIDEO_PREFIX).substringBefore(ScDef.VIDEO_EXTENSION)
                else -> return null
            }
            val matchResult = regex4dpDate.matchEntire(dateString)?: return null
            return try {
                DPDate(matchResult.groupValues[1].toInt(), matchResult.groupValues[2].toInt()-1, matchResult.groupValues[3].toInt())
            } catch (_:Throwable) {
                null
            }
        }


        fun creationDate(item:MetaData): Long {
            return filename2date(item.name)?.time ?: 0L
        }

        suspend fun decodeChaptersString(jsonStr:String): Sequence<IChapter> {
            try {
                val json = JSONArray(jsonStr)
                return sequence<IChapter> {
                    for (i in 0..<json.length()) {
                        val o = json.getJSONObject(i)
                        yield(
                            Chapter(
                                o.optLong("position", 0L),
                                o.optString("label", ""),
                                o.optBoolean("skip", false)
                            )
                        )
                    }
                }
            } catch(e:Throwable) {
                TcClient.logger.error(e)
                return emptySequence()
            }
        }
    }
}

object MetaDB {
    private var dbInstance:Database? = null
    private val db:Database
        get() = synchronized(this) {
            dbInstance ?: throw IllegalStateException("DB not opened")
        }

    private var refCount = 0
    fun open():Boolean {
        return synchronized(this) {
            if (dbInstance == null) {
                dbInstance = Room.databaseBuilder(this.application, Database::class.java, "meta.db").build()
            }
            refCount++
            true
        }
    }

    fun close() {
        synchronized(this) {
            refCount--
            if(refCount==0) {
                dbInstance?.close()
                dbInstance = null
            }
        }
    }

//    private lateinit var db:Database
    lateinit var application:Application
    val logger = UtLog("DB", null, MetaDB::class.java)

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            //カラム追加
            db.execSQL("ALTER TABLE t_meta ADD attr_date INTEGER default 0 not null")
            db.execSQL("ALTER TABLE t_meta ADD label TEXT")
            db.execSQL("ALTER TABLE t_meta ADD category TEXT")
        }
    }

    /**
     * DBを初期化する（Applicationクラスの onCreateから呼ぶ）
     */
    fun initialize(application: Application) {
        this.application = application

        val db = Room.databaseBuilder(this.application, Database::class.java, "meta.db")
            .addMigrations(MIGRATION_1_2)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            // Migration
            val s = db.kvTable().getAt("INIT")
            if (s == null) {
                db.kvTable().insert(KeyValueEntry("INIT", "1"))
            } else if (s.value.toInt() == 1) {
                db.kvTable().update(KeyValueEntry("INIT", "2"))
                val tbl = db.metaDataTable()
                val now = Date().time
                listEx(PlayerActivity.ListMode.ALL).forEach {
                    if (it.data.rating != 0 || it.data.mark != 0 || it.data.group != 0 || !it.chapterList.isNullOrEmpty()) {
                        tbl.update(MetaData.modifiedEntry(it.data, attr_date = now))
                    }
                }
            }

            db.metaDataTable().getAll().filter { it.attr_date != 0L }.forEach {
                logger.debug("${it.name} : ${it.attr_date}")
            }
            db.close()

            open()
            try {
                makeAll()
                deleteTestFile()
            } finally {
                close()
            }
        }
    }

    object KV {
        suspend fun put(key:String, value:String) {
            withContext(Dispatchers.IO) {
                val e = db.kvTable().getAt(key)
                if (e == null) {
                    db.kvTable().insert(KeyValueEntry(key, value))
                } else {
                    db.kvTable().update(KeyValueEntry(key, value))
                }
            }
        }
        suspend fun get(key: String):String? {
            return withContext(Dispatchers.IO) {
                db.kvTable().getAt(key)?.value
            }
        }
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

    private suspend fun metaDataFromName(
        org:MetaData?,
        name:String,
//        group: Int=org?.group?:0,
//        mark:Int=org?.mark?:0,
//        rating:Int=org?.rating?:0,
        cloud: Int=org?.cloud?:CloudStatus.Local.v,
        allowRetry:Int=0,
        updateAttrDate:Boolean=false
        ):MetaData? {
        return withContext(Dispatchers.IO) {
            val type = filename2type(name) ?: return@withContext null
            val file = File(application.filesDir, name)
            val size = file.length()
            val date = file.lastModified() //.filename2date(name)?.time ?: 0L
            val duration = if (type == 1) {
                VideoUtil.getDuration(file, allowRetry)
            } else 0L
            val attrDate = if (updateAttrDate) Date().time else org?.attr_date ?: 0L
            if(org==null) {
                // 新規
                MetaData.newEntry(name, type, date, size, duration)
            } else {
                // 更新
                MetaData.modifiedEntry(org, type = type, date = date, size = size, duration = duration, cloud = cloud, attr_date = attrDate)
            }
        }
    }

    // region READ

    suspend fun itemOf(name:String):MetaData? {
        return withContext(Dispatchers.IO) {
            db.metaDataTable().getDataOf(name)
        }
    }

    suspend fun itemAt(id:Int):MetaData? {
        return withContext(Dispatchers.IO) {
            db.metaDataTable().getDataAt(id)
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
            itemOf(name)?.toItemEx()
        }
    }
    suspend fun itemExAt(id:Int):ItemEx? {
        return withContext(Dispatchers.IO) {
            itemAt(id)?.toItemEx()
        }
    }

    // endregion READ

    // region CREATION

    private suspend fun makeAll() {
        val isNeedUpdate = {m:MetaData->
            when {
                m.isVideo && m.duration == 0L->true
                CloudStatus.valueOf(m.cloud).isFileInLocal && m.size != m.file.length() -> {
                    logger.info("length mismatch: ${m.name} db=${m.size} file=${m.file.length()}")
                    true
                }
                else -> false
            }
        }
        withContext(Dispatchers.IO) {
            val meta = db.metaDataTable()
            application.fileList()?.forEach {filename->
                logger.debug(filename)
                val m = meta.getDataOf(filename)
                if(m==null) {
                    // DB未登録のデータを登録
                    val mn = metaDataFromName(null, filename, allowRetry = 0)
                    if (mn != null) {
                        meta.insert(mn)
                    }
                } else if (isNeedUpdate(m)) {
                    // サイズが違う、durationが未登録のデータがあれば更新
                    val mn = metaDataFromName(m, m.name, allowRetry = 0)
                    if (mn != null) {
                        meta.update(mn)
                    }

                }
            }
        }
    }

    suspend fun register(name:String):MetaData? {
        return withContext(Dispatchers.IO) {
            val exist = db.metaDataTable().getDataOf(name)
            if (exist != null) {
                updateFile(exist, null)
                exist
            } else {
                metaDataFromName(null, name, allowRetry = 10)?.apply {
                    db.metaDataTable().insert(this)
                }
                itemOf(name)?.apply {
                    DBChange.add(id)
                }
            }
        }
    }

    // endregion CREATION

//    private suspend fun updateGroup(data:MetaData, group:Int):MetaData {
//        return withContext(Dispatchers.IO) {
//            MetaData(data.id, data.name, group, data.mark, data.type, data.date, data.size, data.duration).apply {
//                db.metaDataTable().update(this)
//            }
//        }
//    }

//    private suspend fun updateFile(data:MetaData, group:Int?=null, mark:Int?=null):MetaData {
//        return withContext(Dispatchers.IO) {
//            metaDataFromName(data.id, data.name, group ?: data.group, mark ?: data.mark, allowRetry = 10)?.apply {
//                db.metaDataTable().update(this)
//            } ?: throw IllegalStateException("no data to update")
//        }
//    }

    // region DELETION

    private suspend fun deleteFile(data:MetaData) {
        withContext(Dispatchers.IO) {
            try {
                data.file.delete()
            } catch (_: Throwable) {
            }
            db.metaDataTable().delete(data)
            DBChange.delete(data.id)
        }
    }

    suspend fun deleteFile(item:ItemEx) {
        setChaptersFor(item.data, emptyList())
        deleteFile(item.data)
    }

    // endregion DELETION

    // region Chapter

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
            db.metaDataTable().update(MetaData.modifiedEntry(data, attr_date = Date().time))
        }
    }
    // endregion Chapter

    // region UPDATE

    /**
     * Rating, Mark を変更する
     */
    private suspend fun updateMarkRating(data:MetaData, mark:Mark?,rating: Rating?):MetaData {
        if(mark==null && rating==null) return data
        return withContext(Dispatchers.IO) {
            MetaData.modifiedEntry(data, mark =mark?.v?:data.mark, rating = rating?.v?:data.rating, attr_date = Date().time).apply {
                db.metaDataTable().update(this)
                DBChange.update(data.id)
            }
        }
    }
    suspend fun updateMarkRating(item:ItemEx, mark:Mark?, rating: Rating?):ItemEx {
        val newData = updateMarkRating(item.data, mark, rating)
        return ItemEx(newData, item.chapterList)
    }

    /**
     * cloud フラグを変更す
     */
    private suspend fun updateCloud(data:MetaData, cloud: Int):MetaData {
        return withContext(Dispatchers.IO) {
            MetaData(data.id, data.name, data.group, data.mark, data.type, data.date, data.size, data.duration,data.rating,cloud).apply {
                db.metaDataTable().update(this)
                DBChange.update(data.id)
            }
        }
    }

    suspend fun updateCloud(item: ItemEx, cloud:CloudStatus):ItemEx {
        return if(item.cloud!=cloud) {
            ItemEx(updateCloud(item.data, cloud.v), item.chapterList)
        } else item
    }

    suspend fun updateCloud(id:Int, cloud:CloudStatus) {
        val item = itemAt(id) ?: return
        if(item.cloud!=cloud.v) {
            updateCloud(item, cloud.v)
        }
    }

    /**
     * ファイルが編集されたとき、サイズやDurationなどの情報を更新する
     */
    suspend fun updateFile(item:ItemEx, chapterList: List<IChapter>?):ItemEx {
        val newData = updateFile(item.data, chapterList)
        return ItemEx(newData, chapterList?:item.chapterList)
    }

    suspend fun updateFile(data:MetaData, chapterList: List<IChapter>?):MetaData {
        var updateAttr = false
        if(chapterList!=null) {
            setChaptersFor(data, chapterList)
            updateAttr = true
        }
        return metaDataFromName(data, data.name, cloud = CloudStatus.Local.v, allowRetry = 10, updateAttrDate = updateAttr)!!.also { newData ->
            withContext(Dispatchers.IO) {
                db.metaDataTable().update(newData)
                DBChange.update(data.id)
//                val afterData = db.metaDataTable().getDataOf(data.name)
//                if(data.id != newData.id || data.id != afterData?.id) {
//                    logger.error("ID Mismatch")
//                }
            }
        }
    }

    suspend fun repairWithBackup(context: Context, ri: RepairingItem) {
        val chapters = decodeChaptersString(ri.chapters).toList()
        val item = ItemEx(MetaData(
            id = ri.originalId,
            name = ri.name,
            group = 0,
            mark = ri.mark,
            type = if(ri.type=="mp4") 1 else 0,
            date = ri.lastModifiedDate,
            size = ri.size,
            duration = 0L,
            rating = ri.rating,
            cloud = CloudStatus.Cloud.v,
            flag = 0,
            ext = null,
            attr_date = ri.extAttrDate,
            label = ri.label,
            category = ri.category
        ), chapters)
        withContext(Dispatchers.IO) {
            val duration = Converter.analyze(HttpInputFile(context, item.serverUri)).duration
            val newData = MetaData.modifiedEntry(item.data, duration = duration)
            db.metaDataTable().insert(newData)
            db.chapterDataTable().setForOwner(newData.id, chapters.map { ChapterData(0,newData.id, it.position, it.label, it.skip) })
            DBChange.add(newData.id)
        }
    }

    // endregion UPDATING

    // region Cloud Operation

    suspend fun backupToCloud(item: ItemEx):ItemEx {
        if(item.cloud != CloudStatus.Local) {
            logger.warn("not need backup : ${item.name} (${item.cloud})")
            return item
        }
        return withContext(Dispatchers.IO) {
            if (TcClient.uploadToSecureArchive(item)) {
                logger.debug("uploaded: ${item.name}")
                updateCloud(item, CloudStatus.Uploaded)
            } else {
                logger.debug("upload error: ${item.name}")
                item
            }
        }
    }

    suspend fun restoreFromCloud(item: ItemEx):Boolean {
        if(item.cloud != CloudStatus.Cloud) {
            logger.warn("not need restore : ${item.name} (${item.cloud})")
            return true
        }
        return withContext(Dispatchers.IO) {
            if (TcClient.downloadFromSecureArchive(item)) {
                logger.debug("downloaded: ${item.name}")
                updateCloud(item, CloudStatus.Uploaded)
                true
            } else {
                logger.debug("download error: ${item.name}")
                false
            }
        }
    }

    suspend fun recoverFromCloud(item: ItemEx):Boolean {
        return withContext(Dispatchers.IO) {
            if (TcClient.downloadFromSecureArchive(item)) {
                logger.debug("downloaded: ${item.name}")
                updateCloud(updateFile(item, null), CloudStatus.Uploaded)
                true
            } else {
                logger.debug("download error: ${item.name}")
                false
            }
        }
    }

    /**
     * アップロード済みのローカルファイルを削除して、Cloudステータスを Cloud に変更する。
     */
    fun purgeLocalFile(item:ItemEx) {
        if(item.cloud != CloudStatus.Uploaded) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                item.data.file.delete()
                updateCloud(item, CloudStatus.Cloud)
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun purgeAllLocalFiles() {
        withContext(Dispatchers.IO) {
            val items = listEx(PlayerActivity.ListMode.ALL).filter { it.cloud == CloudStatus.Uploaded }
            for(item in items) {
                try {
                    item.data.file.delete()
                    updateCloud(item, CloudStatus.Cloud)
                } catch (_: Throwable) {
                }
            }
        }

    }

    // endregion Operation

    // region Video File Test

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
            register(testItemName)
        }
    }
    // endregion
}