# Changelog

## Unreleased

- Разделены источники входа: `selection_text` для выделенного текста и `share` для обычного Android Sharesheet.
- Восстановлена Room schema version 2 и миграция 1 → 2 без destructive migration, чтобы обновление сохраняло локальную очередь.
