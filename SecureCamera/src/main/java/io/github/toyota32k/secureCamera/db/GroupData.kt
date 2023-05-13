package io.github.toyota32k.secureCamera.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName="t_groups")
data class GroupData(
    @PrimaryKey(autoGenerate = true)
    val id:Int,
    val name:String,
    val flag:Int,
)

@Dao
interface GroupDataTable {
    @Query("SELECT * from t_groups")
    fun getAll(): List<GroupData>

    @Query("SELECT * from t_groups WHERE id=:id")
    fun getAt(id:Int):GroupData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg groupData: GroupData)

    @Delete
    fun delete(vararg groupData: GroupData)

    @Update
    fun update(vararg groupData: GroupData)

}