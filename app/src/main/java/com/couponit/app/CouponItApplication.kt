package com.couponit.app

import android.app.Application
import androidx.room.Room
import com.couponit.app.data.CouponRepository
import com.couponit.app.data.local.CouponDatabase

class CouponItApplication : Application() {
    val database: CouponDatabase by lazy {
        Room.databaseBuilder(this, CouponDatabase::class.java, "couponit.db").build()
    }
    val repository: CouponRepository by lazy { CouponRepository(database.couponDao()) }
}
