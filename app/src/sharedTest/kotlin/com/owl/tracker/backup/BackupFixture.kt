package com.owl.tracker.backup

/**
 * An encrypted backup made outside the app, with Python's hashlib PBKDF2 and the cryptography
 * package's AES-GCM, following the format documented in [BackupCodec]. Opening it proves the app
 * implements that format, on the JVM (Robolectric) and with Android's own crypto (emulators).
 */
object BackupFixture {
    const val PASSWORD = "Owl-fixture-\u20b950"

    const val JSON = """{"format": "owl-tracker-backup", "version": 1, "createdAt": 1761200000000, "app": "fixture", "encrypted": true, "kdf": {"algorithm": "PBKDF2WithHmacSHA256", "iterations": 600000, "salt": "AAECAwQFBgcICQoLDA0ODw==", "password": "utf8+base64"}, "cipher": {"algorithm": "AES-256-GCM", "iv": "ZGVmZ2hpamtsbW5v", "tagBits": 128}, "payload": "8cCU4aCMwIS2maSR/mO3Axv5XcqcJWf1tQxbrFD2FT8jeSsIfPOPFXWH+aJ64TCBcPJRExzjJb5bZDHi6PvFIzH/oe/npoSxKp4G3yUoY+DlEfMHvQY95SkNsUqYLeMIWx1gUVump/4t/H63US9gy/mLF8HdoBx4nVWX80L5367FZ3uvurHuncMjwKkdvn2rdjVh5yxhCbdjCBx1S9oFc+g2jnbBqu23PkhdK3zUdoNvrq+hXcTX1ghI08N17nbpxQiRTA21oJBWMIzy7dRDJoqxRindbkBWngXTMKkj+ZvYF2SXBG0sXYBPeeFX26VJTzUJ3nyiAJERbWCIF5HmKQhUPUjLH7jXqot8vpyJcjQWiJopcTof5t6IlY77XaFWsIUkXldW/StexgrtJd5COhnQxgBgy+FqQ3DH1V3cDscyl+c/9mryMbRzfMieYjIQumZ72MbsiHtK6upbi6NHGzDpxteZNYIWGJkBwBKWOan8z8S63+g4IiJli9kWr2SGY9TiCt18gpl/lsoNPDL8AEwlnuyZh7AnWy9Hm4zg66LxHpV4XHX/gLebcOXtDQDoRWzvSqkSBz8NlQv983dED9p6utYQeeUEUs6kzdefvxxlx2YQuvgUpKIy364yu8xUxRfiBqHTNyQCA7lGQ0v32M12WiFxXwE9IjqutdAw/+2tOYh/nF9PvMRcFjK4yzZf1VyadLhh3zw8inJtdHgKS0ow76wGgOyeofPThIpEgAz+uTtQl52Cd3n2OdUCUFK+BKbrKFAA4w3t3u164cQjxS/bza/vARX44BNdNftyUy/WLxVHGIA8MzTsWPdMorSSUaG9pLcTgI+TT/rg5qaBpWLi2YF1dkdZ5HrooweOVjy1Rq117O+hXMkNTzYCxiXTrTYOTa+icdjIfYwfBX5L8HreYWAxOmgNjSf2c1v5daJ9gmRMgqKbVqNuMemx/mD/RAGW7ei0L8PNJ4I3HAVWwcEqoJm/BRqzHn+dM7L3cSiv3XqZbUwiL18Wm10eb4KGi1ZZZu6qyb+bgOI+Iaui/zmykh7TeK/DSzYq21ETjpCwyufgcCiQpqpGb0jx/1hLu4pwmUib471zjb6mPCTN3QW/JbbBEM7cAPdaHh0Wyco1dcjxSeWsDyKlAkbIKyUSk7KY5zIC59pmdlB9QFmoAOyAoT/L1JLxeLzq"}"""
}
