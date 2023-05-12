package io.github.toyota32k.secureCamera.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
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
) {
    val isVideo:Boolean
        get() = type == 1
    val isPhoto:Boolean
        get() = type == 0
    fun file(context:Context):File
        = File(context.filesDir, name)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg metaData:MetaData)

    @Delete
    fun delete(vararg metaData:MetaData)

    @Update
    fun update(vararg metaData:MetaData)
}