package com.example.data.repository

/** Shared result of draining the single workspace command queue. */
data class SyncResult(val isSuccess: Boolean, val syncedItemsCount: Int, val message: String)
