# Тестовые ROM

## nestest

- `nestest.nes`, `nestest.log` — NES Test by kevtris.
- Источник: https://github.com/christopherpow/nes-test-roms/tree/master/other
- `TestRomTests` запускает `NestestRunner` на полный golden-log (8991 инструкция).

## Blargg PPU tests

- `blargg/palette_ram.nes`
- `blargg/power_up_palette.nes`
- `blargg/sprite_ram.nes`
- `blargg/vram_access.nes`
- `blargg/vbl_clear_time.nes`
- `blargg/test_ppu_read_buffer.nes`

Источник: https://github.com/christopherpow/nes-test-roms/tree/master/blargg_ppu_tests_2005.09.15b
и https://github.com/christopherpow/nes-test-roms/tree/master/ppu_read_buffer

`test_ppu_read_buffer` использует CNROM (mapper 3). Тест считается пройденным,
когда в PRG RAM по адресу `$6000` записан код `0`, а в тексте начиная с
`$6004` есть строка `Passed`.

Для этих тестов ожидается экран с кодом `$01` (PASS). В тестах сравнивается
SHA-256 `framebuffer.idx` с эталоном, снятым после ручной проверки.

## Sprite hit tests

- `sprite-hit/01.basics.nes`
- `sprite-hit/02.alignment.nes`
- `sprite-hit/03.corners.nes`
- `sprite-hit/04.flip.nes`
- `sprite-hit/05.left_clip.nes`

Источник: https://github.com/christopherpow/nes-test-roms/tree/master/sprite_hit_tests_2005.10.05

Все пять экранов должны содержать `PASSED`; в тестах проверяется SHA-256
`framebuffer.idx` после 300 кадров.
