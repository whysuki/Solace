package com.solace.app.data.portfolio

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

data class WorkCount(val portfolioId: Long, val count: Int)

@Dao
interface PortfolioDao {

    @Query("SELECT * FROM portfolios ORDER BY sortOrder, createdAt")
    suspend fun portfolios(): List<PortfolioEntity>

    @Query("SELECT * FROM portfolios WHERE id = :id")
    suspend fun portfolio(id: Long): PortfolioEntity?

    @Insert
    suspend fun insertPortfolio(entity: PortfolioEntity): Long

    @Update
    suspend fun updatePortfolio(entity: PortfolioEntity)

    @Query("DELETE FROM portfolios WHERE id = :id")
    suspend fun deletePortfolio(id: Long)

    @Query("SELECT MAX(sortOrder) FROM portfolios")
    suspend fun maxPortfolioSort(): Long?

    @Query("SELECT portfolioId, COUNT(*) AS count FROM works GROUP BY portfolioId")
    suspend fun workCounts(): List<WorkCount>

    @Query("SELECT * FROM works WHERE portfolioId = :portfolioId ORDER BY sortOrder, createdAt")
    suspend fun works(portfolioId: Long): List<WorkEntity>

    @Query("SELECT * FROM works WHERE portfolioId IN (:portfolioIds) ORDER BY sortOrder, createdAt")
    suspend fun worksOf(portfolioIds: List<Long>): List<WorkEntity>

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun work(id: Long): WorkEntity?

    @Insert
    suspend fun insertWork(entity: WorkEntity): Long

    @Update
    suspend fun updateWork(entity: WorkEntity)

    @Query("DELETE FROM works WHERE id = :id")
    suspend fun deleteWork(id: Long)

    @Query("SELECT MAX(sortOrder) FROM works WHERE portfolioId = :portfolioId")
    suspend fun maxWorkSort(portfolioId: Long): Long?

    @Query("SELECT * FROM work_items WHERE workId = :workId ORDER BY sortOrder, id")
    suspend fun items(workId: Long): List<WorkItemEntity>

    @Query("SELECT * FROM work_items WHERE workId IN (SELECT id FROM works WHERE portfolioId = :portfolioId)")
    suspend fun portfolioItems(portfolioId: Long): List<WorkItemEntity>

    @Query("SELECT * FROM work_items WHERE relativePath = :relativePath")
    suspend fun itemsByPath(relativePath: String): List<WorkItemEntity>

    @Insert
    suspend fun insertItem(entity: WorkItemEntity): Long

    @Query("DELETE FROM work_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COUNT(*) FROM work_items WHERE workId = :workId")
    suspend fun itemCount(workId: Long): Int

    @Query("SELECT MAX(sortOrder) FROM work_items WHERE workId = :workId")
    suspend fun maxItemSort(workId: Long): Long?
}
