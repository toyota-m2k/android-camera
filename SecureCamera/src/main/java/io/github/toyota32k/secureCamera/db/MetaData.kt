package io.github.toyota32k.secureCamera.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import io.github.toyota32k.secureCamera.SCApplication
import java.io.File


@Entity(
    tableName = "t_meta",
    indices = [Index(value = ["name"], unique=true)])
data class MetaData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val name:String,
    val group:Int,      // groupId
    val mark:Int,
    val type:Int,      // 0:Image / 1:Video
    val date:Long,
    val size:Long,
    val duration:Long,
    val rating:Int = 0,
    val cloud:Int = 0,      // 0: Local
    val flag: Int = 0,
    val ext: String? = null,
    val attr_date: Long = 0,
) {
    companion object {
        fun newEntry(
            name:String,
            type:Int,      // 0:Image / 1:Video
            date:Long,
            size:Long,
            duration:Long,
        ): MetaData = MetaData(0, name, 0,0,type, date, size,duration,0,0,0,null,0)

        fun modifiedEntry(
            src:MetaData,
            id:Int = src.id,
            name:String = src.name,
            group:Int = src.group,      // groupId
            mark:Int = src.mark,
            type:Int = src.type,      // 0:Image / 1:Video
            date:Long = src.date,
            size:Long = src.size,
            duration:Long = src.duration,
            rating:Int = src.rating,
            cloud:Int = src.cloud,      // 0: Local
            flag: Int = src.flag,
            ext: String? = src.ext,
            attr_date: Long = src.attr_date,
        ):MetaData = MetaData(id,name,group,mark,type,date,size,duration,rating,cloud,flag,ext, attr_date)

    }

    val isVideo:Boolean
        get() = type == 1
    val isPhoto:Boolean
        get() = type == 0
    val file:File
        get() = File(SCApplication.instance.filesDir, name)
}

@Dao
interface MetaDataTable {
    @Query("SELECT * from t_meta ORDER BY date ASC")
    fun getAll(): List<MetaData>

    @Query("SELECT * from t_meta WHERE type = 0 ORDER BY date ASC")
    fun getImages(): List<MetaData>

    @Query("SELECT * from t_meta WHERE type = 1 ORDER BY date ASC")
    fun getVideos(): List<MetaData>

    @Query("SELECT * from t_meta WHERE name = :name")
    fun getDataOf(name:String):MetaData?

    @Query("SELECT * from t_meta WHERE id = :id")
    fun getDataAt(id:Int):MetaData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg metaData:MetaData)

    @Delete
    fun delete(vararg metaData:MetaData)

    @Update
    fun update(vararg metaData:MetaData)
}