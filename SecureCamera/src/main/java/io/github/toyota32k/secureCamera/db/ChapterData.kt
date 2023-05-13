package io.github.toyota32k.secureCamera.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(
    tableName="t_chapters",
    indices = [Index(value = ["ownerId"]), Index(value = ["ownerId", "position"],unique = true)],
    foreignKeys = [ForeignKey(entity=MetaData::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE)]
)
data class ChapterData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val ownerId: Int,
    val position:Long,
    val label: String,
    val disabled: Boolean
)

@Dao
interface ChapterDataTable {
    @Query("SELECT * from t_chapters WHERE ownerId = :ownerId ORDER BY position ASC")
    fun getByOwner(ownerId: Int): List<ChapterData>

    @Query("DELETE from t_chapters WHERE ownerId = :ownerId")
    fun deleteByOwner(ownerId: Int):Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(vararg chapter:ChapterData)

    @Transaction
    fun setForOwner(ownerId:Int, chapters:List<ChapterData>) {
        deleteByOwner(ownerId)
        insert(*chapters.toTypedArray())
    }

}