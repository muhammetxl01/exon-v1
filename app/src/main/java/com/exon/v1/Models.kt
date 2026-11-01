package com.exon.v1

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

data class Profile(
    val name: String,
    val bio: String,
    val icon: Int,
    val color: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", "profile")
        .put("name", name)
        .put("bio", bio)
        .put("icon", icon)
        .put("color", color)

    companion object {
        fun from(o: JSONObject) = Profile(
            name = o.optString("name", "?"),
            bio = o.optString("bio", ""),
            icon = o.optInt("icon", 0),
            color = o.optInt("color", 0)
        )
    }
}

data class Peer(
    val id: String,
    val name: String,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val profile: Profile? = null
)

data class Msg(val text: String, val mine: Boolean, val time: Long)

class ProfileStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("exon_profile", Context.MODE_PRIVATE)

    fun load(): Profile {
        val name = prefs.getString("name", null)
            ?: "Exon" + Random.nextInt(100, 999)
        return Profile(
            name = name,
            bio = prefs.getString("bio", "Exon v1 kullanıyorum ⚡") ?: "",
            icon = prefs.getInt("icon", 1),
            color = prefs.getInt("color", 0)
        )
    }

    fun save(p: Profile) {
        prefs.edit()
            .putString("name", p.name)
            .putString("bio", p.bio)
            .putInt("icon", p.icon)
            .putInt("color", p.color)
            .apply()
    }
}
