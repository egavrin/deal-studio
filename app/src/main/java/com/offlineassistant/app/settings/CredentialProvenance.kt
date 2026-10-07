package com.offlineassistant.app.settings

internal enum class CredentialProvenance {
    USER,
    BOOTSTRAP,
    CLEARED;

    companion object {
        // Legacy encrypted values and unknown markers fail closed as user-owned.
        fun resolve(marker: String?, hasEncryptedValue: Boolean): CredentialProvenance? = entries.firstOrNull { it.name == marker }
            ?: if (marker != null || hasEncryptedValue) USER else null

        fun permitsBootstrap(provenance: CredentialProvenance?, refreshExisting: Boolean): Boolean = provenance == null || (provenance == BOOTSTRAP && refreshExisting)
    }
}
