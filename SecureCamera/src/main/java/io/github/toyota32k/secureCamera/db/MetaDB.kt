package io.github.toyota32k.secureCamera.db

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.toyota32k.binder.DPDate
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.lib.camera.usecase.ITcUseCase
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.io.HttpInputFile
import io.github.toyota32k.media.lib.processor.Analyzer
import io.github.toyota32k.secureCamera.PlayerActivity
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.ScDef
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.client.TcClient.RepairingItem
import io.github.toyota32k.secureCamera.client.auth.Authentication
import io.github.toyota32k.secureCamera.client.worker.Downloader
import io.github.toyota32k.secureCamera.client.worker.Downloader.Companion.safeDelete
import io.github.toyota32k.secureCamera.client.worker.Uploader
import io.github.toyota32k.secureCamera.db.ItemEx.Companion.decodeChaptersString
import io.github.toyota32k.secureCamera.db.ScDB.Companion.BASE_DB_NAME
import io.github.toyota32k.secureCamera.db.ScDB.Companion.application
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.secureCamera.settings.SlotIndex
import io.github.toyota32k.secureCamera.settings.SlotSettings
import io.github.toyota32k.secureCamera.utils.FileUtil.deleteAllFilesInDir
import io.github.toyota32k.secureCamera.utils.VideoUtil
import io.github.toyota32k.utils.GenericCloseable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ItemEx(val data: MetaData, val slot:Int, val chapterList: List<IChapter>?) {
    val id:Int
        get() = data.id
    val name:String
        get() = data.name
    // ファイルのタイムスタンプ（ファイルを編集すると更新される）
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

    // 動画撮影日時（ファイル名から取得：不変）
    val creationDate:Long by lazy {
        filename2date(name)?.time ?: 0L
    }

    val dpDate: DPDate by lazy {
        filename2dpDate(name) ?: DPDate.Invalid
    }

    val serverUri:String
        get() = "http://${Authentication.activeHostAddress}/slot${slot}/${if(isVideo) "video" else "photo"}?auth=${Authentication.authToken}&o=${Settings.SecureArchive.clientId}&c=${id}"

//    val uri:String
//        get() {
//            return if(cloud.loadFromCloud) {
//                serverUri
//            } else {
//                file.toUri().toString()
//            }
//        }

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

    // SecureArchiveへのアップロード時に、プロパティ情報も送信するための仕掛け。
    // アップロードに multipart/form-data を使ったため、パラメータが１つずつ、part body になってしまって扱いにくいので、
    // 追加情報を１つの json にしてしまうための仕掛け。
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
            return try { ITcUseCase.dateFormatForFilename.parse(dateString) } catch(_:Throwable) { Date() }
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

        fun decodeChaptersString(jsonStr:String): Sequence<IChapter> {
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
interface IKV {
    suspend fun put(key:String, value:String)
    suspend fun get(key:String):String?
}

/**
 * スロットDBクラス
 */
class ScDB(val slotIndex:SlotIndex) : AutoCloseable {
    private var dbInstance:Database? = null
    private val db:Database
        get() = synchronized(this) {
            dbInstance ?: throw IllegalStateException("DB not opened")
        }
    private val dbName:String get() = if (slotIndex == SlotIndex.DEFAULT) "$BASE_DB_NAME.db" else "$SLOT_DB_NAME${slotIndex.index}.db"
    private var refCount = 0
    fun open():Boolean {
        return synchronized(this) {
            if (dbInstance == null) {
                dbInstance = Room.databaseBuilder(application, Database::class.java, dbName).build()
            }
            refCount++
            true
        }
    }

    override fun close() {
        synchronized(this) {
            refCount--
            if(refCount==0) {
                dbInstance?.close()
                dbInstance = null
            }
        }
    }

    /**
     * 強制終了 ... bulse 専用
     */
    fun forceClose() {
        synchronized(this) {
            refCount = 0
            dbInstance?.close()
            dbInstance = null
        }
    }

    val isOpened:Boolean
        get() = synchronized(this) {
            dbInstance != null
        }

    fun checkPoint() {
        synchronized(this) {
            dbInstance?.openHelper?.writableDatabase?.execSQL("PRAGMA wal_checkpoint(full);");
        } ?: return
    }

//    private lateinit var db:Database
    companion object {
        val application:Application get() = SCApplication.instance
        val logger = UtLog("DB", null, ScDB::class.java)
        const val BASE_DB_NAME = "meta"
        const val SLOT_DB_NAME = "slot"

        /**
         * DBファイルをzipファイルに圧縮します。（バックアップ用）
         * zipファイルは、"db_backup_バックアップ日時.zip" という名前で、アプリのcacheディレクトリに保存されます。
         */
        fun zipDbFiles():File? {
            val context = SCApplication.instance.applicationContext

            val dateFormatForFilename = SimpleDateFormat("yyyy.MM.dd-HH.mm.ss",Locale.US)
            val bupName = "db_backup_${dateFormatForFilename.format(Date())}.zip"

            // データベースディレクトリを取得
            val dbDir = context.getDatabasePath("$BASE_DB_NAME.db").parentFile ?: return null
            logger.debug("dbDir=${dbDir.absolutePath}")
            val xxx = dbDir.listFiles()
            if (!xxx.isNullOrEmpty()) {
                for (f in xxx) {
                    logger.debug("file: ${f.name} size=${f.length()} lastModified=${Date(f.lastModified())}")
                }
            }
            return try {
                File(context.cacheDir, bupName).also { zipFile ->
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        val files = dbDir.listFiles { _, name ->
                            name.startsWith(BASE_DB_NAME) || name.startsWith(SLOT_DB_NAME)
                        }
                        files?.forEach { file ->
                            logger.debug("zipping ... file: ${file.name} size=${file.length()} lastModified=${Date(file.lastModified())}")
                            FileInputStream(file).use { fis ->
                                val zipEntry = ZipEntry(file.name)
                                zos.putNextEntry(zipEntry)

                                val buffer = ByteArray(1024)
                                var length: Int
                                while (fis.read(buffer).also { length = it } > 0) {
                                    zos.write(buffer, 0, length)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error(e, "Failed to zip database files")
                return null
            }
        }

        /**
         * zipファイルを解凍し、指定されたディレクトリにファイルを展開します。
         * 未使用 ... バックアップからの復元は現時点で仕様未定・未実装 ... 最悪、デバッガでやるｗ
         *
         * @param zipFile         入力するzipファイル
         */
        fun unzipDbFiles(zipFile: File):Boolean {
            val context = SCApplication.instance.applicationContext
            // データベースディレクトリを取得
            val dbDir = context.getDatabasePath("$BASE_DB_NAME.db").parentFile ?: return false

            val buffer = ByteArray(1024)
            return try {
                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var zipEntry: ZipEntry? = zis.nextEntry
                    while (zipEntry != null) {
                        val newFile = File(dbDir, zipEntry.name)
                        if (zipEntry.isDirectory) {
                            throw IOException("unexpected sub directory: $newFile")
                        } else {
                            if (newFile.exists()) {
                                // 既存のファイルがある場合はバックアップを取る
                                val backup = File(dbDir, zipEntry.name + ".bak")
                                backup.delete()
                                if (!newFile.renameTo(backup)) {
                                    throw IOException("Failed to backup existing file: $newFile")
                                }
                            }
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zipEntry = zis.nextEntry
                    }
                    zis.closeEntry()
                }

                // remove backup files
                dbDir.listFiles { _, name ->
                    name.endsWith(".bak")
                }?.forEach { it.safeDelete() }
                true
            } catch (e: Throwable) {
                logger.error(e)
                // エラーが発生したらバックアップファイルからDBファイルを復元
                dbDir.listFiles { _, name ->
                    name.startsWith(SLOT_DB_NAME) || name.startsWith(BASE_DB_NAME)
                }?.forEach { file ->
                    val backupFile = File(dbDir, file.name + ".bak")
                    if (backupFile.exists()) {
                        try {
                            val oldFile = File(dbDir, file.name + ".old")
                            if (oldFile.exists()) {
                                oldFile.delete()
                            }
                            file.renameTo(oldFile)
                            if (!backupFile.renameTo(file)) {
                                throw IOException("Failed to restore backup file: ${backupFile.name} to ${file.name}")
                            }
                            backupFile.delete()
                            oldFile.delete()
                        } catch(e: Throwable) {
                            logger.error(e, "Failed to restore backup file: ${backupFile.name} to ${file.name}")
                        }
                    }
                }
                false
            }
        }

        fun filesDir(slotIndex:SlotIndex):File {
            return if (slotIndex == SlotIndex.DEFAULT) {
                application.filesDir
            } else {
                File(application.filesDir, "slot${slotIndex.index}")
            }
        }

        fun fileList(slotIndex:SlotIndex):Array<String> {
            if (slotIndex== SlotIndex.DEFAULT) {
                return application.fileList()
            } else {
                val dir = filesDir(slotIndex)
                if (!dir.exists()) {
                    dir.mkdirs()
                    return emptyArray()
                } else {
                    return dir.listFiles()?.filter { it.isFile }?.map { it.name }?.toTypedArray() ?: emptyArray()
                }
            }
        }

    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            //カラム追加
            db.execSQL("ALTER TABLE t_meta ADD attr_date INTEGER default 0 not null")
            db.execSQL("ALTER TABLE t_meta ADD label TEXT")
            db.execSQL("ALTER TABLE t_meta ADD category TEXT")
        }
    }

    /**
     * DBを初期化する
     */
    private fun initialize() {

        val db = Room.databaseBuilder(application, Database::class.java, dbName)
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

    init {
        initialize()
    }

    inner class KVImpl: IKV {
        override suspend fun put(key:String, value:String) {
            withContext(Dispatchers.IO) {
                val e = db.kvTable().getAt(key)
                if (e == null) {
                    db.kvTable().insert(KeyValueEntry(key, value))
                } else {
                    db.kvTable().update(KeyValueEntry(key, value))
                }
            }
        }
        override suspend fun get(key: String):String? {
            return withContext(Dispatchers.IO) {
                db.kvTable().getAt(key)?.value
            }
        }
    }
    val KV:IKV by lazy { KVImpl() }


    private fun filename2type(filename:String): Int? {
        return when {
            filename.startsWith(ScDef.PHOTO_PREFIX)-> 0
            filename.startsWith(ScDef.VIDEO_PREFIX)-> 1
            else -> null
        }
    }

    private suspend fun metaDataFromName(
        org:MetaData?,
        name:String,
        cloud: Int=org?.cloud?:CloudStatus.Local.v,
        allowRetry:Int=0,
        updateAttrDate:Boolean=false
        ):MetaData? {
        return withContext(Dispatchers.IO) {
            val type = filename2type(name) ?: return@withContext null
            val file = File(filesDir, name)
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
        return ItemEx(this, slotIndex.index, chapters)
    }

//    suspend fun toItemEx(meta:MetaData):ItemEx {
//        return meta.toItemEx()
//    }

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
    val filesDir:File
        get() = ScDB.filesDir(slotIndex)
    fun fileOf(name:String):File {
        return File(filesDir, name)
    }
    fun fileOf(meta:MetaData):File {
        return fileOf(meta.name)
    }
    fun fileOf(item:ItemEx):File {
        return fileOf(item.data.name)
    }
    fun urlOf(item:ItemEx):String {
        return if(item.cloud.loadFromCloud) {
            item.serverUri
        } else {
            fileOf(item).toUri().toString()
        }
    }

    private suspend fun makeAll() {
        val isNeedUpdate = {m:MetaData->
            when {
                m.isVideo && m.duration == 0L->true
                CloudStatus.valueOf(m.cloud).isFileInLocal && m.size != fileOf(m).length() -> {
                    logger.info("length mismatch: ${m.name} db=${m.size} file=${fileOf(m).length()}")
                    true
                }
                else -> false
            }
        }
        fun fileList():Array<String> {
            return ScDB.fileList(slotIndex)
        }

        // ダウンロード(repair/restore)中に終了した場合に残るtmpファイルがあれば削除
        val tmpFiles = fileList().filter { it.endsWith(".tmp") }
        if (tmpFiles.isNotEmpty()) {
            logger.warn("deleting tmp files: ${tmpFiles.joinToString(",")}")
            tmpFiles.forEach { fileOf(it).delete() }
        }

        withContext(Dispatchers.IO) {
            val meta = db.metaDataTable()
            fileList().forEach {filename->
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

    suspend fun migrateOne(handle:String, storedEntry: TcClient.StoredFileEntry):MetaData {
        return withContext(Dispatchers.IO) {
            db.runInTransaction<MetaData> {
                // まず挿入
                db.metaDataTable().insert(storedEntry.toMetaData())
                // チャプターを設定
                val data = db.metaDataTable().getDataOf(storedEntry.name) ?: throw IllegalStateException("cannot retrieve inserted data")
                db.chapterDataTable().setForOwner(
                    data.id,
                    storedEntry.toChaptersList()
                        .map { ChapterData(0, data.id, it.position, it.label, it.skip) })
                // 変更イベントを発行
                DBChange.add(data.id)
                data
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
                fileOf(data).delete()
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
        return ItemEx(newData, slotIndex.index, item.chapterList)
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
            ItemEx(updateCloud(item.data, cloud.v), slotIndex.index, item.chapterList)
        } else item
    }

    suspend fun updateCloud(id:Int, cloud:CloudStatus) {
        val item = itemAt(id) ?: return
        if(item.cloud!=cloud.v) {
            updateCloud(item, cloud.v)
        }
    }

    suspend fun updateCloud(id:Int, cloud:CloudStatus, updateFileStatus:Boolean) {
        val item = itemAt(id) ?: return
        if(updateFileStatus) {
            updateCloud(updateFile(item.toItemEx(),null), cloud)
        } else {
            updateCloud(item, cloud.v)
        }
    }

    /**
     * ファイルが編集されたとき、サイズやDurationなどの情報を更新する
     */
    suspend fun updateFile(item:ItemEx, chapterList: List<IChapter>?):ItemEx {
        val newData = updateFile(item.data, chapterList)
        return ItemEx(newData, slotIndex.index, chapterList?:item.chapterList)
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
            duration = ri.duration,
            rating = ri.rating,
            cloud = CloudStatus.Cloud.v,
            flag = 0,
            ext = null,
            attr_date = ri.extAttrDate,
            label = ri.label,
            category = ri.category
        ), slotIndex.index, chapters)
        withContext(Dispatchers.IO) {
            val duration = Analyzer.analyze(HttpInputFile(context, item.serverUri)).duration
            val newData = MetaData.modifiedEntry(item.data, duration = duration)
            db.metaDataTable().insert(newData)
            db.chapterDataTable().setForOwner(newData.id, chapters.map { ChapterData(0,newData.id, it.position, it.label, it.skip) })
            DBChange.add(newData.id)
        }
    }

    // endregion UPDATING

    // region Cloud Operation

    fun backupToCloud(item: ItemEx) {
        if(item.cloud != CloudStatus.Local) {
            logger.warn("not need backup : ${item.name} (${item.cloud})")
            return
        }
        Uploader.upload(SCApplication.instance, item)
    }

    fun restoreFromCloud(item: ItemEx) {
        if(item.cloud != CloudStatus.Cloud) {
            logger.warn("not need restore : ${item.name} (${item.cloud})")
            return
        }
        Downloader.download(SCApplication.instance, item, fileOf(item).absolutePath, false)
    }

    fun recoverFromCloud(item: ItemEx) {
        Downloader.download(SCApplication.instance, item, fileOf(item).absolutePath, true)
    }

    /**
     * アップロード済みのローカルファイルを削除して、Cloudステータスを Cloud に変更する。
     */
    fun purgeLocalFile(item:ItemEx) {
        if(item.cloud != CloudStatus.Uploaded) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fileOf(item.data).delete()
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
                    fileOf(item.data).delete()
                    updateCloud(item, CloudStatus.Cloud)
                } catch (_: Throwable) {
                }
            }
        }

    }

    // endregion Operation

    // region Video File Test

    private val testItemName = "mov-2030.01.01-00:00:00.mp4"

    private suspend fun prepareTestFile():File {
        deleteTestFile()
        return File(filesDir, testItemName)
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

interface IDBSource : Closeable {
    operator fun get(slotIndex: SlotIndex): ScDB
}

object MetaDB {
    val cache = mutableMapOf<SlotIndex, ScDB>()
    operator fun get(slotIndex: SlotIndex): ScDB {
        return synchronized(cache) {
            cache.getOrPut(slotIndex) { ScDB(slotIndex) }
        }.apply { open() }
    }

    /**
     * すべてのスロットDBを開いた状態にする。
     * 戻り値のCloseableをclose()すると、すべてのDBが閉じられる。
     * 一部のスロットだけを利用するなら dbCache() の利用を推奨。
     *
     * @return Closeable
     */
    fun lockDB() : Closeable {
        val list = SlotSettings.activeSlots.map { get(it.index) }
        return GenericCloseable {
            list.forEach { db ->
                db.close()
            }
        }
    }

    /**
     * スロットDBのキャッシュ（IDBSource）の実装
     * 初回の IDBSource#get(index)で、キャッシュに登録され、close() するとすべてのキャッシュが削除される。
     */
    private class DBCache : IDBSource {
        val cache = mutableMapOf<SlotIndex, ScDB>()
        override operator fun get(slotIndex: SlotIndex): ScDB {
            return synchronized(cache) {
                cache.getOrPut(slotIndex) { MetaDB.get(slotIndex) }
            }
        }
        override fun close() {
            synchronized(cache) {
                cache.values.forEach { it.close() }
                cache.clear()
            }
        }
    }

    /**
     * DBキャッシュを取得する。
     * DB操作が終了したら、close()を呼んでキャッシュをクリアすること。
     */
    fun dbCache(): IDBSource {
        return DBCache()
    }


    /**
     * DBを一時的に開いて、処理を実行し、DBを閉じる。
     * slotIndexを省略した場合、現在のスロットが使われる。
     *
     * @param slotIndex スロットインデックス
     * @param fn DBを開いた状態で実行する関数
     * @return fnの戻り値
     */
    inline fun <T> withDB(slotIndex: SlotIndex = SlotSettings.currentSlotIndex, fn: (ScDB) -> T): T {
        val db = get(slotIndex)
        return try {
            db.open()
            fn(db)
        } finally {
            db.close()
        }
    }
    /**
     * DBを一時的に開いて、処理を実行し、DBを閉じる。
     * slotIndexを省略した場合、現在のスロットが使われる。
     *
     * @param slot スロット番号
     * @param fn DBを開いた状態で実行する関数
     * @return fnの戻り値
     */
    inline fun <T> withDB(slot: Int, fn: (ScDB) -> T): T {
        val db = get(SlotIndex.fromIndex(slot))
        return try {
            db.open()
            fn(db)
        } finally {
            db.close()
        }
    }

    interface IVisitor {
        suspend fun visit(
            listMode:PlayerActivity.ListMode=PlayerActivity.ListMode.ALL,
            predicate:(item: ItemEx)->Boolean,
            fn:(db:ScDB, item:ItemEx)->Unit)
    }

    /**
     * すべてのスロット内のデータを巡回するVisitor
     *
     * @param listMode リストモード（ALL/PHOTO/VIDEO）
     * @param predicate 各ItemExに対して実行され、trueを返した場合に fn が実行される。
     * @param fn 各ItemExに対して実行される関数
     */
    private class AllVisitor:  IVisitor {
        override suspend fun visit(listMode: PlayerActivity.ListMode,predicate: (ItemEx) -> Boolean,fn: (ScDB, ItemEx) -> Unit) {
            SlotSettings.activeSlots.forEach { slotInfo ->
                get(slotInfo.index).use { db ->
                    db.listEx(listMode).filter(predicate).forEach { item ->
                        fn(db, item)
                    }
                }
            }
        }
    }
    /**
     * 指定されたスロット内のデータを巡回するVisitor
     *
     * @param slot スロットインデックス
     * @param listMode リストモード（ALL/PHOTO/VIDEO）
     * @param predicate 各ItemExに対して実行され、trueを返した場合に fn が実行される。
     * @param fn 各ItemExに対して実行される関数
     */
    private class SingleVisitor(private val slot: SlotIndex): IVisitor {
        override suspend fun visit(listMode: PlayerActivity.ListMode, predicate: (ItemEx) -> Boolean, fn: (ScDB, ItemEx) -> Unit) {
            get(slot).use { db ->
                db.listEx(listMode).filter(predicate).forEach { item ->
                    fn(db, item)
                }
            }
        }
    }

    /**
     * すべてのスロットを巡回するVisitorを取得する。
     */
    fun allVisitor(): IVisitor {
        return AllVisitor()
    }

    /**
     * 指定されたスロットを巡回するVisitorを取得する。
     * slotIndexを省略した場合、現在のスロットが使われる。
     *
     * @param slot スロットインデックス
     */
    fun singleVisitor(slot:SlotIndex=SlotSettings.currentSlotIndex) : IVisitor {
        return SingleVisitor(slot)
    }

    /**
     * アプリのデータをすべて削除する。
     * 警告: この操作は元に戻せません。
     */
    fun bulse() {
        // メディアファイルを削除
        deleteAllFilesInDir(application.filesDir)
        // 開いているDBがあれば強制的に閉じる
        cache.values.forEach {
            it.forceClose()
        }
        // DBファイルを削除
        val dbDir = application.getDatabasePath("$BASE_DB_NAME.db").parentFile
        if (dbDir!=null) {
            deleteAllFilesInDir(dbDir)
        }
        // スロットの設定をクリア
        SlotSettings.resetAll()
        // アプリの設定をクリア
        Settings.reset()

        // アプリを終了する
        UtImmortalTaskManager.mortalInstanceSource.getOwnerOrNull()?.asActivity()?.finishAndRemoveTask()
    }
}