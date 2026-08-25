package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ChatMessage
import com.yukisoffd.lyracode.data.MediaGenerationKind
import java.util.Locale

/**
 * Media-generation models consume prompt text as generation material rather than
 * agent instructions. Keep this deliberately narrower than a generic modality
 * check so vision, video-understanding, transcription, and ordinary multimodal
 * chat models continue to receive the normal Lyra agent protocol.
 */
internal fun mediaGenerationKindForModel(model: String): MediaGenerationKind? {
    val normalized = model
        .trim()
        .lowercase(Locale.US)
        .replace('_', '-')
        .replace(' ', '-')
    if (normalized.isBlank()) return null

    return when {
        VIDEO_GENERATION_MARKERS.any(normalized::contains) ||
            VIDEO_GENERATION_FAMILIES.any { normalized.containsModelFamily(it) } -> MediaGenerationKind.VIDEO

        MUSIC_GENERATION_MARKERS.any(normalized::contains) ||
            MUSIC_GENERATION_FAMILIES.any { normalized.containsModelFamily(it) } -> MediaGenerationKind.MUSIC

        AUDIO_GENERATION_MARKERS.any(normalized::contains) ||
            AUDIO_GENERATION_FAMILIES.any { normalized.containsModelFamily(it) } -> MediaGenerationKind.AUDIO

        IMAGE_GENERATION_MARKERS.any(normalized::contains) ||
            IMAGE_GENERATION_FAMILIES.any { normalized.containsModelFamily(it) } -> MediaGenerationKind.IMAGE

        else -> null
    }
}

internal fun isMediaGenerationModel(model: String): Boolean = mediaGenerationKindForModel(model) != null

internal fun mediaGenerationToolName(kind: MediaGenerationKind): String = when (kind) {
    MediaGenerationKind.IMAGE -> "generate_image"
    MediaGenerationKind.VIDEO -> "generate_video"
    MediaGenerationKind.MUSIC -> "generate_music"
    MediaGenerationKind.AUDIO -> "generate_audio"
}

internal fun mediaGenerationKindForTool(toolName: String): MediaGenerationKind? =
    MediaGenerationKind.entries.firstOrNull { mediaGenerationToolName(it) == toolName }

internal fun selectMediaGenerationInput(
    messages: List<ChatMessage>,
    excludeMessageId: Long,
): List<ChatMessage> = listOfNotNull(
    messages.asReversed().firstOrNull {
        it.id != excludeMessageId && it.role == "user"
    },
)

private fun String.containsModelFamily(family: String): Boolean {
    var start = indexOf(family)
    while (start >= 0) {
        val before = getOrNull(start - 1)
        val after = getOrNull(start + family.length)
        if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetter())) {
            return true
        }
        start = indexOf(family, start + 1)
    }
    return false
}

private val IMAGE_GENERATION_MARKERS = listOf(
    "text-to-image",
    "text2image",
    "image-generation",
    "image-generator",
    "gpt-image",
    "dall-e",
    "stable-diffusion",
    "qwen-image",
    "seedream",
    "hunyuan-image",
    "gemini-2.5-flash-image",
    "gemini-3-pro-image",
    "gemini-3.1-flash-image",
)

private val IMAGE_GENERATION_FAMILIES = listOf(
    "dalle",
    "imagen",
    "flux",
    "sdxl",
    "kolors",
    "ideogram",
    "recraft",
    "cogview",
)

private val VIDEO_GENERATION_MARKERS = listOf(
    "text-to-video",
    "text2video",
    "image-to-video",
    "image2video",
    "video-generation",
    "video-generator",
    "minimax-video",
    "hunyuan-video",
    "cogvideo",
    "seedance",
    "hailuo",
)

private val VIDEO_GENERATION_FAMILIES = listOf(
    "sora",
    "veo",
    "kling",
    "runway",
    "pixverse",
    "wan2",
    "wan-2",
)

private val AUDIO_GENERATION_MARKERS = listOf(
    "text-to-audio",
    "text2audio",
    "text-to-speech",
    "audio-generation",
    "audio-generator",
    "speech-generation",
    "stable-audio",
    "gpt-audio",
    "gpt-4o-audio",
    "qwen-tts",
    "fish-speech",
    "cosyvoice",
    "melotts",
)

private val AUDIO_GENERATION_FAMILIES = listOf(
    "tts",
    "kokoro",
    "elevenlabs",
)

private val MUSIC_GENERATION_MARKERS = listOf(
    "text-to-music",
    "music-generation",
    "music-generator",
)

private val MUSIC_GENERATION_FAMILIES = listOf(
    "suno",
    "udio",
    "musicgen",
)
