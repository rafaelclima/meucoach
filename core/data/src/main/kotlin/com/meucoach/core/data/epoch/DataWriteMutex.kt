package com.meucoach.core.data.epoch

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide write lock shared by reset and the asynchronous writers that
 * could recreate data (Health Connect and imports). The epoch is the durable
 * barrier; this mutex closes the window between the epoch check and the write.
 */
class DataWriteMutex {
    val mutex = Mutex()
}
