package com.distrigo.app.data.geo

import android.content.Context
import com.distrigo.app.data.model.Commune
import com.distrigo.app.data.model.Wilaya
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * مصدر بيانات جغرافي ثابت (ولايات الجزائر وبلدياتها)، يُحمَّل مرة واحدة من
 * assets/ إلى الذاكرة. لا حاجة لجدول Room: بيانات مرجعية للقراءة فقط،
 * وwilaya_name/commune_name مخزّنة كنص حر denormalized في الكيانات الحالية.
 */
object GeoRepository {

    private var wilayas: List<Wilaya> = emptyList()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        val appContext = context.applicationContext
        appContext.assets.open("wilayas.json").bufferedReader().use { reader ->
            val type = object : TypeToken<List<Wilaya>>() {}.type
            wilayas = Gson().fromJson(reader, type)
        }
        isLoaded = true
    }

    fun getWilayas(): List<Wilaya> = wilayas

    fun getCommunes(wilayaCode: Int): List<Commune> =
        wilayas.find { it.wilayaCode == wilayaCode }?.communes ?: emptyList()

    fun findWilayaByFrName(name: String?): Wilaya? =
        name?.let { n -> wilayas.find { it.nameFr.equals(n, ignoreCase = true) } }

    fun findCommuneByFrName(wilayaCode: Int, name: String?): Commune? =
        name?.let { n -> getCommunes(wilayaCode).find { it.nameFr.equals(n, ignoreCase = true) } }
}