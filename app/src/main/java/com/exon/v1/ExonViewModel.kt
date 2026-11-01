package com.exon.v1

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ExonViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    val profile = MutableStateFlow(store.load())
    val nearby = NearbyManager(app) { profile.value }

    fun saveProfile(p: Profile) {
        store.save(p)
        profile.value = p
        nearby.broadcastProfile()
    }

    override fun onCleared() {
        nearby.stop()
    }
}
