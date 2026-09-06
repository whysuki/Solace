package com.solace.app.data.portfolio

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 作品集：用户自定义的管理单位（一级容器）。 */
@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverWorkId: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 作品：作品集下的一条作品（单视频/单图/多素材组合）。 */
@Entity(
    tableName = "works",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["id"],
            childColumns = ["portfolioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("portfolioId")],
)
data class WorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioId: Long,
    val title: String,
    val coverItemId: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 素材：作品内的媒体文件（私有目录副本引用，与 MediaStore 无关）。 */
@Entity(
    tableName = "work_items",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workId")],
)
data class WorkItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: Long,
    val type: String,
    val sortOrder: Long = 0,
    val displayName: String,
    /** 素材绝对文件路径。 */
    val relativePath: String,
    val mimeType: String = "",
    val size: Long = 0,
    val durationMs: Long = 0,
    val dateTaken: Long = 0,
)
