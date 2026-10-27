package com.jassemsendi.unpacked.data

import android.content.Context
import androidx.room.Room
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import kotlin.time.Clock

/** Opens, or creates, the library database at [path], an absolute path such as `context.getDatabasePath(..)`. */
public fun Library.Companion.open(
    context: Context,
    path: String,
    clock: Clock = Clock.System,
): Library = open(Room.databaseBuilder<LibraryDatabase>(context, path), clock)

/** A library that lives only as long as the process; the seam tests run against this. */
public fun Library.Companion.inMemory(
    context: Context,
    clock: Clock = Clock.System,
): Library = open(Room.inMemoryDatabaseBuilder<LibraryDatabase>(context), clock)
