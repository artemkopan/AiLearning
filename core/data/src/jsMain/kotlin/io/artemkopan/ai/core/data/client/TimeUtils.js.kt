package io.artemkopan.ai.core.data.client

import kotlin.js.Date

internal actual fun currentTimeMillis(): Long = Date.now().toLong()
