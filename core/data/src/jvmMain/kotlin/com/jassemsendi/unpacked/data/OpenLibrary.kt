package com.jassemsendi.unpacked.data

import androidx.room.Room
import com.jassemsendi.unpacked.data.db.LibraryDatabase
import kotlin.time.Clock

/** Opens, or creates, the library database at [path], an absolute file path. */
public fun Library.Companion.open(
    path: String,
    clock: Clock = Clock.System,
): Library = open(Room.databaseBuilder<LibraryDatabase>(name = path), clock)

/** A library that lives only as long as the process; the seam tests run against this. */
public fun Library.Companion.inMemory(clock: Clock = Clock.System): Library =
    open(Room.inMemoryDatabaseBuilder<LibraryDatabase>(), clock)
