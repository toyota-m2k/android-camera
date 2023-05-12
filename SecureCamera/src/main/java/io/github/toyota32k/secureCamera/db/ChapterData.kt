package io.github.toyota32k.secureCamera.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName="t_chapters",
    indices = [Index(value = ["ownerId"])],
    foreignKeys = [ForeignKey(entity=MetaData::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE)]
)
data class ChapterData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val ownerId: Int,
    val position:Long,
    val disabled: Boolean
)

@Dao
interface ChapterDataTable {
    @Query("SELECT * from t_chapters WHERE ownerId = :ownerId ORDER BY position ASC")
    fun getByOwner(ownerId: Int): List<ChapterData>

    @Query("DELETE from t_chapters WHERE ownerId = :ownerId")
    fun deleteByOwner(ownerId: Int):Int

    @Insert
    fun insert(vararg chapter:ChapterData)
}