package com.solace.app.data.portfolio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PortfolioEntity::class, WorkEntity::class, WorkItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PortfolioDatabase : RoomDatabase() {

    abstract fun portfolioDao(): PortfolioDao

    companion object {
        @Volatile
        private var instance: PortfolioDatabase? = null

        fun get(context: Context): PortfolioDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PortfolioDatabase::class.java,
                    "solace_portfolio.db",
                )
                    // 功能尚未发布，历史版本数据无保留价值：版本升级直接重建。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
