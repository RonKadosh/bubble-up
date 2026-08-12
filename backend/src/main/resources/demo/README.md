# Demo assets

The interactive demo deliberately keeps **no binary assets in the repo**:

- **Sample files** (Syllabus.pdf, Welcome.txt, lecture notes, past exam, cheat
  sheet, a 1x1 diagram PNG) are **generated in code** at boot by `DemoAssetRegistry`
  (a tiny valid one-page PDF per document + a plaintext note + a 1px PNG). The bytes
  are held as in-memory templates; each per-session `GroupFile` gets a **fresh upload**
  (a distinct `fileId`), because `group_files.file_id` is unique and can't be shared
  across rows. Nothing to commit.
- **Avatars** are not bundled either: the app draws a generated initials-avatar when
  a user has no `avatarFileId`, which is what the demo uses.

This folder exists only as an **optional** drop-in: if you later want real photos,
place them here and `DemoAssetRegistry.avatar(...)` will pick them up with no code
change:

```
demo/avatars/
├── people/<persona-slug>.jpg     ← optional demo-person avatar
└── experts/<expert-slug>.jpg     ← optional demo-expert avatar
```

(Only loaded when `app.demo.enabled=true`.)
