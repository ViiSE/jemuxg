# Эталонные ROM Atari 2600

Файлы взяты из набора **missingno-vcs-tests** (MIT License):
https://github.com/ajoneil/missingno-vcs-tests

Готовые NTSC-сборки и эталонные PNG находятся в репозитории missingno:
https://github.com/ajoneil/missingno/tree/master/crates/systems/vcs/tests/accuracy/roms/tia-render

## Состав

- `colors_ntsc.a26` — AO/XO-тест палитры: `COLUBK` меняется на каждой
  видимой строке от 0 до 254.
- `colors_ntsc.png` — эталонный кадр Stella, 160×259.
- `score-mode_ntsc.a26` — тест приоритетов и цветов score-режима.
- `score-mode_ntsc.png` — эталонный кадр Stella, 160×259.

PNG содержат больше строк, чем видимое поле jemuxg (192 строки).
`AtariReferenceTests` сравнивает RGB кадр эмулятора с вертикальным срезом
эталона: для `colors` смещение 37 строк, для `score-mode` — 41 строка.
Сравнение точное, без допуска на разницу палитры: эмулятор использует ту же
NTSC-палитру Stella, что и эталон.

## Лицензия

Тестовые ROM и эталонные изображения распространяются по MIT License.
