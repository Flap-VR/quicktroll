JUMPSCARE RESOURCE PACK (you add your own image + sound)

I can't include the FNaF art or audio, so drop your own files in:
  1) Image  -> assets/quicktroll/textures/font/jumpscare.png   (PNG, 16:9 works well, e.g. 1280x720)
  2) Sound  -> assets/quicktroll/sounds/jumpscare.ogg          (OGG Vorbis, mono is best)

Then zip the CONTENTS of this folder (pack.mcmeta must be at the top of the zip),
host it somewhere with a direct download link, and put the link + SHA-1 in server.properties:
  resource-pack=<direct link>
  resource-pack-sha1=<sha1 of the zip>
  require-resource-pack=true   (optional)

Tuning:
  - If the image is too big/small, change "height" and "ascent" in assets/minecraft/font/default.json
    (keep ascent about half of height, or a bit more, to center it).
  - If Minecraft says the pack is incompatible, change "pack_format" in pack.mcmeta
    (75 is my best guess for 1.21.11).
  - Once the sound works, set jumpscare.fallback-sound to "" in plugins/QuickTroll/config.yml.
