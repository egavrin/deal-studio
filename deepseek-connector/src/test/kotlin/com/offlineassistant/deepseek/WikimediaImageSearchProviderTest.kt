package com.offlineassistant.deepseek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikimediaImageSearchProviderTest {
    @Test
    fun `parses attributed HTTPS image and rejects foreign media host`() {
        val result = WikimediaImageSearchProvider().parse(
            """
            {
              "query": {
                "pages": [
                  {
                    "title": "File:Red Square.jpg",
                    "imageinfo": [{
                      "url": "https://upload.wikimedia.org/original.jpg",
                      "thumburl": "https://upload.wikimedia.org/preview.jpg",
                      "descriptionurl": "https://commons.wikimedia.org/wiki/File:Red_Square.jpg",
                      "mime": "image/jpeg",
                      "extmetadata": {
                        "LicenseShortName": {"value": "CC BY-SA 4.0"}
                      }
                    }]
                  },
                  {
                    "title": "File:Unsafe.jpg",
                    "imageinfo": [{
                      "url": "https://example.com/original.jpg",
                      "thumburl": "https://example.com/preview.jpg",
                      "descriptionurl": "https://commons.wikimedia.org/wiki/File:Unsafe.jpg",
                      "mime": "image/jpeg"
                    }]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals("Red Square", result.single().title)
        assertEquals("CC BY-SA 4.0", result.single().attribution)
        assertTrue(result.single().previewUrl.startsWith("https://upload.wikimedia.org/"))
    }
}
