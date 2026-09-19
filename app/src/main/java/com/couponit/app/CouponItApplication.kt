package com.couponit.app

import android.app.Application
import androidx.room.Room
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase
import com.couponit.app.data.local.MIGRATION_1_2

class CouponItApplication : Application() {
    val database: CouponDatabase by lazy {
        Room.databaseBuilder(this, CouponDatabase::class.java, "couponit.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    val repository: CouponRepository by lazy { CouponRepository(database.couponDao()) }
}
